package shite.themint.dbdcpension.pensionservice.participation;

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
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import shite.themint.dbdcpension.pensionservice.error.CoreResponseException;
import shite.themint.dbdcpension.pensionservice.error.CoreUnavailableException;

/**
 * Tests the anti-corruption layer alone: the core is replaced by a mock server, so every
 * success and failure of the core can be produced on demand.
 */
class CoreParticipationAdapterTest {

	private static final String URL = "http://core.test/core/v1/participants/M1001/participation";

	private MockRestServiceServer core;
	private CoreParticipationAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://core.test");
		core = MockRestServiceServer.bindTo(builder).build();
		adapter = new CoreParticipationAdapter(builder.build());
	}

	private static String coreJson(String planType, String endDate) {
		return """
				{"participantId":"M1001","schemeCode":"SCH-001","employerName":"Example Employer B.V.",
				 "planType":%s,"startDate":"2019-09-01","endDate":%s}
				""".formatted(planType, endDate);
	}

	// --- translation

	@Test
	void activeMember_isTranslatedToOurModel() {
		core.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(coreJson("\"DC\"", "null"), MediaType.APPLICATION_JSON));

		Participation result = adapter.getParticipation("M1001");

		assertThat(result).isEqualTo(new Participation("SCH-001", "Example Employer B.V.", ContractType.DC,
				LocalDate.of(2019, 9, 1), null));
		core.verify();
	}

	@Test
	void memberWhoLeft_keepsTheEndDate() {
		core.expect(requestTo(URL))
				.andRespond(withSuccess(coreJson("\"DC\"", "\"2025-12-31\""), MediaType.APPLICATION_JSON));

		assertThat(adapter.getParticipation("M1001").participatedUntil()).isEqualTo(LocalDate.of(2025, 12, 31));
	}

	@Test
	void dbPlan_isMappedToDb() {
		core.expect(requestTo(URL)).andRespond(withSuccess(coreJson("\"DB\"", "null"), MediaType.APPLICATION_JSON));

		assertThat(adapter.getParticipation("M1001").contractType()).isEqualTo(ContractType.DB);
	}

	@Test
	void unknownPlanType_isRejectedAsABadCoreResponse() {
		core.expect(requestTo(URL)).andRespond(withSuccess(coreJson("\"XX\"", "null"), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.getParticipation("M1001")).isInstanceOf(CoreResponseException.class);
	}

	// --- the core's failures become our exceptions

	@Test
	void core404_becomesParticipantNotFound() {
		core.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.getParticipation("M1001")).isInstanceOf(ParticipantNotFoundException.class);
	}

	@Test
	void core500_becomesCoreUnavailable() {
		core.expect(requestTo(URL)).andRespond(withServerError());

		assertThatThrownBy(() -> adapter.getParticipation("M1001")).isInstanceOf(CoreUnavailableException.class);
	}

	@Test
	void connectionFailure_becomesCoreUnavailable() {
		core.expect(requestTo(URL)).andRespond(request -> {
			throw new IOException("connection refused");
		});

		assertThatThrownBy(() -> adapter.getParticipation("M1001")).isInstanceOf(CoreUnavailableException.class);
	}

	@Test
	void noTokenForTheCore_becomesCoreUnavailable() {
		core.expect(requestTo(URL)).andRespond(request -> {
			throw new ClientAuthorizationException(new OAuth2Error("invalid_client"), "core");
		});

		assertThatThrownBy(() -> adapter.getParticipation("M1001")).isInstanceOf(CoreUnavailableException.class);
	}

	@Test
	void unreadableJson_becomesCoreResponseException() {
		core.expect(requestTo(URL)).andRespond(withSuccess("this is not json", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.getParticipation("M1001")).isInstanceOf(CoreResponseException.class);
	}

	@Test
	void emptyAnswer_becomesCoreResponseException() {
		core.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK));

		assertThatThrownBy(() -> adapter.getParticipation("M1001")).isInstanceOf(CoreResponseException.class);
	}

	// --- safety

	@Test
	void idWithSlash_isEncodedAndCannotChangeThePath() {
		core.expect(requestTo("http://core.test/core/v1/participants/a%2Fb/participation"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.getParticipation("a/b")).isInstanceOf(ParticipantNotFoundException.class);
		core.verify();
	}

}
