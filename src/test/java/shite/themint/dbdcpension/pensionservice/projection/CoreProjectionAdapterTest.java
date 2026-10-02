package shite.themint.dbdcpension.pensionservice.projection;

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

/** The projection anti-corruption layer alone: the core is replaced by a mock server. */
class CoreProjectionAdapterTest {

	private static final String URL = "http://core.test/core/v1/participants/M1001/projection";

	private MockRestServiceServer core;
	private CoreProjectionAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://core.test");
		core = MockRestServiceServer.bindTo(builder).build();
		adapter = new CoreProjectionAdapter(builder.build());
	}

	private static String coreJson(String age, String curr, String date, String middle, String low, String high) {
		return """
				{"participantId":"M1001","pensionAge":%s,"curr":%s,"calculationDate":%s,
				 "middleScenario":%s,"lowScenario":%s,"highScenario":%s}
				""".formatted(age, curr, date, middle, low, high);
	}

	private static String validJson() {
		return coreJson("68", "\"EUR\"", "\"2026-09-30\"", "369.42", "117.10", "881.55");
	}

	private void coreAnswers(String json) {
		core.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
	}

	private void assertRejected(String json) {
		core.reset();
		coreAnswers(json);
		assertThatThrownBy(() -> adapter.getProjection("M1001")).isInstanceOf(CoreResponseException.class);
	}

	// --- translation

	@Test
	void projection_isTranslatedToOurModel_withCentsAndTheAmountBasis() {
		coreAnswers(validJson());

		Projection result = adapter.getProjection("M1001");

		assertThat(result.retirementAge()).isEqualTo(68);
		assertThat(result.currency()).isEqualTo("EUR");
		assertThat(result.amountBasis()).isEqualTo(AmountBasis.TODAYS_PRICES);
		assertThat(result.calculatedOn()).isEqualTo(LocalDate.of(2026, 9, 30));
		assertThat(result.scenarios()).extracting(Projection.Scenario::type)
				.containsExactly(ScenarioType.EXPECTED, ScenarioType.UNFAVOURABLE, ScenarioType.FAVOURABLE);
		assertThat(result.scenarios().get(0).grossMonthlyAmount()).isEqualByComparingTo("369.42");
		assertThat(result.scenarios().get(1).grossMonthlyAmount()).isEqualByComparingTo("117.10");
		assertThat(result.scenarios().get(2).grossMonthlyAmount()).isEqualByComparingTo("881.55");
		core.verify();
	}

	@Test
	void amountsAreNotRoundedOrRecomputed() {
		coreAnswers(coreJson("68", "\"EUR\"", "\"2026-09-30\"", "369.4249", "117.1", "881.5"));

		assertThat(adapter.getProjection("M1001").scenarios().get(0).grossMonthlyAmount()).isEqualByComparingTo("369.4249");
	}

	@Test
	void zeroAmountIsAllowed() {
		coreAnswers(coreJson("68", "\"EUR\"", "\"2026-09-30\"", "0", "0", "0"));

		assertThat(adapter.getProjection("M1001").scenarios()).hasSize(3);
	}

	// --- data that makes no sense is rejected

	@Test
	void missingAmount_isRejected() {
		assertRejected(coreJson("68", "\"EUR\"", "\"2026-09-30\"", "null", "117.10", "881.55"));
	}

	@Test
	void negativeAmount_isRejected() {
		assertRejected(coreJson("68", "\"EUR\"", "\"2026-09-30\"", "369.42", "-1", "881.55"));
	}

	@Test
	void impossibleRetirementAge_isRejected() {
		assertRejected(coreJson("12", "\"EUR\"", "\"2026-09-30\"", "369.42", "117.10", "881.55"));
		assertRejected(coreJson("null", "\"EUR\"", "\"2026-09-30\"", "369.42", "117.10", "881.55"));
		assertRejected(coreJson("150", "\"EUR\"", "\"2026-09-30\"", "369.42", "117.10", "881.55"));
	}

	@Test
	void currencyThatIsNotACode_isRejected() {
		assertRejected(coreJson("68", "\"euro\"", "\"2026-09-30\"", "369.42", "117.10", "881.55"));
		assertRejected(coreJson("68", "null", "\"2026-09-30\"", "369.42", "117.10", "881.55"));
	}

	@Test
	void missingCalculationDate_isRejected() {
		assertRejected(coreJson("68", "\"EUR\"", "null", "369.42", "117.10", "881.55"));
	}

	// --- the core's failures become our exceptions

	@Test
	void core404_becomesParticipantNotFound() {
		core.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.getProjection("M1001")).isInstanceOf(ParticipantNotFoundException.class);
	}

	@Test
	void core500_becomesCoreUnavailable() {
		core.expect(requestTo(URL)).andRespond(withServerError());

		assertThatThrownBy(() -> adapter.getProjection("M1001")).isInstanceOf(CoreUnavailableException.class);
	}

	@Test
	void connectionFailure_becomesCoreUnavailable() {
		core.expect(requestTo(URL)).andRespond(request -> {
			throw new IOException("connection refused");
		});

		assertThatThrownBy(() -> adapter.getProjection("M1001")).isInstanceOf(CoreUnavailableException.class);
	}

	@Test
	void unreadableJson_becomesCoreResponseException() {
		assertRejected("this is not json");
	}

	@Test
	void emptyAnswer_becomesCoreResponseException() {
		core.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK));

		assertThatThrownBy(() -> adapter.getProjection("M1001")).isInstanceOf(CoreResponseException.class);
	}

	@Test
	void idWithSlash_isEncodedAndCannotChangeThePath() {
		core.expect(requestTo("http://core.test/core/v1/participants/a%2Fb/projection"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.getProjection("a/b")).isInstanceOf(ParticipantNotFoundException.class);
		core.verify();
	}

}
