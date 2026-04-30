# Tests

Repo-level test notes and future fixtures.

Gradle has `testImplementation(kotlin("test"))` and `tasks.test { useJUnitPlatform() }`, but retrofit found no checked-in Gradle test source files.

Use `./gradlew.bat test` on Windows or `./gradlew test` on Unix-like shells once tests exist.
