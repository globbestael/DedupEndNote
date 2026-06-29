package edu.dedupendnote.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import edu.dedupendnote.domain.BibliographicItem;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EnrichmentService {

	/*
	 * Synthesises the representation of each duplicate set from its members:
	 * marks which items are not the representative (isKeptBibliographicItem = false),
	 * then enriches the representative with data from the others (DOIs, year, pages, title).
	 * Called only in REMOVE mode.
	 */
	public void enrich(List<BibliographicItem> bibliographicItems) {
		log.debug("Start enrich");
		Map<Integer, List<BibliographicItem>> labelMap = bibliographicItems.stream()
				// when comparing 2 files, duplicates of old-file items have a negative label
				.filter(r -> r.getLabel() != null && r.getLabel() >= 0)
				.collect(Collectors.groupingBy(BibliographicItem::getLabel));
		log.debug("Number of duplicate lists {}, and IDs of kept bibliographicItems: {}", labelMap.size(),
				labelMap.keySet());
		List<BibliographicItem> bibliographicItemList;
		if (!labelMap.isEmpty()) {
			for (Map.Entry<Integer, List<BibliographicItem>> entry : labelMap.entrySet()) {
				bibliographicItemList = entry.getValue();
				BibliographicItem bibliographicItemToKeep = bibliographicItemList.remove(0);
				log.debug("Kept: {}: {}", bibliographicItemToKeep.getId(),
						(bibliographicItemToKeep.getTitles().isEmpty() ? "(no titles found)"
								: bibliographicItemToKeep.getTitles().getFirst()));
				/*
					It may look a bit strange to call setKeptBibliographicItem on the BibliographicItems in this
					enrich function, but calling it in the compareSet function is wrong: it is a no-op in MARK mode,
					and enrich is only called in REMOVE mode.
					This field originally had a role in the deduplication phase,
					now it only has a role in the output phase.
				
					An older comment also said:
					Don't set keptPublication in compareSet(): trouble when multiple duplicates and no bibliographicItem year
				*/
				bibliographicItemList.stream().forEach(r -> r.setKeptBibliographicItem(false));

				// Reply and Retraction: replace the title with the longest title from the duplicates
				if (bibliographicItemToKeep.isReply() || (!bibliographicItemToKeep.isClinicalTrialGov()
						&& bibliographicItemToKeep.getTitle() != null)) {
					log.debug("BibliographicItem {} is a reply: ", bibliographicItemToKeep.getId());
					String longestTitle = bibliographicItemList.stream()
							// .filter(BibliographicItem::isReply)
							.map(r -> {
								log.debug("Reply {} has title: {}.", r.getId(), r.getTitle());
								return r.getTitle() != null ? r.getTitle() : r.getTitles().getFirst();
							}).max(Comparator.comparingInt(String::length)).orElse("");
					// There are cases where not all titles are recognized as replies -> bibliographicItem.title can be null
					if (bibliographicItemToKeep.getTitle() == null
							|| bibliographicItemToKeep.getTitle().length() < longestTitle.length()) {
						log.debug("REPLY: changing title {}\nto {}", bibliographicItemToKeep.getTitle(), longestTitle);
						bibliographicItemToKeep.setTitle(longestTitle);
					}
				}
				// Clinical trials from ClinicalTrials.gov: replace the title with the shortest title from the
				// duplicates
				if (bibliographicItemToKeep.isClinicalTrialGov()) {
					log.debug("BibliographicItem {} is a trial: ", bibliographicItemToKeep.getId());
					String shortestTitle = bibliographicItemList.stream().map(r -> {
						log.debug("Trial {} has title: {}.", r.getId(), r.getTitle());
						return r.getTitle() != null ? r.getTitle() : r.getTitles().getFirst();
					}).min(Comparator.comparingInt(String::length)).orElse("");
					// There are cases where bibliographicItem.title can be null (??)
					if (bibliographicItemToKeep.getTitle() == null
							|| bibliographicItemToKeep.getTitle().length() > shortestTitle.length()) {
						log.debug("Trial: changing title {}\nto {}", bibliographicItemToKeep.getTitle(), shortestTitle);
						bibliographicItemToKeep.setTitle(shortestTitle);
					}
				}

				// Gather all the DOIs
				final Set<String> dois = bibliographicItemToKeep.getDois();
				for (BibliographicItem p : bibliographicItemList) {
					if (!p.getDois().isEmpty()) {
						dois.addAll(p.getDois());
					}
				}
				if (!dois.isEmpty()) {
					bibliographicItemToKeep.setDois(dois);
				}

				// Add missing bibliographicItem year
				if (bibliographicItemToKeep.getPublicationYear() == 0) {
					log.debug("Reached bibliographicItem without publicationYear");
					bibliographicItemList.stream().filter(r -> r.getPublicationYear() != 0).findFirst()
							.ifPresent(r -> bibliographicItemToKeep.setPublicationYear(r.getPublicationYear()));
				}

				// Add missing pagesOutput
				if (bibliographicItemToKeep.getPagesOutput() == null
						|| bibliographicItemToKeep.getPagesOutput().isEmpty()) {
					log.debug("Reached bibliographicItem without pagesOutput: {}", bibliographicItemToKeep.getId());
					bibliographicItemList.stream().filter(r -> r.getPagesOutput() != null).findFirst().ifPresent(r -> {
						// publicationToKeep.setPageStart(r.getPageStart());
						// publicationToKeep.setPageEnd(r.getPageEnd());
						bibliographicItemToKeep.setPagesOutput(r.getPagesOutput());
					});
				}

				/*
				 * FIXME: Should empty authors be filled in from the duplicate set? See DOI
				 * 10.2298/sarh0902077c in test database, but the 2 duplicates have not the same
				 * author forms: "Culafic, D." (WoS) and "Dorde, Ć" (Scopus, error)
				 * Better example: 4605 in BIG_TEST without authors, 21391 with authors.
				 * But bibliographicItems can have different authors: in BIG_SET 4226 (none), 21471 (Banks ...), 36519 (Cabot ...)
				 */
			}
		}

		// In two-file mode: new-file items that are duplicates of old-file items have label < 0.
		// They were not processed by the loop above (which only handles label >= 0 sets).
		// Mark them as not-kept so the writer skips them.
		bibliographicItems.stream().filter(r -> r.getId() > 0 && r.getLabel() != null && r.getLabel() < 0)
				.forEach(r -> r.setKeptBibliographicItem(false));

		log.debug("Finished enrich");
	}

	/*
	 * Uppercases pagesOutput for all kept Cochrane items (both those in duplicate sets
	 * and singletons). Must be called after enrich() so isKeptBibliographicItem is set.
	 * Called only in REMOVE mode.
	 */
	public void enrichCochrane(List<BibliographicItem> bibliographicItems) {
		for (BibliographicItem r : bibliographicItems) {
			if (r.isKeptBibliographicItem() && r.isCochrane() && r.getPagesOutput() != null) {
				r.setPagesOutput(r.getPagesOutput().toUpperCase());
			}
		}
	}

	/*
	 * Applies enriched BibliographicItem data to the raw RIS field map assembled by BibliographicItemWriter
	 * from the original input file. Called once per Kept Bibliographic Item during Remove Mode writing.
	 * Complements enrich(): that method enriches the bibliographicItem that was kept with data from the other
	 * bibliographicItems in the same DuplicateSet.  This method projects values from the kept bibliographicItem —
	 * plus RIS-map-specific enrichments (T2 from J2, ClinicalTrials.gov URL, author cleanup) — onto the map.
	 */
	public void enrichMap(Map<String, String> map, BibliographicItem bibliographicItem) {
		if (!bibliographicItem.getDois().isEmpty()) {
			map.put("DO", "https://doi.org/"
					+ bibliographicItem.getDois().stream().collect(Collectors.joining("\nhttps://doi.org/")));
		}
		if (bibliographicItem.getPagesOutput() == null || bibliographicItem.getPagesOutput().isEmpty()) {
			map.remove("SP");
		} else {
			map.put("SP", bibliographicItem.getPagesOutput());
		}
		if (bibliographicItem.isReply() || bibliographicItem.getTitle() != null) {
			map.put("TI", bibliographicItem.getTitle());
			map.put("ST", bibliographicItem.getTitle());
		}
		if (bibliographicItem.isClinicalTrialGov()) {
			map.put("TY", "JOUR");
			map.put("T2", "https://clinicaltrials.gov");
			String url = "https://clinicaltrials.gov/study/" + bibliographicItem.getPageStart();
			List<String> urlList = new ArrayList<>();
			if (map.containsKey("UR")) {
				String urls = map.get("UR");
				urlList.addAll(Arrays.asList(urls.split("\n")));
				urlList.removeIf(u -> u.startsWith("https://clinicaltrials.gov"));
				if (urlList.isEmpty()) {
					map.put("UR", url);
				} else {
					map.put("UR", url + "\nUR  - " + urlList.stream().map(u -> u.replace("UR  - ", ""))
							.collect(Collectors.joining("\nUR  - ")));
				}
			} else {
				map.put("UR", url);
			}
		}

		// Some unusual authors should be kept, e.g. Group authors
		if (bibliographicItem.getAuthors().isEmpty()
				&& ("Anonymous".equals(map.get("AU")) || "Nct".equals(map.get("AU")))) {
			map.remove("AU");
		}
		if (!map.containsKey("PY") && bibliographicItem.getPublicationYear() != 0) {
			map.put("PY", Integer.toString(bibliographicItem.getPublicationYear()));
		}
		if (!map.containsKey("T2")) {
			if (map.containsKey("J2")) {
				map.put("T2", map.get("J2"));
			} else if (map.containsKey("DO") && map.get("DO").contains("https://doi.org/10.2139/ssrn")) {
				// alternative test could be ISSN 1556-5068
				map.put("T2", "Social Science Research Network");
			}
		}
	}

}
