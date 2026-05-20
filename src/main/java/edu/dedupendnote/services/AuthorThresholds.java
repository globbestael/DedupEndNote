package edu.dedupendnote.services;

public record AuthorThresholds(
        double noReply,
        double replySufficientStartPagesOrDois,
        double replyInsufficientStartPagesAndDois) {

    public AuthorThresholds {
        validate("noReply", noReply);
        validate("replySufficientStartPagesOrDois", replySufficientStartPagesOrDois);
        validate("replyInsufficientStartPagesAndDois", replyInsufficientStartPagesAndDois);
    }

    private static void validate(String name, double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0.0, 1.0] but was " + value);
        }
    }

    public static final AuthorThresholds DEFAULT = new AuthorThresholds(0.67, 0.75, 0.80);
}
