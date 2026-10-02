package shite.themint.dbdcpension.pensionservice.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import shite.themint.dbdcpension.pensionservice.error.CoreResponseException;
import shite.themint.dbdcpension.pensionservice.error.CoreUnavailableException;
import shite.themint.dbdcpension.pensionservice.error.ParticipantNotFoundException;

/** The transaction list anti-corruption layer alone: the core is replaced by a mock server. */
class CoreTransactionsAdapterTest {

	private static final String URL = "http://core.test/core/v1/participants/M1001/transactions?page=0&size=10";

	private MockRestServiceServer core;
	private CoreTransactionsAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://core.test");
		core = MockRestServiceServer.bindTo(builder).build();
		adapter = new CoreTransactionsAdapter(builder.build());
	}

	private static String tx(String number, String date, String kind, String amount) {
		return """
				{"transactionNumber":%s,"transactionDate":%s,"transactionKind":%s,"amount":%s}
				""".formatted(number, date, kind, amount);
	}

	private static String goodTx() {
		return tx("\"T20260914-001\"", "\"2026-09-14\"", "\"INVESTMENT_WITHDRAWAL\"", "-862.46");
	}

	private static String page(String currency, String page, String size, String total, String transactions) {
		return """
				{"participantId":"M1001","currencyCode":%s,"page":%s,"pageSize":%s,"totalCount":%s,"transactions":%s}
				""".formatted(currency, page, size, total, transactions);
	}

	private void coreAnswers(String json) {
		core.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
	}

	private void assertRejected(String json) {
		core.reset();
		coreAnswers(json);
		assertThatThrownBy(() -> adapter.getTransactions("M1001", 0, 10)).isInstanceOf(CoreResponseException.class);
	}

	// --- translation

	@Test
	void page_isTranslatedToOurModel_withSignedAmounts() {
		coreAnswers(page("\"EUR\"", "0", "10", "14", "["
				+ tx("\"T20260916-001\"", "\"2026-09-16\"", "\"PRICE_DIFFERENCE\"", "9.05") + ","
				+ goodTx() + "]"));

		TransactionList result = adapter.getTransactions("M1001", 0, 10);

		assertThat(result.currency()).isEqualTo("EUR");
		assertThat(result.page()).isZero();
		assertThat(result.size()).isEqualTo(10);
		assertThat(result.items()).hasSize(2);
		assertThat(result.items().get(0).transactionId()).isEqualTo("T20260916-001");
		assertThat(result.items().get(0).date()).isEqualTo(LocalDate.of(2026, 9, 16));
		assertThat(result.items().get(0).type()).isEqualTo(TransactionType.PRICE_DIFFERENCE);
		assertThat(result.items().get(0).amount()).isEqualByComparingTo("9.05");
		assertThat(result.items().get(1).type()).isEqualTo(TransactionType.INVESTMENT_WITHDRAWAL);
		assertThat(result.items().get(1).amount()).isEqualByComparingTo("-862.46");
		core.verify();
	}

	@Test
	void theRequestCarriesThePageAndTheSize() {
		core.expect(requestTo("http://core.test/core/v1/participants/M1001/transactions?page=3&size=25"))
				.andRespond(withSuccess(page("\"EUR\"", "3", "25", "100", "[]"), MediaType.APPLICATION_JSON));

		TransactionList result = adapter.getTransactions("M1001", 3, 25);

		assertThat(result.page()).isEqualTo(3);
		assertThat(result.size()).isEqualTo(25);
		core.verify();
	}

	@Test
	void hasMore_isTrueWhenTheCoreHasMoreRowsAfterThisPage() {
		coreAnswers(page("\"EUR\"", "0", "10", "14", "[" + goodTx() + "]"));

		assertThat(adapter.getTransactions("M1001", 0, 10).hasMore()).isTrue();
	}

	@Test
	void hasMore_isFalseOnTheLastPage() {
		core.expect(requestTo("http://core.test/core/v1/participants/M1001/transactions?page=1&size=10"))
				.andRespond(withSuccess(page("\"EUR\"", "1", "10", "14", "[" + goodTx() + "]"), MediaType.APPLICATION_JSON));

		assertThat(adapter.getTransactions("M1001", 1, 10).hasMore()).isFalse();
	}

	@Test
	void hasMore_isFalseWhenThePageIsExactlyFull() {
		coreAnswers(page("\"EUR\"", "0", "10", "10", "[" + goodTx() + "]"));

		assertThat(adapter.getTransactions("M1001", 0, 10).hasMore()).isFalse();
	}

	@Test
	void hasMore_usesTheCoresPageSize_notTheRequestedOne() {
		// We asked for 50 but the core answered with its own limit of 10: hasMore must follow what the core used.
		core.expect(requestTo("http://core.test/core/v1/participants/M1001/transactions?page=0&size=50"))
				.andRespond(withSuccess(page("\"EUR\"", "0", "10", "14", "[" + goodTx() + "]"), MediaType.APPLICATION_JSON));

		TransactionList result = adapter.getTransactions("M1001", 0, 50);

		assertThat(result.size()).isEqualTo(10);
		assertThat(result.hasMore()).isTrue();
	}

	@Test
	void emptyList_isValid_aMemberWithoutTransactions() {
		coreAnswers(page("\"EUR\"", "0", "10", "0", "[]"));

		TransactionList result = adapter.getTransactions("M1001", 0, 10);

		assertThat(result.items()).isEmpty();
		assertThat(result.hasMore()).isFalse();
	}

	@Test
	void zeroAmount_isKept() {
		coreAnswers(page("\"EUR\"", "0", "10", "1", "[" + tx("\"T1\"", "\"2026-09-14\"", "\"PRICE_DIFFERENCE\"", "0.00") + "]"));

		assertThat(adapter.getTransactions("M1001", 0, 10).items().get(0).amount()).isEqualByComparingTo("0.00");
	}

	@Test
	void amountsAreNotRounded() {
		coreAnswers(page("\"EUR\"", "0", "10", "1", "[" + tx("\"T1\"", "\"2026-09-14\"", "\"PRICE_DIFFERENCE\"", "9.0549") + "]"));

		assertThat(adapter.getTransactions("M1001", 0, 10).items().get(0).amount()).isEqualByComparingTo("9.0549");
	}

	// --- data that makes no sense is rejected

	@Test
	void missingList_isRejected_itIsNotTheSameAsNoTransactions() {
		assertRejected(page("\"EUR\"", "0", "10", "14", "null"));
	}

	@Test
	void currencyThatIsNotACode_isRejected() {
		assertRejected(page("\"euro\"", "0", "10", "0", "[]"));
		assertRejected(page("null", "0", "10", "0", "[]"));
	}

	@Test
	void missingOrInvalidPaging_isRejected() {
		assertRejected(page("\"EUR\"", "null", "10", "0", "[]"));
		assertRejected(page("\"EUR\"", "-1", "10", "0", "[]"));
		assertRejected(page("\"EUR\"", "0", "null", "0", "[]"));
		assertRejected(page("\"EUR\"", "0", "0", "0", "[]"));
		assertRejected(page("\"EUR\"", "0", "10", "null", "[]"));
		assertRejected(page("\"EUR\"", "0", "10", "-1", "[]"));
	}

	@Test
	void unknownKind_isRejected_notGuessed() {
		assertRejected(page("\"EUR\"", "0", "10", "1", "[" + tx("\"T1\"", "\"2026-09-14\"", "\"SOMETHING_NEW\"", "1.00") + "]"));
		assertRejected(page("\"EUR\"", "0", "10", "1", "[" + tx("\"T1\"", "\"2026-09-14\"", "null", "1.00") + "]"));
	}

	@Test
	void transactionNumberThatIsNotSafeInAUrl_isRejected() {
		assertRejected(page("\"EUR\"", "0", "10", "1", "[" + tx("\"T1/../x\"", "\"2026-09-14\"", "\"PRICE_DIFFERENCE\"", "1.00") + "]"));
		assertRejected(page("\"EUR\"", "0", "10", "1", "[" + tx("\"\"", "\"2026-09-14\"", "\"PRICE_DIFFERENCE\"", "1.00") + "]"));
		assertRejected(page("\"EUR\"", "0", "10", "1", "[" + tx("null", "\"2026-09-14\"", "\"PRICE_DIFFERENCE\"", "1.00") + "]"));
	}

	@Test
	void missingDateOrAmount_isRejected() {
		assertRejected(page("\"EUR\"", "0", "10", "1", "[" + tx("\"T1\"", "null", "\"PRICE_DIFFERENCE\"", "1.00") + "]"));
		assertRejected(page("\"EUR\"", "0", "10", "1", "[" + tx("\"T1\"", "\"2026-09-14\"", "\"PRICE_DIFFERENCE\"", "null") + "]"));
	}

	@Test
	void oneBadRow_rejectsTheWholePage_nothingHalfWrongIsShown() {
		assertRejected(page("\"EUR\"", "0", "10", "2", "[" + goodTx() + ","
				+ tx("\"T2\"", "\"2026-09-13\"", "\"BROKEN\"", "1.00") + "]"));
	}

	// --- the core's failures become our exceptions

	@Test
	void core404_becomesParticipantNotFound() {
		core.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.getTransactions("M1001", 0, 10)).isInstanceOf(ParticipantNotFoundException.class);
	}

	@Test
	void core400_becomesCoreUnavailable_notANotFound() {
		core.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

		assertThatThrownBy(() -> adapter.getTransactions("M1001", 0, 10)).isInstanceOf(CoreUnavailableException.class);
	}

	@Test
	void core500_becomesCoreUnavailable() {
		core.expect(requestTo(URL)).andRespond(withServerError());

		assertThatThrownBy(() -> adapter.getTransactions("M1001", 0, 10)).isInstanceOf(CoreUnavailableException.class);
	}

	@Test
	void connectionFailure_becomesCoreUnavailable() {
		core.expect(requestTo(URL)).andRespond(request -> {
			throw new IOException("connection refused");
		});

		assertThatThrownBy(() -> adapter.getTransactions("M1001", 0, 10)).isInstanceOf(CoreUnavailableException.class);
	}

	@Test
	void unreadableJson_becomesCoreResponseException() {
		assertRejected("this is not json");
	}

	@Test
	void emptyAnswer_becomesCoreResponseException() {
		core.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK));

		assertThatThrownBy(() -> adapter.getTransactions("M1001", 0, 10)).isInstanceOf(CoreResponseException.class);
	}

	@Test
	void idWithSlash_isEncodedAndCannotChangeThePath() {
		core.expect(requestTo("http://core.test/core/v1/participants/a%2Fb/transactions?page=0&size=10"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.getTransactions("a/b", 0, 10)).isInstanceOf(ParticipantNotFoundException.class);
		core.verify();
	}

}
