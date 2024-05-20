package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.domain.ParsedAndConvertedZotero;
import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.XmlTestFile;
import edu.dedupendnote.domain.xml.zotero.Xml;
import edu.dedupendnote.domain.xml.zotero.ZoteroXmlRecord;
import edu.dedupendnote.services.IORisService;
import edu.dedupendnote.services.IOZoteroXmlService;
import edu.dedupendnote.services.IoService;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TestConfiguration
class XmlZoteroTest {
	private IoService ioService = new IORisService();
	private IOZoteroXmlService ioZoteroXmlService = new IOZoteroXmlService();

	@Test
	void testReadWithPullParser() throws JAXBException, XMLStreamException, IOException {
		String prefix = System.getProperty("user.home") + "/dedupendnote_files/xml/zotero/";
		// First file is a subset of BIG_SET: first author >= almadi and first author <= andriulli, order by author = 465 records. XML is pretty-printed
		XmlTestFile shortTest = new XmlTestFile(prefix + "20240209_Upd_All_109_pp.xml", prefix + "20240209_Upd_All_109.ris", 109);
		
		ParsedAndConvertedZotero results = ioZoteroXmlService.readZoteroXmlWithPullParser(shortTest.xmlInputFileName());
		assertThat(results.publications()).hasSize(shortTest.noOfRecords());

		// testing marshalling 1 record
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		ZoteroXmlRecord xmlRecord = results.xmlRecords().get(0);
		JAXBContext jaxbContext = JAXBContext.newInstance(Xml.class);
		jaxbContext.createMarshaller().marshal(xmlRecord, bos);
		
		String firstRecordAsXml = new String(bos.toByteArray());
		log.info("Written back as:\n{}", firstRecordAsXml);

		Path expectedFirstRecordPath = Paths.get(prefix + "20240209_Upd_All_109_written_back_rec_1.xml");
		List<String> allLines = Files.readAllLines(expectedFirstRecordPath);
		String expectedFirstRecord = allLines.stream().collect(Collectors.joining(""));
		
		assertThat(firstRecordAsXml).isEqualTo(expectedFirstRecord);

		// parse the textInputFiles and compare with the parses of the xmlInputFiles
		List<Publication> publicationsFromText = ioService.readPublications(shortTest.textInputFileName());
		
		assertThat(publicationsFromText).hasSize(shortTest.noOfRecords());
		
		int startRecNo = 0;
		/*
		 * The Zotero XML format misses some fields which appear in the RIS format,
		 * a.o. T3 / Titles/TertiaryTitle 
		 */
		List<Integer> publicationsToSkip = List.of(5, 14, 49, 52);

		for (int i = startRecNo; i < shortTest.noOfRecords(); i++) {
			if (publicationsToSkip.contains(i)) continue;
			Publication risPub = publicationsFromText.get(i);
			Publication xmlPub = results.publications().get(i);
			xmlPub.setId(Integer.valueOf(i+1).toString());
			assertThat(xmlPub).as("Comparing publication " + i).usingRecursiveComparison().isEqualTo(risPub);
		}
	}
}
