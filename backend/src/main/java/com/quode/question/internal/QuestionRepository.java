package com.quode.question.internal;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface QuestionRepository extends JpaRepository<Question, UUID> {

  /** Every lookup by id goes through the owner, so another user's question is simply not found. */
  Optional<Question> findByIdAndOwnerId(UUID id, UUID ownerId);

  /**
   * The same lookup, locking the row until commit (select … for update): concurrent edits of one
   * question run one after the other, so the second sees the new version and gets a clean 409.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select q from Question q where q.id = :id and q.ownerId = :ownerId")
  Optional<Question> findForUpdate(UUID id, UUID ownerId);

  /** The owner's questions among {@code ids}, locked until commit (fill answers on import). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select q from Question q where q.ownerId = :ownerId and q.id in :ids")
  List<Question> findAllForUpdate(Collection<UUID> ids, UUID ownerId);

  /** Every prompt of the owner, archived and draft included, for duplicate checks on import. */
  @Query("select q.promptMd from Question q where q.ownerId = :ownerId")
  List<String> findPrompts(UUID ownerId);

  /** The owner's non-archived questions of one status, oldest first (export). */
  @Query(
      "select q from Question q where q.ownerId = :ownerId and q.status = :status"
          + " and q.archivedAt is null order by q.createdAt, q.id")
  List<Question> findForExport(UUID ownerId, QuestionStatus status);
}
