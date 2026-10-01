package shite.themint.dbdcpension.pensionservice.error;

/**
 * The core could not be reached, timed out, failed (5xx), or we could not get a token for it.
 * Becomes a 503 problem detail; the caller decides what a missing section means.
 */
public class CoreUnavailableException extends RuntimeException {

	public CoreUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
