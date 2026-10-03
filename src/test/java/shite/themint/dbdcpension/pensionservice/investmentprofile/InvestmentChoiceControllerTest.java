package shite.themint.dbdcpension.pensionservice.investmentprofile;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** The internal investment choice endpoint on the full application, with the service replaced by a mock. */
@SpringBootTest(properties = "spring.security.oauth2.client.registration.core.client-secret=test-secret")
@AutoConfigureMockMvc
class InvestmentChoiceControllerTest {

	private static final String URL = "/internal/v1/participants/M1001/investment-choice";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private InvestmentChoiceService service;

	private static RequestPostProcessor pensionRead() {
		return jwt().authorities(new SimpleGrantedAuthority("SCOPE_pension.read"));
	}

	@Test
	void returnsOnlyCodes_inOurModelAndNotTheCoresNames() throws Exception {
		when(service.getInvestmentChoice("M1001")).thenReturn(new InvestmentChoice(InvestmentProfile.NEUTRAL,
				PensionTarget.FIXED, List.of(AvailableAction.INVESTMENT_CHOICE, AvailableAction.RETIREMENT_AGE,
						AvailableAction.OTHER_CHOICES)));

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.profile").value("NEUTRAL"))
				.andExpect(jsonPath("$.pensionTarget").value("FIXED"))
				.andExpect(jsonPath("$.availableActions.length()").value(3))
				.andExpect(jsonPath("$.availableActions[0]").value("INVESTMENT_CHOICE"))
				.andExpect(jsonPath("$.riskProfile").doesNotExist())
				.andExpect(jsonPath("$.payoutPreference").doesNotExist())
				.andExpect(jsonPath("$.retirementDate").doesNotExist());
	}

	@Test
	void retiredMember_hasOnlyTheOtherChoices() throws Exception {
		when(service.getInvestmentChoice("M1001")).thenReturn(new InvestmentChoice(InvestmentProfile.NEUTRAL,
				PensionTarget.FIXED, List.of(AvailableAction.OTHER_CHOICES)));

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableActions.length()").value(1))
				.andExpect(jsonPath("$.availableActions[0]").value("OTHER_CHOICES"));
	}

	@Test
	void unknownMember_isProblemDetail404() throws Exception {
		when(service.getInvestmentChoice("M1001")).thenThrow(new ParticipantNotFoundException());

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void coreUnavailable_isProblemDetail503WithoutInternals() throws Exception {
		when(service.getInvestmentChoice("M1001"))
				.thenThrow(new CoreUnavailableException("secret internal detail http://core.internal", new RuntimeException()));

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value("The core system is temporarily unavailable"));
	}

	@Test
	void badCoreResponse_isProblemDetail502() throws Exception {
		when(service.getInvestmentChoice("M1001")).thenThrow(new CoreResponseException("bad"));

		mockMvc.perform(get(URL).with(pensionRead())).andExpect(status().isBadGateway());
	}

	@Test
	void idWithUnexpectedCharacters_isRejectedBeforeAnythingIsCalled() throws Exception {
		mockMvc.perform(get("/internal/v1/participants/M!1001/investment-choice").with(pensionRead()))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(service);
	}

	@Test
	void withoutToken_isUnauthorized() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}

	@Test
	void tokenWithoutScope_isForbidden() throws Exception {
		mockMvc.perform(get(URL).with(jwt())).andExpect(status().isForbidden());
		verifyNoInteractions(service);
	}

}
