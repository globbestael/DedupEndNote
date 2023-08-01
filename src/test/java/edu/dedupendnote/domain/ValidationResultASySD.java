package edu.dedupendnote.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

/*
 * ASySD takes TP as the number of duplicates rightly removed,
 * and TN as the records rightly kept (i.e. the records without a duplicate AND the duplicate kept from a duplicate set)
 */
@Data
@AllArgsConstructor
public class ValidationResultASySD {

	String fileName;

	int tp;

	int fn;

	int tn;

	int fp;

	int uniqueDuplicates; // the number of records in the to_validate files which are tp
							// and where id == dedupId

}
