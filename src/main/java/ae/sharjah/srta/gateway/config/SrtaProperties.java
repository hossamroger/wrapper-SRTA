package ae.sharjah.srta.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Externalised configuration for the SRTA gateway.
 *
 * <p>Everything the OSB pipeline hard-coded (backend URL, the static Bearer JWT,
 * the validation service code, the operation path renames) lives here so it can
 * be supplied per-environment and kept out of source control.
 */
@Component
@ConfigurationProperties(prefix = "srta")
public class SrtaProperties {

    /** Base URL of the SRTA DeG backend, e.g. https://eformstaging.srta.gov.ae/DeG/V1 */
    private String backendBaseUrl;

    private final Backend backend = new Backend();
    private final Ebooking ebooking = new Ebooking();
    private final Validation validation = new Validation();

    /**
     * Inbound sub-path -> backend sub-path overrides. The SRTA backend uses
     * PascalCase operation names that differ from the inbound REST paths, so
     * every operation is mapped here (there is no useful pass-through default).
     */
    private Map<String, String> pathOverrides = new LinkedHashMap<>();

    public static class Backend {
        /**
         * Static Bearer token OSB injected as the {@code authorization} header.
         * Supply via env var (SRTA_BACKEND_BEARER); do NOT commit the real JWT.
         */
        private String bearerToken;

        public String getBearerToken() { return bearerToken; }
        public void setBearerToken(String bearerToken) { this.bearerToken = bearerToken; }
    }

    public static class Ebooking {
        /** Base URL of the taxidispatch backend, e.g. https://ebooking.srta.gov.ae:9101/taxidispatch */
        private String baseUrl;
        /**
         * Static token OSB sent to taxidispatch as the {@code accessToken} header.
         * Supply via env var (SRTA_EBOOKING_TOKEN); do NOT commit the real value.
         */
        private String accessToken;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    }

    public static class Validation {
        /** When false the user-validation hop is skipped entirely. */
        private boolean enabled = true;
        /** Full URL of the validateUser service. */
        private String url;
        /** Service code sent as the {@code dscode} header (OSB used RT-001). */
        private String dscode = "RT-001";
        /**
         * Inbound sub-paths that require validation. Empty = validate all.
         * OSB only validated {@code /deg/lookup}; set this to that value for exact parity.
         */
        private java.util.List<String> paths = new java.util.ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getDscode() { return dscode; }
        public void setDscode(String dscode) { this.dscode = dscode; }
        public java.util.List<String> getPaths() { return paths; }
        public void setPaths(java.util.List<String> paths) { this.paths = paths; }
    }

    /** Resolve the backend sub-path for an inbound sub-path (trailing slash tolerant). */
    public String resolveBackendPath(String inboundSubPath) {
        String normalized = stripTrailingSlash(inboundSubPath);
        String override = pathOverrides.get(normalized);
        if (override == null) {
            override = pathOverrides.get(inboundSubPath);
        }
        return override != null ? override : normalized;
    }

    /** Whether the given inbound sub-path should be validated. */
    public boolean requiresValidation(String inboundSubPath) {
        if (!validation.isEnabled()) {
            return false;
        }
        if (validation.getPaths().isEmpty()) {
            return true;
        }
        String normalized = stripTrailingSlash(inboundSubPath);
        return validation.getPaths().contains(normalized) || validation.getPaths().contains(inboundSubPath);
    }

    private static String stripTrailingSlash(String p) {
        if (p != null && p.length() > 1 && p.endsWith("/")) {
            return p.substring(0, p.length() - 1);
        }
        return p;
    }

    public String getBackendBaseUrl() { return backendBaseUrl; }
    public void setBackendBaseUrl(String backendBaseUrl) { this.backendBaseUrl = backendBaseUrl; }
    public Backend getBackend() { return backend; }
    public Ebooking getEbooking() { return ebooking; }
    public Validation getValidation() { return validation; }
    public Map<String, String> getPathOverrides() { return pathOverrides; }
    public void setPathOverrides(Map<String, String> pathOverrides) { this.pathOverrides = pathOverrides; }
}
