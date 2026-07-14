package ae.sharjah.srta.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the SRTA gateway.
 *
 * <p>The reactive {@code WebClient} auto-configurations are excluded: this app
 * uses {@code RestTemplate}, not {@code WebClient}. On WebLogic the container
 * exposes its own reactive Spring classes, which would otherwise trigger
 * {@code WebClientAutoConfiguration} and fail at startup with
 * {@code NoClassDefFoundError: ...DefaultExchangeStrategiesBuilder}.
 */
@SpringBootApplication(excludeName = {
        "org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration",
        "org.springframework.boot.autoconfigure.web.reactive.function.client.ClientHttpConnectorAutoConfiguration"
})
public class SrtaGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SrtaGatewayApplication.class, args);
    }
}
