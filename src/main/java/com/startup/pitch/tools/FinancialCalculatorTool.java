package com.startup.pitch.tools;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Financial Calculator Tool — available to the Financial Analyst Agent.
 *
 * Provides deterministic financial calculations so the LLM doesn't
 * have to do mental arithmetic. Reduces hallucinated numbers.
 */
@Component
public class FinancialCalculatorTool {

    private static final Logger log = LoggerFactory.getLogger(FinancialCalculatorTool.class);

    @Tool("Calculate compound annual growth rate (CAGR) given start value, end value, and number of years")
    public String calculateCagr(double startValue, double endValue, int years) {
        if (startValue <= 0 || years <= 0) return "Invalid input: values must be positive";
        double cagr = (Math.pow(endValue / startValue, 1.0 / years) - 1) * 100;
        log.debug("CAGR calculated: {}%", String.format("%.2f", cagr));
        return String.format("CAGR = %.2f%% over %d years (from $%.0f to $%.0f)",
            cagr, years, startValue, endValue);
    }

    @Tool("Calculate months of runway given current cash, monthly burn rate, and expected monthly revenue")
    public String calculateRunway(double cashUSD, double monthlyBurnUSD, double monthlyRevenueUSD) {
        double netBurn = monthlyBurnUSD - monthlyRevenueUSD;
        if (netBurn <= 0) return "Company is cash-flow positive — runway is unlimited at current rates";
        double months = cashUSD / netBurn;
        log.debug("Runway: {} months", String.format("%.1f", months));
        return String.format("Runway = %.1f months (net burn: $%.0f/month)", months, netBurn);
    }

    @Tool("Calculate customer acquisition cost (CAC) given total sales+marketing spend and number of new customers")
    public String calculateCac(double totalSalesMarketingSpend, int newCustomers) {
        if (newCustomers <= 0) return "Invalid: customer count must be positive";
        double cac = totalSalesMarketingSpend / newCustomers;
        log.debug("CAC: ${}", String.format("%.2f", cac));
        return String.format("CAC = $%.2f per customer", cac);
    }

    @Tool("Calculate LTV:CAC ratio given average revenue per user (ARPU), gross margin, churn rate, and CAC")
    public String calculateLtvCacRatio(double arpu, double grossMarginPct, double annualChurnPct, double cac) {
        if (annualChurnPct <= 0 || cac <= 0) return "Invalid input";
        double ltv = (arpu * (grossMarginPct / 100)) / (annualChurnPct / 100);
        double ratio = ltv / cac;
        log.debug("LTV:CAC ratio: {}", String.format("%.1f", ratio));
        return String.format("LTV = $%.2f, CAC = $%.2f, LTV:CAC = %.1f:1 (healthy if > 3:1)",
            ltv, cac, ratio);
    }

    @Tool("Project simple linear revenue given base monthly revenue, monthly growth rate percent, and number of months")
    public String projectRevenue(double baseMonthlyRevenue, double monthlyGrowthRatePct, int months) {
        double totalRevenue = 0;
        double current = baseMonthlyRevenue;
        double rate = 1 + (monthlyGrowthRatePct / 100);
        StringBuilder sb = new StringBuilder();
        for (int m = 1; m <= Math.min(months, 12); m++) {
            totalRevenue += current;
            if (m % 3 == 0) sb.append(String.format("Month %d: $%.0f | ", m, current));
            current *= rate;
        }
        return String.format("Projected revenue over %d months = $%.0f. Quarterly: %s", 
            months, totalRevenue, sb.toString());
    }
}
