package edu.dedupendnote.services;

import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import edu.dedupendnote.domain.BibliographicItem;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultPagesComparisonService implements PagesComparisonService {

    @Override
    public boolean compare(BibliographicItem r1, BibliographicItem r2, Map<String, @Nullable Boolean> map) {
        Set<String> dois1 = r1.getDois();
        Set<String> dois2 = r2.getDois();
        boolean bothCochrane = r1.isCochrane() && r2.isCochrane();
        boolean sufficientStartPages = r1.getPageStart() != null && r2.getPageStart() != null;
        boolean sufficientDois = !dois1.isEmpty() && !dois2.isEmpty();
        boolean atLeastOneSeveralPages = r1.isSeveralPages() || r2.isSeveralPages();

        if (sufficientDois) { // this test to keep the initial null value when not both have DOIs
            map.put("isSameDois", UtilitiesService.setsContainSameString(dois1, dois2));
        }

        if (bothCochrane) {
            if (r1.getPublicationYear() == r2.getPublicationYear()) {
                if (sufficientDois) {
                    if (UtilitiesService.setsContainSameString(dois1, dois2)) {
                        log.trace("- 1. DOIs are the same for Cochrane");
                        return true;
                    } else {
                        log.trace("- 1. DOIs are NOT the same for Cochrane");
                        return false;
                    }
                } else if (sufficientStartPages && r1.getPageStart() != null
                        && r1.getPageStart().equals(r2.getPageStart())) {
                    log.trace("- 1. Starting pages are the same for Cochrane");
                    return true;
                }
            }
            log.trace("- 1. NOT the same startPage or DOI for Cochrane");
            return false;
        }

        if (!sufficientStartPages && !sufficientDois) {
            log.trace("- 1. At least one starting page AND at least one DOI are missing, therefore Same");
            return true;
        }
        if (atLeastOneSeveralPages) {
            if (sufficientDois) {
                if (UtilitiesService.setsContainSameString(dois1, dois2)) {
                    log.trace("- 1. DOIs are the same for severalPages");
                    return true;
                }
            }
            if (sufficientStartPages && r1.getPageStart() != null && r1.getPageStart().equals(r2.getPageStart())) {
                log.trace("- 1. Starting pages are the same for severalPages");
                return true;
            }
            log.trace("- 1. NOT the same startPage or DOI for severalPages");
            return false;
        }

        if (sufficientStartPages) {
            if (r1.getPageStart() != null && r1.getPageStart().equals(r2.getPageStart())) {
                log.trace("- 1. Starting pages are the same");
                return true;
            } else {
                log.trace("- 1. Starting pages are NOT the same");
                return false;
            }
        }

        if (UtilitiesService.setsContainSameString(dois1, dois2)) {
            log.trace("- 1. DOIs are the same");
            return true;
        }
        log.trace("- 1. DOIs and starting pages are NOT the same");
        return false;
    }
}
