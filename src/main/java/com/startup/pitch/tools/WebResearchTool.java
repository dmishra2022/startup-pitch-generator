package com.startup.pitch.tools;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Web Research Tool — available to the Market Analyst Agent.
 *
 * In production, wire this to a real search API (Brave Search, Tavily, SerpAPI).
 * The current implementation returns structured mock data that mirrors
 * what a real search would return, giving the agent realistic grounding.
 *
 * Annotated with @Tool so LangChain4j automatically includes it
 * in the agent's tool registry.
 */
@Component
public class WebResearchTool {

    private static final Logger log = LoggerFactory.getLogger(WebResearchTool.class);

    @Tool("Search for market size data, TAM/SAM estimates, and industry reports for a given market")
    public String searchMarketSize(String marketCategory) {
        log.debug("Searching market size for: {}", marketCategory);
        // In production: call Tavily/Brave Search API here
        return """
            Market Research Results for: %s
            - Global market estimated at $50B-$500B depending on scope definition
            - YoY growth rate: 15-25%% (based on recent industry reports)
            - Key segments: B2B SaaS, consumer apps, enterprise platforms
            - Largest players control ~40%% of market
            - Long tail of niche players with strong retention
            - Geographic concentration: NA (45%%), Europe (25%%), APAC (20%%)
            Note: Verify with specific industry reports (Gartner, IDC, CB Insights)
            """.formatted(marketCategory);
    }

    @Tool("Search for competitors in a given space and return competitive landscape data")
    public String searchCompetitors(String productCategory) {
        log.debug("Searching competitors for: {}", productCategory);
        return """
            Competitive Landscape for: %s
            - 3-5 established players with $10M+ ARR
            - Multiple early-stage startups (seed to Series A)
            - Key differentiators: pricing, UX, integrations, vertical focus
            - No single dominant player in most niches
            - Recent M&A activity suggests consolidation trend
            - Moats: data network effects, switching costs, brand trust
            Note: Check Crunchbase, G2, Product Hunt for current landscape
            """.formatted(productCategory);
    }

    @Tool("Search for recent news, trends, and demand signals relevant to a startup idea")
    public String searchTrends(String topic) {
        log.debug("Searching trends for: {}", topic);
        return """
            Trend Analysis for: %s
            - Growing consumer/enterprise interest (based on search volume trends)
            - Adjacent markets showing strong VC investment activity
            - Regulatory tailwinds/headwinds: check local compliance requirements
            - Technology enablers: AI/ML, cloud infrastructure reducing build costs
            - User pain point validated by communities (Reddit, LinkedIn discussions)
            - Timing signal: enterprise budgets shifting to this category
            """.formatted(topic);
    }

    @Tool("Search for pricing benchmarks and business model data for similar products")
    public String searchPricingBenchmarks(String productType) {
        log.debug("Searching pricing benchmarks for: {}", productType);
        return """
            Pricing Benchmarks for: %s
            - SaaS: $29-$299/month for SMB, $500-$5000/month enterprise
            - Marketplace: 5-20%% take rate typical
            - Freemium: 2-5%% free-to-paid conversion industry average
            - Annual contracts: 10-20%% discount vs monthly
            - Enterprise: $50K-$500K ACV with dedicated CSM
            - Pricing psychology: 3-tier plans maximize conversion
            """.formatted(productType);
    }
}
