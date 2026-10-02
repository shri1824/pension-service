package shite.themint.dbdcpension.pensionservice.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The core's transactions page JSON, with the core's own names. Package-private on purpose: only the
 * adapter may know it, so a change in the core touches this package and nothing else.
 */
record CoreTransactionsPageResponse(
		String participantId,
		String currencyCode,
		Integer page,
		Integer pageSize,
		Integer totalCount,
		List<Transaction> transactions) {

	record Transaction(
			String transactionNumber,
			LocalDate transactionDate,
			String transactionKind,
			BigDecimal amount) {
	}

}
