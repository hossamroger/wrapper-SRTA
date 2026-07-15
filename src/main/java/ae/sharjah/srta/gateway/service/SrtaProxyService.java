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

import java.util.function.Consumer;

/**
 * Orchestrates the OSB {@code ShjRoadsTransport} flows. The gateway is a
 * transparent facade over two native SRTA backends:
 *
 * <ul>
 *   <li><b>DeG/V1</b> (complaints/lookup) — authenticated with a static Bearer JWT
 *       ({@link #forward}).</li>
 *   <li><b>taxidispatch / ebooking</b> — authenticated with a static
 *       {@code accessToken} header ({@link #forwardEbooking}).</li>
 * </ul>
 *
 * Both first validate the caller (dscode RT-001, 401 short-circuit), then forward
 * the request 1:1 to the native backend.
 */
@Service
public class SrtaProxyService {

    private static final Logger log = LoggerFactory.getLogger(SrtaProxyService.class);

    /** Headers not forwarded from the inbound request (we set our own). */
    private static final String[] HOP_BY_HOP = {
            HttpHeaders.HOST, HttpHeaders.CONTENT_LENGTH, HttpHeaders.CONNECTION,
            HttpHeaders.TRANSFER_ENCODING, HttpHeaders.AUTHORIZATION, "authorization", "accesstoken"
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

    /** DeG/V1 backend — static Bearer JWT auth. */
    public ResponseEntity<byte[]> forward(String subPath, HttpMethod method, String queryString,
                                          HttpHeaders headers, byte[] body) {
        return forwardTo(subPath, method, queryString, headers, body,
                props.getBackendBaseUrl(), this::applyBearer);
    }

    /** taxidispatch / ebooking backend — static {@code accessToken} header auth. */
    public ResponseEntity<byte[]> forwardEbooking(String subPath, HttpMethod method, String queryString,
                                                  HttpHeaders headers, byte[] body) {
        return forwardTo(subPath, method, queryString, headers, body,
                props.getEbooking().getBaseUrl(), this::applyAccessToken);
    }

    private ResponseEntity<byte[]> forwardTo(String subPath, HttpMethod method, String queryString,
                                             HttpHeaders headers, byte[] body,
                                             String baseUrl, Consumer<HttpHeaders> authApplier) {

        // Step 1: validate user (dscode RT-001) where required
        if (props.requiresValidation(subPath)) {
            String dstoken = headers.getFirst("dstoken");
            if (!validationService.isAuthorized(dstoken)) {
                throw new UnauthorizedException("401 UNAUTHORIZED");
            }
        }

        // Transparent facade: inbound path is the native path, forwarded 1:1.
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path(props.resolveBackendPath(subPath));
        if (StringUtils.hasText(queryString)) {
            uriBuilder.query(queryString);
        }
        String backendUrl = uriBuilder.build(true).toUriString();

        // Steps 2 & 3: inject backend auth and call the native backend
        HttpHeaders outHeaders = buildBackendHeaders(headers, authApplier);
        HttpEntity<byte[]> entity = new HttpEntity<>(body, outHeaders);

        log.debug("Forwarding {} {} -> {}", method, subPath, backendUrl);
        ResponseEntity<byte[]> backendResponse =
                restTemplate.exchange(backendUrl, method, entity, byte[].class);

        return copyResponse(backendResponse);
    }

    /** DeG auth: static Bearer JWT (OSB hard-coded this). */
    private void applyBearer(HttpHeaders out) {
        String token = props.getBackend().getBearerToken();
        if (StringUtils.hasText(token)) {
            out.set(HttpHeaders.AUTHORIZATION, token.startsWith("Bearer ") ? token : "Bearer " + token);
        }
    }

    /** taxidispatch auth: static accessToken header (OSB hard-coded this). */
    private void applyAccessToken(HttpHeaders out) {
        String token = props.getEbooking().getAccessToken();
        if (StringUtils.hasText(token)) {
            out.set("accessToken", token);
        }
    }

    private HttpHeaders buildBackendHeaders(HttpHeaders inbound, Consumer<HttpHeaders> authApplier) {
        HttpHeaders out = new HttpHeaders();
        inbound.forEach((name, values) -> {
            if (!isHopByHop(name)) {
                out.put(name, values);
            }
        });
        authApplier.accept(out);
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
