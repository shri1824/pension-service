package shite.themint.dbdcpension.pensionservice.projection;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import shite.themint.dbdcpension.pensionservice.config.CacheConfig;
import shite.themint.dbdcpension.pensionservice.core.CoreCalls;
import shite.themint.dbdcpension.pensionservice.error.CoreResponseException;

/**
 * The anti-corruption layer for the projection: the only place that knows the core's URL and
 * field names. Data that makes no sense (missing or negative amounts, an impossible age, a
 * currency that is not a code) is rejected instead of being shown to a member as a pension.
 */
@Component
class CoreProjectionAdapter implements ProjectionPort {

	private static final String PATH = "/core/v1/participants/{participantId}/projection";

	private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
	private static final int MIN_AGE = 50;
	private static final int MAX_AGE = 80;

	private final RestClient coreRestClient;

	CoreProjectionAdapter(RestClient coreRestClient) {
		this.coreRestClient = coreRestClient;
	}

	// Only successes are cached: an exception is never stored, so a core outage or a 404 is retried next time.
	@Override
	@Cacheable(cacheNames = CacheConfig.PROJECTION_CACHE, key = "#participantId")
	public Projection getProjection(String participantId) {
		return toProjection(CoreCalls.get(coreRestClient, PATH, participantId, CoreProjectionResponse.class));
	}

	static Projection toProjection(CoreProjectionResponse core) {
		if (core.pensionAge() == null || core.pensionAge() < MIN_AGE || core.pensionAge() > MAX_AGE) {
			throw new CoreResponseException("Retirement age from the core is missing or out of range");
		}
		if (core.curr() == null || !CURRENCY.matcher(core.curr()).matches()) {
			throw new CoreResponseException("Currency from the core is missing or not a currency code");
		}
		if (core.calculationDate() == null) {
			throw new CoreResponseException("Calculation date from the core is missing");
		}
		return new Projection(
				core.pensionAge(),
				core.curr(),
				// The core does not state the basis; the regulated amounts are euros of today (assumption A7).
				AmountBasis.TODAYS_PRICES,
				core.calculationDate(),
				List.of(
						new Projection.Scenario(ScenarioType.EXPECTED, amount(core.middleScenario())),
						new Projection.Scenario(ScenarioType.UNFAVOURABLE, amount(core.lowScenario())),
						new Projection.Scenario(ScenarioType.FAVOURABLE, amount(core.highScenario()))));
	}

	private static BigDecimal amount(BigDecimal value) {
		if (value == null || value.signum() < 0) {
			throw new CoreResponseException("A scenario amount from the core is missing or negative");
		}
		return value;
	}

}
