package edu.dedupendnote;

import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.xml.endnote.Author;
import edu.dedupendnote.domain.xml.endnote.EndnoteXmlRecord;
import edu.dedupendnote.domain.xml.endnote.Style;
import edu.dedupendnote.domain.xml.endnote.Xml;
import edu.dedupendnote.services.IOService;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TestConfiguration
class XmlEndnoteTest {
	private IOService ioService = new IOService();
	String wssessionId = "";

	private record TestFile(File xmlInputFile, File textInputFile, int noOfRecords) {};
	private record ParsedAndConverted(List<EndnoteXmlRecord> xmlRecords, List<Publication> publications) {};
	
	@Test
	void testReadWithPullParser() throws JAXBException, XMLStreamException, IOException {
		String prefix = System.getProperty("user.home") + "/dedupendnote_files/xml/";
		// First file is a subset of BIG_SET: first author >= almadi and first author <= andriulli, order by author = 465 records. XML is pretty-printed
		TestFile shortTest = new TestFile(new File(prefix + "BIG_SET_part_pp.xml"), new File(prefix + "BIG_SET_part.txt"), 465);
		TestFile longTest = new TestFile(new File(prefix + "BIG_SET.xml"), new File(prefix + "BIG_SET.txt"),  52828);

		// testing a small pretty printed XML files
		ParsedAndConverted results = readWithPullParser(shortTest.xmlInputFile);
		assertThat(results.publications).hasSize(shortTest.noOfRecords);

		// testing a big XML file
		results = readWithPullParser(longTest.xmlInputFile);
		assertThat(results.publications).hasSize(longTest.noOfRecords);
		
		// testing marshalling 1 record
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		EndnoteXmlRecord xmlRecord = results.xmlRecords.get(0);
		JAXBContext jaxbContext = JAXBContext.newInstance(Xml.class);
		jaxbContext.createMarshaller().marshal(xmlRecord, bos);
		
		String firstRecordAsXml = new String(bos.toByteArray());
		log.info("Written back as:\n{}", firstRecordAsXml);
		
		Path expectedFirstRecordPath = Paths.get(prefix + "BIG_SET_written_back_rec_1.xml");
		List<String> allLines = Files.readAllLines(expectedFirstRecordPath);
		String expectedFirstRecord = allLines.stream().collect(Collectors.joining(""));
		
		assertThat(firstRecordAsXml).isEqualTo(expectedFirstRecord);
		
		// parse the textInputFiles and compare with the parses of the xmlInputFiles
		
		List<Publication> publicationsFromText = ioService.readPublications(longTest.textInputFile.toString());
		
		assertThat(publicationsFromText).hasSize(longTest.noOfRecords);
		
		int startRecNo = 0;
		for (int i = startRecNo; i < longTest.noOfRecords; i++) {
			Publication risPub = publicationsFromText.get(i);
			Publication xmlPub = results.publications.get(i);
			assertThat(xmlPub).as("Comparing publication " + i).usingRecursiveComparison().isEqualTo(risPub);
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
	ParsedAndConverted readWithPullParser(File inputFile) throws JAXBException, FileNotFoundException, XMLStreamException {
		JAXBContext jaxbContext = JAXBContext.newInstance(Xml.class);
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
		XMLInputFactory xif = XMLInputFactory.newInstance();
		XMLStreamReader xsr = xif.createXMLStreamReader(new FileReader(inputFile));

		int i = 0;
		List<EndnoteXmlRecord> xmlRecords = new ArrayList<>();
		List<Publication> publications = new ArrayList<>();

		xsr.nextTag();
		xsr.require(START_ELEMENT, null, "xml");
		xsr.nextTag();
		xsr.require(START_ELEMENT, null, "records");
		xsr.nextTag(); // Should now be at the first EndNoteXmlRecord
		
		while (xsr.getEventType() == START_ELEMENT) {
			xsr.require(START_ELEMENT, null, "record");
			EndnoteXmlRecord xmlRecord = (EndnoteXmlRecord) unmarshaller.unmarshal(xsr);
			xmlRecords.add(xmlRecord);
			System.err.println("Record " + ++i + " with recNo " + xmlRecord.getRecNumber());
			if (xsr.getEventType() == CHARACTERS) {
				xsr.next(); // skip the whitespace between EndNoteXmlRecords
			}
			
			convertToPublication(xmlRecord, publications);
			if (xmlRecord.getTitles().getTitle() != null) {
				System.err.println("Record: " + getAllText(xmlRecord.getTitles().getTitle().getStyle()));
			} else {
				System.err.println("Record: has no Title");
			}
		}
		System.out.println("Finished with " + i + " records");
		return new ParsedAndConverted(xmlRecords, publications);
	}
	
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

	private void convertToPublication(EndnoteXmlRecord xmlRecord, List<Publication> xmlPublications) {
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
			if ("24035".equals(pub.getId())) {
				log.info("hier");
			}
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
				if (!IOService.conferencePattern.matcher(j).matches()) {
					pub.addJournals(j);
					pub.addTitles(j);
				}
			}
			if (xmlRecord.getTitles().getTitle() != null) {
				String title = getAllText(xmlRecord.getTitles().getTitle().getStyle());
				pub.addTitles(title);
				if (IOService.replyPattern.matcher(title.toLowerCase()).matches()) {
					pub.setReply(true);
					pub.setTitle(title);
				}
				if (IOService.phasePattern.matcher(title.toLowerCase()).matches()) {
					pub.setPhase(true);
				}
			}
		}
		
		if (xmlRecord.getAltPeriodical() != null) {
			// if (xmlRecord.getAltPeriodical().getFullTitle() != null) pub.addJournals(getAllText(xmlRecord.getAltPeriodical().getFullTitle().getStyle()));
			// if (xmlRecord.getAltPeriodical().getAbbr1() != null) pub.addJournals(getAllText(xmlRecord.getAltPeriodical().getAbbr1().getStyle()));
		}
		
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
		return IOService.unusualWhiteSpacePattern.matcher(line).replaceAll(" ");
	}

}
