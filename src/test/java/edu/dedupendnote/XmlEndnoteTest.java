package edu.dedupendnote;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import edu.dedupendnote.domain.xml.endnote.EndnoteXmlRecord;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TestConfiguration
class XmlEndnoteTest {

	@Test
	void readEndNoteXml() throws FileNotFoundException, XMLStreamException {
		XMLInputFactory f = XMLInputFactory.newFactory();
		/*
		 * The file with "_pp" is pretty printed XML, the file without is the original EndNote XML output.
		 * Following code can handle both cases thanks to interspersed "while (sr.isWhiteSpace()) sr.next();".
		 * No configuration option found in FasterXML to do this automatically.
		 */
		File inputFile = new File("C:\\Users\\geert\\dedupendnote_files\\xml/BIG_SET_part_pp.xml");
		// File inputFile = new File("C:\\Users\\geert\\dedupendnote_files\\xml/BIG_SET_part_pp.xml");
		XMLStreamReader sr = f.createXMLStreamReader(new FileInputStream(inputFile));

		/*
		 * See also https://stackify.com/java-xml-jackson/
		 */
		EndnoteXmlRecord xmlRecord;
		try {
			/*
			 * By changing the defaultUse Wrapper to false, 
			 * it is not necessary to use "@JacksonXmlElementWrapper(useWrapping = false)" for every List<...> element.
			 */
			JacksonXmlModule xmlModule = new JacksonXmlModule();
			xmlModule.setDefaultUseWrapper(false);
			XmlMapper mapper  = new XmlMapper(xmlModule);
			
			mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
			mapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);

			// use this isWhiteSpace check to handle pretty printed XML files
			while (sr.isWhiteSpace()) sr.next();
			sr.next();
			// System.err.println("NAme: "+ sr.getName());
			sr.next(); // to point to <root>

			while (sr.isWhiteSpace()) sr.next();
			// System.err.println("NAme: "+ sr.getName());
			sr.next(); // to point to root-element under root

			xmlRecord = mapper.readValue(sr, EndnoteXmlRecord.class);
			// sr now points to matching END_ELEMENT, so move forward
			System.err.println("First endnoteXmlRecord: " + xmlRecord.getTitles().getTitle().getStyle().get(0).getvalue());
			log.error("Reached end of first endnoteXmlRecord: {}", xmlRecord);
			sr.next(); // should verify it's either closing root or new start, left as exercise
			xmlRecord = mapper.readValue(sr, EndnoteXmlRecord.class);
			System.err.println("Second endnoteXmlRecord: " + xmlRecord);
			
			int i = 3;
			while (i < 1000) {
				sr.next(); // should verify it's either closing root or new start, left as exercise
				while (sr.isWhiteSpace()) sr.next();
				if (sr.isEndElement() && sr.getLocalName().equals("records")) {
					break;
				}
				xmlRecord = mapper.readValue(sr, EndnoteXmlRecord.class);
				System.err.println("EndnoteRecord " + i + ": " + xmlRecord);
				i++;
			}
			sr.close();		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
