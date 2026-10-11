package com.quode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

  // Static analysis only (ArchUnit under the hood): no Spring context is started.
  private final ApplicationModules modules = ApplicationModules.of(QuodeApplication.class);

  @Test
  void modulesRespectTheirBoundaries() {
    // Fails on dependency cycles between modules and on access to another module's internals.
    modules.verify();
  }

  @Test
  void detectsExactlyTheDocumentedModules() {
    // The identifier is the package name relative to com.quode, e.g. "review".
    assertThat(modules.stream().map(ApplicationModule::getIdentifier).map(Object::toString))
        .containsExactlyInAnyOrder(
            "shared", "user", "taxonomy", "question", "quiz", "study", "review");
  }
}
