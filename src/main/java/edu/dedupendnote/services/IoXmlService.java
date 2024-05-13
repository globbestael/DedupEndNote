package edu.dedupendnote.services;

import java.util.Map;

public abstract class IoXmlService implements IoService {

	// TODO: Expand to all refTypes?
	protected Map<String,String> refTypes = Map.of(
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

}
