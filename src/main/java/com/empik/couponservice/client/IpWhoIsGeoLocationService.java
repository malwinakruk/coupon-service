package com.empik.couponservice.client;

import com.empik.couponservice.exception.GeoLocationUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * {@link GeoLocationService} backed by the ipwho.is API.
 */
@Service
class IpWhoIsGeoLocationService implements GeoLocationService {

    private final IpWhoIsClient client;

    /**
     * Creates the service with its HTTP client dependency.
     *
     * @param client used to call ipwho.is, with retry already applied
     */
    IpWhoIsGeoLocationService(IpWhoIsClient client) {
        this.client = client;
    }

    @Override
    public String lookupCountry(String ipAddress) {
        IpWhoIsResponse response;
        try {
            response = client.fetchLocation(ipAddress);
        } catch (RestClientException e) {
            throw new GeoLocationUnavailableException(ipAddress, e);
        }

        if (response == null || !response.success() || response.countryCode() == null) {
            throw new GeoLocationUnavailableException(ipAddress, null);
        }
        return response.countryCode();
    }
}
