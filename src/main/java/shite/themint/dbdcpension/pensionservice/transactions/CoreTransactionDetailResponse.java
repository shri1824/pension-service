package shite.themint.dbdcpension.pensionservice.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The core's transaction detail JSON, with the core's own names. Package-private on purpose: only the
 * adapter may know it, so a change in the core touches this package and nothing else.
 */
record CoreTransactionDetailResponse(
		String transactionNumber,
		LocalDate transactionDate,
		String transactionKind,
		String currencyCode,
		BigDecimal amount,
		List<Line> lines,
		List<FundMovement> fundMovements) {

	record Line(String lineCode, BigDecimal amount) {
	}

	record FundMovement(
			String fundCode,
			String fundName,
			BigDecimal unitCount,
			LocalDate priceDate,
			BigDecimal unitPrice,
			BigDecimal movementValue) {
	}

}
