package edu.dedupendnote.domain;

import java.util.List;

import edu.dedupendnote.domain.xml.zotero.ZoteroXmlRecord;

public record ParsedAndConvertedZotero(List<ZoteroXmlRecord> xmlRecords, List<Publication> publications) {};