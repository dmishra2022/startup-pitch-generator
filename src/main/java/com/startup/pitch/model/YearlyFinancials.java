package com.startup.pitch.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Single year of financial data within a 5-year projection.
 */
public record YearlyFinancials(

    @JsonProperty("year")
    int year,

    @JsonProperty("revenue")
    double revenue,

    @JsonProperty("costs")
    double costs,

    @JsonProperty("profit")
    double profit,

    @JsonProperty("customers")
    int customers

) {
    public double profitMarginPercent() {
        if (revenue == 0) return 0.0;
        return (profit / revenue) * 100.0;
    }

    public boolean isProfitable() {
        return profit > 0;
    }
}
