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
 * The single dynamic controller that handles every SRTA {@code /deg/*} endpoint
 * routing to the DeG/V1 backend. Instead of one handler per operation (the OSB
 * pipeline had 4 route-nodes), the inbound path is captured at runtime and
 * forwarded generically through {@link SrtaProxyService}.
 */
@RestController
public class SrtaProxyController {

    private final SrtaProxyService proxyService;

    public SrtaProxyController(SrtaProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @RequestMapping("/deg/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,
                                        @RequestHeader HttpHeaders headers,
                                        @RequestBody(required = false) byte[] body) {

        String subPath = extractSubPath(request);
        HttpMethod method = HttpMethod.resolve(request.getMethod());
        String queryString = request.getQueryString();

        return proxyService.forward(subPath, method, queryString, headers, body);
    }

    /**
     * Returns the path within the handler mapping (relative to the servlet
     * context), always starting with '/', e.g. {@code /deg/lookup}.
     */
    private String extractSubPath(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (!StringUtils.hasText(path)) {
            path = request.getRequestURI();
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
