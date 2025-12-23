package edu.dedupendnote.domain;

import java.util.List;

public record TitleRecord(String originalTitle, List<String> titles) {
}