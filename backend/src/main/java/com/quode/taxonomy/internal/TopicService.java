package com.quode.taxonomy.internal;

import com.quode.shared.ConflictException;
import com.quode.shared.NotFoundException;
import com.quode.taxonomy.TopicUsage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The topic tree rules (docs/domain.md §4, docs/specs/M1-topics.md). Every method is per owner. */
@Service
class TopicService {

  static final String NOT_FOUND = "Topic not found.";
  static final String DUPLICATE_NAME = "A topic with this name already exists here.";
  static final String CYCLE = "A topic can't move under itself or its sub-topics.";
  static final String HAS_CHILDREN = "Move or delete its sub-topics first.";
  static final String IN_USE = "Questions use this topic.";
  static final String NOT_EMPTY = "Starter topics are only for an empty topic list.";
  static final String BAD_NAME = "A topic name needs 1 to 60 characters.";

  private static final int MAX_NAME = 60;

  private static final Comparator<Topic> BY_NAME =
      Comparator.comparing((Topic t) -> t.getName().toLowerCase(Locale.ROOT))
          .thenComparing(Topic::getName);

  private final TopicRepository topics;
  private final ObjectProvider<TopicUsage> usage;
  private final JdbcTemplate jdbc;

  TopicService(TopicRepository topics, ObjectProvider<TopicUsage> usage, JdbcTemplate jdbc) {
    this.topics = topics;
    this.usage = usage;
    this.jdbc = jdbc;
  }

  /** The owner's tree in display order: parents before children, siblings A–Z ignoring case. */
  @Transactional(readOnly = true)
  List<TopicView> list(UUID ownerId) {
    List<Topic> all = topics.findAllByOwnerId(ownerId);
    Map<UUID, Long> counts = countQuestions(ownerId);
    Map<UUID, List<Topic>> children = new HashMap<>();
    all.forEach(t -> children.computeIfAbsent(t.getParentId(), k -> new ArrayList<>()).add(t));
    List<TopicView> ordered = new ArrayList<>(all.size());
    addInOrder(null, children, counts, ordered);
    return ordered;
  }

  @Transactional
  TopicView create(UUID ownerId, String name, UUID parentId) {
    lockTree(ownerId);
    String clean = cleanName(name);
    if (parentId != null) {
      find(ownerId, parentId);
    }
    requireUniqueName(ownerId, parentId, clean, null);
    Topic topic = new Topic(ownerId, parentId, clean);
    return view(saveUnique(topic), 0);
  }

  @Transactional
  TopicView rename(UUID ownerId, UUID id, String name) {
    lockTree(ownerId);
    Topic topic = find(ownerId, id);
    String clean = cleanName(name);
    requireUniqueName(ownerId, topic.getParentId(), clean, id);
    topic.rename(clean);
    return view(saveUnique(topic), countQuestions(ownerId).getOrDefault(id, 0L));
  }

  @Transactional
  TopicView move(UUID ownerId, UUID id, UUID parentId) {
    // The lock comes first: two cross moves must see each other's result, never interleave.
    lockTree(ownerId);
    Topic topic = find(ownerId, id);
    if (parentId != null) {
      find(ownerId, parentId);
      if (topics.isSelfOrAncestor(id, parentId)) {
        throw new ConflictException(CYCLE);
      }
    }
    requireUniqueName(ownerId, parentId, topic.getName(), id);
    topic.moveTo(parentId);
    return view(saveUnique(topic), countQuestions(ownerId).getOrDefault(id, 0L));
  }

  @Transactional
  void delete(UUID ownerId, UUID id) {
    lockTree(ownerId);
    Topic topic = find(ownerId, id);
    if (topics.existsByParentId(id)) {
      throw new ConflictException(HAS_CHILDREN);
    }
    if (usage.stream().anyMatch(u -> u.isUsed(id))) {
      throw new ConflictException(IN_USE);
    }
    topics.delete(topic);
  }

  @Transactional
  List<TopicView> addStarterTopics(UUID ownerId) {
    lockTree(ownerId);
    if (topics.existsByOwnerId(ownerId)) {
      throw new ConflictException(NOT_EMPTY);
    }
    StarterTopics.TREE.forEach(node -> createStarter(ownerId, null, node));
    topics.flush();
    return list(ownerId);
  }

  private void createStarter(UUID ownerId, UUID parentId, StarterTopics.Node node) {
    Topic topic = topics.save(new Topic(ownerId, parentId, node.name()));
    node.children().forEach(child -> createStarter(ownerId, topic.getId(), child));
  }

  private Topic find(UUID ownerId, UUID id) {
    return topics
        .findByIdAndOwnerId(id, ownerId)
        .orElseThrow(() -> new NotFoundException(NOT_FOUND));
  }

  // Serializes one owner's tree writes until commit, so concurrent requests can't race the rules.
  private void lockTree(UUID ownerId) {
    jdbc.queryForObject(
        "select 1 from pg_advisory_xact_lock(hashtextextended(?, 0))",
        Integer.class,
        "topic:" + ownerId);
  }

  // Checked before the entity changes: the query would otherwise flush the change and hit the
  // index.
  private void requireUniqueName(UUID ownerId, UUID parentId, String name, UUID excludeId) {
    if (topics.existsSibling(ownerId, parentId, name, excludeId)) {
      throw new ConflictException(DUPLICATE_NAME);
    }
  }

  // The unique index has the last word on sibling names; its violation is the same 409.
  private Topic saveUnique(Topic topic) {
    try {
      return topics.saveAndFlush(topic);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException(DUPLICATE_NAME);
    }
  }

  private static String cleanName(String name) {
    String clean = name == null ? "" : name.strip();
    if (clean.isEmpty() || clean.length() > MAX_NAME) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, BAD_NAME);
    }
    return clean;
  }

  // Distinct questions per topic subtree: a question tagged in two places counts once higher up.
  private Map<UUID, Long> countQuestions(UUID ownerId) {
    Map<UUID, Set<UUID>> byTopic = new HashMap<>();
    usage.forEach(
        u ->
            u.activeQuestionIdsByTopic(ownerId)
                .forEach((t, qs) -> byTopic.computeIfAbsent(t, k -> new HashSet<>()).addAll(qs)));
    if (byTopic.isEmpty()) {
      return Map.of();
    }
    Map<UUID, Set<UUID>> perSubtree = new HashMap<>();
    for (TopicRepository.SubtreeRow row : topics.findSubtrees(ownerId)) {
      Set<UUID> questions = byTopic.get(row.getMemberId());
      if (questions != null) {
        perSubtree.computeIfAbsent(row.getTopicId(), k -> new HashSet<>()).addAll(questions);
      }
    }
    Map<UUID, Long> counts = new HashMap<>();
    perSubtree.forEach((t, qs) -> counts.put(t, (long) qs.size()));
    return counts;
  }

  private static void addInOrder(
      UUID parentId, Map<UUID, List<Topic>> children, Map<UUID, Long> counts, List<TopicView> out) {
    List<Topic> siblings = children.getOrDefault(parentId, List.of());
    siblings.stream()
        .sorted(BY_NAME)
        .forEach(
            t -> {
              out.add(view(t, counts.getOrDefault(t.getId(), 0L)));
              addInOrder(t.getId(), children, counts, out);
            });
  }

  private static TopicView view(Topic t, long count) {
    return new TopicView(t.getId(), t.getParentId(), t.getName(), count);
  }
}
