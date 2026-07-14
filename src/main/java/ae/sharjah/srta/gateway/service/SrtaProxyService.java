package ae.sharjah.srta.gateway.service;

import ae.sharjah.srta.gateway.config.SrtaProperties;
import ae.sharjah.srta.gateway.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Orchestrates the OSB {@code ShjRoadsTransportDeptServices} flow for every
 * {@code /deg/*} sub-path that routes to the SRTA DeG/V1 backend:
 *
 * <ol>
 *   <li>validate the caller (401 short-circuit) with dscode RT-001
 *       (OSB validated only /deg/lookup; configurable via srta.validation.paths)</li>
 *   <li>inject the static Bearer token as the {@code authorization} header</li>
 *   <li>POST to the SRTA backend at the renamed path and return its response</li>
 * </ol>
 *
 * <p>There is no token service and no DB lookup: the OSB business service
 * authenticated to SRTA with a hard-coded Bearer JWT, injected here.
 */
@Service
public class SrtaProxyService {

    private static final Logger log = LoggerFactory.getLogger(SrtaProxyService.class);

    /** Headers not forwarded from the inbound request (we set our own). */
    private static final String[] HOP_BY_HOP = {
            HttpHeaders.HOST, HttpHeaders.CONTENT_LENGTH, HttpHeaders.CONNECTION,
            HttpHeaders.TRANSFER_ENCODING, HttpHeaders.AUTHORIZATION, "authorization"
    };

    private final RestTemplate restTemplate;
    private final SrtaProperties props;
    private final UserValidationService validationService;

    public SrtaProxyService(RestTemplate restTemplate,
                            SrtaProperties props,
                            UserValidationService validationService) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.validationService = validationService;
    }

    /**
     * @param subPath     the inbound path after the context (e.g. {@code /deg/lookup})
     * @param method      the inbound HTTP method
     * @param queryString the raw inbound query string (may be null)
     * @param headers     the inbound headers
     * @param body        the inbound body (may be null)
     */
    public ResponseEntity<byte[]> forward(String subPath, HttpMethod method, String queryString,
                                          HttpHeaders headers, byte[] body) {

        // Step 1: validate user (dscode RT-001) where required
        if (props.requiresValidation(subPath)) {
            String dstoken = headers.getFirst("dstoken");
            if (!validationService.isAuthorized(dstoken)) {
                throw new UnauthorizedException("401 UNAUTHORIZED");
            }
        }

        // Resolve the backend sub-path (PascalCase operation name)
        String backendPath = props.resolveBackendPath(subPath);
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromHttpUrl(props.getBackendBaseUrl())
                .path(backendPath);
        if (StringUtils.hasText(queryString)) {
            uriBuilder.query(queryString);
        }
        String backendUrl = uriBuilder.build(true).toUriString();

        // Steps 2 & 3: inject the static Bearer and call SRTA
        HttpHeaders outHeaders = buildBackendHeaders(headers);
        HttpEntity<byte[]> entity = new HttpEntity<>(body, outHeaders);

        log.debug("Forwarding {} {} -> {}", method, subPath, backendUrl);
        ResponseEntity<byte[]> backendResponse =
                restTemplate.exchange(backendUrl, method, entity, byte[].class);

        return copyResponse(backendResponse);
    }

    private HttpHeaders buildBackendHeaders(HttpHeaders inbound) {
        HttpHeaders out = new HttpHeaders();
        inbound.forEach((name, values) -> {
            if (!isHopByHop(name)) {
                out.put(name, values);
            }
        });
        // SRTA auth: static Bearer JWT (OSB hard-coded this).
        String token = props.getBackend().getBearerToken();
        if (StringUtils.hasText(token)) {
            String value = token.startsWith("Bearer ") ? token : "Bearer " + token;
            out.set(HttpHeaders.AUTHORIZATION, value);
        }
        return out;
    }

    private ResponseEntity<byte[]> copyResponse(ResponseEntity<byte[]> backendResponse) {
        HttpHeaders responseHeaders = new HttpHeaders();
        backendResponse.getHeaders().forEach((name, values) -> {
            if (!HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(name)) {
                responseHeaders.put(name, values);
            }
        });
        responseHeaders.set("Access-Control-Allow-Origin", "*");
        return ResponseEntity.status(backendResponse.getStatusCode())
                .headers(responseHeaders)
                .body(backendResponse.getBody());
    }

    private static boolean isHopByHop(String header) {
        for (String h : HOP_BY_HOP) {
            if (h.equalsIgnoreCase(header)) {
                return true;
            }
        }
        return false;
    }
}
