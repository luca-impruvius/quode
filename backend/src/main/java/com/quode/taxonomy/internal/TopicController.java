package com.quode.taxonomy.internal;

import com.quode.user.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The current user's topic tree. Rules: docs/specs/M1-topics.md. */
@RestController
@RequestMapping("/v1/topics")
class TopicController {

  record CreateTopic(String name, UUID parentId) {}

  record RenameTopic(String name) {}

  /** {@code parentId} null moves the topic to the top level. */
  record MoveTopic(UUID parentId) {}

  private final TopicService topics;
  private final CurrentUser currentUser;

  TopicController(TopicService topics, CurrentUser currentUser) {
    this.topics = topics;
    this.currentUser = currentUser;
  }

  @GetMapping
  List<TopicView> list() {
    return topics.list(currentUser.id());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  TopicView create(@RequestBody CreateTopic request) {
    return topics.create(currentUser.id(), request.name(), request.parentId());
  }

  @PutMapping("/{id}/name")
  TopicView rename(@PathVariable UUID id, @RequestBody RenameTopic request) {
    return topics.rename(currentUser.id(), id, request.name());
  }

  @PutMapping("/{id}/parent")
  TopicView move(@PathVariable UUID id, @RequestBody MoveTopic request) {
    return topics.move(currentUser.id(), id, request.parentId());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID id) {
    topics.delete(currentUser.id(), id);
  }

  @PostMapping("/starter")
  @ResponseStatus(HttpStatus.CREATED)
  List<TopicView> addStarterTopics() {
    return topics.addStarterTopics(currentUser.id());
  }
}
