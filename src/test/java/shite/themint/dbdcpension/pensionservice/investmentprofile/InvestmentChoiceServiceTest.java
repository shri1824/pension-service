package shite.themint.dbdcpension.pensionservice.investmentprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import shite.themint.dbdcpension.pensionservice.participation.ContractType;

class InvestmentChoiceServiceTest {

	private static InvestmentChoiceService serviceOn(InvestmentProfilePort port, String isoDate) {
		return new InvestmentChoiceService(port,
				Clock.fixed(Instant.parse(isoDate + "T08:00:00Z"), ZoneOffset.UTC));
	}

	@Test
	void profileAndTargetPassThrough_andTheActionsComeFromThePolicy() {
		InvestmentProfilePort port = mock(InvestmentProfilePort.class);
		when(port.getInvestmentProfile("M1001")).thenReturn(new InvestmentProfileData(
				InvestmentProfile.NEUTRAL, PensionTarget.FIXED, ContractType.DC, LocalDate.of(2056, 6, 1)));

		InvestmentChoice result = serviceOn(port, "2026-10-03").getInvestmentChoice("M1001");

		assertThat(result.profile()).isEqualTo(InvestmentProfile.NEUTRAL);
		assertThat(result.pensionTarget()).isEqualTo(PensionTarget.FIXED);
		assertThat(result.availableActions()).containsExactly(AvailableAction.INVESTMENT_CHOICE,
				AvailableAction.RETIREMENT_AGE, AvailableAction.OTHER_CHOICES);
	}

	@Test
	void theSameCachedData_givesDifferentActionsOnDifferentDays_becauseTheyAreNotCached() {
		InvestmentProfilePort port = mock(InvestmentProfilePort.class);
		when(port.getInvestmentProfile("M1001")).thenReturn(new InvestmentProfileData(
				InvestmentProfile.NEUTRAL, PensionTarget.FIXED, ContractType.DC, LocalDate.of(2026, 10, 4)));

		assertThat(serviceOn(port, "2026-10-03").getInvestmentChoice("M1001").availableActions())
				.contains(AvailableAction.INVESTMENT_CHOICE);
		assertThat(serviceOn(port, "2026-10-04").getInvestmentChoice("M1001").availableActions())
				.containsExactly(AvailableAction.OTHER_CHOICES);
	}

	@Test
	void retiredMember_hasNoInvestmentChoice() {
		InvestmentProfilePort port = mock(InvestmentProfilePort.class);
		when(port.getInvestmentProfile("M1008")).thenReturn(new InvestmentProfileData(
				InvestmentProfile.NEUTRAL, PensionTarget.FIXED, ContractType.DC, LocalDate.of(2025, 1, 1)));

		assertThat(serviceOn(port, "2026-10-03").getInvestmentChoice("M1008").availableActions())
				.containsExactly(AvailableAction.OTHER_CHOICES);
	}

}
