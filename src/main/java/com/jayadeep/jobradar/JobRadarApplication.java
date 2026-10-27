package com.jayadeep.jobradar;

import com.jayadeep.jobradar.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class JobRadarApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobRadarApplication.class, args);
    }
}
