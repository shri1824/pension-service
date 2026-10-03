package shite.themint.dbdcpension.pensionservice.investmentchange;

/** The message is not a valid status-changed event. Retrying cannot help, so it goes straight to the dead-letter topic. */
class MalformedEventException extends RuntimeException {

	MalformedEventException(String message) {
		super(message);
	}

	MalformedEventException(String message, Throwable cause) {
		super(message, cause);
	}

}
