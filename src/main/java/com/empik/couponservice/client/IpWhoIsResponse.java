package com.empik.couponservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body from the ipwho.is geolocation API.
 *
 * @param success whether the lookup succeeded
 * @param countryCode ISO 3166-1 alpha-2 country code, present only when {@code success} is true
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IpWhoIsResponse(boolean success, @JsonProperty("country_code") String countryCode) {
}
