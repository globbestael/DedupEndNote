package edu.dedupendnote.services;

import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.springframework.stereotype.Service;

import edu.dedupendnote.domain.ParsedAndConvertedZotero;
import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.xml.zotero.ObjectFactory;
import edu.dedupendnote.domain.xml.zotero.Pages;
import edu.dedupendnote.domain.xml.zotero.Titles;
import edu.dedupendnote.domain.xml.zotero.Xml;
import edu.dedupendnote.domain.xml.zotero.Year;
import edu.dedupendnote.domain.xml.zotero.ZoteroXmlRecord;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class IOZoteroXmlService extends IoXmlService {
	
	ObjectFactory objectFactory = new ObjectFactory();

	@Override
	public  List<Publication> readPublications(String inputFileName) {
		ParsedAndConvertedZotero parsedAndConvertedZotero = readZoteroXmlWithPullParser(inputFileName);
		return parsedAndConvertedZotero.publications();
	}

	@Override
	public int writeDeduplicatedRecords(List<Publication> publications, String inputFileName, String outputFileName) {
		return writeRecords(publications, inputFileName, outputFileName, false);
	}
	
	@Override
	public int writeMarkedRecords(List<Publication> publications, String inputFileName, String outputFileName) {
		return writeRecords(publications, inputFileName, outputFileName, true);
	}

	private int writeRecords(List<Publication> publications, String inputFileName, String outputFileName, boolean markMode) {
		log.debug("Start writing to file {}", outputFileName);

		Map<String, Publication> recordIdMap = publications.stream()
				.filter(r -> !r.getId().startsWith("-"))	// skip he records from first file if comparing 2 files
				.collect(Collectors.toMap(Publication::getId, Function.identity()));

		int numberWritten = 0;
		int i = 0;
		Publication publication;
		FileReader fileReader = null;
		XMLStreamReader reader = null;
		
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(Xml.class);
			
			// input-related
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			XMLInputFactory xif = XMLInputFactory.newInstance();
			fileReader = new FileReader(inputFileName);
			reader = xif.createXMLStreamReader(fileReader);
	
			// output-related
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
			FileOutputStream fos = new FileOutputStream(outputFileName);
			XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(fos);

			reader.nextTag();
			writer.writeStartDocument();
			reader.require(START_ELEMENT, null, "xml");
			reader.nextTag();
			writer.writeStartElement("xml");
			reader.require(START_ELEMENT, null, "records");
			reader.nextTag(); // Should now be at the first EndNoteXmlRecord
			writer.writeStartElement("records");
			
			while (reader.getEventType() == START_ELEMENT) {
				reader.require(START_ELEMENT, null, "record");
				ZoteroXmlRecord xmlRecord = (ZoteroXmlRecord) unmarshaller.unmarshal(reader);
				i++;
				String recordId = Integer.valueOf(i).toString();
				log.debug("Record {} with recNo {}", i, recordId);
				publication = recordIdMap.get(recordId);
				if (markMode) {
					xmlRecord.setLabel(publication.getLabel());
					marshaller.marshal(xmlRecord, writer);
					numberWritten++;
					log.error("Record {} saved", recordId);
				} else {
					if (publication != null && publication.isKeptRecord()) {
						enrichXmlOutput(xmlRecord, publication);
						marshaller.marshal(xmlRecord, writer);
						numberWritten++;
						log.error("Record {} saved", recordId);
					} else {
						log.error("Record {} skipped", recordId);
					}
				}
				if (reader.getEventType() == CHARACTERS) {
					reader.next(); // skip the whitespace between EndNoteXmlRecords
				}
			}
			writer.writeEndDocument(); // this will close any open tags
			writer.close();
		} catch (JAXBException | FileNotFoundException | XMLStreamException e) {
			e.printStackTrace();
		} finally {
			try {
				if (reader != null) {
					// Frees any resources associated with this Reader. This method does not close the underlying input source.
					reader.close();
					fileReader.close();
					log.error("File {} is closed", inputFileName);
				}
			} catch (XMLStreamException | IOException e) {
				e.printStackTrace();
			}
		}
		log.info("Finished with {} records", numberWritten);

		return numberWritten;
	}

	private void enrichXmlOutput(ZoteroXmlRecord xmlRecord, Publication publication) {
		if (!publication.getDois().isEmpty()) {
			xmlRecord.setElectronicResourceNum(publication.getDois().stream().collect(Collectors.joining("\nhttps://doi.org/", "https://doi.org/", "")));
		}
		
		if (publication.getPageStart() != null) {
			Pages pages = objectFactory.createPages();
			if (publication.getPageEnd() != null && !publication.getPageEnd().equals(publication.getPageStart())) {
				pages.setvalue(publication.getPageStart() + "-" + publication.getPageEnd());
			} else {
				pages.setvalue(publication.getPageStart());
			}
		}

		if (publication.isReply()) {
			if (xmlRecord.getTitles() != null) {
				xmlRecord.getTitles().setTitle(publication.getTitle());
			} else {
				Titles titles = objectFactory.createTitles();
				xmlRecord.setTitles(titles);
				titles.setTitle(publication.getTitle());
			}
		}

		// Only Anonymous can be removed, not the other skipped authors
		if (publication.getAuthors().isEmpty()
				&&  (xmlRecord.getContributors() != null 
				&& xmlRecord.getContributors().getAuthors() != null
				&& xmlRecord.getContributors().getAuthors().getAuthor() != null)) {
			String firstAuthor = xmlRecord.getContributors().getAuthors().getAuthor().getFirst().getvalue();
			if (firstAuthor.startsWith("Anonymous")) {	// format can be "Anonymous,"
				xmlRecord.getContributors().getAuthors().getAuthor().clear();
			}
		}

		if (publication.getPublicationYear() != 0) {
			if (xmlRecord.getDates() == null) {
				xmlRecord.setDates(objectFactory.createDates());
			}
			if (xmlRecord.getDates().getYear() == null) {
				Year year = objectFactory.createYear();
				xmlRecord.getDates().setYear(year);
				year.setvalue(publication.getPublicationYear().toString());
			} else {
				xmlRecord.getDates().getYear().setvalue(publication.getPublicationYear().toString());
			}
		}
		
		// remove C7/custom7 (Article Number)
		if (xmlRecord.getCustom7() != null) {
			xmlRecord.setCustom7(null);
		}
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
		FileReader fileReader = null;
		XMLStreamReader xsr = null;

		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(edu.dedupendnote.domain.xml.zotero.Xml.class);
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			XMLInputFactory xif = XMLInputFactory.newInstance();
			fileReader = new FileReader(inputFileName);
			xsr = xif.createXMLStreamReader(fileReader);
	
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
		} finally {
			try {
				if (xsr != null) {
					// Frees any resources associated with this Reader. This method does not close the underlying input source.
					xsr.close();
					fileReader.close();
					log.error("File {} is closed", inputFileName);
				}
			} catch (XMLStreamException | IOException e) {
				e.printStackTrace();
			}
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