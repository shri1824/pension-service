package shite.themint.dbdcpension.pensionservice.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One page of a DC member's transactions, newest first, in OUR model (the "Transaction overview"
 * list). Amounts are exactly the core's and signed: negative means units were sold. The list is empty
 * (not an error) for a member without transactions and for a page beyond the last one.
 *
 * @param page    zero-based
 * @param size    the page size the core used
 * @param hasMore true when there are more transactions after this page ("See more transactions")
 */
public record TransactionList(String currency, int page, int size, boolean hasMore, List<Item> items) {

	/** @param transactionId the id to ask the detail with; plain characters only, safe to put in a URL */
	public record Item(String transactionId, LocalDate date, TransactionType type, BigDecimal amount) {
	}

}
