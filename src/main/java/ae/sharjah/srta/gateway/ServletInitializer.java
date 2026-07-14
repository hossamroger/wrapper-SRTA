package ae.sharjah.srta.gateway;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Enables the WAR to be deployed to an external servlet container
 * (WebLogic / Tomcat), as the OSB layer was hosted on WebLogic.
 */
public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(SrtaGatewayApplication.class);
    }
}
