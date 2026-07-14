package ae.sharjah.srta.gateway.service;

import ae.sharjah.srta.gateway.config.SrtaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Step 1 of the OSB flow: call the validateUser service with the caller's
 * {@code dstoken} and {@code dscode}. OSB treated a response
 * {@code statusCode == 401} as unauthorized and short-circuited the pipeline.
 */
@Service
public class UserValidationService {

    private static final Logger log = LoggerFactory.getLogger(UserValidationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final SrtaProperties props;

    public UserValidationService(RestTemplate restTemplate, SrtaProperties props) {
        this.restTemplate = restTemplate;
        this.props = props;
    }

    /**
     * @return {@code true} if the caller is authorized, {@code false} if the
     *         validateUser service reported 401.
     */
    public boolean isAuthorized(String dstoken) {
        SrtaProperties.Validation cfg = props.getValidation();
        if (!StringUtils.hasText(cfg.getUrl())) {
            log.warn("Validation required but srta.validation.url is not set; skipping validation.");
            return true;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(dstoken)) {
            headers.add("dstoken", dstoken);
        }
        headers.add("dscode", cfg.getDscode());

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    cfg.getUrl(), HttpMethod.POST, new HttpEntity<>(null, headers), String.class);
            return !isUnauthorized(response.getBody());
        } catch (Exception ex) {
            // Do not let a transient validation failure open the gate.
            log.warn("User validation call failed: {}", ex.toString());
            return false;
        }
    }

    private boolean isUnauthorized(String body) {
        if (!StringUtils.hasText(body)) {
            return false;
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode status = root.get("statusCode");
            return status != null && "401".equals(status.asText());
        } catch (Exception ex) {
            log.warn("Unable to parse validation response, treating as authorized: {}", ex.toString());
            return false;
        }
    }
}
