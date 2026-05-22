package edu.dedupendnote.services;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import edu.dedupendnote.domain.BibliographicItem;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ComparisonService {

    private final AuthorsComparisonService authorsComparisonService;
    private final TitleComparisonService titleComparisonService;
    private final JournalComparisonService journalComparisonService;
    private final PagesComparisonService pagesComparisonService;

    public ComparisonService() {
        this(new DefaultAuthorsComparisonService(),
             new DefaultTitleComparisonService(),
             new DefaultJournalComparisonService(),
             new DefaultPagesComparisonService());
    }

    public ComparisonService(AuthorsComparisonService authorsComparisonService,
                             TitleComparisonService titleComparisonService,
                             JournalComparisonService journalComparisonService,
                             PagesComparisonService pagesComparisonService) {
        this.authorsComparisonService = authorsComparisonService;
        this.titleComparisonService = titleComparisonService;
        this.journalComparisonService = journalComparisonService;
        this.pagesComparisonService = pagesComparisonService;
    }

    public boolean compareAuthors(BibliographicItem r1, BibliographicItem r2) {
        return authorsComparisonService.compare(r1, r2);
    }

    public boolean compareTitles(BibliographicItem r1, BibliographicItem r2) {
        return titleComparisonService.compare(r1, r2);
    }

    public boolean compareJournals(BibliographicItem r1, BibliographicItem r2, @Nullable Boolean isSameDois) {
        return journalComparisonService.compare(r1, r2, isSameDois);
    }

    public boolean compareStartPagesOrDois(BibliographicItem r1, BibliographicItem r2, Map<String, @Nullable Boolean> map) {
        return pagesComparisonService.compare(r1, r2, map);
    }

    /*
     * Compares the ISBNs or the ISSNs of 2 bibliographicItems
     */
    public static boolean compareIssns(BibliographicItem r1, BibliographicItem r2, @Nullable Boolean isSameDois) {
        if (!r1.getIsbns().isEmpty() && !r2.getIsbns().isEmpty()) {
            if (UtilitiesService.setsContainSameString(r1.getIsbns(), r2.getIsbns())) {
                log.trace("- 4. ISBNs are the same");
                return true;
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("- 4. ISBNs are NOT the same: {} and {}", r1.getIsbns(), r2.getIsbns());
                }
                return false;
            }
        }
        if (UtilitiesService.setsContainSameString(r1.getIssns(), r2.getIssns())) {
            log.trace("- 4. ISSNs are the same");
            return true;
        } else {
            if (log.isTraceEnabled()) {
                log.trace("- 4. ISSNs are NOT the same: {} and {}", r1.getIssns(), r2.getIssns());
            }
            return false;
        }
    }

    /*
     * Does NOT compare the DOIs of 2 bibliographicItems, but the field map.isSameDois in the compareSet method
     */
    public static boolean compareSameDois(BibliographicItem r1, BibliographicItem r2, @Nullable Boolean isSameDois) {
        if (Boolean.TRUE.equals(isSameDois)) {
            if (log.isTraceEnabled()) {
                log.trace("- 4. DOIs are the same (ISSNs and Journals are NOT compared)");
            }
            return true;
        }
        if (log.isTraceEnabled()) {
            log.trace("- 4. DOIs are NOT the same: {} and {}", r1.getDois(), r2.getDois());
        }
        return false;
    }
}
