package shite.themint.dbdcpension.pensionservice.valuation;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import shite.themint.dbdcpension.pensionservice.config.CacheConfig;
import shite.themint.dbdcpension.pensionservice.core.CoreCalls;
import shite.themint.dbdcpension.pensionservice.error.CoreResponseException;

/**
 * The anti-corruption layer for the valuation: the only place that knows the core's URL and field
 * names. Data that makes no sense (missing or negative amounts, no date, a currency that is not a
 * code) is rejected instead of being shown to a member. The return is worked out here, never by the core.
 */
@Component
class CoreValuationAdapter implements ValuationPort {

	private static final String PATH = "/core/v1/participants/{participantId}/valuation";

	private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

	private final RestClient coreRestClient;

	CoreValuationAdapter(RestClient coreRestClient) {
		this.coreRestClient = coreRestClient;
	}

	// Only successes are cached: an exception is never stored, so a core outage or a 404 is retried next time.
	// Never served stale after a core failure: a days-old valuation could mislead (design section 6).
	@Override
	@Cacheable(cacheNames = CacheConfig.VALUATION_CACHE, key = "#participantId")
	public Valuation getValuation(String participantId) {
		return toValuation(CoreCalls.get(coreRestClient, PATH, participantId, CoreValuationResponse.class));
	}

	static Valuation toValuation(CoreValuationResponse core) {
		if (core.currencyCode() == null || !CURRENCY.matcher(core.currencyCode()).matches()) {
			throw new CoreResponseException("Currency from the core is missing or not a currency code");
		}
		if (core.valuationDate() == null) {
			throw new CoreResponseException("Valuation date from the core is missing");
		}
		BigDecimal contributions = amount(core.premiumsPaidTotal());
		BigDecimal capital = amount(core.marketValue());
		return new Valuation(
				core.currencyCode(),
				contributions,
				capital,
				core.valuationDate(),
				ReturnCalculator.calculate(contributions, capital));
	}

	private static BigDecimal amount(BigDecimal value) {
		if (value == null || value.signum() < 0) {
			throw new CoreResponseException("An amount from the core is missing or negative");
		}
		return value;
	}

}
