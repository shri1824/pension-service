package shite.themint.dbdcpension.pensionservice.participation;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import shite.themint.dbdcpension.pensionservice.config.CacheConfig;
import shite.themint.dbdcpension.pensionservice.core.CoreCalls;
import shite.themint.dbdcpension.pensionservice.error.CoreResponseException;

/**
 * The anti-corruption layer for participation: the only place that knows the core's URL and
 * field names. It calls the core, turns the core's failures into our own exceptions, and
 * translates the core's JSON into {@link Participation}.
 */
@Component
class CoreParticipationAdapter implements ParticipationPort {

	private static final String PATH = "/core/v1/participants/{participantId}/participation";

	private final RestClient coreRestClient;

	CoreParticipationAdapter(RestClient coreRestClient) {
		this.coreRestClient = coreRestClient;
	}

	// Only successes are cached: an exception is never stored, so a core outage or a 404 is retried next time.
	@Override
	@Cacheable(cacheNames = CacheConfig.PARTICIPATION_CACHE, key = "#participantId")
	public Participation getParticipation(String participantId) {
		return toParticipation(CoreCalls.get(coreRestClient, PATH, participantId, CoreParticipationResponse.class));
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
