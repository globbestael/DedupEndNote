package edu.dedupendnote.domain;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import lombok.Data;

@Data
public class ValidationResult {

	String fileName;
	int tp;
	int fn;
	int tn;
	int fp;
	long duration;
	// The number of publications in the to_validate files which are tp and where id == dedupId
	int uniqueDuplicates;

	int total; // = tp + fn + tn + fp;
	double percDuplicates; // = (tp + fn) * 100.0 / (tp + tn + fp + fn);

	double precision;
	double sensitivity; // == recall
	double specificity;
	double accuracy;
	double f1Score;

	Map<Integer, List<List<Publication>>> fnPairs = new TreeMap<>();
	Map<Integer, List<List<Publication>>> fpPairs = new TreeMap<>();

	public ValidationResult(String fileName, int tp, int fn, int tn, int fp, long duration, int uniqueDuplicates) {
		this.fileName = fileName;
		this.tp = tp;
		this.fn = fn;
		this.tn = tn;
		this.fp = fp;
		this.duration = duration;
		this.uniqueDuplicates = uniqueDuplicates;

		// computed fields
		this.total = tp + fn + tn + fp;
		this.percDuplicates = (tp + fn) * 100.0 / total;
		this.precision = tp * 100.0 / (tp + fp);
		this.sensitivity = tp * 100.0 / (tp + fn);
		this.specificity = tn * 100.0 / (tn + fp);
		this.accuracy = (tp + tn) * 100.0 / total;
		this.f1Score = 2 * precision * sensitivity / (precision + sensitivity);

		/*
		 * In ValidationTests there is also a function printValidationResultsASySD() to print the performance as in the ASySD publication
		 * See: https://github.com/camaradesuk/ASySD
		 * See: https://link.springer.com/article/10.1186/s12915-023-01686-z
		 * 
		 * TP = all records marked as duplicates except for the duplicate kept (i.e. all duplicate rightly removed)
		 */
	}
}
