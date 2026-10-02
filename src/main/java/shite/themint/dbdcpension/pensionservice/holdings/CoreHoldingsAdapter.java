package shite.themint.dbdcpension.pensionservice.holdings;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import shite.themint.dbdcpension.pensionservice.config.CacheConfig;
import shite.themint.dbdcpension.pensionservice.core.CoreCalls;
import shite.themint.dbdcpension.pensionservice.error.CoreResponseException;

/**
 * The anti-corruption layer for the fund holdings: the only place that knows the core's URL and
 * field names. Data that makes no sense (a missing list, missing or negative figures, no date or name,
 * a currency that is not a code) is rejected instead of being shown to a member. An EMPTY list is valid:
 * it is a member who has not invested anything yet. Figures are never recomputed.
 */
@Component
class CoreHoldingsAdapter implements HoldingsPort {

	private static final String PATH = "/core/v1/participants/{participantId}/holdings";

	private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

	private final RestClient coreRestClient;

	CoreHoldingsAdapter(RestClient coreRestClient) {
		this.coreRestClient = coreRestClient;
	}

	// Only successes are cached: an exception is never stored, so a core outage or a 404 is retried next time.
	// Never served stale after a core failure: a days-old valuation could mislead (design section 6).
	@Override
	@Cacheable(cacheNames = CacheConfig.HOLDINGS_CACHE, key = "#participantId")
	public Holdings getHoldings(String participantId) {
		return toHoldings(CoreCalls.get(coreRestClient, PATH, participantId, CoreHoldingsResponse.class));
	}

	static Holdings toHoldings(CoreHoldingsResponse core) {
		if (core.currencyCode() == null || !CURRENCY.matcher(core.currencyCode()).matches()) {
			throw new CoreResponseException("Currency from the core is missing or not a currency code");
		}
		if (core.positions() == null) {
			// A missing list is not the same as "no funds": the core did not tell us.
			throw new CoreResponseException("Fund list from the core is missing");
		}
		return new Holdings(core.currencyCode(), core.positions().stream().map(CoreHoldingsAdapter::toFund).toList());
	}

	private static Holdings.Fund toFund(CoreHoldingsResponse.Position position) {
		if (position == null || isBlank(position.fundCode()) || isBlank(position.fundName())) {
			throw new CoreResponseException("A fund from the core has no code or no name");
		}
		if (position.priceDate() == null) {
			throw new CoreResponseException("A fund from the core has no price date");
		}
		return new Holdings.Fund(
				position.fundCode(),
				position.fundName(),
				figure(position.unitCount()),
				position.priceDate(),
				figure(position.unitPrice()),
				figure(position.positionValue()));
	}

	private static BigDecimal figure(BigDecimal value) {
		if (value == null || value.signum() < 0) {
			throw new CoreResponseException("A fund figure from the core is missing or negative");
		}
		return value;
	}

	private static boolean isBlank(String text) {
		return text == null || text.isBlank();
	}

}
