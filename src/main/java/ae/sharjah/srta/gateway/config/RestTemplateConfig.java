package ae.sharjah.srta.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Value("${srta.http.connect-timeout-ms:10000}")
    private long connectTimeoutMs;

    @Value("${srta.http.read-timeout-ms:30000}")
    private long readTimeoutMs;

    /**
     * A single {@link RestTemplate} used for both outbound hops (validate, SRTA backend).
     * The error handler is a no-op so that non-2xx backend responses are passed
     * through to the caller verbatim instead of raising an exception.
     */
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
        restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) {
                // pass through: the caller inspects the status code itself
            }
        });
        return restTemplate;
    }
}
