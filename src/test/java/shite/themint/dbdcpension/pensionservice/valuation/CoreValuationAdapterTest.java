package shite.themint.dbdcpension.pensionservice.valuation;

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

/** The valuation anti-corruption layer alone: the core is replaced by a mock server. */
class CoreValuationAdapterTest {

	private static final String URL = "http://core.test/core/v1/participants/M1001/valuation";

	private MockRestServiceServer core;
	private CoreValuationAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://core.test");
		core = MockRestServiceServer.bindTo(builder).build();
		adapter = new CoreValuationAdapter(builder.build());
	}

	private static String coreJson(String currency, String paid, String value, String date) {
		return """
				{"participantId":"M1001","currencyCode":%s,"premiumsPaidTotal":%s,"marketValue":%s,"valuationDate":%s}
				""".formatted(currency, paid, value, date);
	}

	private void coreAnswers(String json) {
		core.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
	}

	private void assertRejected(String json) {
		core.reset();
		coreAnswers(json);
		assertThatThrownBy(() -> adapter.getValuation("M1001")).isInstanceOf(CoreResponseException.class);
	}

	// --- translation

	@Test
	void valuation_isTranslatedToOurModel_withTheReturnWorkedOutByUs() {
		coreAnswers(coreJson("\"EUR\"", "20769.28", "25992.60", "\"2026-09-24\""));

		Valuation result = adapter.getValuation("M1001");

		assertThat(result.currency()).isEqualTo("EUR");
		assertThat(result.totalContributions()).isEqualByComparingTo("20769.28");
		assertThat(result.capitalValue()).isEqualByComparingTo("25992.60");
		assertThat(result.valuedOn()).isEqualTo(LocalDate.of(2026, 9, 24));
		assertThat(result.investmentReturn().amount()).isEqualByComparingTo("5223.32");
		assertThat(result.investmentReturn().percentage()).isEqualByComparingTo("25.15");
		core.verify();
	}

	@Test
	void coreAmountsAreNotRounded() {
		coreAnswers(coreJson("\"EUR\"", "20769.2849", "25992.6", "\"2026-09-24\""));

		Valuation result = adapter.getValuation("M1001");

		assertThat(result.totalContributions()).isEqualByComparingTo("20769.2849");
		assertThat(result.capitalValue()).isEqualByComparingTo("25992.6");
	}

	@Test
	void newMember_hasNoPercentage() {
		coreAnswers(coreJson("\"EUR\"", "0.00", "0.00", "\"2026-09-24\""));

		assertThat(adapter.getValuation("M1001").investmentReturn().percentage()).isNull();
	}

	@Test
	void lossMakingMember_hasANegativeReturn() {
		coreAnswers(coreJson("\"EUR\"", "10000.00", "9500.00", "\"2026-09-24\""));

		assertThat(adapter.getValuation("M1001").investmentReturn().amount()).isEqualByComparingTo("-500.00");
	}

	// --- data that makes no sense is rejected

	@Test
	void missingAmounts_areRejected() {
		assertRejected(coreJson("\"EUR\"", "null", "25992.60", "\"2026-09-24\""));
		assertRejected(coreJson("\"EUR\"", "20769.28", "null", "\"2026-09-24\""));
	}

	@Test
	void negativeAmounts_areRejected() {
		assertRejected(coreJson("\"EUR\"", "-1", "25992.60", "\"2026-09-24\""));
		assertRejected(coreJson("\"EUR\"", "20769.28", "-0.01", "\"2026-09-24\""));
	}

	@Test
	void missingValuationDate_isRejected() {
		assertRejected(coreJson("\"EUR\"", "20769.28", "25992.60", "null"));
	}

	@Test
	void currencyThatIsNotACode_isRejected() {
		assertRejected(coreJson("\"euro\"", "20769.28", "25992.60", "\"2026-09-24\""));
		assertRejected(coreJson("null", "20769.28", "25992.60", "\"2026-09-24\""));
	}

	// --- the core's failures become our exceptions

	@Test
	void core404_becomesParticipantNotFound() {
		core.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.getValuation("M1001")).isInstanceOf(ParticipantNotFoundException.class);
	}

	@Test
	void core500_becomesCoreUnavailable() {
		core.expect(requestTo(URL)).andRespond(withServerError());

		assertThatThrownBy(() -> adapter.getValuation("M1001")).isInstanceOf(CoreUnavailableException.class);
	}

	@Test
	void connectionFailure_becomesCoreUnavailable() {
		core.expect(requestTo(URL)).andRespond(request -> {
			throw new IOException("connection refused");
		});

		assertThatThrownBy(() -> adapter.getValuation("M1001")).isInstanceOf(CoreUnavailableException.class);
	}

	@Test
	void unreadableJson_becomesCoreResponseException() {
		assertRejected("this is not json");
	}

	@Test
	void emptyAnswer_becomesCoreResponseException() {
		core.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK));

		assertThatThrownBy(() -> adapter.getValuation("M1001")).isInstanceOf(CoreResponseException.class);
	}

	@Test
	void idWithSlash_isEncodedAndCannotChangeThePath() {
		core.expect(requestTo("http://core.test/core/v1/participants/a%2Fb/valuation"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.getValuation("a/b")).isInstanceOf(ParticipantNotFoundException.class);
		core.verify();
	}

}
