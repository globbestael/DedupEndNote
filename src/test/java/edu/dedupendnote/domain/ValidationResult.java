package edu.dedupendnote.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ValidationResult {

	String fileName;

	int tp;

	int fn;

	int tn;

	int fp;

}
