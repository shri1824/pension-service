package shite.themint.dbdcpension.pensionservice.projection;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import shite.themint.dbdcpension.pensionservice.error.CoreResponseException;
import shite.themint.dbdcpension.pensionservice.error.CoreUnavailableException;
import shite.themint.dbdcpension.pensionservice.error.ParticipantNotFoundException;

/** The internal projection endpoint on the full application, with the port replaced by a mock. */
@SpringBootTest(properties = "spring.security.oauth2.client.registration.core.client-secret=test-secret")
@AutoConfigureMockMvc
class ProjectionControllerTest {

	private static final String URL = "/internal/v1/participants/M1001/projection";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProjectionPort projectionPort;

	private static RequestPostProcessor pensionRead() {
		return jwt().authorities(new SimpleGrantedAuthority("SCOPE_pension.read"));
	}

	@Test
	void returnsTheProjectionInOurModelAndNotTheCoresNames() throws Exception {
		when(projectionPort.getProjection("M1001")).thenReturn(new Projection(68, "EUR", AmountBasis.TODAYS_PRICES,
				LocalDate.of(2026, 9, 30), List.of(
						new Projection.Scenario(ScenarioType.EXPECTED, new BigDecimal("369.42")),
						new Projection.Scenario(ScenarioType.UNFAVOURABLE, new BigDecimal("117.10")),
						new Projection.Scenario(ScenarioType.FAVOURABLE, new BigDecimal("881.55")))));

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.retirementAge").value(68))
				.andExpect(jsonPath("$.currency").value("EUR"))
				.andExpect(jsonPath("$.amountBasis").value("TODAYS_PRICES"))
				.andExpect(jsonPath("$.calculatedOn").value("2026-09-30"))
				.andExpect(jsonPath("$.scenarios[0].type").value("EXPECTED"))
				.andExpect(jsonPath("$.scenarios[0].grossMonthlyAmount").value(369.42))
				.andExpect(jsonPath("$.scenarios[1].type").value("UNFAVOURABLE"))
				.andExpect(jsonPath("$.scenarios[2].grossMonthlyAmount").value(881.55))
				.andExpect(jsonPath("$.pensionAge").doesNotExist())
				.andExpect(jsonPath("$.middleScenario").doesNotExist());
	}

	@Test
	void unknownMember_isProblemDetail404() throws Exception {
		when(projectionPort.getProjection("M1001")).thenThrow(new ParticipantNotFoundException());

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void coreUnavailable_isProblemDetail503WithoutInternals() throws Exception {
		when(projectionPort.getProjection("M1001"))
				.thenThrow(new CoreUnavailableException("secret internal detail http://core.internal", new RuntimeException()));

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value("The core system is temporarily unavailable"));
	}

	@Test
	void badCoreResponse_isProblemDetail502() throws Exception {
		when(projectionPort.getProjection("M1001")).thenThrow(new CoreResponseException("bad"));

		mockMvc.perform(get(URL).with(pensionRead())).andExpect(status().isBadGateway());
	}

	@Test
	void idWithUnexpectedCharacters_isRejectedBeforeAnythingIsCalled() throws Exception {
		mockMvc.perform(get("/internal/v1/participants/M!1001/projection").with(pensionRead()))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(projectionPort);
	}

	@Test
	void withoutToken_isUnauthorized() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
		verifyNoInteractions(projectionPort);
	}

	@Test
	void tokenWithoutScope_isForbidden() throws Exception {
		mockMvc.perform(get(URL).with(jwt())).andExpect(status().isForbidden());
		verifyNoInteractions(projectionPort);
	}

}
