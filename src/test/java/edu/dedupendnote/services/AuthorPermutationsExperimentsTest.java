package edu.dedupendnote.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;

import lombok.extern.slf4j.Slf4j;

/*
 * Preliminary tests for producing ALL permutations of complex names.
 * The current implementation (Record::addAuthors(...)) uses only the first permutation: "De Brouwer de Boer, A." --> "Brouwer de Boer, A. D."
 * 
 * Results: the number of permutations are staggering. See testPermutations3: 2187 permutations for 7 authors
 */

@Disabled("Experiment that will not be pursuited")
@Slf4j
@TestConfiguration
public class AuthorPermutationsExperimentsTest {

	// from: https://stackoverflow.com/questions/17192796/generate-all-combinations-from-multiple-lists 
    void generatePermutations(List<List<String>> lists, List<String> result, int depth, String current) {
        if (depth == lists.size()) {
            result.add(current);
            return;
        }

        for (int i = 0; i < lists.get(depth).size(); i++) {
            generatePermutations(lists, result, depth + 1, current + ("".equals(current) ? "" : "; ") + lists.get(depth).get(i));
        }
    }
    
    private List<String> generatePermutedAuthorLists(List<String> authors) {
    	List<List<String>> authorsList = new ArrayList<List<String>>();

		for (String author : authors) {
			log.error("Author {}", author);
			List<String> a = new ArrayList<>();
			authorsList.add(a);
			author = author.replaceAll("-", " ");
			// get the record version with only initials
			List<String> twoParts = Arrays.asList(author.split(", "));
			a.add(twoParts.get(0).toLowerCase() + " " + twoParts.get(1).replaceAll("[^A-Z]", "").toLowerCase());

			author = author.replaceAll("(\\.|,)", "").toLowerCase();
			List<String> parts = new ArrayList<>();
			parts.addAll(Arrays.asList(author.split(" ")));
			int size = parts.size();
			for (int i = 0; i < size - 1; i++) {
				String first = parts.remove(0);
				// parts.add(first.substring(0, 1));
				parts.add(first);
				if (parts.get(0).length() > 1) {
					String newAuthor = parts.get(0) + " "
							+ parts.stream().skip(1L).map(s -> s.substring(0, 1)).collect(Collectors.joining(""));
					log.error("Adding author: {}", newAuthor);
					a.add(newAuthor);
				}
			}
			log.error("Authors are: " + a);
		}
    	List<String> result = new ArrayList<>();
    	generatePermutations(authorsList, result, 0, "");
    	
    	List<String> sorted = result.stream()
                .sorted(Comparator.comparingInt(String::length))
                .collect(Collectors.toList());
    	return sorted;
    }
    
    @Test
    void testPermutations0() {
    	List<String> authors = new ArrayList<>();
    	authors.add("De Brouwer de Boer, A.");
    	
    	List<String> permutedAuthorLists = generatePermutedAuthorLists(authors);
    	permutedAuthorLists.stream().forEach(System.err::println);
    	log.error("There are {} permutations", permutedAuthorLists.size());
    	assertThat(permutedAuthorLists).hasSize(4);
    }
    
    @Test
    void testPermutations1() {
    	List<String> authors = new ArrayList<>();
    	authors.add("De Joode, E. A.");
    	authors.add("Van Heugten, C. M.");
    	authors.add("Verheij, F. R. J.");
    	authors.add("van Boxtel, M. P. J.");
    	authors.add("Adriana, Bintintan");
    	
    	List<String> permutedAuthorLists = generatePermutedAuthorLists(authors);
    	permutedAuthorLists.stream().forEach(System.err::println);
    	log.error("There are {} permutations", permutedAuthorLists.size());
    	assertThat(permutedAuthorLists).hasSize(2 * 2 * 2 * 2);
    }
    
    @Test
    void testPermutations2() {
    	List<String> authors = new ArrayList<>();
    	authors.add("Adriana, Bintintan C.");
    	authors.add("Adriana, C. Bintintan");
    	authors.add("Van Zwieten sive Zwieten, Pieter Alexander");
    	
    	List<String> permutedAuthorLists = generatePermutedAuthorLists(authors);
    	permutedAuthorLists.stream().forEach(System.err::println);
    	log.error("There are {} permutations", permutedAuthorLists.size());
    	assertThat(permutedAuthorLists).hasSize(2 * 2 * 6);
    }
    
    @Test
    void testPermutations3() {
    	String authors1 = "Ching-yi, Wu; Chieh-ling, Yang; Li-ling, Chuang; Keh-chung, Lin; Hsieh-ching, Chen; Ming-de, Chen; Wan-chien, Huang";
    	List<String> authorList1 = new ArrayList<>();
    	authorList1.addAll(Arrays.asList(authors1.split("; ")));
    	List<String> permutedAuthorLists = generatePermutedAuthorLists(authorList1);
    	permutedAuthorLists.stream().forEach(System.err::println);
    	log.error("There are {} permutations", permutedAuthorLists.size());
    	
    	// FIXME: does it make sense to first use contains() and then use JWS? or overlap of 2 authorlists?
    	String other = "wu cy; yang cl; chuang ll; lin kc; chen hc; chen md; huang wc";
    	for (int i = 0; i < permutedAuthorLists.size() - 1; i++) {
    		if (permutedAuthorLists.get(i).equals(other)) {
    			System.err.println("Found at " + i);
    		}
    	}
    	assertThat(permutedAuthorLists).contains(other);
    	assertThat(permutedAuthorLists).hasSize(2187);
    	assertThat(3 * 3 * 3 * 3 * 3 * 3 * 3).isEqualTo(2187);
    }

    @Test
    void testPermutations4() {
    	String authors1 = "Adriana, Bintintan; Petru Adrian, Mircea; Romeo, Chira; Georgiana, Nagy; Roberta Manzat, Saplacan; Simona, Valean";
    	List<String> authorList1 = new ArrayList<>();
    	authorList1.addAll(Arrays.asList(authors1.split("; ")));
    	List<String> permutedAuthorLists = generatePermutedAuthorLists(authorList1);
    	permutedAuthorLists.stream().forEach(System.err::println);
    	log.error("There are {} permutations", permutedAuthorLists.size());
    	
    	// FIXME: does it make sense to first use contains() and then use JWS? or overlap of 2 authorlists?
    	String other = "bintintan a; mircea pa; chira r; nagy g; manzat sr; valean s";
    	for (int i = 0; i < permutedAuthorLists.size() - 1; i++) {
    		if (permutedAuthorLists.get(i).equals(other)) {
    			System.err.println("Found at " + i);
    		}
    	}
    	assertThat(permutedAuthorLists).contains(other);
    	assertThat(permutedAuthorLists).hasSize(144);
    	assertThat(permutedAuthorLists).hasSize(2 * 3 * 2 * 2 * 3 * 2);
    }

}
