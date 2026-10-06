# ADR-0034: Spring MVC on virtual threads, RestClient for outgoing HTTP

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

Requests mostly wait on I/O (database, Google, later the AI provider). Persistence is JPA, which is blocking (ADR-0028).

## Options considered

Spring MVC with platform threads; reactive WebFlux (needs R2DBC instead of JPA); Spring MVC with virtual threads.

## Decision

Spring MVC with `spring.threads.virtual.enabled=true`; `RestClient` for outgoing HTTP. No WebFlux, WebClient or RestTemplate.

## Consequences

Simple blocking code and readable stack traces with near-reactive scalability for I/O-bound work. Java 25 avoids the old `synchronized` pinning issue. The connection pool remains the concurrency limit; CPU-bound work gains nothing.
