package edu.dedupendnote.services;

import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
public class IOZoteroXmlService implements IoService {

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
//	public ParsedAndConvertedEndnote readEndnoteXmlWithPullParser(File inputFile) {
//		List<EndnoteXmlRecord> xmlRecords = new ArrayList<>();
//		List<Publication> publications = new ArrayList<>();
//		int i = 0;
//
//		try {
//			JAXBContext jaxbContext = JAXBContext.newInstance(Xml.class);
//			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
//			XMLInputFactory xif = XMLInputFactory.newInstance();
//			XMLStreamReader xsr = xif.createXMLStreamReader(new FileReader(inputFile));
//	
//	
//			xsr.nextTag();
//			xsr.require(START_ELEMENT, null, "xml");
//			xsr.nextTag();
//			xsr.require(START_ELEMENT, null, "records");
//			xsr.nextTag(); // Should now be at the first EndNoteXmlRecord
//			
//			while (xsr.getEventType() == START_ELEMENT) {
//				xsr.require(START_ELEMENT, null, "record");
//				EndnoteXmlRecord xmlRecord = (EndnoteXmlRecord) unmarshaller.unmarshal(xsr);
//				i++;
//				xmlRecords.add(xmlRecord);
//				log.debug("Record {} with recNo {}", i, xmlRecord.getRecNumber());
//				if (xsr.getEventType() == CHARACTERS) {
//					xsr.next(); // skip the whitespace between EndNoteXmlRecords
//				}
//				
//				convertEndnoteXmlToPublication(xmlRecord, publications);
//				if (xmlRecord.getTitles().getTitle() != null) {
//					log.debug("Record: {}", getAllText(xmlRecord.getTitles().getTitle().getStyle()));
//				} else {
//					log.debug("Record has no Title");
//				}
//			}
//		} catch (JAXBException | FileNotFoundException | XMLStreamException e) {
//			e.printStackTrace();
//		} 
//		log.info("Finished with {} records", i);
//		return new ParsedAndConvertedEndnote(xmlRecords, publications);
//	}
	
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

//	private void convertEndnoteXmlToPublication(EndnoteXmlRecord xmlRecord, List<Publication> xmlPublications) {
//		Publication pub = new Publication();
//		xmlPublications.add(pub);
//		
//		pub.setId(xmlRecord.getRecNumber());
//		pub.setReferenceType(refTypes.getOrDefault(xmlRecord.getRefType().getName(), "JOUR"));
//
//		if (xmlRecord.getContributors() != null) {
//			if (xmlRecord.getContributors().getAuthors() != null) {
//				for (Author author : xmlRecord.getContributors().getAuthors().getAuthor()) {
//					// This is a difference between RIS output ("AU  - Shalimar") and XML output ("Shalimar,")
//					pub.addAuthors(getAllText(author.getStyle()).replaceAll(",$", ""));
//				}
//			}
//		}
//		
//		if (xmlRecord.getOrigPub() != null) {
//			pub.addTitles(getAllText(xmlRecord.getOrigPub().getStyle()));
//		}
//		
//		if (xmlRecord.getTitles() != null) {
//			// in RIS: ST before TI
//			if (xmlRecord.getTitles().getShortTitle() != null) {
//				pub.addTitles(getAllText(xmlRecord.getTitles().getShortTitle().getStyle()));
//			}
//			if (xmlRecord.getTitles().getSecondaryTitle() != null) {
//				pub.addJournals(getAllText(xmlRecord.getTitles().getSecondaryTitle().getStyle()));
//			}
//			if (xmlRecord.getTitles().getAltTitle() != null) {
//				pub.addJournals(getAllText(xmlRecord.getTitles().getAltTitle().getStyle()));
//			}
//			if (xmlRecord.getTitles().getTertiaryTitle() != null) {
//				// conferencePattern
//				String j = getAllText(xmlRecord.getTitles().getTertiaryTitle().getStyle());
//				if (!IOEndnoteRisService.conferencePattern.matcher(j).matches()) {
//					pub.addJournals(j);
//					pub.addTitles(j);
//				}
//			}
//			if (xmlRecord.getTitles().getTitle() != null) {
//				String title = getAllText(xmlRecord.getTitles().getTitle().getStyle());
//				pub.addTitles(title);
//				if (IOEndnoteRisService.replyPattern.matcher(title.toLowerCase()).matches()) {
//					pub.setReply(true);
//					pub.setTitle(title);
//				}
//				if (IOEndnoteRisService.phasePattern.matcher(title.toLowerCase()).matches()) {
//					pub.setPhase(true);
//				}
//			}
//		}
//		
////		if (xmlRecord.getAltPeriodical() != null) {
////			 if (xmlRecord.getAltPeriodical().getFullTitle() != null) {
////				 pub.addJournals(getAllText(xmlRecord.getAltPeriodical().getFullTitle().getStyle()));
////			 }
////			 if (xmlRecord.getAltPeriodical().getAbbr1() != null) {
////				 pub.addJournals(getAllText(xmlRecord.getAltPeriodical().getAbbr1().getStyle()));
////			 }
////		}
//		
//		if (xmlRecord.getCustom7() != null) {
//			pub.parsePages(getAllText(xmlRecord.getCustom7().getStyle()));
//		}
//
//		if (xmlRecord.getPages() != null) {
//			pub.parsePages(getAllText(xmlRecord.getPages().getStyle()));
//		}
//		
//		if (xmlRecord.getDates() != null && xmlRecord.getDates().getYear() != null) {
//			pub.parsePublicationYear(getAllText(xmlRecord.getDates().getYear().getStyle()));
//		}
//
//		if (xmlRecord.getIsbn() != null) {
//			pub.addIssns(getAllText(xmlRecord.getIsbn().getStyle()));
//		}
//		
//		if (xmlRecord.getElectronicResourceNum() != null) {
//			pub.addDois(getAllText(xmlRecord.getElectronicResourceNum().getStyle()));
//		}
//
//		pub.addReversedTitles();
//		pub.fillAllAuthors();
//	}

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

//	private String getAllText(List<Style> style) {
//		String line = style.stream().map(Style::getvalue).collect(Collectors.joining(" "));
//		return IOEndnoteRisService.unusualWhiteSpacePattern.matcher(line).replaceAll(" ");
//	}

	// TODO: Expand to all refTypes?
	Map<String,String> refTypes = Map.of(
//			"Aggregated Database", "",
//			"Ancient Text", "",
//			"Artwork", "",
//			"Audiovisual Material", "",
//			"Bill", "",
//			"Blog", "",
			"Book Section", "CHAP",
			"Book", "BOOK",
//			"Case", "",
//			"Catalog", "",
//			"Chart or Table", "",
//			"Classical Work", "",
//			"Computer Program", "",
//			"Conference Paper", "",
			"Conference Proceedings", "CONF",
//			"Dataset", "",
//			"Dictionary", "",
//			"Discussion Forum", "",
//			"Edited Book", "",
//			"Electronic Article", "",
//			"Electronic Book Section", "",
//			"Electronic Book", "",
//			"Encyclopedia", "",
//			"Equation", "",
//			"Figure", "",
//			"Film or Broadcast", "",
			"Generic", "GEN",
//			"Government Document", "",
//			"Grant">, "",
//			"Hearing", "",
//			"Interview", "",
			"Journal Article", "JOUR",
//			"Legal Rule or Regulation", "",
//			"Magazine Article", "",
//			"Manuscript", "",
//			"Map", "",
//			"Multimedia Application", "",
//			"Music", "",
//			"Newspaper Article", "",
//			"Online Database", "",
//			"Online Multimedia", "",
//			"Pamphlet", "",
//			"Patent", "",
//			"Personal Communication", "",
//			"Podcast", "",
//			"Press Release", "",
//			"Report", "",
			"Serial", "SER",
//			"Social Media", "",
//			"Standard", "",
//			"Statute", "",
//			"Television Episode", "",
//			"Thesis", "",
//			"Unpublished Work", "",
//			"Unused 1", "",
//			"Unused 2", "",
//			"Unused 3", "",
//			"Web Page"
			"ZZZDUMMY", ""
			);

//	public List<Publication> readEndnoteXmlPublications(String inputFileName) {
//		ParsedAndConvertedEndnote parsedAndConvertedEndnote = readEndnoteXmlWithPullParser(new File(inputFileName));
//		return parsedAndConvertedEndnote.publications();
//	}

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
}
