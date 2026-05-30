package edu.dedupendnote.services;

public record FieldComparators(
		AuthorsComparisonService authors,
		TitleComparisonService titles,
		JournalComparisonService journals,
		PagesComparisonService pages) {
}
