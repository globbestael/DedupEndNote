package edu.dedupendnote.services.comparison;

public record FieldComparators(
		AuthorsComparisonService authors,
		TitleComparisonService titles,
		JournalComparisonService journals,
		PagesComparisonService pages) {
}
