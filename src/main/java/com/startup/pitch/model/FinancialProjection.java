package com.startup.pitch.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Structured output from the Financial Analyst Agent.
 * Enforces strict JSON schema to prevent downstream breakage.
 * Contains assumptions + 5-year projections.
 */
public record FinancialProjection(

    @JsonProperty("pricingModel")
    String pricingModel,

    @JsonProperty("conversionAndAdoption")
    String conversionAndAdoption,

    @JsonProperty("keyCostDrivers")
    String keyCostDrivers,

    @JsonProperty("financialNotes")
    String financialNotes,

    @JsonProperty("yearlyProjections")
    List<YearlyFinancials> yearlyProjections,

    @JsonProperty("requiredInvestmentUSD")
    double requiredInvestmentUSD,

    @JsonProperty("breakEvenYear")
    int breakEvenYear,

    @JsonProperty("revenueModel")
    String revenueModel

) {
    /** Returns the final year's data, i.e. Year 5. */
    public YearlyFinancials finalYear() {
        if (yearlyProjections == null || yearlyProjections.isEmpty()) return null;
        return yearlyProjections.get(yearlyProjections.size() - 1);
    }

    /** Total 5-year revenue. */
    public double totalRevenue() {
        if (yearlyProjections == null) return 0;
        return yearlyProjections.stream().mapToDouble(YearlyFinancials::revenue).sum();
    }
}
