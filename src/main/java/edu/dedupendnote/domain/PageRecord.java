package edu.dedupendnote.domain;

import org.jspecify.annotations.Nullable;

public record PageRecord(@Nullable String originalPages, @Nullable String pageStart, @Nullable String pagesOutput,
		boolean isSeveralPages) {
}