package shite.themint.dbdcpension.pensionservice.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import shite.themint.dbdcpension.pensionservice.error.CoreResponseException;
import shite.themint.dbdcpension.pensionservice.error.CoreUnavailableException;
import shite.themint.dbdcpension.pensionservice.error.ParticipantNotFoundException;

/**
 * How every adapter calls the core: one GET with the member id as a path variable, and every
 * failure turned into one of OUR exceptions, so no adapter repeats this and none lets a
 * RestClient or OAuth2 exception escape.
 */
public final class CoreCalls {

	private static final Logger log = LoggerFactory.getLogger(CoreCalls.class);

	private CoreCalls() {
	}

	/**
	 * @param path a URI template with exactly one variable, the participant id
	 * @throws ParticipantNotFoundException the core answered 404
	 * @throws CoreUnavailableException     the core could not be reached, failed, or no token was available
	 * @throws CoreResponseException        the core answered with something we cannot read
	 */
	public static <T> T get(RestClient coreRestClient, String path, String participantId, Class<T> type) {
		T response;
		try {
			// The id is a URI variable, so it is encoded and cannot change the path.
			response = coreRestClient.get().uri(path, participantId).retrieve().body(type);
		}
		catch (HttpClientErrorException.NotFound ex) {
			throw new ParticipantNotFoundException();
		}
		catch (HttpStatusCodeException | ResourceAccessException ex) {
			log.warn("Core call failed: {}", ex.getClass().getSimpleName());
			throw new CoreUnavailableException("Core call failed", ex);
		}
		catch (OAuth2AuthorizationException ex) {
			// No token means no way to call the core. Never log the exception message or secrets.
			log.error("Could not get a token for the core: {}", ex.getError().getErrorCode());
			throw new CoreUnavailableException("Could not get a token for the core", ex);
		}
		catch (RestClientException ex) {
			log.warn("Core answer could not be read: {}", ex.getClass().getSimpleName());
			throw new CoreResponseException("Core answer could not be read", ex);
		}

		if (response == null) {
			throw new CoreResponseException("Empty answer from the core");
		}
		return response;
	}

}
