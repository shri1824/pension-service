package shite.themint.dbdcpension.pensionservice.holdings;

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

/** The holdings anti-corruption layer alone: the core is replaced by a mock server. */
class CoreHoldingsAdapterTest {

	private static final String URL = "http://core.test/core/v1/participants/M1001/holdings";

	private MockRestServiceServer core;
	private CoreHoldingsAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://core.test");
		core = MockRestServiceServer.bindTo(builder).build();
		adapter = new CoreHoldingsAdapter(builder.build());
	}

	private static String position(String code, String name, String units, String date, String price, String value) {
		return """
				{"fundCode":%s,"fundName":%s,"unitCount":%s,"priceDate":%s,"unitPrice":%s,"positionValue":%s}
				""".formatted(code, name, units, date, price, value);
	}

	private static String goodPosition() {
		return position("\"FUND-MIX-N\"", "\"Example Mixed Fund Neutral\"", "195.228665", "\"2026-09-24\"", "134.19", "26197.32");
	}

	private static String coreJson(String currency, String positions) {
		return """
				{"participantId":"M1001","currencyCode":%s,"positions":%s}
				""".formatted(currency, positions);
	}

	private void coreAnswers(String json) {
		core.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
	}

	private void assertRejected(String json) {
		core.reset();
		coreAnswers(json);
		assertThatThrownBy(() -> adapter.getHoldings("M1001")).isInstanceOf(CoreResponseException.class);
	}

	// --- translation

	@Test
	void fund_isTranslatedToOurModel_withEveryFigureExactlyAsTheCoreSentIt() {
		coreAnswers(coreJson("\"EUR\"", "[" + goodPosition() + "]"));

		Holdings result = adapter.getHoldings("M1001");

		assertThat(result.currency()).isEqualTo("EUR");
		assertThat(result.funds()).hasSize(1);
		Holdings.Fund fund = result.funds().get(0);
		assertThat(fund.fundId()).isEqualTo("FUND-MIX-N");
		assertThat(fund.name()).isEqualTo("Example Mixed Fund Neutral");
		assertThat(fund.units()).isEqualByComparingTo("195.228665");
		assertThat(fund.priceDate()).isEqualTo(LocalDate.of(2026, 9, 24));
		assertThat(fund.unitPrice()).isEqualByComparingTo("134.19");
		assertThat(fund.totalValue()).isEqualByComparingTo("26197.32");
		core.verify();
	}

	@Test
	void totalValueIsTheCoresOwnNumber_neverUnitsTimesPrice() {
		// 195.228665 x 134.19 = 26197.73; the core says 26197.32 and that is what must come out.
		coreAnswers(coreJson("\"EUR\"", "[" + goodPosition() + "]"));

		assertThat(adapter.getHoldings("M1001").funds().get(0).totalValue()).isEqualByComparingTo("26197.32");
	}

	@Test
	void unitsKeepAllSixDecimals() {
		coreAnswers(coreJson("\"EUR\"", "[" + position("\"F\"", "\"Fund\"", "0.000001", "\"2026-09-24\"", "1.00", "0.00") + "]"));

		assertThat(adapter.getHoldings("M1001").funds().get(0).units()).isEqualByComparingTo("0.000001");
	}

	@Test
	void severalFunds_keepTheCoresOrder() {
		coreAnswers(coreJson("\"EUR\"", "["
				+ position("\"A\"", "\"First\"", "1", "\"2026-09-24\"", "1", "1") + ","
				+ position("\"B\"", "\"Second\"", "2", "\"2026-09-24\"", "2", "4") + "]"));

		assertThat(adapter.getHoldings("M1001").funds()).extracting(Holdings.Fund::fundId).containsExactly("A", "B");
	}

	@Test
	void emptyList_isValid_aMemberWhoHasNotInvestedYet() {
		coreAnswers(coreJson("\"EUR\"", "[]"));

		Holdings result = adapter.getHoldings("M1001");

		assertThat(result.funds()).isEmpty();
		assertThat(result.currency()).isEqualTo("EUR");
	}

	// --- data that makes no sense is rejected

	@Test
	void missingList_isRejected_itIsNotTheSameAsNoFunds() {
		assertRejected(coreJson("\"EUR\"", "null"));
	}

	@Test
	void currencyThatIsNotACode_isRejected() {
		assertRejected(coreJson("\"euro\"", "[]"));
		assertRejected(coreJson("null", "[]"));
	}

	@Test
	void fundWithoutCodeOrName_isRejected() {
		assertRejected(coreJson("\"EUR\"", "[" + position("null", "\"Fund\"", "1", "\"2026-09-24\"", "1", "1") + "]"));
		assertRejected(coreJson("\"EUR\"", "[" + position("\"F\"", "\"  \"", "1", "\"2026-09-24\"", "1", "1") + "]"));
	}

	@Test
	void fundWithoutPriceDate_isRejected() {
		assertRejected(coreJson("\"EUR\"", "[" + position("\"F\"", "\"Fund\"", "1", "null", "1", "1") + "]"));
	}

	@Test
	void missingOrNegativeFigures_areRejected() {
		assertRejected(coreJson("\"EUR\"", "[" + position("\"F\"", "\"Fund\"", "null", "\"2026-09-24\"", "1", "1") + "]"));
		assertRejected(coreJson("\"EUR\"", "[" + position("\"F\"", "\"Fund\"", "-1", "\"2026-09-24\"", "1", "1") + "]"));
		assertRejected(coreJson("\"EUR\"", "[" + position("\"F\"", "\"Fund\"", "1", "\"2026-09-24\"", "-0.01", "1") + "]"));
		assertRejected(coreJson("\"EUR\"", "[" + position("\"F\"", "\"Fund\"", "1", "\"2026-09-24\"", "1", "null") + "]"));
	}

	@Test
	void oneBadFund_rejectsTheWholeAnswer_nothingHalfWrongIsShown() {
		assertRejected(coreJson("\"EUR\"", "["
				+ position("\"A\"", "\"First\"", "1", "\"2026-09-24\"", "1", "1") + ","
				+ position("\"B\"", "\"Second\"", "-2", "\"2026-09-24\"", "2", "4") + "]"));
	}

	// --- the core's failures become our exceptions

	@Test
	void core404_becomesParticipantNotFound() {
		core.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.getHoldings("M1001")).isInstanceOf(ParticipantNotFoundException.class);
	}

	@Test
	void core500_becomesCoreUnavailable() {
		core.expect(requestTo(URL)).andRespond(withServerError());

		assertThatThrownBy(() -> adapter.getHoldings("M1001")).isInstanceOf(CoreUnavailableException.class);
	}

	@Test
	void connectionFailure_becomesCoreUnavailable() {
		core.expect(requestTo(URL)).andRespond(request -> {
			throw new IOException("connection refused");
		});

		assertThatThrownBy(() -> adapter.getHoldings("M1001")).isInstanceOf(CoreUnavailableException.class);
	}

	@Test
	void unreadableJson_becomesCoreResponseException() {
		assertRejected("this is not json");
	}

	@Test
	void emptyAnswer_becomesCoreResponseException() {
		core.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK));

		assertThatThrownBy(() -> adapter.getHoldings("M1001")).isInstanceOf(CoreResponseException.class);
	}

	@Test
	void idWithSlash_isEncodedAndCannotChangeThePath() {
		core.expect(requestTo("http://core.test/core/v1/participants/a%2Fb/holdings"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.getHoldings("a/b")).isInstanceOf(ParticipantNotFoundException.class);
		core.verify();
	}

}
