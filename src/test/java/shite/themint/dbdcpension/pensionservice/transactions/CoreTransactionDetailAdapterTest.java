package shite.themint.dbdcpension.pensionservice.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
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

/** The transaction detail anti-corruption layer alone: the core is replaced by a mock server. */
class CoreTransactionDetailAdapterTest {

	private static final String ID = "T20260914-001";
	private static final String URL = "http://core.test/core/v1/participants/M1001/transactions/" + ID;

	private MockRestServiceServer core;
	private CoreTransactionsAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://core.test");
		core = MockRestServiceServer.bindTo(builder).build();
		adapter = new CoreTransactionsAdapter(builder.build());
	}

	private static String line(String code, String amount) {
		return "{\"lineCode\":%s,\"amount\":%s}".formatted(code, amount);
	}

	private static String movement(String code, String name, String units, String date, String price, String value) {
		return """
				{"fundCode":%s,"fundName":%s,"unitCount":%s,"priceDate":%s,"unitPrice":%s,"movementValue":%s}
				""".formatted(code, name, units, date, price, value);
	}

	private static String goodMovement() {
		return movement("\"FUND-MIX-N\"", "\"Example Mixed Fund Neutral\"", "-6.502952", "\"2026-09-14\"", "132.63", "-862.46");
	}

	private static String goodLines() {
		return "[" + line("\"DEFINED_CONTRIBUTION\"", "-862.51") + "," + line("\"BONUS\"", "-0.01") + ","
				+ line("\"ADMINISTRATION_COSTS\"", "0.04") + "]";
	}

	private static String detail(String number, String kind, String currency, String date, String amount,
			String lines, String movements) {
		return """
				{"transactionNumber":%s,"transactionDate":%s,"transactionKind":%s,"currencyCode":%s,"amount":%s,
				 "lines":%s,"fundMovements":%s}
				""".formatted(number, date, kind, currency, amount, lines, movements);
	}

	private static String goodDetail() {
		return detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "-862.46",
				goodLines(), "[" + goodMovement() + "]");
	}

	private void coreAnswers(String json) {
		core.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
	}

	private void assertRejected(String json) {
		core.reset();
		coreAnswers(json);
		assertThatThrownBy(() -> adapter.getTransaction("M1001", ID)).isInstanceOf(CoreResponseException.class);
	}

	// --- translation

	@Test
	void withdrawal_isTranslatedToOurModel_signedAndExact() {
		coreAnswers(goodDetail());

		TransactionDetail result = adapter.getTransaction("M1001", ID);

		assertThat(result.transactionId()).isEqualTo(ID);
		assertThat(result.date()).isEqualTo(LocalDate.of(2026, 9, 14));
		assertThat(result.type()).isEqualTo(TransactionType.INVESTMENT_WITHDRAWAL);
		assertThat(result.currency()).isEqualTo("EUR");
		assertThat(result.amount()).isEqualByComparingTo("-862.46");
		assertThat(result.components()).extracting(TransactionDetail.Component::code)
				.containsExactly("DEFINED_CONTRIBUTION", "BONUS", "ADMINISTRATION_COSTS");
		assertThat(result.components().get(0).amount()).isEqualByComparingTo("-862.51");
		assertThat(result.components().get(2).amount()).isEqualByComparingTo("0.04");
		assertThat(result.funds()).hasSize(1);
		TransactionDetail.FundLine fund = result.funds().get(0);
		assertThat(fund.fundId()).isEqualTo("FUND-MIX-N");
		assertThat(fund.name()).isEqualTo("Example Mixed Fund Neutral");
		assertThat(fund.units()).isEqualByComparingTo("-6.502952");
		assertThat(fund.priceDate()).isEqualTo(LocalDate.of(2026, 9, 14));
		assertThat(fund.unitPrice()).isEqualByComparingTo("132.63");
		assertThat(fund.totalValue()).isEqualByComparingTo("-862.46");
		core.verify();
	}

	@Test
	void componentsAndTotal_areNeverRecomputed_theyDoNotAddUpAndThatIsFine() {
		coreAnswers(goodDetail());

		TransactionDetail result = adapter.getTransaction("M1001", ID);
		BigDecimal sum = result.components().stream().map(TransactionDetail.Component::amount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		// -862.51 - 0.01 + 0.04 = -862.48, the core says -862.46: both come out exactly as the core sent them.
		assertThat(sum).isEqualByComparingTo("-862.48");
		assertThat(result.amount()).isEqualByComparingTo("-862.46");
		assertThat(result.funds().get(0).totalValue()).isEqualByComparingTo("-862.46");
	}

	@Test
	void priceDifference_hasNoComponents_andAPriceDateAfterTheTransactionDate() {
		String id = "T20260916-001";
		core.expect(requestTo("http://core.test/core/v1/participants/M1001/transactions/" + id)).andRespond(withSuccess(
				detail("\"" + id + "\"", "\"PRICE_DIFFERENCE\"", "\"EUR\"", "\"2026-09-16\"", "9.05", "[]",
						"[" + movement("\"FUND-MIX-N\"", "\"Example Mixed Fund Neutral\"", "0.067880", "\"2026-09-17\"", "133.29", "9.05") + "]"),
				MediaType.APPLICATION_JSON));

		TransactionDetail result = adapter.getTransaction("M1001", id);

		assertThat(result.type()).isEqualTo(TransactionType.PRICE_DIFFERENCE);
		assertThat(result.components()).isEmpty();
		assertThat(result.date()).isEqualTo(LocalDate.of(2026, 9, 16));
		assertThat(result.funds().get(0).priceDate()).isEqualTo(LocalDate.of(2026, 9, 17));
	}

	@Test
	void unitsKeepAllSixDecimals() {
		coreAnswers(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "0.01", "[]",
				"[" + movement("\"F\"", "\"Fund\"", "0.000001", "\"2026-09-14\"", "1.00", "0.01") + "]"));

		assertThat(adapter.getTransaction("M1001", ID).funds().get(0).units()).isEqualByComparingTo("0.000001");
	}

	@Test
	void aNewKindOfComponent_isPassedOnAsItIs() {
		coreAnswers(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "1.00",
				"[" + line("\"NEW_KIND_OF_COST\"", "-0.02") + "]", "[" + goodMovement() + "]"));

		assertThat(adapter.getTransaction("M1001", ID).components().get(0).code()).isEqualTo("NEW_KIND_OF_COST");
	}

	@Test
	void severalFundLines_keepTheCoresOrder() {
		coreAnswers(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "3.00", "[]",
				"[" + movement("\"A\"", "\"First\"", "1", "\"2026-09-14\"", "1", "1") + ","
						+ movement("\"B\"", "\"Second\"", "2", "\"2026-09-14\"", "1", "2") + "]"));

		assertThat(adapter.getTransaction("M1001", ID).funds()).extracting(TransactionDetail.FundLine::fundId)
				.containsExactly("A", "B");
	}

	// --- data that makes no sense is rejected

	@Test
	void anotherTransactionThanTheOneAskedFor_isRejected() {
		assertRejected(detail("\"T20260101-999\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "-862.46",
				goodLines(), "[" + goodMovement() + "]"));
	}

	@Test
	void missingOrUnsafeTransactionNumber_isRejected() {
		assertRejected(detail("null", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "1", "[]", "[]"));
		assertRejected(detail("\"T1/../x\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "1", "[]", "[]"));
	}

	@Test
	void unknownKind_isRejected_notGuessed() {
		assertRejected(detail("\"" + ID + "\"", "\"SOMETHING_NEW\"", "\"EUR\"", "\"2026-09-14\"", "1", "[]", "[]"));
		assertRejected(detail("\"" + ID + "\"", "null", "\"EUR\"", "\"2026-09-14\"", "1", "[]", "[]"));
	}

	@Test
	void currencyThatIsNotACode_isRejected() {
		assertRejected(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"euro\"", "\"2026-09-14\"", "1", "[]", "[]"));
		assertRejected(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "null", "\"2026-09-14\"", "1", "[]", "[]"));
	}

	@Test
	void missingDateOrAmount_isRejected() {
		assertRejected(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "null", "1", "[]", "[]"));
		assertRejected(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "null", "[]", "[]"));
	}

	@Test
	void missingLists_areRejected_butEmptyListsAreFine() {
		assertRejected(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "1", "null", "[]"));
		assertRejected(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "1", "[]", "null"));

		core.reset();
		coreAnswers(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "1", "[]", "[]"));
		TransactionDetail result = adapter.getTransaction("M1001", ID);
		assertThat(result.components()).isEmpty();
		assertThat(result.funds()).isEmpty();
	}

	@Test
	void badComponent_isRejected() {
		assertRejected(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "1",
				"[" + line("null", "1") + "]", "[]"));
		assertRejected(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "1",
				"[" + line("\"lower case\"", "1") + "]", "[]"));
		assertRejected(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "1",
				"[" + line("\"BONUS\"", "null") + "]", "[]"));
	}

	@Test
	void badFundLine_isRejected() {
		assertRejected(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "1", "[]",
				"[" + movement("null", "\"Fund\"", "1", "\"2026-09-14\"", "1", "1") + "]"));
		assertRejected(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "1", "[]",
				"[" + movement("\"F\"", "\" \"", "1", "\"2026-09-14\"", "1", "1") + "]"));
		assertRejected(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "1", "[]",
				"[" + movement("\"F\"", "\"Fund\"", "1", "null", "1", "1") + "]"));
		assertRejected(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "1", "[]",
				"[" + movement("\"F\"", "\"Fund\"", "null", "\"2026-09-14\"", "1", "1") + "]"));
		assertRejected(detail("\"" + ID + "\"", "\"INVESTMENT_WITHDRAWAL\"", "\"EUR\"", "\"2026-09-14\"", "1", "[]",
				"[" + movement("\"F\"", "\"Fund\"", "1", "\"2026-09-14\"", "-0.01", "1") + "]"));
	}

	@Test
	void negativeUnitsAndTotals_areFine_aSaleIsNegative() {
		coreAnswers(goodDetail());

		TransactionDetail result = adapter.getTransaction("M1001", ID);

		assertThat(result.funds().get(0).units().signum()).isNegative();
		assertThat(result.funds().get(0).totalValue().signum()).isNegative();
	}

	// --- the core's failures become our exceptions

	@Test
	void core404_becomesNotFound_forAnUnknownTransactionOrOneOfAnotherMember() {
		core.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.getTransaction("M1001", ID)).isInstanceOf(ParticipantNotFoundException.class);
	}

	@Test
	void core500_becomesCoreUnavailable() {
		core.expect(requestTo(URL)).andRespond(withServerError());

		assertThatThrownBy(() -> adapter.getTransaction("M1001", ID)).isInstanceOf(CoreUnavailableException.class);
	}

	@Test
	void connectionFailure_becomesCoreUnavailable() {
		core.expect(requestTo(URL)).andRespond(request -> {
			throw new IOException("connection refused");
		});

		assertThatThrownBy(() -> adapter.getTransaction("M1001", ID)).isInstanceOf(CoreUnavailableException.class);
	}

	@Test
	void unreadableJson_becomesCoreResponseException() {
		assertRejected("this is not json");
	}

	@Test
	void emptyAnswer_becomesCoreResponseException() {
		core.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK));

		assertThatThrownBy(() -> adapter.getTransaction("M1001", ID)).isInstanceOf(CoreResponseException.class);
	}

	@Test
	void idsWithSlash_areEncodedAndCannotChangeThePath() {
		core.expect(requestTo("http://core.test/core/v1/participants/a%2Fb/transactions/c%2Fd"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.getTransaction("a/b", "c/d")).isInstanceOf(ParticipantNotFoundException.class);
		core.verify();
	}

}
