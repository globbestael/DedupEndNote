# Version number and release

The version is defined once: `<version>x.y.z</version>` at the top of `pom.xml` (the standard Maven project version). Everything else derives from it at build time:
- `application.properties` contains `spring.application.version=@project.version@` (Maven-filtered at build)
- `ProjectVersionAdvice` reads `${spring.application.version}` and injects `projectVersion` into every Thymeleaf model — consumed by `fragments.html` (navbar, used by all pages) and `index.html` (citing accordion)
- `src/main/cff/citation.cff` is the template; `maven-resources-plugin` filters it to the root `citation.cff` on every build. Both `version` and `date-released` are substituted: `version` from `${project.version}`, `date-released` from `${build.date}` (which captures `${maven.build.timestamp}` formatted as `yyyy-MM-dd`)
- The fat JAR is named `DedupEndNote.jar` (version excluded via `<finalName>${project.artifactId}</finalName>` in `pom.xml`)

**Release workflow:** update `<version>` in `pom.xml` → run `./mvnw package` → commit `pom.xml` and the regenerated root `citation.cff` → add the new `<h2>` + `<ul>` entry to `changelog.html` manually.
