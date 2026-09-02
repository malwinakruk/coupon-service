package com.empik.couponservice.client;

import com.empik.couponservice.exception.GeoLocationUnavailableException;

/**
 * Resolves the country a given IP address is in.
 */
public interface GeoLocationService {

    /**
     * Looks up the country for an IP address.
     *
     * @param ipAddress the IP address to resolve
     * @return the ISO 3166-1 alpha-2 country code
     * @throws GeoLocationUnavailableException if the lookup fails, even after retries
     */
    String lookupCountry(String ipAddress);
}
