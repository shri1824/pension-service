package shite.themint.dbdcpension.pensionservice.holdings;

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

/** The internal holdings endpoint on the full application, with the port replaced by a mock. */
@SpringBootTest(properties = "spring.security.oauth2.client.registration.core.client-secret=test-secret")
@AutoConfigureMockMvc
class HoldingsControllerTest {

	private static final String URL = "/internal/v1/participants/M1001/holdings";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private HoldingsPort holdingsPort;

	private static RequestPostProcessor pensionRead() {
		return jwt().authorities(new SimpleGrantedAuthority("SCOPE_pension.read"));
	}

	@Test
	void returnsTheFundsInOurModelAndNotTheCoresNames() throws Exception {
		when(holdingsPort.getHoldings("M1001")).thenReturn(new Holdings("EUR", List.of(new Holdings.Fund(
				"FUND-MIX-N", "Example Mixed Fund Neutral", new BigDecimal("195.228665"), LocalDate.of(2026, 9, 24),
				new BigDecimal("134.19"), new BigDecimal("26197.32")))));

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currency").value("EUR"))
				.andExpect(jsonPath("$.funds.length()").value(1))
				.andExpect(jsonPath("$.funds[0].fundId").value("FUND-MIX-N"))
				.andExpect(jsonPath("$.funds[0].name").value("Example Mixed Fund Neutral"))
				.andExpect(jsonPath("$.funds[0].units").value(195.228665))
				.andExpect(jsonPath("$.funds[0].priceDate").value("2026-09-24"))
				.andExpect(jsonPath("$.funds[0].unitPrice").value(134.19))
				.andExpect(jsonPath("$.funds[0].totalValue").value(26197.32))
				.andExpect(jsonPath("$.positions").doesNotExist())
				.andExpect(jsonPath("$.funds[0].fundCode").doesNotExist());
	}

	@Test
	void newMember_getsAnEmptyList() throws Exception {
		when(holdingsPort.getHoldings("M1001")).thenReturn(new Holdings("EUR", List.of()));

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.funds").isEmpty());
	}

	@Test
	void unknownMember_isProblemDetail404() throws Exception {
		when(holdingsPort.getHoldings("M1001")).thenThrow(new ParticipantNotFoundException());

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void coreUnavailable_isProblemDetail503WithoutInternals() throws Exception {
		when(holdingsPort.getHoldings("M1001"))
				.thenThrow(new CoreUnavailableException("secret internal detail http://core.internal", new RuntimeException()));

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value("The core system is temporarily unavailable"));
	}

	@Test
	void badCoreResponse_isProblemDetail502() throws Exception {
		when(holdingsPort.getHoldings("M1001")).thenThrow(new CoreResponseException("bad"));

		mockMvc.perform(get(URL).with(pensionRead())).andExpect(status().isBadGateway());
	}

	@Test
	void idWithUnexpectedCharacters_isRejectedBeforeAnythingIsCalled() throws Exception {
		mockMvc.perform(get("/internal/v1/participants/M!1001/holdings").with(pensionRead()))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(holdingsPort);
	}

	@Test
	void withoutToken_isUnauthorized() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
		verifyNoInteractions(holdingsPort);
	}

	@Test
	void tokenWithoutScope_isForbidden() throws Exception {
		mockMvc.perform(get(URL).with(jwt())).andExpect(status().isForbidden());
		verifyNoInteractions(holdingsPort);
	}

}
