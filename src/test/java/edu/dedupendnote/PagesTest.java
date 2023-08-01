package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.domain.Publication;

@TestConfiguration
public class PagesTest {

	@ParameterizedTest(name = "{index}: parsePages({0})=({1},{2},{3})")
	@MethodSource("argumentProvider")
	void parsePagesTest(String pages, String start, String end, String pageForComparison) {
		Publication r = new Publication();
		r.parsePages(pages);

		assertThat(start).as("PageStart comparison").isEqualTo(r.getPageStart());
		assertThat(end).as("PageEnd comparison").isEqualTo(r.getPageEnd());
		assertThat(pageForComparison).as("Input '%s' has wrong pageForComparison", pages)
			.isEqualTo(r.getPageForComparison());
	}

	static Stream<Arguments> argumentProvider() {
		return Stream.of(arguments("1", "1", null, "1"), arguments("1-2", "1", "2", "1"),
				arguments("12-4", "12", "14", "12"), arguments("1251-4", "1251", "1254", "1251"),
				arguments("12-14", "12", "14", "12"),
				arguments("12-4; author reply 15", "12", "4; author reply 15", "12"),
				arguments("12; author reply 15", "12; author reply 15", null, "12"),
				arguments("12-14, 17-18", "12", "14, 17-18", "12"), arguments("S12-14", "S12", "S14", "12"),
				arguments("S12-S14", "S12", "S14", "12"), arguments("S99-103", "S99", "103", "99"),
				arguments("S-12", "S12", null, "12"), arguments("S-12-S-14", "S12", "S-14", "12"),
				arguments("1165a-1166A", "1165a", "1166A", "1165"), arguments("11+136", "11+136", null, "11"),
				arguments("11-14+136", "11", "14+136", "11"), arguments("11+136-7", "11+136", "11+137", "11"), // !
				arguments("ii22-ii33", "ii22", "ii33", "22"), arguments("ii-ix", "ii", "ix", null),
				arguments("UNSP e12345", "e12345", null, "12345"), arguments("1-125", "1", "125", "125"), // books
																											// /
																											// reports/
																											// ...
																											// starting
																											// at
																											// page
																											// 1
				arguments("1-99", "1", "99", "1"), arguments("A1-A125", "A1", "A125", "125"),
				arguments("A1-A99", "A1", "A99", "1"), arguments("001-099", "1", "99", "1"), // leading
																								// zeros
				arguments("A1-A099", "A1", "A099", "1"));
	}

}