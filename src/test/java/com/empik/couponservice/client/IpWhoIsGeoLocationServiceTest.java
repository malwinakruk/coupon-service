package com.empik.couponservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import com.empik.couponservice.exception.GeoLocationUnavailableException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.matchers.Times;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestClient;

/**
 * {@link IpWhoIsGeoLocationService} against a real HTTP call to MockServer, isolated from the
 * rest of the business logic. A minimal Spring context (not the full application) is used so
 * that {@code @Retryable} on {@link IpWhoIsClient} runs through Spring's real proxy — retry is
 * exactly what these tests need to exercise.
 */
@ExtendWith(MockServerExtension.class)
@MockServerSettings(ports = 18080)
@SpringJUnitConfig(IpWhoIsGeoLocationServiceTest.TestConfig.class)
class IpWhoIsGeoLocationServiceTest {

    @Autowired
    private GeoLocationService geoLocationService;

    /** A found IP resolves to its country. */
    @Test
    void returnsCountryOnSuccess(MockServerClient mockServer) {
        mockServer
                .when(request().withPath("/1.2.3.4"))
                .respond(response()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"country_code\":\"PL\"}"));

        assertThat(geoLocationService.lookupCountry("1.2.3.4")).isEqualTo("PL");
    }

    /** A transient server error is retried, and a subsequent success is returned. */
    @Test
    void retriesOnServerErrorThenSucceeds(MockServerClient mockServer) {
        mockServer.when(request().withPath("/5.6.7.8"), Times.exactly(1)).respond(response().withStatusCode(500));
        mockServer
                .when(request().withPath("/5.6.7.8"))
                .respond(response()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"country_code\":\"DE\"}"));

        assertThat(geoLocationService.lookupCountry("5.6.7.8")).isEqualTo("DE");
    }

    /** Once every retry is exhausted, the lookup fails closed. */
    @Test
    void exhaustedRetriesFailClosed(MockServerClient mockServer) {
        mockServer.when(request().withPath("/9.9.9.9")).respond(response().withStatusCode(500));

        assertThatThrownBy(() -> geoLocationService.lookupCountry("9.9.9.9"))
                .isInstanceOf(GeoLocationUnavailableException.class);
    }

    /** A 200 response reporting failure (e.g. an unresolvable IP) also fails closed. */
    @Test
    void unsuccessfulResponseFailsClosed(MockServerClient mockServer) {
        mockServer
                .when(request().withPath("/0.0.0.0"))
                .respond(response()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":false,\"message\":\"invalid IP\"}"));

        assertThatThrownBy(() -> geoLocationService.lookupCountry("0.0.0.0"))
                .isInstanceOf(GeoLocationUnavailableException.class);
    }

    @Configuration
    @EnableResilientMethods
    static class TestConfig {

        @Bean
        RestClient geoLocationRestClient() {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofSeconds(2));
            requestFactory.setReadTimeout(Duration.ofSeconds(2));
            return RestClient.builder()
                    .baseUrl("http://localhost:18080")
                    .requestFactory(requestFactory)
                    .build();
        }

        @Bean
        IpWhoIsClient ipWhoIsClient(RestClient restClient) {
            return new IpWhoIsClient(restClient);
        }

        @Bean
        GeoLocationService geoLocationService(IpWhoIsClient client) {
            return new IpWhoIsGeoLocationService(client);
        }
    }
}
