package com.jayadeep.jobradar.apply;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ResumePdfRendererTest {

    private final ResumePdfRenderer renderer = new ResumePdfRenderer();

    @Test
    void rendersAFullResumeOnOnePage() throws Exception {
        byte[] pdf = renderer.render(TestProfiles.profile(true, false), ResumeTailor.trimmed(TestProfiles.resume()));

        assertThat(ResumePdfRenderer.pageCount(pdf)).isEqualTo(1);
        // Saved so CI can publish it for a visual check.
        Path out = Path.of("target", "resume-sample.pdf");
        Files.createDirectories(out.getParent());
        Files.write(out, pdf);
    }

    @Test
    void escapesSpecialCharacters() {
        String html = new ResumePdfRenderer().html(TestProfiles.profile(true, false),
                new CandidateProfile.Resume("C++ & <Java> – 4+ years", null, null, null, null, null), 10, 1.2);
        assertThat(html).contains("C++ &#38; &#60;Java&#62; &#8211; 4+ years");
    }

    @Test
    void dropsOldestBulletsFirstWhenTrimming() {
        CandidateProfile.Resume r = ResumeTailor.trimmed(TestProfiles.resume());
        CandidateProfile.Resume shorter = ResumePdfRenderer.dropOneBullet(r);
        assertThat(shorter.experience().get(3).bullets()).hasSize(r.experience().get(3).bullets().size() - 1);
        assertThat(shorter.experience().get(0).bullets()).hasSize(r.experience().get(0).bullets().size());
    }
}
