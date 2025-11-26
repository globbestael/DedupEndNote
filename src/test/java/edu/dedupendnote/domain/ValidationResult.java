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

	Map<Integer, List<Publication>> fnPairs = new TreeMap<>();
	Map<Integer, List<Publication>> fpPairs = new TreeMap<>();

	public ValidationResult(String fileName, int tp, int fn, int tn, int fp, long durationMilliseconds) {
		this.fileName = fileName;
		this.tp = tp;
		this.fn = fn;
		this.tn = tn;
		this.fp = fp;
		this.durationMilliseconds = durationMilliseconds;
	}

}
