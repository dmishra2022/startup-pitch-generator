package com.startup.pitch.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Output from the Pitch Creator Agent.
 * Deck content is generated as Marp Markdown for easy preview and export.
 * The fixed slide structure ensures consistent, predictable output.
 */
public record PitchDeck(

    @JsonProperty("title")
    String title,

    @JsonProperty("tagline")
    String tagline,

    @JsonProperty("marpMarkdown")
    String marpMarkdown,

    @JsonProperty("executiveSummary")
    String executiveSummary,

    @JsonProperty("slideCount")
    int slideCount

) {
    /** Standard slide order enforced via prompt. */
    public static final String[] SLIDE_STRUCTURE = {
        "Title & Tagline",
        "Problem",
        "Market Opportunity",
        "Solution & Product",
        "Key Features",
        "Financial Highlights",
        "Why Now",
        "The Ask & Vision"
    };
}
