package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.domain.ParsedAndConvertedEndnote;
import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.XmlTestFile;
import edu.dedupendnote.domain.xml.endnote.EndnoteXmlRecord;
import edu.dedupendnote.domain.xml.endnote.Xml;
import edu.dedupendnote.services.IOService;
import edu.dedupendnote.services.IOXmlService;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TestConfiguration
class XmlEndnoteTest {
	private IOService ioService = new IOService();
	private IOXmlService ioXmlService = new IOXmlService();

	@Test
	void testReadWithPullParser() throws JAXBException, XMLStreamException, IOException {
		String prefix = System.getProperty("user.home") + "/dedupendnote_files/xml/";
		// First file is a subset of BIG_SET: first author >= almadi and first author <= andriulli, order by author = 465 records. XML is pretty-printed
		XmlTestFile shortTest = new XmlTestFile(new File(prefix + "BIG_SET_part_pp.xml"), new File(prefix + "BIG_SET_part.txt"), 465);
		XmlTestFile longTest = new XmlTestFile(new File(prefix + "BIG_SET.xml"), new File(prefix + "BIG_SET.txt"),  52828);

		// testing a small pretty printed XML files
		ParsedAndConvertedEndnote results = ioXmlService.readEndnoteXmlWithPullParser(shortTest.xmlInputFile());
		assertThat(results.publications()).hasSize(shortTest.noOfRecords());

		// testing a big XML file
		results = ioXmlService.readEndnoteXmlWithPullParser(longTest.xmlInputFile());
		assertThat(results.publications()).hasSize(longTest.noOfRecords());
		
		// testing marshalling 1 record
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		EndnoteXmlRecord xmlRecord = results.xmlRecords().get(0);
		JAXBContext jaxbContext = JAXBContext.newInstance(Xml.class);
		jaxbContext.createMarshaller().marshal(xmlRecord, bos);
		
		String firstRecordAsXml = new String(bos.toByteArray());
		log.info("Written back as:\n{}", firstRecordAsXml);
		
		Path expectedFirstRecordPath = Paths.get(prefix + "BIG_SET_written_back_rec_1.xml");
		List<String> allLines = Files.readAllLines(expectedFirstRecordPath);
		String expectedFirstRecord = allLines.stream().collect(Collectors.joining(""));
		
		assertThat(firstRecordAsXml).isEqualTo(expectedFirstRecord);
		
		// parse the textInputFiles and compare with the parses of the xmlInputFiles
		
		List<Publication> publicationsFromText = ioService.readPublications(longTest.textInputFile().toString());
		
		assertThat(publicationsFromText).hasSize(longTest.noOfRecords());
		
		int startRecNo = 0;
		for (int i = startRecNo; i < longTest.noOfRecords(); i++) {
			Publication risPub = publicationsFromText.get(i);
			Publication xmlPub = results.publications().get(i);
			assertThat(xmlPub).as("Comparing publication " + i).usingRecursiveComparison().isEqualTo(risPub);
		}
	}
	
}
