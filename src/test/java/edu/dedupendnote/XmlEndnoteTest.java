package edu.dedupendnote;

import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.xml.endnote.Author;
import edu.dedupendnote.domain.xml.endnote.EndnoteXmlRecord;
import edu.dedupendnote.domain.xml.endnote.Xml;
import edu.dedupendnote.services.IOService;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TestConfiguration
class XmlEndnoteTest {

	private record TestFile(File xmlInputFile, File textInputFile, int noOfRecords) {};
	private record ParsedAndConverted(List<EndnoteXmlRecord> xmlRecords, List<Publication> publications) {};
	
	@Test
	void testReadWithPullParser() throws FileNotFoundException, JAXBException, XMLStreamException {
		String prefix = System.getProperty("user.home") + "/dedupendnote_files/xml/";
		// First file is a subset of BIG_SET: first author >= almadi and first author <= andriulli, order by author = 465 records. XML is pretty-printed
		TestFile shortTest = new TestFile(new File(prefix + "BIG_SET_part_pp.xml"), new File(prefix + "BIG_SET_part.txt"), 465);
		TestFile longTest = new TestFile(new File(prefix + "BIG_SET.xml"), new File(prefix + "BIG_SET.txt"),  52828);
		
		ParsedAndConverted results = readWithPullParser(shortTest.xmlInputFile);
		assertThat(results.publications).hasSize(shortTest.noOfRecords);

		results = readWithPullParser(longTest.xmlInputFile);
		assertThat(results.publications).hasSize(longTest.noOfRecords);
		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		EndnoteXmlRecord xmlRecord = results.xmlRecords.get(0);
		JAXBContext jaxbContext = JAXBContext.newInstance(Xml.class);
		jaxbContext.createMarshaller().marshal(xmlRecord, bos);
		
		System.err.println("Written back as: " + new String(bos.toByteArray()));

		// TODO: parse the textInputFiles and compare with the parses of the xmlInputFiles
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
	 * The first 2 elements are skipped. By using nextTag() whitespace is skipped automaically.
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
				System.err.println("Record: " + xmlRecord.getTitles().getTitle().getStyle().get(0).getvalue());
			} else {
				System.err.println("Record: has no Title");
			}
		}
		System.out.println("Finished with " + i + " records");
		return new ParsedAndConverted(xmlRecords, publications);
	}
	
	// TODO: Expand to all refTypes?
	Map<String,String> refTypes = Map.of(
			"Journal Article", "JOUR",
			"Book Section", "CHAP");

	private void convertToPublication(EndnoteXmlRecord xmlRecord, List<Publication> xmlPublications) {
		Publication pub = new Publication();
		xmlPublications.add(pub);
		
		if (xmlRecord.getRecNumber().equals("12451")) {
			log.equals("hier");
		}
		pub.setId(xmlRecord.getRecNumber());
		pub.setReferenceType(refTypes.get(xmlRecord.getRefType().getName()));

		if (xmlRecord.getContributors() != null) {
			if (xmlRecord.getContributors().getAuthors() != null) {
				for (Author author : xmlRecord.getContributors().getAuthors().getAuthor()) {
					pub.addAuthors(author.getStyle().get(0).getvalue());
				}
			}
		}
		
		if (xmlRecord.getOrigPub() != null) {
			pub.addTitles(xmlRecord.getOrigPub().getStyle().get(0).getvalue());
		}
		// in RIS: ST before TI
		if (xmlRecord.getTitles().getShortTitle() != null) pub.addTitles(xmlRecord.getTitles().getShortTitle().getStyle().get(0).getvalue());
		if (xmlRecord.getTitles().getTitle() != null) pub.addTitles(xmlRecord.getTitles().getTitle().getStyle().get(0).getvalue());
		
		if (xmlRecord.getTitles() != null) {
			if (xmlRecord.getTitles().getSecondaryTitle() != null) pub.addJournals(xmlRecord.getTitles().getSecondaryTitle().getStyle().get(0).getvalue());
			if (xmlRecord.getTitles().getAltTitle() != null) pub.addJournals(xmlRecord.getTitles().getAltTitle().getStyle().get(0).getvalue());
		}
		
		if (xmlRecord.getAltPeriodical() != null) {
			// if (xmlRecord.getAltPeriodical().getFullTitle() != null) pub.addJournals(xmlRecord.getAltPeriodical().getFullTitle().getStyle().get(0).getvalue());
			// if (xmlRecord.getAltPeriodical().getAbbr1() != null) pub.addJournals(xmlRecord.getAltPeriodical().getAbbr1().getStyle().get(0).getvalue());
		}
		
		if (xmlRecord.getPeriodical() != null) {
			// if (xmlRecord.getPeriodical().getFullTitle() != null) pub.addJournals(xmlRecord.getPeriodical().getFullTitle().getStyle().get(0).getvalue());
			// if (xmlRecord.getPeriodical().getAbbr1() != null) pub.addJournals(xmlRecord.getPeriodical().getAbbr1().getStyle().get(0).getvalue());
			if (xmlRecord.getTitles() != null && xmlRecord.getTitles().getTertiaryTitle() != null) {
				// conferencePattern
				String j = xmlRecord.getTitles().getTertiaryTitle().getStyle().get(0).getvalue();
				if (!IOService.conferencePattern.matcher(j).matches()) {
					pub.addJournals(j);
					pub.addTitles(j);
				}
			}
		}

		if (xmlRecord.getCustom7() != null) {
			pub.parsePages(xmlRecord.getCustom7().getStyle().get(0).getvalue());
		}

		if (xmlRecord.getPages() != null) pub.parsePages(xmlRecord.getPages().getStyle().get(0).getvalue());
		
		if (xmlRecord.getDates() != null && xmlRecord.getDates().getYear() != null) {
			pub.setPublicationYear(Integer.valueOf(xmlRecord.getDates().getYear().getStyle().get(0).getvalue()));
		}

		if (xmlRecord.getIsbn() != null) pub.addIssns(xmlRecord.getIsbn().getStyle().get(0).getvalue());
		
		if (xmlRecord.getElectronicResourceNum() != null) pub.addDois(xmlRecord.getElectronicResourceNum().getStyle().get(0).getvalue());

		pub.addReversedTitles();
		pub.fillAllAuthors();
	}
}
