package shite.themint.dbdcpension.pensionservice.participation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import shite.themint.dbdcpension.pensionservice.config.CacheConfig;
import shite.themint.dbdcpension.pensionservice.error.CoreResponseException;
import shite.themint.dbdcpension.pensionservice.error.CoreUnavailableException;

/**
 * The anti-corruption layer for participation: the only place that knows the core's URL and
 * field names. It calls the core, turns the core's failures into our own exceptions, and
 * translates the core's JSON into {@link Participation}.
 */
@Component
class CoreParticipationAdapter implements ParticipationPort {

	private static final Logger log = LoggerFactory.getLogger(CoreParticipationAdapter.class);

	private static final String PATH = "/core/v1/participants/{participantId}/participation";

	private final RestClient coreRestClient;

	CoreParticipationAdapter(RestClient coreRestClient) {
		this.coreRestClient = coreRestClient;
	}

	// Only successes are cached: an exception is never stored, so a core outage or a 404 is retried next time.
	@Override
	@Cacheable(cacheNames = CacheConfig.PARTICIPATION_CACHE, key = "#participantId")
	public Participation getParticipation(String participantId) {
		CoreParticipationResponse response;
		try {
			// The id is a URI variable, so it is encoded and cannot change the path.
			response = coreRestClient.get().uri(PATH, participantId).retrieve().body(CoreParticipationResponse.class);
		}
		catch (HttpClientErrorException.NotFound ex) {
			throw new ParticipantNotFoundException();
		}
		catch (HttpStatusCodeException | ResourceAccessException ex) {
			log.warn("Core participation call failed: {}", ex.getClass().getSimpleName());
			throw new CoreUnavailableException("Core participation call failed", ex);
		}
		catch (OAuth2AuthorizationException ex) {
			// No token means no way to call the core. Never log the exception message or secrets.
			log.error("Could not get a token for the core: {}", ex.getError().getErrorCode());
			throw new CoreUnavailableException("Could not get a token for the core", ex);
		}
		catch (RestClientException ex) {
			log.warn("Core participation answer could not be read: {}", ex.getClass().getSimpleName());
			throw new CoreResponseException("Core participation answer could not be read", ex);
		}

		if (response == null) {
			throw new CoreResponseException("Empty answer from the core");
		}
		return toParticipation(response);
	}

	static Participation toParticipation(CoreParticipationResponse core) {
		return new Participation(
				core.schemeCode(),
				core.employerName(),
				toContractType(core.planType()),
				core.startDate(),
				core.endDate());
	}

	private static ContractType toContractType(String planType) {
		if ("DC".equals(planType)) {
			return ContractType.DC;
		}
		if ("DB".equals(planType)) {
			return ContractType.DB;
		}
		throw new CoreResponseException("Unknown plan type from the core");
	}

}
