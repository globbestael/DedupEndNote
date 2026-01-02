package edu.dedupendnote.domain;

import java.util.List;

import org.jspecify.annotations.Nullable;

public record TitleRecord(@Nullable String originalTitle, List<String> titles) {
}