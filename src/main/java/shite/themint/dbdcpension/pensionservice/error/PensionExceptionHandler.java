package shite.themint.dbdcpension.pensionservice.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import shite.themint.dbdcpension.pensionservice.participation.ParticipantNotFoundException;

/**
 * Turns our exceptions into RFC 9457 problem details. The messages are short and generic:
 * no stack trace, no URL of the core, nothing internal reaches the caller.
 */
@RestControllerAdvice
public class PensionExceptionHandler {

	@ExceptionHandler(ParticipantNotFoundException.class)
	public ProblemDetail handleNotFound(ParticipantNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", "Participant not found");
	}

	@ExceptionHandler(CoreUnavailableException.class)
	public ProblemDetail handleCoreUnavailable(CoreUnavailableException ex) {
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Core unavailable", "The core system is temporarily unavailable");
	}

	@ExceptionHandler(CoreResponseException.class)
	public ProblemDetail handleCoreResponse(CoreResponseException ex) {
		return problem(HttpStatus.BAD_GATEWAY, "Unexpected core response", "The core system returned data we cannot use");
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}
