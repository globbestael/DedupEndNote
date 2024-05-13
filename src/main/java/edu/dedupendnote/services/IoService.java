package edu.dedupendnote.services;

import java.util.List;

import edu.dedupendnote.domain.Publication;

public interface IoService {

	List<Publication> readPublications(String inputFileName);

	int writeDeduplicatedRecords(List<Publication> publications, String inputFileName, String outputFileName);

	int writeMarkedRecords(List<Publication> publications, String inputFileName, String outputFileName);

}