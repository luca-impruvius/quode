package com.quode.taxonomy.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CurrentTimestamp;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

/** A node in one owner's topic tree; the parent is a plain id (adjacency list, ADR-0003). */
@Entity
@Table(name = "topic")
class Topic {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "owner_id", nullable = false, updatable = false)
  private UUID ownerId;

  @Column(name = "parent_id")
  private UUID parentId;

  @Column(nullable = false, length = 60)
  private String name;

  @CurrentTimestamp(event = EventType.INSERT, source = SourceType.DB)
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @CurrentTimestamp(source = SourceType.DB)
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Topic() {
    // For JPA.
  }

  Topic(UUID ownerId, UUID parentId, String name) {
    this.ownerId = ownerId;
    this.parentId = parentId;
    this.name = name;
  }

  UUID getId() {
    return id;
  }

  UUID getOwnerId() {
    return ownerId;
  }

  UUID getParentId() {
    return parentId;
  }

  String getName() {
    return name;
  }

  void rename(String name) {
    this.name = name;
  }

  void moveTo(UUID parentId) {
    this.parentId = parentId;
  }
}
