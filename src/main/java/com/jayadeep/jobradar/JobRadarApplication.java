package com.jayadeep.jobradar;

import com.jayadeep.jobradar.apply.ApplyProperties;
import com.jayadeep.jobradar.apply.CandidateProfile;
import com.jayadeep.jobradar.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({AppProperties.class, CandidateProfile.class, ApplyProperties.class})
public class JobRadarApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobRadarApplication.class, args);
    }
}
