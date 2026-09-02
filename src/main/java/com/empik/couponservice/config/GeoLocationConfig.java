package com.empik.couponservice.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.client.RestClient;

/**
 * Wiring for the geolocation HTTP client: the ipwho.is base URL, request timeouts, and the
 * {@code @Retryable} proxying used by {@link com.empik.couponservice.client.IpWhoIsClient}.
 */
@Configuration
@EnableResilientMethods
class GeoLocationConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    /**
     * Builds the HTTP client used to call ipwho.is, with explicit timeouts so a slow provider
     * fails fast instead of hanging the request.
     *
     * @return a {@code RestClient} configured for the geolocation provider
     */
    @Bean
    RestClient geoLocationRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder().baseUrl("https://ipwho.is").requestFactory(requestFactory).build();
    }
}
