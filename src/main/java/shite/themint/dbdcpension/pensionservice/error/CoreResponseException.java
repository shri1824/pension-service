package shite.themint.dbdcpension.pensionservice.error;

/**
 * The core answered, but with data we cannot use (empty body, unreadable JSON, an unknown
 * plan type). Becomes a 502 problem detail.
 */
public class CoreResponseException extends RuntimeException {

	public CoreResponseException(String message) {
		super(message);
	}

	public CoreResponseException(String message, Throwable cause) {
		super(message, cause);
	}

}
