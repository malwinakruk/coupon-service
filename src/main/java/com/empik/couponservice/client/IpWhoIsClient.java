package com.empik.couponservice.client;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin HTTP client for the ipwho.is API. Retry is applied here rather than on
 * {@link IpWhoIsGeoLocationService} so that it goes through Spring's proxy: an annotated method
 * called on {@code this} from within the same class would silently skip the proxy and never
 * retry.
 */
@Component
class IpWhoIsClient {

    private final RestClient restClient;

    /**
     * Creates the client with its HTTP client dependency.
     *
     * @param restClient configured with the ipwho.is base URL and request timeouts
     */
    IpWhoIsClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Calls ipwho.is for the given IP address, retrying with exponential backoff on transport or
     * server errors.
     *
     * @param ipAddress the IP address to look up
     * @return the parsed response body
     * @throws RestClientException if every retry attempt fails
     */
    @Retryable(value = RestClientException.class, maxRetries = 2, delay = 200, multiplier = 2.0)
    IpWhoIsResponse fetchLocation(String ipAddress) {
        return restClient.get().uri("/{ip}", ipAddress).retrieve().body(IpWhoIsResponse.class);
    }
}
