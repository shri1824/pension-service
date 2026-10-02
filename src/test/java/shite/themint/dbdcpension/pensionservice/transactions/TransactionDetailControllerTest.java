package shite.themint.dbdcpension.pensionservice.transactions;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

/** The internal transaction detail endpoint on the full application, with the port replaced by a mock. */
@SpringBootTest(properties = "spring.security.oauth2.client.registration.core.client-secret=test-secret")
@AutoConfigureMockMvc
class TransactionDetailControllerTest {

	private static final String URL = "/internal/v1/participants/M1001/transactions/T20260914-001";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TransactionsPort transactionsPort;

	private static RequestPostProcessor pensionRead() {
		return jwt().authorities(new SimpleGrantedAuthority("SCOPE_pension.read"));
	}

	private static TransactionDetail someDetail() {
		return new TransactionDetail("T20260914-001", LocalDate.of(2026, 9, 14), TransactionType.INVESTMENT_WITHDRAWAL,
				"EUR", new BigDecimal("-862.46"),
				List.of(new TransactionDetail.Component("DEFINED_CONTRIBUTION", new BigDecimal("-862.51")),
						new TransactionDetail.Component("BONUS", new BigDecimal("-0.01")),
						new TransactionDetail.Component("ADMINISTRATION_COSTS", new BigDecimal("0.04"))),
				List.of(new TransactionDetail.FundLine("FUND-MIX-N", "Example Mixed Fund Neutral",
						new BigDecimal("-6.502952"), LocalDate.of(2026, 9, 14), new BigDecimal("132.63"),
						new BigDecimal("-862.46"))));
	}

	@Test
	void returnsTheDetailInOurModelAndNotTheCoresNames() throws Exception {
		when(transactionsPort.getTransaction("M1001", "T20260914-001")).thenReturn(someDetail());

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.transactionId").value("T20260914-001"))
				.andExpect(jsonPath("$.date").value("2026-09-14"))
				.andExpect(jsonPath("$.type").value("INVESTMENT_WITHDRAWAL"))
				.andExpect(jsonPath("$.currency").value("EUR"))
				.andExpect(jsonPath("$.amount").value(-862.46))
				.andExpect(jsonPath("$.components.length()").value(3))
				.andExpect(jsonPath("$.components[0].code").value("DEFINED_CONTRIBUTION"))
				.andExpect(jsonPath("$.components[0].amount").value(-862.51))
				.andExpect(jsonPath("$.components[2].amount").value(0.04))
				.andExpect(jsonPath("$.funds.length()").value(1))
				.andExpect(jsonPath("$.funds[0].fundId").value("FUND-MIX-N"))
				.andExpect(jsonPath("$.funds[0].units").value(-6.502952))
				.andExpect(jsonPath("$.funds[0].priceDate").value("2026-09-14"))
				.andExpect(jsonPath("$.funds[0].unitPrice").value(132.63))
				.andExpect(jsonPath("$.funds[0].totalValue").value(-862.46))
				.andExpect(jsonPath("$.lines").doesNotExist())
				.andExpect(jsonPath("$.fundMovements").doesNotExist())
				.andExpect(jsonPath("$.transactionNumber").doesNotExist());
	}

	@Test
	void priceDifference_hasAnEmptyComponentList() throws Exception {
		when(transactionsPort.getTransaction("M1001", "T20260916-001")).thenReturn(new TransactionDetail(
				"T20260916-001", LocalDate.of(2026, 9, 16), TransactionType.PRICE_DIFFERENCE, "EUR", new BigDecimal("9.05"),
				List.of(), List.of(new TransactionDetail.FundLine("FUND-MIX-N", "Example Mixed Fund Neutral",
						new BigDecimal("0.067880"), LocalDate.of(2026, 9, 17), new BigDecimal("133.29"), new BigDecimal("9.05")))));

		mockMvc.perform(get("/internal/v1/participants/M1001/transactions/T20260916-001").with(pensionRead()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("PRICE_DIFFERENCE"))
				.andExpect(jsonPath("$.components").isEmpty())
				.andExpect(jsonPath("$.funds[0].priceDate").value("2026-09-17"));
	}

	@Test
	void unknownTransactionOrMember_isProblemDetail404() throws Exception {
		when(transactionsPort.getTransaction("M1001", "T20260914-001")).thenThrow(new ParticipantNotFoundException());

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void coreUnavailable_isProblemDetail503WithoutInternals() throws Exception {
		when(transactionsPort.getTransaction("M1001", "T20260914-001"))
				.thenThrow(new CoreUnavailableException("secret internal detail http://core.internal", new RuntimeException()));

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value("The core system is temporarily unavailable"));
	}

	@Test
	void badCoreResponse_isProblemDetail502() throws Exception {
		when(transactionsPort.getTransaction("M1001", "T20260914-001")).thenThrow(new CoreResponseException("bad"));

		mockMvc.perform(get(URL).with(pensionRead())).andExpect(status().isBadGateway());
	}

	@Test
	void unexpectedCharactersInEitherId_areRejectedBeforeAnythingIsCalled() throws Exception {
		mockMvc.perform(get("/internal/v1/participants/M!1001/transactions/T20260914-001").with(pensionRead()))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/internal/v1/participants/M1001/transactions/T2026!0914").with(pensionRead()))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/internal/v1/participants/M1001/transactions/" + "T".repeat(41)).with(pensionRead()))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(transactionsPort);
	}

	@Test
	void withoutToken_isUnauthorized() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
		verifyNoInteractions(transactionsPort);
	}

	@Test
	void tokenWithoutScope_isForbidden() throws Exception {
		mockMvc.perform(get(URL).with(jwt())).andExpect(status().isForbidden());
		verify(transactionsPort, never()).getTransaction(anyString(), anyString());
	}

}
