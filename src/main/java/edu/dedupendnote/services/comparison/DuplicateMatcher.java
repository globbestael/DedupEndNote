package edu.dedupendnote.services.comparison;

import org.springframework.stereotype.Component;

import edu.dedupendnote.domain.BibliographicItem;
import edu.dedupendnote.services.UtilitiesService;
import lombok.extern.slf4j.Slf4j;

/**
 * Decides whether two Bibliographic Items are Duplicates.
 *
 * <p>
 * Owns the order of the 5-step comparison; all steps must pass (see docs/algorithm.md):
 * <ol>
 * <li>publication year: checked by the caller (year buckets), not here</li>
 * <li>starting page or DOI</li>
 * <li>authors</li>
 * <li>title</li>
 * <li>shared DOI, or else ISBN/ISSN, or else journal name</li>
 * </ol>
 *
 * <p>
 * A pure predicate: it never changes the items. The per-field comparators are internal seams.
 */
@Component
@Slf4j
public class DuplicateMatcher {

	private final FieldComparators fieldComparators;

	public DuplicateMatcher(FieldComparators fieldComparators) {
		this.fieldComparators = fieldComparators;
	}

	public boolean areDuplicates(BibliographicItem a, BibliographicItem b) {
		return fieldComparators.pages().compare(a, b) && fieldComparators.authors().compare(a, b)
				&& fieldComparators.titles().compare(a, b)
				&& (haveSameDois(a, b) || fieldComparators.journals().compareIssns(a, b)
						|| fieldComparators.journals().compare(a, b));
	}

	/**
	 * True if both items have DOIs and share at least one. When true, ISSNs and journal names are not compared.
	 */
	private boolean haveSameDois(BibliographicItem a, BibliographicItem b) {
		boolean same = !a.getDois().isEmpty() && !b.getDois().isEmpty()
				&& UtilitiesService.setsContainSameString(a.getDois(), b.getDois());
		if (log.isTraceEnabled()) {
			if (same) {
				log.trace("- 4. DOIs are the same (ISSNs and Journals are NOT compared)");
			} else {
				log.trace("- 4. DOIs are NOT the same: {} and {}", a.getDois(), b.getDois());
			}
		}
		return same;
	}
}
