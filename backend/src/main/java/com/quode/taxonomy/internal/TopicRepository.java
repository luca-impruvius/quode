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
          )
          select exists (select 1 from up where id = :topicId)
          """)
  boolean isSelfOrAncestor(UUID topicId, UUID startId);
}
