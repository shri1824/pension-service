package shite.themint.dbdcpension.pensionservice.holdings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The core's holdings JSON, with the core's own names. Package-private on purpose: only the adapter
 * may know it, so a change in the core touches this package and nothing else.
 */
record CoreHoldingsResponse(String participantId, String currencyCode, List<Position> positions) {

	record Position(
			String fundCode,
			String fundName,
			BigDecimal unitCount,
			LocalDate priceDate,
			BigDecimal unitPrice,
			BigDecimal positionValue) {
	}

}
