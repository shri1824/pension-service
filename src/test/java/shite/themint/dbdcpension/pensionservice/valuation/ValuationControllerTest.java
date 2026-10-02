package shite.themint.dbdcpension.pensionservice.valuation;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import shite.themint.dbdcpension.pensionservice.error.ParticipantNotFoundException;

/** The internal valuation endpoint on the full application, with the port replaced by a mock. */
@SpringBootTest(properties = "spring.security.oauth2.client.registration.core.client-secret=test-secret")
@AutoConfigureMockMvc
class ValuationControllerTest {

	private static final String URL = "/internal/v1/participants/M1001/valuation";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ValuationPort valuationPort;

	private static RequestPostProcessor pensionRead() {
		return jwt().authorities(new SimpleGrantedAuthority("SCOPE_pension.read"));
	}

	@Test
	void returnsTheValuationInOurModelAndNotTheCoresNames() throws Exception {
		when(valuationPort.getValuation("M1001")).thenReturn(new Valuation("EUR", new BigDecimal("20769.28"),
				new BigDecimal("25992.60"), LocalDate.of(2026, 9, 24),
				new InvestmentReturn(new BigDecimal("5223.32"), new BigDecimal("25.15"))));

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currency").value("EUR"))
				.andExpect(jsonPath("$.totalContributions").value(20769.28))
				.andExpect(jsonPath("$.capitalValue").value(25992.60))
				.andExpect(jsonPath("$.valuedOn").value("2026-09-24"))
				.andExpect(jsonPath("$.investmentReturn.amount").value(5223.32))
				.andExpect(jsonPath("$.investmentReturn.percentage").value(25.15))
				.andExpect(jsonPath("$.premiumsPaidTotal").doesNotExist())
				.andExpect(jsonPath("$.marketValue").doesNotExist());
	}

	@Test
	void newMember_hasANullPercentage() throws Exception {
		when(valuationPort.getValuation("M1001")).thenReturn(new Valuation("EUR", new BigDecimal("0.00"),
				new BigDecimal("0.00"), LocalDate.of(2026, 9, 24),
				new InvestmentReturn(new BigDecimal("0.00"), null)));

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.investmentReturn.percentage").value(nullValue()));
	}

	@Test
	void unknownMember_isProblemDetail404() throws Exception {
		when(valuationPort.getValuation("M1001")).thenThrow(new ParticipantNotFoundException());

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void coreUnavailable_isProblemDetail503WithoutInternals() throws Exception {
		when(valuationPort.getValuation("M1001"))
				.thenThrow(new CoreUnavailableException("secret internal detail http://core.internal", new RuntimeException()));

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value("The core system is temporarily unavailable"));
	}

	@Test
	void badCoreResponse_isProblemDetail502() throws Exception {
		when(valuationPort.getValuation("M1001")).thenThrow(new CoreResponseException("bad"));

		mockMvc.perform(get(URL).with(pensionRead())).andExpect(status().isBadGateway());
	}

	@Test
	void idWithUnexpectedCharacters_isRejectedBeforeAnythingIsCalled() throws Exception {
		mockMvc.perform(get("/internal/v1/participants/M!1001/valuation").with(pensionRead()))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(valuationPort);
	}

	@Test
	void withoutToken_isUnauthorized() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
		verifyNoInteractions(valuationPort);
	}

	@Test
	void tokenWithoutScope_isForbidden() throws Exception {
		mockMvc.perform(get(URL).with(jwt())).andExpect(status().isForbidden());
		verifyNoInteractions(valuationPort);
	}

}
