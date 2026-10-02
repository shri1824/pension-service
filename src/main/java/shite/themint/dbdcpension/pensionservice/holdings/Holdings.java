package shite.themint.dbdcpension.pensionservice.holdings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The investment funds a DC member holds, in OUR model (the "Investment funds and costs" table).
 * Everything is exactly what the core gave: units, prices and totals are never recomputed or rounded
 * here, because a fund's total does not always equal units x price to the cent. The list is empty for
 * a member who has not invested anything yet.
 */
public record Holdings(String currency, List<Fund> funds) {

	/**
	 * @param units      units held, up to 6 decimals
	 * @param priceDate  the day {@code unitPrice} is from; it can differ from the valuation date of the tiles
	 * @param totalValue the core's own total for this fund
	 */
	public record Fund(
			String fundId,
			String name,
			BigDecimal units,
			LocalDate priceDate,
			BigDecimal unitPrice,
			BigDecimal totalValue) {
	}

}
