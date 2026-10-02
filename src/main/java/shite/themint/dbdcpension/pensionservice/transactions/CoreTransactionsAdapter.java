package shite.themint.dbdcpension.pensionservice.transactions;

import java.util.regex.Pattern;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import shite.themint.dbdcpension.pensionservice.config.CacheConfig;
import shite.themint.dbdcpension.pensionservice.core.CoreCalls;
import shite.themint.dbdcpension.pensionservice.error.CoreResponseException;

/**
 * The anti-corruption layer for the transaction list: the only place that knows the core's URL, field
 * names and codes. Data that makes no sense (a missing list, a missing date or amount, a transaction
 * number that is not safe to put in a URL, a kind we do not know) is rejected instead of being shown to
 * a member, and one bad row rejects the whole page. An EMPTY list is valid. Amounts are never recomputed.
 */
@Component
class CoreTransactionsAdapter implements TransactionsPort {

	private static final String PATH = "/core/v1/participants/{participantId}/transactions?page={page}&size={size}";

	private static final String DETAIL_PATH =
			"/core/v1/participants/{participantId}/transactions/{transactionNumber}";

	private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

	/** A component code such as DEFINED_CONTRIBUTION: passed on as it is, but it must look like a code. */
	private static final Pattern COMPONENT_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,39}$");

	/** The same rule the detail endpoint applies to the id it receives, so every listed id can be asked for. */
	private static final Pattern TRANSACTION_ID = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");

	private final RestClient coreRestClient;

	CoreTransactionsAdapter(RestClient coreRestClient) {
		this.coreRestClient = coreRestClient;
	}

	// Only successes are cached, for a short time (transactions change when a contribution is booked).
	// Never served stale after a core failure: a wrong list of transactions could mislead (design section 6).
	@Override
	@Cacheable(cacheNames = CacheConfig.TRANSACTIONS_CACHE, key = "#participantId + ':' + #page + ':' + #size")
	public TransactionList getTransactions(String participantId, int page, int size) {
		return toTransactionList(CoreCalls.getWithVariables(coreRestClient, PATH, CoreTransactionsPageResponse.class,
				participantId, page, size));
	}

	static TransactionList toTransactionList(CoreTransactionsPageResponse core) {
		if (core.currencyCode() == null || !CURRENCY.matcher(core.currencyCode()).matches()) {
			throw new CoreResponseException("Currency from the core is missing or not a currency code");
		}
		if (core.page() == null || core.page() < 0 || core.pageSize() == null || core.pageSize() < 1) {
			throw new CoreResponseException("Paging information from the core is missing or invalid");
		}
		if (core.totalCount() == null || core.totalCount() < 0) {
			throw new CoreResponseException("Total count from the core is missing or negative");
		}
		if (core.transactions() == null) {
			// A missing list is not the same as "no transactions": the core did not tell us.
			throw new CoreResponseException("Transaction list from the core is missing");
		}
		var items = core.transactions().stream().map(CoreTransactionsAdapter::toItem).toList();
		// Worked out from the core's own numbers: is there anything after the last row of this page?
		boolean hasMore = ((long) core.page() + 1) * core.pageSize() < core.totalCount();
		return new TransactionList(core.currencyCode(), core.page(), core.pageSize(), hasMore, items);
	}

	// Cached a little longer than the list: a booked transaction does not change afterwards.
	// Never served stale after a core failure (design section 6).
	@Override
	@Cacheable(cacheNames = CacheConfig.TRANSACTION_DETAIL_CACHE, key = "#participantId + ':' + #transactionId")
	public TransactionDetail getTransaction(String participantId, String transactionId) {
		TransactionDetail detail = toTransactionDetail(CoreCalls.getWithVariables(coreRestClient, DETAIL_PATH,
				CoreTransactionDetailResponse.class, participantId, transactionId));
		if (!detail.transactionId().equals(transactionId)) {
			// The core answered with another transaction than the one we asked for: never show it.
			throw new CoreResponseException("The core answered with a different transaction than the one asked for");
		}
		return detail;
	}

	static TransactionDetail toTransactionDetail(CoreTransactionDetailResponse core) {
		if (core.transactionNumber() == null || !TRANSACTION_ID.matcher(core.transactionNumber()).matches()) {
			throw new CoreResponseException("A transaction number from the core is missing or not plain");
		}
		if (core.currencyCode() == null || !CURRENCY.matcher(core.currencyCode()).matches()) {
			throw new CoreResponseException("Currency from the core is missing or not a currency code");
		}
		if (core.transactionDate() == null) {
			throw new CoreResponseException("The transaction from the core has no date");
		}
		if (core.amount() == null) {
			throw new CoreResponseException("The transaction from the core has no amount");
		}
		if (core.lines() == null || core.fundMovements() == null) {
			// An empty list is fine (a price difference has no components); a missing one is not.
			throw new CoreResponseException("Components or fund lines from the core are missing");
		}
		return new TransactionDetail(
				core.transactionNumber(),
				core.transactionDate(),
				toType(core.transactionKind()),
				core.currencyCode(),
				core.amount(),
				core.lines().stream().map(CoreTransactionsAdapter::toComponent).toList(),
				core.fundMovements().stream().map(CoreTransactionsAdapter::toFundLine).toList());
	}

	private static TransactionDetail.Component toComponent(CoreTransactionDetailResponse.Line line) {
		if (line == null || line.lineCode() == null || !COMPONENT_CODE.matcher(line.lineCode()).matches()) {
			throw new CoreResponseException("A component code from the core is missing or not a code");
		}
		if (line.amount() == null) {
			throw new CoreResponseException("A component from the core has no amount");
		}
		return new TransactionDetail.Component(line.lineCode(), line.amount());
	}

	private static TransactionDetail.FundLine toFundLine(CoreTransactionDetailResponse.FundMovement movement) {
		if (movement == null || isBlank(movement.fundCode()) || isBlank(movement.fundName())) {
			throw new CoreResponseException("A fund line from the core has no code or no name");
		}
		if (movement.priceDate() == null) {
			throw new CoreResponseException("A fund line from the core has no price date");
		}
		if (movement.unitCount() == null || movement.unitPrice() == null || movement.movementValue() == null) {
			throw new CoreResponseException("A fund line from the core has a missing figure");
		}
		if (movement.unitPrice().signum() < 0) {
			// Units and totals are signed (a sale is negative); a price never is.
			throw new CoreResponseException("A unit price from the core is negative");
		}
		return new TransactionDetail.FundLine(
				movement.fundCode(),
				movement.fundName(),
				movement.unitCount(),
				movement.priceDate(),
				movement.unitPrice(),
				movement.movementValue());
	}

	private static boolean isBlank(String text) {
		return text == null || text.isBlank();
	}

	private static TransactionList.Item toItem(CoreTransactionsPageResponse.Transaction transaction) {
		if (transaction == null || transaction.transactionNumber() == null
				|| !TRANSACTION_ID.matcher(transaction.transactionNumber()).matches()) {
			throw new CoreResponseException("A transaction number from the core is missing or not plain");
		}
		if (transaction.transactionDate() == null) {
			throw new CoreResponseException("A transaction from the core has no date");
		}
		if (transaction.amount() == null) {
			throw new CoreResponseException("A transaction from the core has no amount");
		}
		return new TransactionList.Item(
				transaction.transactionNumber(),
				transaction.transactionDate(),
				toType(transaction.transactionKind()),
				// Signed and exactly as the core sent it: negative means units were sold.
				transaction.amount());
	}

	private static TransactionType toType(String kind) {
		if ("INVESTMENT_WITHDRAWAL".equals(kind)) {
			return TransactionType.INVESTMENT_WITHDRAWAL;
		}
		if ("PRICE_DIFFERENCE".equals(kind)) {
			return TransactionType.PRICE_DIFFERENCE;
		}
		throw new CoreResponseException("Unknown transaction kind from the core");
	}

}
