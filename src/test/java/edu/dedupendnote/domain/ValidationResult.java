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
	long durationMilliseconds;

	int total = tp + fn + tn + fp;
	double percDuplicates = (tp + fn) * 100.0 / (tp + tn + fp + fn);

	double precision;
	double sensitivity; // == recall
	double specificity;
	double accuracy;
	double f1Score;

	Map<Integer, List<List<Publication>>> fnPairs = new TreeMap<>();
	Map<Integer, List<List<Publication>>> fpPairs = new TreeMap<>();

	public ValidationResult(String fileName, int tp, int fn, int tn, int fp, long durationMilliseconds) {
		this.fileName = fileName;
		this.tp = tp;
		this.fn = fn;
		this.tn = tn;
		this.fp = fp;
		this.durationMilliseconds = durationMilliseconds;

		// computed fields
		this.total = tp + fn + tn + fp;
		this.percDuplicates = (tp + fn) * 100.0 / total;
		this.precision = tp * 100.0 / (tp + fp);
		this.sensitivity = tp * 100.0 / (tp + fn);
		this.specificity = tn * 100.0 / (tn + fp);
		this.accuracy = (tp + tn) * 100.0 / total;
		this.f1Score = 2 * precision * sensitivity / (precision + sensitivity);
	}
}
