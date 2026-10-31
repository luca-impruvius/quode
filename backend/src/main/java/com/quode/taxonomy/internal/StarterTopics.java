package com.quode.taxonomy.internal;

import java.util.List;

/**
 * The seed tree from docs/domain.md §1, created for a user only when they ask for it ("Add starter
 * topics"). Keep the two in sync.
 */
final class StarterTopics {

  record Node(String name, List<Node> children) {
    static Node of(String name, Node... children) {
      return new Node(name, List.of(children));
    }
  }

  static final List<Node> TREE =
      List.of(
          Node.of(
              "Languages",
              Node.of(
                  "Java",
                  Node.of("Collections"),
                  Node.of("Concurrency"),
                  Node.of("JVM & Memory"),
                  Node.of("Streams & Lambdas"))),
          Node.of(
              "Frameworks",
              Node.of(
                  "Spring",
                  Node.of("Spring Boot"),
                  Node.of("Spring Security"),
                  Node.of("Spring Data JPA"))),
          Node.of("Build Tools", Node.of("Gradle"), Node.of("Maven")),
          Node.of(
              "Fundamentals",
              Node.of("OOP & Design Patterns"),
              Node.of("Data Structures"),
              Node.of("Algorithms")),
          Node.of(
              "Web",
              Node.of("HTTP & REST"),
              Node.of("HTML & CSS"),
              Node.of("Auth", Node.of("JWT"), Node.of("OAuth2 / OIDC"))),
          Node.of(
              "Databases",
              Node.of("SQL"),
              Node.of("PostgreSQL"),
              Node.of("Indexing & Transactions")),
          Node.of("Networking", Node.of("TCP/IP"), Node.of("DNS")),
          Node.of(
              "Cloud",
              Node.of(
                  "GCP",
                  Node.of("Cloud Run"),
                  Node.of("Cloud SQL"),
                  Node.of("Pub/Sub"),
                  Node.of("IAM"))));

  private StarterTopics() {}
}
