package shite.themint.dbdcpension.pensionservice.valuation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The return rule (assumption A2): {@code return = capital value - total contributions} and
 * {@code percentage = return / total contributions x 100}, both rounded to 2 decimals, half up.
 * This is a SIMPLE return, not a time-weighted one; the figures on the Accrued tab screenshot are
 * consistent with exactly this rule. The method itself should be reviewed by pension calculations
 * and compliance before go-live.
 */
public final class ReturnCalculator {

	private static final BigDecimal HUNDRED = new BigDecimal("100");

	private ReturnCalculator() {
	}

	/** @param totalContributions zero or more; @param capitalValue zero or more */
	public static InvestmentReturn calculate(BigDecimal totalContributions, BigDecimal capitalValue) {
		BigDecimal amount = capitalValue.subtract(totalContributions).setScale(2, RoundingMode.HALF_UP);
		BigDecimal percentage = totalContributions.signum() == 0 ? null
				: amount.multiply(HUNDRED).divide(totalContributions, 2, RoundingMode.HALF_UP);
		return new InvestmentReturn(amount, percentage);
	}

}
