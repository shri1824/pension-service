package shite.themint.dbdcpension.pensionservice.valuation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The core's valuation JSON, with the core's own names. Package-private on purpose: only the
 * adapter may know it, so a change in the core touches this package and nothing else.
 */
record CoreValuationResponse(
		String participantId,
		String currencyCode,
		BigDecimal premiumsPaidTotal,
		BigDecimal marketValue,
		LocalDate valuationDate) {
}
