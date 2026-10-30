plugins {
  java
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.spotless)
  alias(libs.plugins.gradle.git.properties)
}

// group and version come from gradle.properties.

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

repositories { mavenCentral() }

dependencies {
  implementation(libs.spring.boot.starter.webmvc)
  implementation(libs.spring.boot.starter.actuator)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.spring.modulith.starter.core)
  implementation(libs.springdoc.openapi.starter.webmvc.api)
  testImplementation(libs.spring.boot.starter.webmvc.test)
  testImplementation(libs.spring.boot.starter.actuator.test)
  testImplementation(libs.spring.boot.starter.validation.test)
  testImplementation(libs.spring.modulith.starter.test)
  testRuntimeOnly(libs.junit.platform.launcher)
}

dependencyManagement {
  imports { mavenBom(libs.spring.modulith.bom.get().toString()) }
}

// Writes META-INF/build-info.properties; Actuator's /info shows it as "build".
springBoot { buildInfo() }

// Writes git.properties; Actuator's /info shows it as "git".
// The Gradle build lives in backend/, but .git is in the repository root.
gitProperties { dotGitDirectory = layout.projectDirectory.dir("../.git") }

// spotlessCheck is attached to `check`, so `./gradlew build` fails on bad formatting.
// Fix locally with `./gradlew spotlessApply`.
spotless { java { googleJavaFormat(libs.versions.google.java.format.get()) } }

tasks.withType<Test> { useJUnitPlatform() }
