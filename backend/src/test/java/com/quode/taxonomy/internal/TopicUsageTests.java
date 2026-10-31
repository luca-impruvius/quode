package com.quode.taxonomy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quode.TestcontainersConfiguration;
import com.quode.shared.ConflictException;
import com.quode.taxonomy.TopicUsage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts and the delete guard, with a stand-in for the question module (M1-04 brings the real one).
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TopicUsageTests.FakeQuestions.class})
@Transactional
class TopicUsageTests {

  @Autowired private TopicService topics;

  @Autowired private FakeQuestions.Usage usage;

  @Autowired private JdbcTemplate jdbc;

  private UUID owner;

  @BeforeEach
  void setUp() {
    usage.active.clear();
    usage.used.clear();
    owner =
        jdbc.queryForObject(
            "insert into app_user (email, google_sub, display_name)"
                + " values ('me@example.com', 'sub-me', 'Me') returning id",
            UUID.class);
  }

  @Test
  void countsIncludeSubTopicsAndCountEachQuestionOnce() {
    UUID java = topics.create(owner, "Java", null).id();
    UUID collections = topics.create(owner, "Collections", java).id();
    UUID streams = topics.create(owner, "Streams", java).id();
    UUID gradle = topics.create(owner, "Gradle", null).id();
    UUID q1 = UUID.randomUUID();
    UUID q2 = UUID.randomUUID();
    UUID q3 = UUID.randomUUID();
    // q1 is tagged both Java and Collections: it counts once under Java.
    usage.active.put(java, Set.of(q1));
    usage.active.put(collections, Set.of(q1, q2));
    usage.active.put(streams, Set.of(q3));

    Map<UUID, Long> counts = new HashMap<>();
    topics.list(owner).forEach(topic -> counts.put(topic.id(), topic.questionCount()));

    assertThat(counts)
        .containsEntry(java, 3L)
        .containsEntry(collections, 2L)
        .containsEntry(streams, 1L)
        .containsEntry(gradle, 0L);
  }

  @Test
  void aTopicUsedByAnyQuestionCantBeDeleted() {
    UUID java = topics.create(owner, "Java", null).id();
    // Used only by an archived question: no count, but still referenced.
    usage.used.add(java);

    assertThatThrownBy(() -> topics.delete(owner, java))
        .isInstanceOf(ConflictException.class)
        .hasMessage(TopicService.IN_USE);
    assertThat(topics.list(owner))
        .singleElement()
        .extracting(TopicView::questionCount)
        .isEqualTo(0L);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FakeQuestions {

    static class Usage implements TopicUsage {
      final Map<UUID, Set<UUID>> active = new HashMap<>();
      final Set<UUID> used = new HashSet<>();

      @Override
      public Map<UUID, Set<UUID>> activeQuestionIdsByTopic(UUID ownerId) {
        return active;
      }

      @Override
      public boolean isUsed(UUID topicId) {
        return used.contains(topicId) || active.containsKey(topicId);
      }
    }

    @Bean
    Usage fakeTopicUsage() {
      return new Usage();
    }
  }
}
