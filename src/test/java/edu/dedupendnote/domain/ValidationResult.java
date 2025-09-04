package edu.dedupendnote.domain;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class ValidationResult {

	String fileName;
	int tp;
	int fn;
	int tn;
	int fp;
	long durationMilliseconds;

	List<List<Publication>> fnPairs = new ArrayList<>();
	List<List<Publication>> fpPairs = new ArrayList<>();

	public ValidationResult(String fileName, int tp, int fn, int tn, int fp, long durationMilliseconds) {
		this.fileName = fileName;
		this.tp = tp;
		this.fn = fn;
		this.tn = tn;
		this.fp = fp;
		this.durationMilliseconds = durationMilliseconds;
	}

}
