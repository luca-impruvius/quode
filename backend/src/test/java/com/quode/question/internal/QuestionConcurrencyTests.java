package com.quode.question.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.quode.TestcontainersConfiguration;
import com.quode.shared.ConflictException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

// Not @Transactional: the two saves must run in their own, real transactions.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class QuestionConcurrencyTests {

  @Autowired private QuestionService questions;

  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from question");
    jdbc.update("delete from topic");
    jdbc.update("delete from app_user where google_sub = 'sub-race'");
  }

  @Test
  void twoSavesOfTheSameVersionCantBothWin() throws Exception {
    UUID owner =
        jdbc.queryForObject(
            "insert into app_user (email, google_sub, display_name)"
                + " values ('race@example.com', 'sub-race', 'Race') returning id",
            UUID.class);
    UUID topic =
        jdbc.queryForObject(
            "insert into topic (owner_id, name) values (?, 'Java') returning id",
            UUID.class,
            owner);
    UUID id = questions.create(owner, input("Original", topic, null)).id();
    CountDownLatch start = new CountDownLatch(1);

    CompletableFuture<Boolean> first = saveAfter(start, owner, id, input("First", topic, 0L));
    CompletableFuture<Boolean> second = saveAfter(start, owner, id, input("Second", topic, 0L));
    start.countDown();

    assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
    Long version = jdbc.queryForObject("select version from question where id = ?", Long.class, id);
    assertThat(version).isEqualTo(1L);
  }

  private static QuestionInput input(String prompt, UUID topic, Long version) {
    return new QuestionInput(
        QuestionType.FLASHCARD,
        prompt,
        "Answer",
        null,
        2,
        null,
        List.of(topic),
        topic,
        List.of(),
        version);
  }

  private CompletableFuture<Boolean> saveAfter(
      CountDownLatch start, UUID owner, UUID id, QuestionInput input) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            start.await();
            questions.update(owner, id, input);
            return true;
          } catch (ConflictException e) {
            return false;
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }
        });
  }
}
