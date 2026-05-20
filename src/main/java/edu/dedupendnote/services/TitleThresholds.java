package edu.dedupendnote.services;

public record TitleThresholds(
        double sufficientStartPagesOrDois,
        double insufficientStartPagesAndDois,
        double phase) {

    public TitleThresholds {
        validate("sufficientStartPagesOrDois", sufficientStartPagesOrDois);
        validate("insufficientStartPagesAndDois", insufficientStartPagesAndDois);
        validate("phase", phase);
    }

    private static void validate(String name, double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0.0, 1.0] but was " + value);
        }
    }

    public static final TitleThresholds DEFAULT = new TitleThresholds(0.89, 0.94, 0.96);
}
