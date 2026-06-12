package edu.dedupendnote.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UtilitiesService {

	/*
	 * detectBom: Detect UTF-8 BOM
	 *
	 * See: https://stackoverflow.com/questions/4897876/reading-utf-8-bom-marker
	 *
	 * See also:
	 * https://mkyong.com/java/java-how-to-add-and-remove-bom-from-utf-8-file/
	 */
	public static boolean detectBom(Path inputPath) {
		boolean hasBom = false;
		try (BufferedReader br = Files.newBufferedReader(inputPath)) {
			String line = br.readLine();
			hasBom = line != null && line.startsWith("\uFEFF");
		} catch (IOException e) {
			log.error("Error detecting BOM in file {}", inputPath, e);
		}
		return hasBom;
	}

	public static Path createPath(Path inputPath, @Nullable String addition, String newExtension) {
		if (newExtension == null || newExtension.isBlank()) {
			throw new IllegalArgumentException("newExtension must not be null or blank");
		}
		Path parent = inputPath.getParent();
		if (parent == null) {
			throw new IllegalArgumentException("inputPath must have a parent directory: " + inputPath);
		}
		String baseName = removeFileExtension(inputPath.getFileName().toString());
		String suffix = (addition == null || addition.isBlank()) ? "" : addition;
		return parent.resolve(baseName + suffix + "." + newExtension);
	}

	/*
	 * Based on https://www.baeldung.com/java-filename-without-extension
	 */	
	public static String removeFileExtension(String filename) {
		if (filename == null || filename.isEmpty()) {
			return filename;
		}
		return filename.replaceAll("(?<!^)[.][^.]*$", "");
	}

	public static Path getSessionDir(String uploadDir, UUID sessionId) {
		return Path.of(uploadDir).toAbsolutePath().normalize().resolve(sessionId.toString());
	}

	public static Path resolveInSessionDir(String uploadDir, UUID sessionId, @Nullable String userFileName) {
		Path sessionDir = getSessionDir(uploadDir, sessionId);
		if (userFileName == null || userFileName.isEmpty()) {
			throw new IllegalArgumentException("Filename must not be null or empty");
		}
		// OWASP A05 risk: On Logback, no JNDI injection risk, but enables log forging if the filename contains newlines.
		if (userFileName.contains("\r") || userFileName.contains("\n")) {
			throw new IllegalArgumentException("Filename must not contain line-break characters");
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
		Path resolved = sessionDir.resolve(parsed).normalize();
		if (!resolved.startsWith(sessionDir)) {
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
