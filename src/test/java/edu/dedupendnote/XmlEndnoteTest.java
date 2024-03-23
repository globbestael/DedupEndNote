package edu.dedupendnote;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.xml.endnote.Author;
import edu.dedupendnote.domain.xml.endnote.EndnoteXmlRecord;
import edu.dedupendnote.services.IOService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TestConfiguration
class XmlEndnoteTest {

	String homeDir = System.getProperty("user.home");

	@Test
	void readEndNoteXml() throws FileNotFoundException, XMLStreamException {
		XMLInputFactory f = XMLInputFactory.newFactory();
		/*
		 * File is a subset of BIG_SET:
		 * - first author >= almadi and first author <= andriulli
		 * - order by author
		 * 465 records
		 * 
		 * The file with "_pp" is pretty printed XML, the file without is the original EndNote XML output.
		 * Following code can handle both cases thanks to interspersed "while (sr.isWhiteSpace()) sr.next();".
		 * No configuration option found in FasterXML to do this automatically.
		 */
		File risInputFile = new File(homeDir + "/dedupendnote_files/xml/BIG_SET_part.txt");
		IOService ioService = new IOService();
		List<Publication> risPublications = ioService.readPublications(risInputFile.getAbsolutePath());
		log.error("Eerste RIS record: {}", risPublications.get(0));
		
		File xmlInputFile = new File(homeDir + "/dedupendnote_files/xml/BIG_SET_part_pp.xml");
		// File xmlInputFile = new File(homeDir + "/dedupendnote_files/xml/BIG_SET_part.xml");
		XMLStreamReader sr = f.createXMLStreamReader(new FileInputStream(xmlInputFile));

		/*
		 * See also https://stackify.com/java-xml-jackson/
		 */
		EndnoteXmlRecord xmlRecord;
		List<Publication> xmlPublications = new ArrayList<>();
		
		/*
		 * By changing the defaultUse Wrapper to false, 
		 * it is not necessary to use "@JacksonXmlElementWrapper(useWrapping = false)" for every List<...> element.
		 */
		JacksonXmlModule xmlModule = new JacksonXmlModule();
		xmlModule.setDefaultUseWrapper(false);
		XmlMapper xmlMapper  = new XmlMapper(xmlModule);
		
		xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
		xmlMapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
		
		try {
			// use this isWhiteSpace check to handle pretty printed XML files
			while (sr.isWhiteSpace()) sr.next();
			sr.next();
			// System.err.println("NAme: "+ sr.getName());
			sr.next(); // to point to <root>

			while (sr.isWhiteSpace()) sr.next();
			// System.err.println("NAme: "+ sr.getName());
			sr.next(); // to point to root-element under root

			xmlRecord = xmlMapper.readValue(sr, EndnoteXmlRecord.class);
			// sr now points to matching END_ELEMENT, so move forward
			
			convertToPublication(xmlRecord, xmlPublications);
			System.err.println("First endnoteXmlRecord: " + xmlRecord.getTitles().getTitle().getStyle().get(0).getvalue());
			log.error("Reached end of first endnoteXmlRecord: {}", xmlRecord);
			
			sr.next(); // should verify it's either closing root or new start, left as exercise
			xmlRecord = xmlMapper.readValue(sr, EndnoteXmlRecord.class);
			convertToPublication(xmlRecord, xmlPublications);
			System.err.println("Second endnoteXmlRecord: " + xmlRecord);
			
			int i = 3;
			while (i < 1000) {
				sr.next(); // should verify it's either closing root or new start, left as exercise
				while (sr.isWhiteSpace()) sr.next();
				if (sr.isEndElement() && sr.getLocalName().equals("records")) {
					break;
				}
				xmlRecord = xmlMapper.readValue(sr, EndnoteXmlRecord.class);
				convertToPublication(xmlRecord, xmlPublications);
				System.err.println("EndnoteRecord " + i + ": " + xmlRecord);
				i++;
			}
			sr.close();		

			String xml = xmlMapper.writeValueAsString(xmlRecord);
			/*
			 * The output file has NOT the same format as the input file, see e.g. the style element
			 * Input:
			 * 			<pages>
			 * 				<style face="normal" font="default" size="100%">278-285</style>
			 * 			</pages>
			 * 
			 * Output:
			 * 	<pages>
			 * 		<style>
			 * 			<color/>
			 * 			<face>normal</face>
			 * 			<font>default</font>
			 * 			<size>100%</size>179-192</style>
			 * 	</pages>
			 */
			System.err.println("Written: " + xml);
		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
//		for (int i = 0; i < xmlPublications.size(); i++) {
//			if (! risPublications.get(i).equals(xmlPublications.get(i))) {
//				log.error("Record {} is different\n{}\n{}", i, risPublications.get(i), xmlPublications.get(i));
//			}
//		}
		
	}
	
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
		pub.addTitles(xmlRecord.getTitles().getShortTitle().getStyle().get(0).getvalue());
		pub.addTitles(xmlRecord.getTitles().getTitle().getStyle().get(0).getvalue());
		
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
