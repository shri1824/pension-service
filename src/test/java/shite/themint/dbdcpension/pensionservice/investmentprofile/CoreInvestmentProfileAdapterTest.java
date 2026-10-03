package shite.themint.dbdcpension.pensionservice.investmentprofile;

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
import shite.themint.dbdcpension.pensionservice.participation.ContractType;

/** The investment choice anti-corruption layer alone: the core is replaced by a mock server. */
class CoreInvestmentProfileAdapterTest {

	private static final String URL = "http://core.test/core/v1/participants/M1001/investment-profile";

	private MockRestServiceServer core;
	private CoreInvestmentProfileAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://core.test");
		core = MockRestServiceServer.bindTo(builder).build();
		adapter = new CoreInvestmentProfileAdapter(builder.build());
	}

	private static String coreJson(String risk, String payout, String plan, String retires) {
		return """
				{"participantId":"M1001","riskProfile":%s,"payoutPreference":%s,"planType":%s,"retirementDate":%s}
				""".formatted(risk, payout, plan, retires);
	}

	private void coreAnswers(String json) {
		core.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
	}

	private void assertRejected(String json) {
		core.reset();
		coreAnswers(json);
		assertThatThrownBy(() -> adapter.getInvestmentProfile("M1001")).isInstanceOf(CoreResponseException.class);
	}

	// --- translation of the core's codes

	@Test
	void screenshotMember_isNeutralAndHeadingForAFixedPension() {
		coreAnswers(coreJson("\"NEUTRAL\"", "\"FIXED_ANNUITY\"", "\"DC\"", "\"2056-06-01\""));

		InvestmentProfileData result = adapter.getInvestmentProfile("M1001");

		assertThat(result.profile()).isEqualTo(InvestmentProfile.NEUTRAL);
		assertThat(result.pensionTarget()).isEqualTo(PensionTarget.FIXED);
		assertThat(result.contractType()).isEqualTo(ContractType.DC);
		assertThat(result.retirementDate()).isEqualTo(LocalDate.of(2056, 6, 1));
		core.verify();
	}

	@Test
	void everyProfileCode_isTranslated() {
		String[][] pairs = { { "DEFENSIVE", "DEFENSIVE" }, { "NEUTRAL", "NEUTRAL" }, { "AGGRESSIVE", "AGGRESSIVE" },
				{ "EXECUTION_ONLY", "SELF_DIRECTED" } };
		for (String[] pair : pairs) {
			core.reset();
			coreAnswers(coreJson("\"" + pair[0] + "\"", "\"FIXED_ANNUITY\"", "\"DC\"", "\"2056-06-01\""));

			assertThat(adapter.getInvestmentProfile("M1001").profile()).as(pair[0]).isEqualTo(InvestmentProfile.valueOf(pair[1]));
		}
	}

	@Test
	void variableAnnuity_isAVariablePension() {
		coreAnswers(coreJson("\"AGGRESSIVE\"", "\"VARIABLE_ANNUITY\"", "\"DC\"", "\"2050-03-01\""));

		assertThat(adapter.getInvestmentProfile("M1001").pensionTarget()).isEqualTo(PensionTarget.VARIABLE);
	}

	@Test
	void missingPayoutPreference_defaultsToAFixedPension_assumptionA4() {
		coreAnswers(coreJson("\"NEUTRAL\"", "null", "\"DC\"", "\"2056-06-01\""));

		assertThat(adapter.getInvestmentProfile("M1001").pensionTarget()).isEqualTo(PensionTarget.FIXED);
	}

	@Test
	void dbMember_isTranslated() {
		coreAnswers(coreJson("\"NEUTRAL\"", "\"FIXED_ANNUITY\"", "\"DB\"", "\"2056-06-01\""));

		assertThat(adapter.getInvestmentProfile("M1001").contractType()).isEqualTo(ContractType.DB);
	}

	// --- codes we do not know are rejected, not guessed

	@Test
	void unknownRiskProfile_isRejected() {
		assertRejected(coreJson("\"BALANCED\"", "\"FIXED_ANNUITY\"", "\"DC\"", "\"2056-06-01\""));
		assertRejected(coreJson("null", "\"FIXED_ANNUITY\"", "\"DC\"", "\"2056-06-01\""));
	}

	@Test
	void unknownPayoutPreference_isRejected_onlyAMissingOneGetsTheDefault() {
		assertRejected(coreJson("\"NEUTRAL\"", "\"LUMP_SUM\"", "\"DC\"", "\"2056-06-01\""));
	}

	@Test
	void unknownPlanType_isRejected() {
		assertRejected(coreJson("\"NEUTRAL\"", "\"FIXED_ANNUITY\"", "\"CDC\"", "\"2056-06-01\""));
		assertRejected(coreJson("\"NEUTRAL\"", "\"FIXED_ANNUITY\"", "null", "\"2056-06-01\""));
	}

	@Test
	void missingRetirementDate_isRejected_theAvailabilityRuleNeedsIt() {
		assertRejected(coreJson("\"NEUTRAL\"", "\"FIXED_ANNUITY\"", "\"DC\"", "null"));
	}

	// --- the core's failures become our exceptions

	@Test
	void core404_becomesParticipantNotFound() {
		core.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.getInvestmentProfile("M1001")).isInstanceOf(ParticipantNotFoundException.class);
	}

	@Test
	void core500_becomesCoreUnavailable() {
		core.expect(requestTo(URL)).andRespond(withServerError());

		assertThatThrownBy(() -> adapter.getInvestmentProfile("M1001")).isInstanceOf(CoreUnavailableException.class);
	}

	@Test
	void connectionFailure_becomesCoreUnavailable() {
		core.expect(requestTo(URL)).andRespond(request -> {
			throw new IOException("connection refused");
		});

		assertThatThrownBy(() -> adapter.getInvestmentProfile("M1001")).isInstanceOf(CoreUnavailableException.class);
	}

	@Test
	void unreadableJson_becomesCoreResponseException() {
		assertRejected("this is not json");
	}

	@Test
	void emptyAnswer_becomesCoreResponseException() {
		core.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK));

		assertThatThrownBy(() -> adapter.getInvestmentProfile("M1001")).isInstanceOf(CoreResponseException.class);
	}

	@Test
	void idWithSlash_isEncodedAndCannotChangeThePath() {
		core.expect(requestTo("http://core.test/core/v1/participants/a%2Fb/investment-profile"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.getInvestmentProfile("a/b")).isInstanceOf(ParticipantNotFoundException.class);
		core.verify();
	}

}
