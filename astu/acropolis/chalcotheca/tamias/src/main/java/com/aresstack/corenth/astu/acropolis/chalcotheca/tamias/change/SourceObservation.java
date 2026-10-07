package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change;

/**
 * The current observation of a resource at its source: absent, or present together with the
 * {@link ContentComparison} against the record.
 *
 * <p>A failed or unauthorised source check is not an observation and must not be passed as
 * {@link #absent()}; it belongs to the access and run models.
 */
public final class SourceObservation {

    private static final SourceObservation ABSENT = new SourceObservation(false, null);
    private static final SourceObservation FIRST_SIGHTING = new SourceObservation(true, ContentComparison.NOT_COMPARED);
    private static final SourceObservation SAME = new SourceObservation(true, ContentComparison.SAME_AS_LATEST_OBSERVED);
    private static final SourceObservation DIFFERENT = new SourceObservation(true, ContentComparison.DIFFERS_FROM_LATEST_OBSERVED);

    private final boolean present;
    private final ContentComparison comparison;

    private SourceObservation(boolean present, ContentComparison comparison) {
        this.present = present;
        this.comparison = comparison;
    }

    /** Observe the resource as present with the given comparison result. */
    public static SourceObservation present(ContentComparison comparison) {
        if (comparison == null) {
            throw new IllegalArgumentException("comparison must not be null");
        }
        switch (comparison) {
            case SAME_AS_LATEST_OBSERVED:
                return SAME;
            case DIFFERS_FROM_LATEST_OBSERVED:
                return DIFFERENT;
            default:
                return FIRST_SIGHTING;
        }
    }

    /** Observe the resource as present while no record exists to compare with. */
    public static SourceObservation presentWithoutRecord() {
        return FIRST_SIGHTING;
    }

    /** Observe the resource as confirmed to no longer exist at its source. */
    public static SourceObservation absent() {
        return ABSENT;
    }

    /** Return whether the resource is present at its source. */
    public boolean isPresent() {
        return present;
    }

    /** Return the comparison result, or {@code null} if the resource is absent. */
    public ContentComparison comparison() {
        return comparison;
    }

    @Override
    public String toString() {
        return present ? "SourceObservation{present, " + comparison + "}" : "SourceObservation{absent}";
    }
}
