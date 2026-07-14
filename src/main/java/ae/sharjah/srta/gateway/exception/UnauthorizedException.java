package ae.sharjah.srta.gateway.exception;

/**
 * Raised when user validation fails (OSB returned statusCode 401 from the
 * validateUser service). Mapped to an HTTP 401 response by the controller.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
