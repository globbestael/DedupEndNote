# Copies test input files from ~/dedupendnote_files (old layout)
# to ~/dedupendnote_input_files (new tier-based layout).
# Safe to run multiple times - existing files are overwritten.
# The old folder is not touched.

$src = Join-Path $HOME "dedupendnote_files"
$dst = Join-Path $HOME "dedupendnote_input_files"

$copies = @(
    # --- unit ---
    @{ from = "experiments\validated_authors_pairs.txt";                                   to = "unit\experiments\validated_authors_pairs.txt" },
    @{ from = "experiments\validated_journal_pairs.txt";                                   to = "unit\experiments\validated_journal_pairs.txt" },
    @{ from = "all\all__ST_TI_ending_with_round_bracket.txt";                             to = "unit\all\all__ST_TI_ending_with_round_bracket.txt" },
    @{ from = "all\All__comment__positive_examples.txt";                                   to = "unit\all\All__comment__positive_examples.txt" },
    @{ from = "all\All__comment__negative_examples.txt";                                   to = "unit\all\All__comment__negative_examples.txt" },
    @{ from = "all\All__comment_AND_reply__positive_examples.txt";                         to = "unit\all\All__comment_AND_reply__positive_examples.txt" },

    # --- integration ---
    @{ from = "experiments\t1.txt";                                                        to = "integration\experiments\t1.txt" },
    @{ from = "experiments\test805.txt";                                                   to = "integration\experiments\test805.txt" },
    @{ from = "experiments\Non_Latin_input.txt";                                           to = "integration\experiments\Non_Latin_input.txt" },
    @{ from = "experiments\Dedup_PATIJ2_Possibly_missed.txt";                             to = "integration\experiments\Dedup_PATIJ2_Possibly_missed.txt" },
    @{ from = "experiments\Bestand_met_duplicate_IDs.txt";                                to = "integration\experiments\Bestand_met_duplicate_IDs.txt" },
    @{ from = "experiments\TwoFiles_1.txt";                                                to = "integration\experiments\TwoFiles_1.txt" },
    @{ from = "experiments\TwoFiles_2.txt";                                                to = "integration\experiments\TwoFiles_2.txt" },
    @{ from = "experiments\Recurrance_rate_EndNote_Library_original_deduplicated.txt";    to = "integration\experiments\Recurrance_rate_EndNote_Library_original_deduplicated.txt" },
    @{ from = "experiments\Recurrence_rate_search_updated_sept_18_deduplicated.txt";      to = "integration\experiments\Recurrence_rate_search_updated_sept_18_deduplicated.txt" },
    @{ from = "problems\Rayyan\missed_10_11.txt";                                          to = "integration\problems\Rayyan\missed_10_11.txt" },
    @{ from = "ASySD\dedupendnote_files\missed_duplicates\SRSR_Human_missed_2_4.txt";     to = "integration\problems\missed_duplicates\SRSR_Human_missed_2_4.txt" },
    @{ from = "ASySD\dedupendnote_files\missed_duplicates\SRSR_Human_missed_2_6.txt";     to = "integration\problems\missed_duplicates\SRSR_Human_missed_2_6.txt" },

    # --- validation ---
    @{ from = "AI_subset\AI_subset.txt";                                                   to = "validation\AI_subset\AI_subset.txt" },
    @{ from = "AI_subset\AI_subset_TRUTH.txt";                                             to = "validation\AI_subset\AI_subset_TRUTH.txt" },
    @{ from = "ASySD\dedupendnote_files\Cardiac_human.txt";                               to = "validation\ASySD\Cardiac_human.txt" },
    @{ from = "ASySD\dedupendnote_files\Cardiac_human_TRUTH.txt";                         to = "validation\ASySD\Cardiac_human_TRUTH.txt" },
    @{ from = "ASySD\dedupendnote_files\Diabetes.txt";                                    to = "validation\ASySD\Diabetes.txt" },
    @{ from = "ASySD\dedupendnote_files\Diabetes_TRUTH.txt";                              to = "validation\ASySD\Diabetes_TRUTH.txt" },
    @{ from = "ASySD\dedupendnote_files\Neuroimaging_sorted.txt";                         to = "validation\ASySD\Neuroimaging_sorted.txt" },
    @{ from = "ASySD\dedupendnote_files\Neuroimaging_sorted_TRUTH.txt";                   to = "validation\ASySD\Neuroimaging_sorted_TRUTH.txt" },
    @{ from = "ASySD\dedupendnote_files\SRSR_Human.txt";                                  to = "validation\ASySD\SRSR_Human.txt" },
    @{ from = "ASySD\dedupendnote_files\SRSR_Human_TRUTH.txt";                            to = "validation\ASySD\SRSR_Human_TRUTH.txt" },
    @{ from = "own\BIG_SET.txt";                                                           to = "validation\own\BIG_SET.txt" },
    @{ from = "own\BIG_SET_TRUTH.txt";                                                     to = "validation\own\BIG_SET_TRUTH.txt" },
    @{ from = "Clinical_trials\clinicaltrialsdotgov.txt";                                  to = "validation\Clinical_trials\clinicaltrialsdotgov.txt" },
    @{ from = "Clinical_trials\clinicaltrialsdotgov_TRUTH.txt";                            to = "validation\Clinical_trials\clinicaltrialsdotgov_TRUTH.txt" },
    @{ from = "McKeown_S_2021\dedupendnote_files\McKeown_2021.txt";                       to = "validation\McKeown_S_2021\McKeown_2021.txt" },
    @{ from = "McKeown_S_2021\dedupendnote_files\McKeown_2021_TRUTH.txt";                 to = "validation\McKeown_S_2021\McKeown_2021_TRUTH.txt" },
    @{ from = "SRA2\Cytology_screening.txt";                                               to = "validation\SRA2\Cytology_screening.txt" },
    @{ from = "SRA2\Cytology_screening_TRUTH.txt";                                         to = "validation\SRA2\Cytology_screening_TRUTH.txt" },
    @{ from = "SRA2\Haematology.txt";                                                      to = "validation\SRA2\Haematology.txt" },
    @{ from = "SRA2\Haematology_TRUTH.txt";                                                to = "validation\SRA2\Haematology_TRUTH.txt" },
    @{ from = "SRA2\Respiratory.txt";                                                      to = "validation\SRA2\Respiratory.txt" },
    @{ from = "SRA2\Respiratory_TRUTH.txt";                                                to = "validation\SRA2\Respiratory_TRUTH.txt" },
    @{ from = "SRA2\Stroke.txt";                                                           to = "validation\SRA2\Stroke.txt" },
    @{ from = "SRA2\Stroke_TRUTH.txt";                                                     to = "validation\SRA2\Stroke_TRUTH.txt" },
    @{ from = "TIL\TIL.txt";                                                               to = "validation\TIL\TIL.txt" },
    @{ from = "TIL\TIL_TRUTH.txt";                                                         to = "validation\TIL\TIL_TRUTH.txt" },
    @{ from = "TIL\TIL_Zotero.ris";                                                        to = "validation\TIL\TIL_Zotero.ris" },
    @{ from = "Dedupe-sweep\dedupendnote_files\BIG_SET_mark_DS.txt";                      to = "validation\Dedupe-sweep\BIG_SET_mark_DS.txt" }
)

$ok = 0; $skip = 0; $fail = 0

foreach ($entry in $copies) {
    $srcFile = Join-Path $src $entry.from
    $dstFile = Join-Path $dst $entry.to

    if (-not (Test-Path $srcFile)) {
        Write-Warning "SOURCE NOT FOUND: $srcFile"
        $skip++
        continue
    }

    $dstDir = Split-Path $dstFile
    if (-not (Test-Path $dstDir)) {
        New-Item -ItemType Directory -Force -Path $dstDir | Out-Null
    }

    try {
        Copy-Item -Path $srcFile -Destination $dstFile -Force
        Write-Host "OK  $($entry.to)"
        $ok++
    } catch {
        Write-Warning "FAILED: $($entry.to) - $_"
        $fail++
    }
}

Write-Host ""
Write-Host "Done: $ok copied, $skip skipped (source missing), $fail failed."
