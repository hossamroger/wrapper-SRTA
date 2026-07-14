package ae.sharjah.srta.gateway;

import ae.sharjah.srta.gateway.config.SrtaProperties;
import ae.sharjah.srta.gateway.exception.UnauthorizedException;
import ae.sharjah.srta.gateway.service.SrtaProxyService;
import ae.sharjah.srta.gateway.service.UserValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifies the OSB flow (validate -> static Bearer -> native SRTA backend) end to
 * end against a stubbed HTTP layer. The gateway is a transparent facade: inbound
 * paths are the native operation names, forwarded 1:1.
 */
class SrtaProxyServiceTest {

    private static final String VALIDATE_URL = "http://validate.local/validate";
    private static final String BACKEND_BASE = "http://srta.local/DeG/V1";
    private static final String JWT = "eyJtest.jwt.token";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private SrtaProperties props;
    private SrtaProxyService service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse r) { return false; }
            @Override
            public void handleError(org.springframework.http.client.ClientHttpResponse r) { }
        });
        server = MockRestServiceServer.createServer(restTemplate);

        props = new SrtaProperties();
        props.setBackendBaseUrl(BACKEND_BASE);
        props.getBackend().setBearerToken(JWT);
        props.getValidation().setUrl(VALIDATE_URL);

        UserValidationService validation = new UserValidationService(restTemplate, props);
        service = new SrtaProxyService(restTemplate, props, validation);
    }

    private HttpHeaders inbound() {
        HttpHeaders h = new HttpHeaders();
        h.add("dstoken", "USER-TOKEN");
        h.add("language", "en");
        return h;
    }

    @Test
    void lookup_validatesThenPostsToNativeLockupWithStaticBearer() {
        server.expect(requestTo(VALIDATE_URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("dstoken", "USER-TOKEN"))
                .andExpect(header("dscode", "RT-001"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // Inbound path is the native name; forwarded 1:1.
        server.expect(requestTo(BACKEND_BASE + "/Lockup")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + JWT))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        ResponseEntity<byte[]> response =
                service.forward("/Lockup", HttpMethod.POST, null, inbound(), "{}".getBytes());

        server.verify();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("*", response.getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    @Test
    void unauthorized_shortCircuitsBeforeBackend() {
        server.expect(requestTo(VALIDATE_URL))
                .andRespond(withSuccess("{\"statusCode\":\"401\"}", MediaType.APPLICATION_JSON));

        assertThrows(UnauthorizedException.class, () ->
                service.forward("/Lockup", HttpMethod.POST, null, inbound(), "{}".getBytes()));

        server.verify();
    }

    @Test
    void complaintPath_forwardsNativeNameUnchanged() {
        // Scope validation to Lockup only (OSB parity) -> this path skips validation.
        props.getValidation().setPaths(Arrays.asList("/Lockup"));

        server.expect(requestTo(BACKEND_BASE + "/TaxiComplaintDetails")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + JWT))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        ResponseEntity<byte[]> response =
                service.forward("/TaxiComplaintDetails", HttpMethod.POST, null, inbound(), "{}".getBytes());

        server.verify();
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void backendErrorStatus_isPassedThrough() {
        server.expect(requestTo(VALIDATE_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BACKEND_BASE + "/Lockup"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("upstream down"));

        ResponseEntity<byte[]> response =
                service.forward("/Lockup", HttpMethod.POST, null, inbound(), "{}".getBytes());

        server.verify();
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
    }
}
