package edu.dedupendnote.services;

public record JournalThresholds(double noReply, double reply) {

    public JournalThresholds {
        validate("noReply", noReply);
        validate("reply", reply);
    }

    private static void validate(String name, double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0.0, 1.0] but was " + value);
        }
    }

    public static final JournalThresholds DEFAULT = new JournalThresholds(0.90, 0.93);
}
