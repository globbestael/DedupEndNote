package edu.dedupendnote.services;

import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
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

import edu.dedupendnote.domain.ParsedAndConvertedEndnote;
import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.xml.endnote.Author;
import edu.dedupendnote.domain.xml.endnote.ElectronicResourceNum;
import edu.dedupendnote.domain.xml.endnote.EndnoteXmlRecord;
import edu.dedupendnote.domain.xml.endnote.ObjectFactory;
import edu.dedupendnote.domain.xml.endnote.Pages;
import edu.dedupendnote.domain.xml.endnote.Style;
import edu.dedupendnote.domain.xml.endnote.Title;
import edu.dedupendnote.domain.xml.endnote.Titles;
import edu.dedupendnote.domain.xml.endnote.Xml;
import edu.dedupendnote.domain.xml.endnote.Year;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class IOEndnoteXmlService extends IoXmlService {
	
	ObjectFactory objectFactory = new ObjectFactory();

	@Override
	public List<Publication> readPublications(String inputFileName) {
		ParsedAndConvertedEndnote parsedAndConvertedEndnote = readEndnoteXmlWithPullParser(inputFileName);
		return parsedAndConvertedEndnote.publications();
	}

	@Override
	public int writeDeduplicatedRecords(List<Publication> publications, String inputFileName, String outputFileName) {
		log.debug("Start writing to file {}", outputFileName);

		Map<String, Publication> recordIdMap = publications.stream()
				.filter(r -> !r.getId().startsWith("-"))	// skip he records from first file if comparing 2 files
				.collect(Collectors.toMap(Publication::getId, Function.identity()));

		int numberWritten = 0;
		int i = 0;
		Publication publication;
		
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(Xml.class);
			
			// input-related
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			XMLInputFactory xif = XMLInputFactory.newInstance();
			XMLStreamReader reader = xif.createXMLStreamReader(new FileReader(inputFileName));
	
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
				EndnoteXmlRecord xmlRecord = (EndnoteXmlRecord) unmarshaller.unmarshal(reader);
				i++;
				String recordId = xmlRecord.getRecNumber();
				log.debug("Record {} with recNo {}", i, recordId);
				publication = recordIdMap.get(recordId);
				if (publication != null && publication.isKeptRecord()) {
					enrichXmlOutput(xmlRecord, publication);
					marshaller.marshal(xmlRecord, writer);
					numberWritten++;
					log.error("Record {} saved", recordId);
				} else {
					log.error("Record {} skipped", recordId);
				}
				
				if (reader.getEventType() == CHARACTERS) {
					reader.next(); // skip the whitespace between EndNoteXmlRecords
				}
			}
			writer.writeEndDocument(); // this will close any open tags
			writer.close();
		} catch (JAXBException | FileNotFoundException | XMLStreamException e) {
			e.printStackTrace();
		} 
		log.info("Finished with {} records", numberWritten);

		return numberWritten;
	}

	private void enrichXmlOutput(EndnoteXmlRecord xmlRecord, Publication publication) {
		if (!publication.getDois().isEmpty()) {
			Style style;
			if (xmlRecord.getElectronicResourceNum() != null) {
				style = xmlRecord.getElectronicResourceNum().getStyle().getFirst();
			} else {
				ElectronicResourceNum electronicResourceNum = objectFactory.createElectronicResourceNum();
				xmlRecord.setElectronicResourceNum(electronicResourceNum);
				style = objectFactory.createStyle();
				electronicResourceNum.getStyle().add(style);
			}
			setDefaultAttributes(style);
			style.setvalue(publication.getDois().stream().collect(Collectors.joining("\nhttps://doi.org/", "https://doi.org/", "")));
		}
		
		if (publication.getPageStart() != null) {
			Style style;
			if (xmlRecord.getPages() != null) {
				style = xmlRecord.getPages().getStyle().getFirst();
			} else {
				Pages pages = objectFactory.createPages();
				xmlRecord.setPages(pages);
				style = objectFactory.createStyle();
				pages.getStyle().add(style);
			}
			setDefaultAttributes(style);

			if (publication.getPageEnd() != null && !publication.getPageEnd().equals(publication.getPageStart())) {
				style.setvalue(publication.getPageStart() + "-" + publication.getPageEnd());
			} else {
				style.setvalue(publication.getPageStart());
			}
		}

		if (publication.isReply()) {
			Style style;
			if (xmlRecord.getTitles() != null) {
				if (xmlRecord.getTitles().getTitle() != null) {
					style = xmlRecord.getTitles().getTitle().getStyle().getFirst();
				} else {
					Title title = objectFactory.createTitle();
					xmlRecord.getTitles().setTitle(title);
					style = objectFactory.createStyle();
					title.getStyle().add(style);
				}
			} else {
				Titles titles = objectFactory.createTitles();
				xmlRecord.setTitles(titles);
				Title title = objectFactory.createTitle();
				xmlRecord.getTitles().setTitle(title);
				style = objectFactory.createStyle();
				title.getStyle().add(style);
			}
			setDefaultAttributes(style);
			style.setvalue(publication.getTitle());
		}

		// Only Anonymous can be removed, not the other skipped authors
		if (publication.getAuthors().isEmpty()
				&&  (xmlRecord.getContributors() != null 
				&& xmlRecord.getContributors().getAuthors() != null
				&& xmlRecord.getContributors().getAuthors().getAuthor() != null)) {
			String firstAuthor = xmlRecord.getContributors().getAuthors().getAuthor().getFirst().getStyle().getFirst().getvalue();
			if (firstAuthor.startsWith("Anonymous")) {	// format can be "Anonymous,"
				xmlRecord.getContributors().getAuthors().getAuthor().clear();
			}
		}

		if (publication.getPublicationYear() != 0) {
			Style style;
			if (xmlRecord.getDates() == null) {
				xmlRecord.setDates(objectFactory.createDates());
			}
			if (xmlRecord.getDates().getYear() == null) {
				Year year = objectFactory.createYear();
				xmlRecord.getDates().setYear(year);
				style = objectFactory.createStyle();
				year.getStyle().add(style);
			} else {
				style = xmlRecord.getDates().getYear().getStyle().getFirst();
			}

			setDefaultAttributes(style);
			style.setvalue(publication.getPublicationYear().toString());
		}
		
		// remove C7/custom7 (Article Number)
		if (xmlRecord.getCustom7() != null) {
			xmlRecord.setCustom7(null);
		}
	}

	private void setDefaultAttributes(Style style) {
		style.setFace("normal");
		style.setFont("default");
		style.setSize("100%");
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
	public ParsedAndConvertedEndnote readEndnoteXmlWithPullParser(String inputFileName) {
		List<EndnoteXmlRecord> xmlRecords = new ArrayList<>();
		List<Publication> publications = new ArrayList<>();
		int i = 0;

		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(Xml.class);
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			XMLInputFactory xif = XMLInputFactory.newInstance();
			XMLStreamReader xsr = xif.createXMLStreamReader(new FileReader(inputFileName));
	
			xsr.nextTag();
			xsr.require(START_ELEMENT, null, "xml");
			xsr.nextTag();
			xsr.require(START_ELEMENT, null, "records");
			xsr.nextTag(); // Should now be at the first EndNoteXmlRecord
			
			while (xsr.getEventType() == START_ELEMENT) {
				xsr.require(START_ELEMENT, null, "record");
				EndnoteXmlRecord xmlRecord = (EndnoteXmlRecord) unmarshaller.unmarshal(xsr);
				i++;
				xmlRecords.add(xmlRecord);
				log.debug("Record {} with recNo {}", i, xmlRecord.getRecNumber());
				if (xsr.getEventType() == CHARACTERS) {
					xsr.next(); // skip the whitespace between EndNoteXmlRecords
				}
				
				convertEndnoteXmlToPublication(xmlRecord, publications);
				if (xmlRecord.getTitles().getTitle() != null) {
					log.debug("Record: {}", getAllText(xmlRecord.getTitles().getTitle().getStyle()));
				} else {
					log.debug("Record has no Title");
				}
			}
		} catch (JAXBException | FileNotFoundException | XMLStreamException e) {
			e.printStackTrace();
		} 
		log.info("Finished with {} records", i);
		return new ParsedAndConvertedEndnote(xmlRecords, publications);
	}
	
	private void convertEndnoteXmlToPublication(EndnoteXmlRecord xmlRecord, List<Publication> xmlPublications) {
		Publication pub = new Publication();
		xmlPublications.add(pub);
		
		pub.setId(xmlRecord.getRecNumber());
		pub.setReferenceType(refTypes.getOrDefault(xmlRecord.getRefType().getName(), "JOUR"));

		if (xmlRecord.getContributors() != null) {
			if (xmlRecord.getContributors().getAuthors() != null) {
				for (Author author : xmlRecord.getContributors().getAuthors().getAuthor()) {
					// This is a difference between RIS output ("AU  - Shalimar") and XML output ("Shalimar,")
					pub.addAuthors(getAllText(author.getStyle()).replaceAll(",$", ""));
				}
			}
		}
		
		if (xmlRecord.getOrigPub() != null) {
			pub.addTitles(getAllText(xmlRecord.getOrigPub().getStyle()));
		}
		
		if (xmlRecord.getTitles() != null) {
			// in RIS: ST before TI
			if (xmlRecord.getTitles().getShortTitle() != null) {
				pub.addTitles(getAllText(xmlRecord.getTitles().getShortTitle().getStyle()));
			}
			if (xmlRecord.getTitles().getSecondaryTitle() != null) {
				pub.addJournals(getAllText(xmlRecord.getTitles().getSecondaryTitle().getStyle()));
			}
			if (xmlRecord.getTitles().getAltTitle() != null) {
				pub.addJournals(getAllText(xmlRecord.getTitles().getAltTitle().getStyle()));
			}
			if (xmlRecord.getTitles().getTertiaryTitle() != null) {
				// conferencePattern
				String j = getAllText(xmlRecord.getTitles().getTertiaryTitle().getStyle());
				if (!IORisService.conferencePattern.matcher(j).matches()) {
					pub.addJournals(j);
					pub.addTitles(j);
				}
			}
			if (xmlRecord.getTitles().getTitle() != null) {
				String title = getAllText(xmlRecord.getTitles().getTitle().getStyle());
				pub.addTitles(title);
				if (IORisService.replyPattern.matcher(title.toLowerCase()).matches()) {
					pub.setReply(true);
					pub.setTitle(title);
				}
				if (IORisService.phasePattern.matcher(title.toLowerCase()).matches()) {
					pub.setPhase(true);
				}
			}
		}
		
//		if (xmlRecord.getAltPeriodical() != null) {
//			 if (xmlRecord.getAltPeriodical().getFullTitle() != null) {
//				 pub.addJournals(getAllText(xmlRecord.getAltPeriodical().getFullTitle().getStyle()));
//			 }
//			 if (xmlRecord.getAltPeriodical().getAbbr1() != null) {
//				 pub.addJournals(getAllText(xmlRecord.getAltPeriodical().getAbbr1().getStyle()));
//			 }
//		}
		
		if (xmlRecord.getCustom7() != null) {
			pub.parsePages(getAllText(xmlRecord.getCustom7().getStyle()));
		}

		if (xmlRecord.getPages() != null) {
			pub.parsePages(getAllText(xmlRecord.getPages().getStyle()));
		}
		
		if (xmlRecord.getDates() != null && xmlRecord.getDates().getYear() != null) {
			pub.parsePublicationYear(getAllText(xmlRecord.getDates().getYear().getStyle()));
		}

		if (xmlRecord.getIsbn() != null) {
			pub.addIssns(getAllText(xmlRecord.getIsbn().getStyle()));
		}
		
		if (xmlRecord.getElectronicResourceNum() != null) {
			pub.addDois(getAllText(xmlRecord.getElectronicResourceNum().getStyle()));
		}

		pub.addReversedTitles();
		pub.fillAllAuthors();
	}

	private String getAllText(List<Style> style) {
		String line = style.stream().map(Style::getvalue).collect(Collectors.joining(" "));
		return IORisService.unusualWhiteSpacePattern.matcher(line).replaceAll(" ");
	}
}
