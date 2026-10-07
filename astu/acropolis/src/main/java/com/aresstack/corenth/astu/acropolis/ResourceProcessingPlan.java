package com.aresstack.corenth.astu.acropolis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The ordered step types a lifecycle may execute for one resource (#10 Slice 4).
 *
 * <p>Immutable. Executed steps form a subsequence of the plan: a resource may stop early or skip
 * steps, but never runs them out of order or twice. {@link #accepts(List)} checks that.
 */
public final class ResourceProcessingPlan {

    private static final ResourceProcessingPlan STANDARD = new ResourceProcessingPlan(Arrays.asList(
            ResourceProcessingStepType.INDEXING_POLICY_BEFORE_ACQUISITION,
            ResourceProcessingStepType.MEDIATED_ACQUISITION,
            ResourceProcessingStepType.INDEXING_POLICY_WITH_SIZE,
            ResourceProcessingStepType.CHANGE_DETECTION,
            ResourceProcessingStepType.CONTENT_INSPECTION,
            ResourceProcessingStepType.LEXICAL_INDEXING,
            ResourceProcessingStepType.RECORD_UPDATE,
            ResourceProcessingStepType.DERIVED_STATE_CLEANUP));

    private final List<ResourceProcessingStepType> steps;

    public ResourceProcessingPlan(List<ResourceProcessingStepType> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("steps must not be empty");
        }
        Set<ResourceProcessingStepType> seen = EnumSet.noneOf(ResourceProcessingStepType.class);
        for (ResourceProcessingStepType step : steps) {
            if (step == null) throw new IllegalArgumentException("step must not be null");
            if (!seen.add(step)) throw new IllegalArgumentException("duplicate step: " + step);
        }
        this.steps = Collections.unmodifiableList(new ArrayList<ResourceProcessingStepType>(steps));
    }

    /** Returns the plan of the standard resource lifecycle. */
    public static ResourceProcessingPlan standard() {
        return STANDARD;
    }

    /** Returns the planned step types in execution order. */
    public List<ResourceProcessingStepType> steps() {
        return steps;
    }

    /** Returns whether the executed steps follow this plan's order without repetition. */
    public boolean accepts(List<ResourceProcessingStep> executed) {
        int position = -1;
        for (ResourceProcessingStep step : executed) {
            int index = steps.indexOf(step.type());
            if (index <= position) {
                return false;
            }
            position = index;
        }
        return true;
    }

    @Override
    public String toString() {
        return "ResourceProcessingPlan" + steps;
    }
}
