package com.jayadeep.jobradar.domain;

/** The scorer's recommendation for a job. */
public enum Verdict {
    APPLY,
    MAYBE,
    SKIP;

    /** Maps a 0-100 fit score to a verdict. Any deal-breaker forces SKIP. */
    public static Verdict from(int score, boolean hasDealBreakers) {
        if (hasDealBreakers) {
            return SKIP;
        }
        if (score >= 70) {
            return APPLY;
        }
        return score >= 45 ? MAYBE : SKIP;
    }
}
