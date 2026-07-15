package ae.sharjah.srta.gateway.web;

import ae.sharjah.srta.gateway.service.SrtaProxyService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * Transparent facade over the native SRTA <b>taxidispatch</b> (ebooking) backend.
 * Inbound paths are the native operation names, forwarded 1:1 to
 * {@code https://ebooking.srta.gov.ae:9101/taxidispatch}; the gateway validates
 * the user and injects the static {@code accessToken} header.
 */
@RestController
public class EbookingProxyController {

    private final SrtaProxyService proxyService;

    public EbookingProxyController(SrtaProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @RequestMapping({
            "/vehicleType", "/vehiclelocation", "/cancel", "/getFare", "/driverPhoto",
            "/searchAddrByLatLon", "/nearbyvehicles", "/searchAddrByKeyword",
            "/jobstatus", "/jobdetails", "/book"
    })
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,
                                        @RequestHeader HttpHeaders headers,
                                        @RequestBody(required = false) byte[] body) {

        String subPath = extractSubPath(request);
        HttpMethod method = HttpMethod.resolve(request.getMethod());
        String queryString = request.getQueryString();

        return proxyService.forwardEbooking(subPath, method, queryString, headers, body);
    }

    private String extractSubPath(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (!StringUtils.hasText(path)) {
            path = request.getRequestURI();
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
