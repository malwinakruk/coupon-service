package com.empik.couponservice.exception;

/**
 * Thrown when the geolocation provider is unreachable, errors, or returns an unusable response,
 * even after retries. UC2 fails closed on this — the request is rejected rather than let through
 * without knowing the user's country.
 */
public class GeoLocationUnavailableException extends RuntimeException {

    /**
     * Creates the exception for a lookup that could not be completed.
     *
     * @param ipAddress the IP address the lookup was for
     * @param cause the underlying failure, if any
     */
    public GeoLocationUnavailableException(String ipAddress, Throwable cause) {
        super("Geolocation lookup failed for IP address: " + ipAddress, cause);
    }
}
