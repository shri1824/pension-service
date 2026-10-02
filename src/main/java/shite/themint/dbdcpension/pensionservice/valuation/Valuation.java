package shite.themint.dbdcpension.pensionservice.valuation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A DC member's valuation in OUR model: what was paid in, what it is worth on {@code valuedOn}, and
 * the return worked out by {@link ReturnCalculator}. Amounts are exactly the core's (never rounded
 * here); only the return is ours. Every valuation carries its date.
 */
public record Valuation(
		String currency,
		BigDecimal totalContributions,
		BigDecimal capitalValue,
		LocalDate valuedOn,
		InvestmentReturn investmentReturn) {
}
