package edu.dedupendnote.domain;

import org.jspecify.annotations.Nullable;

public record AuthorRecord(@Nullable String author, @Nullable String authorTransposed, boolean isAuthorTransposed) {
}