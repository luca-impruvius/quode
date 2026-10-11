// settings.gradle.kts can't set the name from gradle.properties by itself, so read it explicitly.
rootProject.name = providers.gradleProperty("rootProjectName").get()
