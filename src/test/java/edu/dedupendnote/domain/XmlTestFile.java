package edu.dedupendnote.domain;

import java.io.File;

public record XmlTestFile(File xmlInputFile, File textInputFile, int noOfRecords) {};