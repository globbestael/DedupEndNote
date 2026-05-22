package edu.dedupendnote.services;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.jspecify.annotations.Nullable;

import edu.dedupendnote.domain.BibliographicItem;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultJournalComparisonService implements JournalComparisonService {

    private static final JaroWinklerSimilarity JWS = new JaroWinklerSimilarity();
    private static final Map<String, Pattern> ABBREVIATION_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Pattern> INITIALISM_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Pattern> STARTING_INITIALISM_CACHE = new ConcurrentHashMap<>();

    private final JournalThresholds thresholds;

    public DefaultJournalComparisonService() {
        this(JournalThresholds.DEFAULT);
    }

    public DefaultJournalComparisonService(JournalThresholds thresholds) {
        this.thresholds = thresholds;
    }

    @Override
    public boolean compare(BibliographicItem r1, BibliographicItem r2, @Nullable Boolean isSameDois) {
        if (!r1.getIsbns().isEmpty() && !r2.getIsbns().isEmpty()) {
            return false;
        }
        Set<String> set1 = r1.getJournals();
        Set<String> set2 = r2.getJournals();
        boolean isReply = r1.isReply() || r2.isReply();

        if (set1.isEmpty() || set2.isEmpty()) {
            log.trace("- 4. At least 1 of the bibliographicItems has no journal");
            return false;
        }

        Set<String> commonJournals = new HashSet<>(set1);
        commonJournals.retainAll(set2);
        if (!commonJournals.isEmpty()) {
            log.trace("- 4. Some journals are the same");
            return true;
        }

        for (String s1 : set1) {
            for (String s2 : set2) {
                if (s1.startsWith("http") && s2.startsWith("http") && !s1.equals(s2)) {
                    continue;
                }
                Double similarity = JWS.apply(s1.toLowerCase(), s2.toLowerCase());
                if (isReply && similarity > thresholds.reply()) {
                    log.trace("- 4. Journal similarity above treshold (reply)");
                    return true;
                }
                if (!isReply && similarity > thresholds.noReply()) {
                    log.trace("- 4. Journal similarity ({}) above treshold (not reply)", similarity);
                    return true;
                }
                /*
                    Claude analysis of Jspecify and NullAway has a minor issue with these charAt() calls. However the Set<String> journals
                    cannot contain null Strings.
                 */
                if (s1.toLowerCase().charAt(0) != s2.toLowerCase().charAt(0)) {
                    continue;
                }
                if (compareJournals_FirstAsAbbreviation(s1, s2)) {
                    log.trace("- 4. compareJournals_FirstAsAbbreviation(1,2) is true");
                    return true;
                }
                if (compareJournals_FirstAsAbbreviation(s2, s1)) {
                    log.trace("- 4. compareJournals_FirstAsAbbreviation(2,2) is true");
                    return true;
                }
                if (s1.length() < 10 && s1.toUpperCase().equals(s1) && compareJournals_FirstAsInitialism(s1, s2)) {
                    log.trace("- 4. compareJournals_FirstAsInitialism(1,2) is true");
                    return true;
                }
                if (s2.length() < 10 && s2.toUpperCase().equals(s2) && compareJournals_FirstAsInitialism(s2, s1)) {
                    log.trace("- 4. compareJournals_FirstAsInitialism(2,1) is true");
                    return true;
                }
                if (compareJournals_FirstWithStartingInitialism(s1, s2)) {
                    log.trace("- 4. compareJournals_FirstWithStartingInitialism(1,2) is true");
                    return true;
                }
                if (compareJournals_FirstWithStartingInitialism(s2, s1)) {
                    log.trace("- 4. compareJournals_FirstWithStartingInitialism(2,1) is true");
                    return true;
                }
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("- 4. Journals are NOT the same: {} and {}", r1.getJournals(), r2.getJournals());
        }
        return false;
    }

    private static boolean compareJournals_FirstAsAbbreviation(String j1, String j2) {
        Pattern pattern = ABBREVIATION_CACHE.computeIfAbsent(j1,
                k -> Pattern.compile("\\b" + k.replaceAll("\\s", ".*\\\\b") + ".*", Pattern.CASE_INSENSITIVE));
        Matcher matcher = pattern.matcher(j2);
        return matcher.find();
    }

    private static boolean compareJournals_FirstAsInitialism(String s1, String s2) {
        Pattern patternShort2 = INITIALISM_CACHE.computeIfAbsent(s1, k -> {
            String patternString = k.chars().mapToObj(c -> String.valueOf((char) c))
                    .collect(Collectors.joining(".*\\b", "\\b", ".*"));
            return Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
        });
        Matcher matcher = patternShort2.matcher(s2);
        return matcher.find();
    }

    private static boolean compareJournals_FirstWithStartingInitialism(String s1, String s2) {
        String[] words = s1.split("\\s");
        if ("Samj".equals(words[0])) {
            words[0] = "SAMJ";
        }
        if (words[0].length() > 2 && words[0].equals(words[0].toUpperCase())
                || words.length == 1 && words[0].length() < 6) {
            if ("AJNR".equals(words[0])) {
                words[0] = "AJN";
            }
            Pattern patternShort3 = STARTING_INITIALISM_CACHE.computeIfAbsent(words[0], k -> {
                String patternString = k.chars().mapToObj(c -> String.valueOf((char) c))
                        .collect(Collectors.joining(".*\\b", "\\b", ".*"));
                return Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
            });
            Matcher matcher = patternShort3.matcher(s2);
            return matcher.find();
        }
        return false;
    }
}
