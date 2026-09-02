package com.empik.couponservice.client;

import com.empik.couponservice.exception.GeoLocationUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * {@link GeoLocationService} backed by the ipwho.is API.
 */
@Service
class IpWhoIsGeoLocationService implements GeoLocationService {

    private static final Logger LOG = LoggerFactory.getLogger(IpWhoIsGeoLocationService.class);

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
            LOG.warn("Geolocation lookup failed for IP {}: {}", ipAddress, e.getMessage());
            throw new GeoLocationUnavailableException(ipAddress, e);
        }

        if (response == null || !response.success() || response.countryCode() == null) {
            LOG.warn("Geolocation lookup returned an unusable response for IP {}", ipAddress);
            throw new GeoLocationUnavailableException(ipAddress, null);
        }
        LOG.debug("Resolved IP {} to country {}", ipAddress, response.countryCode());
        return response.countryCode();
    }
}
