package edu.dedupendnote.services;

import java.util.Set;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;

import edu.dedupendnote.domain.Publication;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultTitleComparisonService implements TitleComparisonService {

    private static final JaroWinklerSimilarity JWS = new JaroWinklerSimilarity();

    private final TitleThresholds thresholds;

    public DefaultTitleComparisonService() {
        this(TitleThresholds.DEFAULT);
    }

    public DefaultTitleComparisonService(TitleThresholds thresholds) {
        this.thresholds = thresholds;
    }

    @Override
    public boolean compare(Publication r1, Publication r2) {
        if (r1.isReply() || r2.isReply()) {
            return true;
        }
        if (r1.isClinicalTrialGov() && r2.isClinicalTrialGov()) {
            log.trace("- 3. Both publications are from ClinicalTrials.gov");
            return true;
        }

        Double similarity = 0.0;
        Set<String> titles1 = r1.getTitles();
        Set<String> titles2 = r2.getTitles();
        boolean sufficientStartPages = r1.getPageStart() != null && r2.getPageStart() != null;
        boolean sufficientDois = !r1.getDois().isEmpty() && !r2.getDois().isEmpty();
        boolean isPhase = r1.isPhase() || r2.isPhase();

        if (titles1.isEmpty() || titles2.isEmpty()) {
            log.trace("- 3. No comparison of titles because no titles for at least one publication");
            return true;
        }
        Double highestSimilarity = 0.0;
        String highestTitle1 = "";
        String highestTitle2 = "";
        for (String title1 : titles1) {
            for (String title2 : titles2) {
                int minLength = Math.min(title1.length(), title2.length()) - 1;
                if (minLength < 1) {
                    log.error("For publ {} or {} the titles are too short: '{}' or '{}'", r1.getId(), r2.getId(),
                            title1, title2);
                    similarity = JWS.apply(title1, title2);
                } else {
                    similarity = JWS.apply(title1.substring(0, minLength), title2.substring(0, minLength));
                }

                // similarity = jws.apply(title1, title2);
                if (log.isTraceEnabled() && similarity > highestSimilarity) {
                    highestSimilarity = similarity;
                    highestTitle1 = title1;
                    highestTitle2 = title2;
                }

                if (isPhase) {
                    if (similarity > thresholds.phase()) {
                        if (log.isTraceEnabled()) {
                            log.trace("- 3. Title similarity (for Phase) {} is above threshold: '{}' and '{}'",
                                    similarity, title1, title2);
                        }
                        return true;
                    }
                } else {
                    if (sufficientStartPages || sufficientDois) {
                        if (similarity > thresholds.sufficientStartPagesOrDois()) {
                            if (log.isTraceEnabled()) {
                                log.trace(
                                        "- 3. Title similarity (with pages or DOIs) {} is above threshold: '{}' and '{}'",
                                        similarity, title1, title2);
                            }
                            return true;
                        }
                    }
                    if (!(sufficientStartPages || sufficientDois)) {
                        if (similarity > thresholds.insufficientStartPagesAndDois()) {
                            if (log.isTraceEnabled()) {
                                log.trace(
                                        "- 3. Title similarity (without sufficient pages or DOIs) {} is above threshold: '{}' and '{}'",
                                        similarity, title1, title2);
                            }
                            return true;
                        }
                    }
                }
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("- 3. Title similarity {} is below threshold: '{}' and '{}'], subtype {}", highestSimilarity,
                    highestTitle1, highestTitle2,
                    (isPhase ? "Phase"
                            : (sufficientStartPages || sufficientDois) ? "sufficient startPages or DOIs"
                                    : "not sufficient startPages or DOIs"));
        }
        return false;
    }
}
