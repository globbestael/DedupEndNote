# Single source of truth for the application version number

## Context

The user-facing version number (`1.1.5`) is currently hardcoded in five places:

| Location | Element |
|---|---|
| `citation.cff` | `version:` field (line 15) |
| `src/main/resources/templates/changelog.html` | First `<h2>` (line 28) |
| `src/main/resources/templates/index.html` | `.navbar-brand` (line 193) |
| `src/main/resources/templates/index.html` | `#collapseCiting` accordion body (line 380) |
| `src/main/resources/templates/twofiles.html` | `.navbar-brand` (line 192) |

Note: `pom.xml` has its own `<version>` (`0.9.7b-SNAPSHOT`) which follows a separate versioning scheme and is NOT the source of truth for the user-facing version.

## Approach

### Single source of truth

Add `<app.version>1.1.5</app.version>` to the `<properties>` section of `pom.xml`. This is the single place to edit when releasing.

Spring Boot's parent POM already applies resource filtering to `src/main/resources` using `@…@` delimiters. Set `app.version=@app.version@` in `application.properties` so Maven substitutes the value at build time, making it available to Spring's `@Value("${app.version}")` at runtime.

The `maven-resources-plugin` execution for `citation.cff` also reads `${app.version}` as a Maven property, so both mechanisms draw from the same `pom.xml` source.

### HTML templates (index.html, twofiles.html, changelog.html)

Create a `@ControllerAdvice` that reads `@Value("${app.version}")` and exposes it as a `@ModelAttribute("appVersion")` on every request. All three Thymeleaf templates then use `th:text="${appVersion}"` instead of hardcoded strings.

`changelog.html` is **not converted** to a Thymeleaf template. Each release the developer adds a new `<h2>` + `<ul>` block manually anyway, so the version number in that heading is written by hand as part of that same edit. No automation needed here — the file stays plain HTML.

### citation.cff

Place the template at `src/main/cff/citation.cff` with placeholder `${app.version}`. Configure `maven-resources-plugin` to filter and copy it to the project root during the `generate-resources` phase.

**Why `src/main/cff/` and not `src/main/resources/cff/`?**
`src/main/resources` is Spring Boot's default resource directory — everything in it is copied into the JAR. Placing `citation.cff` there would require adding an exclusion for `cff/**` to the default resource processing configuration in `pom.xml`. Using a separate `src/main/cff/` directory avoids that pom.xml complexity entirely.

**Template edits:** All changes to `citation.cff` (metadata, title, authors, etc.) must be made in `src/main/cff/citation.cff`, not in the generated root `citation.cff`. The root file is overwritten on every build.

**Version control:** Both files are committed to git:
- `src/main/cff/citation.cff` — the template (contains `${app.version}` placeholder)
- `citation.cff` — the generated output (contains the real version number; GitHub reads it from here)

## Files to change

| File | Change |
|---|---|
| `pom.xml` `<properties>` | Add `<app.version>1.1.5</app.version>` — the single source of truth |
| `src/main/resources/application.properties` | Add `app.version=@app.version@` (filtered at build time from Maven property) |
| `pom.xml` | Add `maven-resources-plugin` execution to filter `src/main/cff/citation.cff` → project root |
| `src/main/cff/citation.cff` | New file: copy of current `citation.cff` with `version: ${app.version}` |
| `citation.cff` | Replace `version: 1.1.5` with `version: ${app.version}` — becomes the generated output (but still committed) |
| `src/main/java/edu/dedupendnote/controllers/AppVersionAdvice.java` | New `@ControllerAdvice` class exposing `appVersion` model attribute |
| `src/main/resources/templates/index.html` | Replace two hardcoded `1.1.5` occurrences with `th:text="${appVersion}"` |
| `src/main/resources/templates/twofiles.html` | Replace hardcoded `v1.1.5` with `th:text="${appVersion}"` |
| `src/main/resources/templates/changelog.html` | No change — version in `<h2>` is written manually when the developer adds a new release entry |
| `CLAUDE.md` | No change needed (version locations are not documented there) |

## AppVersionAdvice sketch

```java
package edu.dedupendnote.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class AppVersionAdvice {

    @Value("${app.version}")
    private String appVersion;

    @ModelAttribute("appVersion")
    public String appVersion() {
        return appVersion;
    }
}
```

## maven-resources-plugin configuration sketch

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-resources-plugin</artifactId>
    <executions>
        <execution>
            <id>filter-citation-cff</id>
            <phase>generate-resources</phase>
            <goals><goal>copy-resources</goal></goals>
            <configuration>
                <outputDirectory>${project.basedir}</outputDirectory>
                <resources>
                    <resource>
                        <directory>${project.basedir}/src/main/cff</directory>
                        <filtering>true</filtering>
                    </resource>
                </resources>
                <delimiters><delimiter>${*}</delimiter></delimiters>
                <useDefaultDelimiters>false</useDefaultDelimiters>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Thymeleaf changes (examples)

**index.html navbar-brand** — before:
```html
<span class="navbar-brand fw-bold">DedupEndNote <small class="fw-normal text-muted fs-6">v1.1.5</small></span>
```
After:
```html
<span class="navbar-brand fw-bold">DedupEndNote <small class="fw-normal text-muted fs-6" th:text="'v' + ${appVersion}">v1.1.5</small></span>
```

**index.html collapseCiting** — before:
```html
<p>Lobbestael, G. (2026). DedupEndNote (Version 1.1.5) [Computer software].
```
After:
```html
<p>Lobbestael, G. (2026). DedupEndNote (Version <span th:text="${appVersion}">1.1.5</span>) [Computer software].
```

## How to verify

1. `./mvnw clean package` — confirm `citation.cff` at the root has `version: 1.1.5` (not the placeholder)
2. `./mvnw spring-boot:run` — visit:
   - `http://localhost:9777/` — navbar and citing accordion show correct version
   - `http://localhost:9777/twofiles` — navbar shows correct version
   - `http://localhost:9777/changelog` — opens normally (no version injection)
3. Change `app.version` to a test value, rebuild, verify all five locations update together
4. Revert the test change

## Release workflow going forward

To release a new version:
1. Update `app.version=x.y.z` in `application.properties` — single source of truth for the four automated locations
2. Run `./mvnw package` — regenerates root `citation.cff` from the template
3. Commit `application.properties` and `citation.cff`
4. Add the new `<h2>` + `<ul>` entry to `changelog.html` manually (including the version number and date) — this is done as part of writing the release notes anyway
