package shite.themint.dbdcpension.pensionservice.projection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A member's expected pension in OUR model: gross amount per month at retirement, in three
 * scenarios, with the age, the currency, the basis of the amounts and the date of the calculation.
 * Amounts are returned exactly as the core gave them (never recomputed or rounded here).
 */
public record Projection(
		int retirementAge,
		String currency,
		AmountBasis amountBasis,
		LocalDate calculatedOn,
		List<Scenario> scenarios) {

	/** @param grossMonthlyAmount gross per month, in {@link Projection#currency()} */
	public record Scenario(ScenarioType type, BigDecimal grossMonthlyAmount) {
	}

}
