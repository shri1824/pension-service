package shite.themint.dbdcpension.pensionservice.transactions;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

/** The internal transactions endpoint on the full application, with the port replaced by a mock. */
@SpringBootTest(properties = "spring.security.oauth2.client.registration.core.client-secret=test-secret")
@AutoConfigureMockMvc
class TransactionsControllerTest {

	private static final String URL = "/internal/v1/participants/M1001/transactions";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TransactionsPort transactionsPort;

	private static RequestPostProcessor pensionRead() {
		return jwt().authorities(new SimpleGrantedAuthority("SCOPE_pension.read"));
	}

	private static TransactionList somePage() {
		return new TransactionList("EUR", 0, 10, true, List.of(
				new TransactionList.Item("T20260916-001", LocalDate.of(2026, 9, 16), TransactionType.PRICE_DIFFERENCE, new BigDecimal("9.05")),
				new TransactionList.Item("T20260914-001", LocalDate.of(2026, 9, 14), TransactionType.INVESTMENT_WITHDRAWAL, new BigDecimal("-862.46"))));
	}

	@Test
	void returnsThePageInOurModelAndNotTheCoresNames() throws Exception {
		when(transactionsPort.getTransactions("M1001", 0, 10)).thenReturn(somePage());

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currency").value("EUR"))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(10))
				.andExpect(jsonPath("$.hasMore").value(true))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].transactionId").value("T20260916-001"))
				.andExpect(jsonPath("$.items[0].date").value("2026-09-16"))
				.andExpect(jsonPath("$.items[0].type").value("PRICE_DIFFERENCE"))
				.andExpect(jsonPath("$.items[0].amount").value(9.05))
				.andExpect(jsonPath("$.items[1].amount").value(-862.46))
				.andExpect(jsonPath("$.transactions").doesNotExist())
				.andExpect(jsonPath("$.items[0].transactionNumber").doesNotExist());
	}

	@Test
	void withoutParameters_pageZeroAndSizeTenAreAsked() throws Exception {
		when(transactionsPort.getTransactions("M1001", 0, 10)).thenReturn(somePage());

		mockMvc.perform(get(URL).with(pensionRead())).andExpect(status().isOk());

		verify(transactionsPort).getTransactions("M1001", 0, 10);
	}

	@Test
	void pageAndSizeAreHandedOn() throws Exception {
		when(transactionsPort.getTransactions("M1001", 2, 25)).thenReturn(somePage());

		mockMvc.perform(get(URL).param("page", "2").param("size", "25").with(pensionRead())).andExpect(status().isOk());

		verify(transactionsPort).getTransactions("M1001", 2, 25);
	}

	@Test
	void theLargestAllowedSizeIsAccepted() throws Exception {
		when(transactionsPort.getTransactions("M1001", 0, 50)).thenReturn(somePage());

		mockMvc.perform(get(URL).param("size", "50").with(pensionRead())).andExpect(status().isOk());
	}

	@Test
	void badPagingParameters_are400_beforeAnythingIsCalled() throws Exception {
		mockMvc.perform(get(URL).param("page", "-1").with(pensionRead())).andExpect(status().isBadRequest());
		mockMvc.perform(get(URL).param("size", "0").with(pensionRead())).andExpect(status().isBadRequest());
		mockMvc.perform(get(URL).param("size", "51").with(pensionRead())).andExpect(status().isBadRequest());
		mockMvc.perform(get(URL).param("page", "abc").with(pensionRead())).andExpect(status().isBadRequest());
		mockMvc.perform(get(URL).param("size", "10; DROP").with(pensionRead())).andExpect(status().isBadRequest());

		verifyNoInteractions(transactionsPort);
	}

	@Test
	void emptyPage_isAnEmptyList() throws Exception {
		when(transactionsPort.getTransactions("M1001", 0, 10)).thenReturn(new TransactionList("EUR", 0, 10, false, List.of()));

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty())
				.andExpect(jsonPath("$.hasMore").value(false));
	}

	@Test
	void unknownMember_isProblemDetail404() throws Exception {
		when(transactionsPort.getTransactions("M1001", 0, 10)).thenThrow(new ParticipantNotFoundException());

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void coreUnavailable_isProblemDetail503WithoutInternals() throws Exception {
		when(transactionsPort.getTransactions("M1001", 0, 10))
				.thenThrow(new CoreUnavailableException("secret internal detail http://core.internal", new RuntimeException()));

		mockMvc.perform(get(URL).with(pensionRead()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value("The core system is temporarily unavailable"));
	}

	@Test
	void badCoreResponse_isProblemDetail502() throws Exception {
		when(transactionsPort.getTransactions("M1001", 0, 10)).thenThrow(new CoreResponseException("bad"));

		mockMvc.perform(get(URL).with(pensionRead())).andExpect(status().isBadGateway());
	}

	@Test
	void idWithUnexpectedCharacters_isRejectedBeforeAnythingIsCalled() throws Exception {
		mockMvc.perform(get("/internal/v1/participants/M!1001/transactions").with(pensionRead()))
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
		verify(transactionsPort, org.mockito.Mockito.never()).getTransactions(anyString(), anyInt(), anyInt());
	}

}
