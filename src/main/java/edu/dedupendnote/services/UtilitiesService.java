package edu.dedupendnote.services;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import edu.dedupendnote.domain.DeduplicationMode;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
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
	public static boolean detectBom(Path inputPath) {
		boolean hasBom = false;
		try (BufferedReader br = new BufferedReader(new FileReader(inputPath.toFile()))) {
			String line = br.readLine();
			hasBom = line != null && line.startsWith("\uFEFF");
		} catch (IOException e) {
			log.error("Error detecting BOM in file {}", inputPath, e);
		}
		return hasBom;
	}

	public static String createOutputFileName(String fileName, DeduplicationMode mode) {
		String extension = StringUtils.getFilenameExtension(fileName);
		if (extension == null || extension.isEmpty()) {
			return fileName + (mode == DeduplicationMode.MARK ? "_mark" : "_deduplicated");
		}
		return fileName.replaceAll("\\." + Pattern.quote(extension) + "$",
				(mode == DeduplicationMode.MARK ? "_mark." : "_deduplicated.") + extension);
	}

	public static Path createOutputPath(Path inputPath, DeduplicationMode mode) {
		String outputFileName = createOutputFileName(inputPath.getFileName().toString(), mode);
		Path parent = inputPath.getParent();
		if (parent == null) {
			throw new IllegalArgumentException("inputPath must have a parent directory: " + inputPath);
		}
		return parent.resolve(outputFileName);
	}

	/**
	 * Returns the inputfilename with the last or all extensions removed. A dotfile is safe.
	 * 
	 * Source: https://www.baeldung.com/java-filename-without-extension
	 * 
	 * @param filename
	 * @param removeAllExtensions
	 * @return
	 */
	public static String removeFileExtension(String filename, boolean removeAllExtensions) {
		if (filename == null || filename.isEmpty()) {
			return filename;
		}

		String extPattern = "(?<!^)[.]" + (removeAllExtensions ? ".*" : "[^.]*$");
		return filename.replaceAll(extPattern, "");
	}

	public static String removeFileExtension(String filename) {
		return removeFileExtension(filename, false);
	}

	/**
	 * Resolves {@code userFileName} within {@code uploadDir}, rejecting any input that
	 * contains path separators, parent-directory references, or is absolute.
	 *
	 * @throws IllegalArgumentException if the filename is null, empty, absolute, contains
	 *     path separators, or is a directory reference ({@code ..} / {@code .})
	 */
	public static Path resolveInUploadDir(String uploadDir, @Nullable String userFileName) {
		if (userFileName == null || userFileName.isEmpty()) {
			throw new IllegalArgumentException("Filename must not be null or empty");
		}
		Path parsed = Path.of(userFileName);
		if (parsed.isAbsolute() || parsed.getNameCount() != 1) {
			throw new IllegalArgumentException(
					"Filename must be a simple name with no path separators: " + userFileName);
		}
		String name = parsed.toString();
		if (name.equals("..") || name.equals(".")) {
			throw new IllegalArgumentException("Filename must not be a directory reference: " + userFileName);
		}
		Path base = Path.of(uploadDir).toAbsolutePath().normalize();
		Path resolved = base.resolve(parsed).normalize();
		if (!resolved.startsWith(base)) {
			throw new IllegalArgumentException("Path traversal attempt rejected: " + userFileName);
		}
		return resolved;
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
					.toList();
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

	static boolean setsContainSameString(Set<String> set1, Set<String> set2) {
		if (set1.isEmpty() || set2.isEmpty()) {
			return false;
		}
		Set<String> common = new HashSet<>(set1);
		common.retainAll(set2);
		return !common.isEmpty();
	}
}
