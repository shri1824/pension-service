package shite.themint.dbdcpension.pensionservice.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One transaction in full, in OUR model: what the member sees when a row of the Transaction overview
 * is expanded. Everything is exactly what the core gave and signed (negative means units were sold).
 * <ul>
 * <li>The components do NOT always add up to {@code amount} to the cent, and nothing here recomputes
 * either of them.</li>
 * <li>A PRICE_DIFFERENCE transaction has no components.</li>
 * <li>The price date of a fund line can be AFTER the transaction date; both are kept.</li>
 * </ul>
 */
public record TransactionDetail(
		String transactionId,
		LocalDate date,
		TransactionType type,
		String currency,
		BigDecimal amount,
		List<Component> components,
		List<FundLine> funds) {

	/**
	 * A contribution or a cost that affects the number of units.
	 *
	 * @param code   a code such as DEFINED_CONTRIBUTION, BONUS or ADMINISTRATION_COSTS; passed on as the core
	 *               sends it, so a new kind of component does not change this contract (the front end writes the text)
	 * @param amount signed
	 */
	public record Component(String code, BigDecimal amount) {
	}

	/**
	 * @param units      signed, up to 6 decimals
	 * @param priceDate  the day {@code unitPrice} is from
	 * @param totalValue the core's own total for this fund line (signed)
	 */
	public record FundLine(
			String fundId,
			String name,
			BigDecimal units,
			LocalDate priceDate,
			BigDecimal unitPrice,
			BigDecimal totalValue) {
	}

}
