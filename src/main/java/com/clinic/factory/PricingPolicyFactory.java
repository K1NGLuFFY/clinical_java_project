package com.clinic.factory;

import com.clinic.exception.InvalidInputException;
import com.clinic.model.PatientCategory;
import com.clinic.pricing.InsuredPricing;
import com.clinic.pricing.PricingPolicy;
import com.clinic.pricing.SeniorPricing;
import com.clinic.pricing.StandardPricing;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy registry that maps every {@link PatientCategory} to exactly one pricing policy.
 *
 * <p>Callers look the policy up by category instead of branching on it, which keeps the
 * pricing rules open for extension without touching the billing service.</p>
 */
public final class PricingPolicyFactory {

    private final Map<PatientCategory, PricingPolicy> policies = new EnumMap<>(PatientCategory.class);

    /** Registers the standard, insured and senior policies. */
    public PricingPolicyFactory() {
        register(new StandardPricing());
        register(new InsuredPricing());
        register(new SeniorPricing());
    }

    /**
     * Returns the pricing policy registered for a patient category.
     *
     * @param category pricing category of the patient
     * @return the matching policy, never {@code null}
     * @throws InvalidInputException when the category is null or has no registered policy
     */
    public PricingPolicy forCategory(PatientCategory category) throws InvalidInputException {
        if (category == null) {
            throw new InvalidInputException("Patient category is required (STANDARD, INSURED or SENIOR).");
        }
        PricingPolicy policy = policies.get(category);
        if (policy == null) {
            throw new InvalidInputException("No pricing policy is registered for patient category " + category + ".");
        }
        return policy;
    }

    /**
     * All registered policies in category order.
     *
     * @return an immutable list of the registered policies
     */
    public List<PricingPolicy> all() {
        return List.copyOf(policies.values());
    }

    private void register(PricingPolicy policy) {
        policies.put(policy.category(), policy);
    }
}
