<!--
Sync Impact Report
- Version change: none (initial) → 1.0.0
- Modified principles: none (initial ratification)
- Added sections: Core Principles (I–V), Technology Stack, Development Workflow, Governance
- Removed sections: none
- Follow-up TODOs: none
-->
# Dashboard Constitution

## Core Principles

### I. Clean Code & Simplicity

Code MUST follow clean code practices: small, single-purpose methods and classes; meaningful
names; no dead code. Comments are kept to a minimum — code must be self-explanatory; comments
are allowed only where intent is non-obvious. Start with the simplest solution that satisfies
the requirement (YAGNI); speculative abstractions and unused configurability are prohibited.

Rationale: simple, readable code lowers maintenance cost and defect rate in a growing codebase.

### II. Test Coverage for Changes (NON-NEGOTIABLE)

Every new feature or bug fix MUST ship with automated tests covering the changed behavior:
unit tests for business logic and Spring Boot integration tests (`@SpringBootTest`, MockMvc)
for web endpoints and Kafka producers/consumers. A change is not complete while its tests are
red or missing. Tests MUST run via `./gradlew test` before a task is reported done.

Rationale: the dashboard integrates web and messaging layers where regressions are costly;
tests are the only reliable gate.

### III. Layered Spring Boot Architecture

The backend MUST follow the standard layered structure: controllers (web) → services
(business logic) → repositories/clients (data & integration). Business logic MUST NOT live in
controllers; web and Kafka concerns MUST NOT leak into the service layer. Dependencies are
wired via constructor injection; field injection is prohibited.

Rationale: strict layering keeps components independently testable and replaceable.

### IV. Lombok for Boilerplate Reduction

Lombok MUST be used where it removes boilerplate (`@Getter`, `@RequiredArgsConstructor`,
`@Slf4j`, builders) instead of hand-written accessors, constructors, and loggers. Lombok MUST
NOT be used where it obscures behavior (e.g., `@Data` on JPA entities or classes with
non-trivial `equals`/`hashCode` requirements).

Rationale: Lombok keeps classes concise without hiding semantics, consistent with the
project's existing build configuration.

### V. Explicit, Testable Contracts

Public API endpoints and Kafka message schemas MUST be treated as contracts: changes to them
require updated tests and documentation in the same change set. Breaking changes MUST be
called out explicitly in the change description. No silent contract drift.

Rationale: web and Kafka consumers depend on these contracts; drift causes runtime failures
that tests would otherwise catch.

## Technology Stack

The project is a Spring Boot 4.x application built with Gradle (Groovy DSL) on Java 25.
Mandatory stack decisions:

- Backend: Spring Boot WebMVC for HTTP, Spring Kafka for messaging.
- Boilerplate: Lombok (compileOnly + annotationProcessor).
- Testing: JUnit Platform with Spring Boot test starters (`spring-boot-starter-webmvc-test`,
  `spring-boot-starter-kafka-test`).
- New dependencies require justification and must not duplicate existing stack capabilities.

## Development Workflow

- Build and verify with `./gradlew build`; tests with `./gradlew test`.
- All changes go through review; reviewers MUST verify compliance with this constitution.
- Every PR/change set includes tests for changed behavior and passes the full build.
- Complexity beyond the simplest viable solution MUST be justified in the change description.
- Git mutations (commit, push, rebase) are performed only on explicit user request.

## Governance

This constitution supersedes ad-hoc practices. Amendments require: a documented rationale,
an update to this file with a version bump, and a sync impact review of dependent templates
and workflows.

Versioning policy (semantic):

- MAJOR: backward-incompatible removal or redefinition of a principle.
- MINOR: new principle or section, or materially expanded guidance.
- PATCH: clarifications, wording, and non-semantic fixes.

Compliance: all reviews MUST check changes against the Core Principles; violations require
either a fix or an explicit, documented exception approved during review.

**Version**: 1.0.0 | **Ratified**: 2026-09-08 | **Last Amended**: 2026-09-08
