package edu.dedupendnote.services;

import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.springframework.stereotype.Service;

import edu.dedupendnote.domain.ParsedAndConvertedZotero;
import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.xml.zotero.ZoteroXmlRecord;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class IOZoteroXmlService extends IoXmlService {

	@Override
	public  List<Publication> readPublications(String inputFileName) {
		ParsedAndConvertedZotero parsedAndConvertedZotero = readZoteroXmlWithPullParser(inputFileName);
		return parsedAndConvertedZotero.publications();
	}

	@Override
	public int writeDeduplicatedRecords(List<Publication> publications, String inputFileName, String outputFileName) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	@Override
	public int writeMarkedRecords(List<Publication> publications, String inputFileName, String outputFileName) {
		// TODO Auto-generated method stub
		return 0;
	}

	/*
	 * JAXB User Guide: 4.4. Dealing with large documents
	 * https://eclipse-ee4j.github.io/jaxb-ri/4.0.5/docs/ch03.html#unmarshalling-dealing-with-large-documents
	 *   
	 * See also the samples for JAXB
	 * - https://github.com/eclipse-ee4j/jaxb-ri/tree/master/jaxb-ri/samples/src/main/samples/partial-unmarshalling
	 * - https://github.com/eclipse-ee4j/jaxb-ri/tree/master/jaxb-ri/samples/src/main/samples/pull-parser
	 * - https://github.com/eclipse-ee4j/jaxb-ri/tree/master/jaxb-ri/samples/src/main/samples/streaming-unmarshalling
	 * - https://github.com/eclipse-ee4j/jaxb-ri/tree/master/jaxb-ri/samples/src/main/samples/xml-channel
	 * 
	 * Because writing the results will need to process one record at a time, using a pull parser might be the best choice. 
	 * https://github.com/eclipse-ee4j/jaxb-ri/blob/master/jaxb-ri/samples/src/main/samples/pull-parser/src/Main.java
	 * 
	 * The first 2 elements are skipped. By using nextTag() whitespace is skipped automatically.
	 * In the while loop however unmarshal(...) does not skip whiteSpace.
	 */
	public ParsedAndConvertedZotero readZoteroXmlWithPullParser(String inputFileName) {
		List<ZoteroXmlRecord> xmlRecords = new ArrayList<>();
		List<Publication> publications = new ArrayList<>();
		int i = 0;

		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(edu.dedupendnote.domain.xml.zotero.Xml.class);
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			XMLInputFactory xif = XMLInputFactory.newInstance();
			XMLStreamReader xsr = xif.createXMLStreamReader(new FileReader(inputFileName));
	
			xsr.nextTag();
			xsr.require(START_ELEMENT, null, "xml");
			xsr.nextTag();
			xsr.require(START_ELEMENT, null, "records");
			xsr.nextTag(); // Should now be at the first ZoteroXmlRecord
			
			while (xsr.getEventType() == START_ELEMENT) {
				xsr.require(START_ELEMENT, null, "record");
				ZoteroXmlRecord xmlRecord = (ZoteroXmlRecord) unmarshaller.unmarshal(xsr);
				i++;
				xmlRecord.setRecNumber(Integer.valueOf(i).toString());
				xmlRecords.add(xmlRecord);
				log.debug("Record {} with recNo {}", i, xmlRecord.getRecNumber());
				if (xsr.getEventType() == CHARACTERS) {
					xsr.next(); // skip the whitespace between ZoteroXmlRecords
				}
				
				convertZoteroXmlToPublication(xmlRecord, publications);
				if (xmlRecord.getTitles().getTitle() != null) {
					log.debug("Record: {}", xmlRecord.getTitles().getTitle());
				} else {
					log.debug("Record has no Title");
				}
			}
		} catch (JAXBException | FileNotFoundException | XMLStreamException e) {
			e.printStackTrace();
		} 
		log.info("Finished with {} records", i);
		return new ParsedAndConvertedZotero(xmlRecords, publications);
	}

	private void convertZoteroXmlToPublication(ZoteroXmlRecord xmlRecord, List<Publication> xmlPublications) {
		Publication pub = new Publication();
		xmlPublications.add(pub);
		
		pub.setId(xmlRecord.getRecNumber());
		pub.setReferenceType(refTypes.get(xmlRecord.getRefType().getName()));

		if (xmlRecord.getContributors() != null) {
			if (xmlRecord.getContributors().getAuthors() != null) {
				for (edu.dedupendnote.domain.xml.zotero.Author author : xmlRecord.getContributors().getAuthors().getAuthor()) {
					pub.addAuthors(author.getvalue());
				}
			}
		}
		
		if (xmlRecord.getOrigPub() != null) {
			pub.addTitles(xmlRecord.getOrigPub());
		}
		// in RIS: ST before TI
		
		if (xmlRecord.getTitles() != null) {
			if (xmlRecord.getTitles().getShortTitle() != null) {
				pub.addTitles(xmlRecord.getTitles().getShortTitle());
			}
			if (xmlRecord.getTitles().getTitle() != null) {
				pub.addTitles(xmlRecord.getTitles().getTitle());
			}
			if (xmlRecord.getTitles().getSecondaryTitle() != null) {
				pub.addJournals(xmlRecord.getTitles().getSecondaryTitle());
			}
			if (xmlRecord.getTitles().getAltTitle() != null) {
				pub.addJournals(xmlRecord.getTitles().getAltTitle());
			}
		}
		
		//?? why test getPeriodical before getTitles?
		if (xmlRecord.getPeriodical() != null) {
			if (xmlRecord.getTitles() != null && xmlRecord.getTitles().getTertiaryTitle() != null) {
				// conferencePattern
				String j = xmlRecord.getTitles().getTertiaryTitle();
				if (!IORisService.conferencePattern.matcher(j).matches()) {
					pub.addJournals(j);
					pub.addTitles(j);
				}
			}
			if (xmlRecord.getPeriodical().getAbbr1() != null) {
				pub.addJournals(xmlRecord.getPeriodical().getAbbr1());
			}
		}

		if (xmlRecord.getCustom7() != null) {
			pub.parsePages(xmlRecord.getCustom7());
		}

		if (xmlRecord.getPages() != null) {
			pub.parsePages(xmlRecord.getPages().getvalue());
		}
		
		if (xmlRecord.getDates() != null && xmlRecord.getDates().getYear() != null) {
			pub.setPublicationYear(Integer.valueOf(xmlRecord.getDates().getYear().getvalue()));
		}

		if (xmlRecord.getIsbn() != null) {
			pub.addIssns(xmlRecord.getIsbn());
		}
		
		if (xmlRecord.getElectronicResourceNum() != null) {
			pub.addDois(xmlRecord.getElectronicResourceNum());
		}

		pub.addReversedTitles();
		pub.fillAllAuthors();
	}
}