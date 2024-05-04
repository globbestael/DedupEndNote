package edu.dedupendnote.domain;

import java.util.List;

import edu.dedupendnote.domain.xml.endnote.EndnoteXmlRecord;

public record ParsedAndConvertedEndnote(List<EndnoteXmlRecord> xmlRecords, List<Publication> publications) {}
