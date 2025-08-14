package edu.dedupendnote.services;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UtilitiesService {

	/*
	 * detectBom: Detect UTF-8 BOM
	 *
	 * Apache commons.io BOMInputStream can't work with BufferedReader / FileReader
	 * and with try block
	 *
	 * See: https://stackoverflow.com/questions/4897876/reading-utf-8-bom-marker
	 *
	 * See also:
	 * https://mkyong.com/java/java-how-to-add-and-remove-bom-from-utf-8-file/
	 */
	public static boolean detectBom(String inputFileName) {
		boolean hasBom = false;
		try (BufferedReader br = new BufferedReader(new FileReader(inputFileName))) {
			String line = br.readLine();
			hasBom = line.startsWith("\uFEFF");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return hasBom;
	}

	public static String createOutputFileName(String fileName, Boolean markMode) {
		String extension = StringUtils.getFilenameExtension(fileName);
		return fileName.replaceAll("." + extension + "$",
				(Boolean.TRUE.equals(markMode) ? "_mark." : "_deduplicated.") + extension);
	}
}
