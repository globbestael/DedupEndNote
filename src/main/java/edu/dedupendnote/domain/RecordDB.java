package edu.dedupendnote.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Data;

@Data
@JsonPropertyOrder({"id", "dedupid", "correction", "validated", "true_pos", "true_neg", "false_pos", "false_neg", "unsolvable", "authors_truncated", "authors", "publ_year", "title_truncated", "title", "title2", "volume", "issue", "pages", "article_number", "dois", "publ_type", "database", "number_authors"})
public class RecordDB {
	Integer id;
	Integer dedupid;
	Integer correction;
	boolean validated = false;
	@JsonProperty("true_pos")
	boolean truePositive = false;
	@JsonProperty("true_neg")
	boolean trueNegative = false;
	@JsonProperty("false_pos")
	boolean falsePositive = false;
	@JsonProperty("false_neg")
	boolean falseNegative = false;
	boolean unsolvable = false;
	@JsonProperty("authors_truncated")
	String authorsTruncated;
	String authors;
	@JsonProperty("publ_year")
	Integer publYear;
	@JsonProperty("title_truncated")
	String titleTruncated;
	String title;
	String title2;
	String volume;
	String issue;
	String pages;
	@JsonProperty("article_number")
	String articleNumber;
	String dois;
	@JsonProperty("publ_type")
	String publType;
	String database;
	@JsonProperty("number_authors")
	Integer numberAuthors;
	
	@JsonIgnore
	List<String> authorsList = new ArrayList<>();
	@JsonIgnore
	List<String> doisList = new ArrayList<>();
	
	private static String TAB = "\t";
	private static String NEWLINE = "\n";
	// private static final Object NULL_OBJECT = null;

	private static String quoted(String text) {
//		return "\"" + text + "\"";
		return text;
	}
	
	public String toDBLine() {
		StringBuilder sb = new StringBuilder();
		sb.append(id).append(TAB)
			.append(dedupid != null ? dedupid : "").append(TAB)
			.append(correction != null ? correction : "").append(TAB)
			.append(validated).append(TAB)
			.append(truePositive).append(TAB)
			.append(trueNegative).append(TAB)
			.append(falsePositive).append(TAB)
			.append(falseNegative).append(TAB)
			.append(unsolvable).append(TAB)
			.append(quoted(Objects.toString(authorsTruncated, ""))).append(TAB)
			.append(quoted(Objects.toString(authors, ""))).append(TAB)
			.append(publYear != null && publYear > 0 ? publYear : "").append(TAB)
			.append(quoted(Objects.toString(titleTruncated, ""))).append(TAB)
			.append(quoted(Objects.toString(title, ""))).append(TAB)
			.append(quoted(Objects.toString(title2, ""))).append(TAB)
			.append(quoted(Objects.toString(volume, ""))).append(TAB)
			.append(quoted(Objects.toString(issue, ""))).append(TAB)
			.append(quoted(Objects.toString(pages, ""))).append(TAB)
			.append(quoted(Objects.toString(articleNumber, ""))).append(TAB)
			.append(quoted(Objects.toString(dois, ""))).append(TAB)
			.append(quoted(Objects.toString(publType, ""))).append(TAB)
			.append(quoted(Objects.toString(database, ""))).append(TAB)
			.append(authorsList.size()).append(NEWLINE);
		return sb.toString();
	}
}
