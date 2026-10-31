package com.quode.taxonomy.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TopicRepository extends JpaRepository<Topic, UUID> {

  /** Every lookup by id goes through the owner, so another user's topic is simply not found. */
  Optional<Topic> findByIdAndOwnerId(UUID id, UUID ownerId);

  List<Topic> findAllByOwnerId(UUID ownerId);

  boolean existsByOwnerId(UUID ownerId);

  boolean existsByParentId(UUID parentId);

  /**
   * Whether a sibling (same owner and parent) already has this name, ignoring case. {@code
   * excludeId} skips the topic being renamed or moved; null when creating.
   */
  @Query(
      """
      select count(t) > 0 from Topic t
       where t.ownerId = :ownerId
         and lower(t.name) = lower(:name)
         and (t.parentId = :parentId or (:parentId is null and t.parentId is null))
         and (:excludeId is null or t.id <> :excludeId)
      """)
  boolean existsSibling(UUID ownerId, UUID parentId, String name, UUID excludeId);

  /** A topic with its path from the top level ("Languages › Java"). */
  interface PathRow {
    UUID getId();

    String getName();

    String getPath();
  }

  /**
   * The owner's topics among {@code ids}, with their paths; others' and unknown ids are skipped.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          with recursive up (start_id, id, parent_id, path) as (
            select id, id, parent_id, name::text from topic
             where owner_id = :ownerId and id = any (:ids)
            union all
            select up.start_id, t.id, t.parent_id, t.name || ' › ' || up.path
              from up join topic t on t.id = up.parent_id
          ) cycle id set looped using visited
          select s.id as id, s.name as name, up.path as path
            from up join topic s on s.id = up.start_id
           where up.parent_id is null
          """)
  List<PathRow> findPaths(UUID ownerId, UUID[] ids);

  /** One row per (topic, descendant-or-self) pair of this owner's tree. */
  interface SubtreeRow {
    UUID getTopicId();

    UUID getMemberId();
  }

  @Query(
      nativeQuery = true,
      value =
          """
          with recursive subtree (topic_id, member_id) as (
            select id, id from topic where owner_id = :ownerId
            union all
            select s.topic_id, c.id from subtree s join topic c on c.parent_id = s.member_id
          )
          select topic_id as topicId, member_id as memberId from subtree
          """)
  List<SubtreeRow> findSubtrees(UUID ownerId);

  /**
   * Whether {@code topicId} is {@code startId} or one of its ancestors: a move there is a cycle.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          with recursive up (id, parent_id) as (
            select id, parent_id from topic where id = :startId
            union all
            select t.id, t.parent_id from topic t join up on t.id = up.parent_id
          ) cycle id set looped using path
          select exists (select 1 from up where id = :topicId)
          """)
  boolean isSelfOrAncestor(UUID topicId, UUID startId);
}
