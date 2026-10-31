package com.quode.question.internal;

import jakarta.persistence.LockModeType;
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
}
