package edu.dedupendnote.services;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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

	public static String createOutputFileName(String fileName, boolean markMode) {
		String extension = StringUtils.getFilenameExtension(fileName);
		return fileName.replaceAll("." + extension + "$",
				(Boolean.TRUE.equals(markMode) ? "_mark." : "_deduplicated.") + extension);
	}

	/*
	 * From: https://www.baeldung.com/java-convert-roman-arabic
	 */
	enum RomanNumeral {
		I(1), IV(4), V(5), IX(9), X(10), XL(40), L(50), XC(90), C(100), CD(400), D(500), CM(900), M(1000);

		private int value;

		RomanNumeral(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}

		public static List<RomanNumeral> getReverseSortedValues() {
			return Arrays.stream(values()).sorted(Comparator.comparing((RomanNumeral e) -> e.value).reversed())
					.collect(Collectors.toList());
		}
	}

	public static int romanToArabic(String input) {
		String romanNumeral = input.toUpperCase();
		int result = 0;

		List<RomanNumeral> romanNumerals = RomanNumeral.getReverseSortedValues();

		int i = 0;

		while ((romanNumeral.length() > 0) && (i < romanNumerals.size())) {
			RomanNumeral symbol = romanNumerals.get(i);
			if (romanNumeral.startsWith(symbol.name())) {
				result += symbol.getValue();
				romanNumeral = romanNumeral.substring(symbol.name().length());
			} else {
				i++;
			}
		}

		if (romanNumeral.length() > 0) {
			throw new IllegalArgumentException(input + " cannot be converted to a Roman Numeral");
		}

		return result;
	}
}
