package edu.dedupendnote.services;

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

	public void enrich(List<BibliographicItem> bibliographicItems) {
		log.debug("Start enrich");
		// First the bibliographicItems with duplicates
		Map<String, List<BibliographicItem>> labelMap = bibliographicItems.stream()
				// when comparing 2 files, duplicates from the old file start with "-"
				.filter(r -> r.getLabel() != null && !r.getLabel().startsWith("-"))
				.collect(Collectors.groupingBy(BibliographicItem::getLabel));
		log.debug("Number of duplicate lists {}, and IDs of kept bibliographicItems: {}", labelMap.size(),
				labelMap.keySet());
		List<BibliographicItem> bibliographicItemList;
		if (!labelMap.isEmpty()) {
			for (Map.Entry<String, List<BibliographicItem>> entry : labelMap.entrySet()) {
				bibliographicItemList = entry.getValue();
				BibliographicItem bibliographicItemToKeep = bibliographicItemList.remove(0);
				log.debug("Kept: {}: {}", bibliographicItemToKeep.getId(),
						(bibliographicItemToKeep.getTitles().isEmpty() ? "(no titles found)"
								: bibliographicItemToKeep.getTitles().getFirst()));
				// TODO: test whether this could move to compareSet() — enrich() is only called in REMOVE mode, so setting it here is a no-op in MARK mode
				// Don't set keptPublication in compareSet(): trouble when multiple duplicates and no bibliographicItem year
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

				if (bibliographicItemToKeep.isCochrane() && bibliographicItemToKeep.getPagesOutput() != null) {
					// replaceCochranePageStart(bibliographicItemToKeep, bibliographicItemList);
					bibliographicItemToKeep.setPagesOutput(bibliographicItemToKeep.getPagesOutput().toUpperCase());
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

		// Then the Cochrane bibliographicItems without duplicates
		for (BibliographicItem r : bibliographicItems) {
			if (r.isCochrane() && r.getLabel() == null && r.getPagesOutput() != null) {
				// replaceCochranePageStart(r, Collections.emptyList());
				r.setPagesOutput(r.getPagesOutput().toUpperCase());
			}
		}

		log.debug("Finished enrich");
	}

}
