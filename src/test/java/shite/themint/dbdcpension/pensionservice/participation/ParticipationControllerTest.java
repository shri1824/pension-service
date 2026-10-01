package shite.themint.dbdcpension.pensionservice.participation;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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

/**
 * The internal endpoint on the full application, with the port replaced by a mock so the
 * HTTP side (shape, status codes, problem details, security) is tested on its own.
 */
@SpringBootTest(properties = "spring.security.oauth2.client.registration.core.client-secret=test-secret")
@AutoConfigureMockMvc
class ParticipationControllerTest {

	private static final String URL = "/internal/v1/participants/M1001/participation";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ParticipationPort participationPort;

	private static RequestPostProcessor pensionRead() {
		return jwt().authorities(new SimpleGrantedAuthority("SCOPE_pension.read"));
	}

	@Test
	void returnsParticipationInOurModelAndNotTheCoresNames() throws Exception {
		when(participationPort.getParticipation("M1001")).thenReturn(new Participation(
				"SCH-001", "Example Employer B.V.", ContractType.DC, LocalDate.of(2019, 9, 1), null));

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.schemeId").value("SCH-001"))
				.andExpect(jsonPath("$.employerName").value("Example Employer B.V."))
				.andExpect(jsonPath("$.contractType").value("DC"))
				.andExpect(jsonPath("$.participatedFrom").value("2019-09-01"))
				.andExpect(jsonPath("$.participatedUntil").value(nullValue()))
				.andExpect(jsonPath("$.schemeCode").doesNotExist())
				.andExpect(jsonPath("$.planType").doesNotExist());
	}

	@Test
	void unknownMember_isProblemDetail404() throws Exception {
		when(participationPort.getParticipation("M1001")).thenThrow(new ParticipantNotFoundException());

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Participant not found"));
	}

	@Test
	void coreUnavailable_isProblemDetail503WithoutInternals() throws Exception {
		when(participationPort.getParticipation("M1001"))
				.thenThrow(new CoreUnavailableException("secret internal detail http://core.internal", new RuntimeException()));

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("The core system is temporarily unavailable"))
				.andExpect(jsonPath("$.trace").doesNotExist());
	}

	@Test
	void badCoreResponse_isProblemDetail502() throws Exception {
		when(participationPort.getParticipation("M1001")).thenThrow(new CoreResponseException("bad"));

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.detail").value("The core system returned data we cannot use"));
	}

	@Test
	void idWithUnexpectedCharacters_isRejectedBeforeAnythingIsCalled() throws Exception {
		mockMvc.perform(get("/internal/v1/participants/M!1001/participation").with(pensionRead()))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(participationPort);
	}

	@Test
	void withoutToken_isUnauthorized() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
		verifyNoInteractions(participationPort);
	}

	@Test
	void tokenWithoutScope_isForbidden() throws Exception {
		mockMvc.perform(get(URL).with(jwt())).andExpect(status().isForbidden());
		verifyNoInteractions(participationPort);
	}

}
