package edu.dedupendnote.domain;

import java.util.Set;

public record IsbnIssnRecord(Set<String> isbns, Set<String> issns) {
}