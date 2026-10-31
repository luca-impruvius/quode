package com.quode.taxonomy.internal;

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

// Not @Transactional: the two moves must run in their own, real transactions.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TopicConcurrencyTests {

  @Autowired private TopicService topics;

  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from topic");
    jdbc.update("delete from app_user where google_sub = 'sub-race'");
  }

  @Test
  void twoCrossMovesCantBuildACycleTogether() throws Exception {
    UUID owner =
        jdbc.queryForObject(
            "insert into app_user (email, google_sub, display_name)"
                + " values ('race@example.com', 'sub-race', 'Race') returning id",
            UUID.class);
    UUID a = topics.create(owner, "A", null).id();
    UUID b = topics.create(owner, "B", null).id();
    CountDownLatch start = new CountDownLatch(1);

    CompletableFuture<Boolean> aUnderB = moveAfter(start, owner, a, b);
    CompletableFuture<Boolean> bUnderA = moveAfter(start, owner, b, a);
    start.countDown();

    assertThat(List.of(aUnderB.get(), bUnderA.get())).containsExactlyInAnyOrder(true, false);
    Integer topLevel =
        jdbc.queryForObject("select count(*) from topic where parent_id is null", Integer.class);
    assertThat(topLevel).isEqualTo(1);
  }

  private CompletableFuture<Boolean> moveAfter(
      CountDownLatch start, UUID owner, UUID topic, UUID parent) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            start.await();
            topics.move(owner, topic, parent);
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
