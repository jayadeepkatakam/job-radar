package com.jayadeep.jobradar.apply;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AtsTest {

    @Test
    void detectsAtsAndApplicationPage() {
        assertThat(Ats.detect("https://job-boards.greenhouse.io/acme/jobs/123")).isEqualTo(Ats.GREENHOUSE);
        assertThat(Ats.detect("https://acme.com/careers?gh_jid=123")).isEqualTo(Ats.GREENHOUSE);
        assertThat(Ats.detect("https://acme.wd5.myworkdayjobs.com/en-US/External/job/Austin/SWE_R123")).isEqualTo(Ats.WORKDAY);
        assertThat(Ats.detect("not a url")).isEqualTo(Ats.OTHER);

        assertThat(Ats.LEVER.applicationUrl("https://jobs.lever.co/acme/abc-123?lever-source=x"))
                .isEqualTo("https://jobs.lever.co/acme/abc-123/apply");
        assertThat(Ats.ASHBY.applicationUrl("https://jobs.ashbyhq.com/acme/abc/"))
                .isEqualTo("https://jobs.ashbyhq.com/acme/abc/application");
        assertThat(Ats.GREENHOUSE.applicationUrl(" https://job-boards.greenhouse.io/acme/jobs/1 "))
                .isEqualTo("https://job-boards.greenhouse.io/acme/jobs/1");
    }
}
