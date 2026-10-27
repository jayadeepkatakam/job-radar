package com.jayadeep.jobradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * All tunable settings, bound from the {@code jobradar.*} section of application.yml.
 *
 * @param boards       job boards to pull from on each ingestion run
 * @param titleInclude a job title must contain at least one of these (case-insensitive)
 * @param titleExclude a job title containing any of these is skipped
 * @param skills       skills from the candidate's resume, used by the keyword scorer
 * @param profile      plain-text resume summary sent to the LLM scorer
 * @param claude       Claude API settings; scoring falls back to keywords when no key is set
 */
@ConfigurationProperties(prefix = "jobradar")
public record AppProperties(
        List<Board> boards,
        List<String> titleInclude,
        List<String> titleExclude,
        List<String> skills,
        String profile,
        Claude claude
) {

    public AppProperties {
        boards = boards == null ? List.of() : List.copyOf(boards);
        titleInclude = titleInclude == null ? List.of() : List.copyOf(titleInclude);
        titleExclude = titleExclude == null ? List.of() : List.copyOf(titleExclude);
        skills = skills == null ? List.of() : List.copyOf(skills);
        profile = profile == null ? "" : profile;
        claude = claude == null ? new Claude(null, null, null) : claude;
    }

    /** A company job board. {@code source} is "greenhouse" or "lever"; {@code token} is the board's slug. */
    public record Board(String source, String token, String company) {
    }

    public record Claude(String apiKey, String model, String baseUrl) {
        public Claude {
            model = (model == null || model.isBlank()) ? "claude-sonnet-5-5" : model;
            baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.anthropic.com" : baseUrl;
        }

        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
