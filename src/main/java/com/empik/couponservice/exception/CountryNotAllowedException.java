package com.empik.couponservice.exception;

/**
 * Thrown when the user's geolocated country doesn't match the coupon's target country.
 */
public class CountryNotAllowedException extends RuntimeException {

    /**
     * Creates the exception for the given country mismatch.
     *
     * @param userCountry the country the user was geolocated to
     * @param couponCountry the country the coupon is restricted to
     */
    public CountryNotAllowedException(String userCountry, String couponCountry) {
        super("User's country (" + userCountry + ") does not match the coupon's country (" + couponCountry + ")");
    }
}
