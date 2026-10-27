package com.jayadeep.jobradar.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Configuration
public class HttpConfig {

    /**
     * Applied to every RestClient.Builder Spring Boot hands out. Conservative timeouts so one slow
     * job board can't stall an ingestion run.
     */
    @Bean
    public RestClientCustomizer jobRadarHttpDefaults() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
            factory.setReadTimeout((int) Duration.ofSeconds(60).toMillis());
            builder.requestFactory(factory).defaultHeader("User-Agent", "job-radar/0.1");
        };
    }
}
