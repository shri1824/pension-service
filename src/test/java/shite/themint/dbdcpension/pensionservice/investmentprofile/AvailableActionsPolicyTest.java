package shite.themint.dbdcpension.pensionservice.investmentprofile;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import shite.themint.dbdcpension.pensionservice.investmentprofile.AvailableAction;
import shite.themint.dbdcpension.pensionservice.participation.ContractType;

/** The rule for which changes a member may make right now (assumption A1). */
class AvailableActionsPolicyTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 3);

	@Test
	void dcMemberWhoHasNotRetired_mayChangeEverything() {
		assertThat(AvailableActionsPolicy.actionsFor(ContractType.DC, LocalDate.of(2056, 6, 1), TODAY))
				.containsExactly(AvailableAction.INVESTMENT_CHOICE, AvailableAction.RETIREMENT_AGE, AvailableAction.OTHER_CHOICES);
	}

	@Test
	void dbMemberWhoHasNotRetired_hasNoInvestmentChoice_butMayChangeTheRetirementAge() {
		assertThat(AvailableActionsPolicy.actionsFor(ContractType.DB, LocalDate.of(2056, 6, 1), TODAY))
				.containsExactly(AvailableAction.RETIREMENT_AGE, AvailableAction.OTHER_CHOICES);
	}

	@Test
	void dcMemberWhoHasRetired_mayOnlyDoTheOtherChoices() {
		assertThat(AvailableActionsPolicy.actionsFor(ContractType.DC, LocalDate.of(2025, 1, 1), TODAY))
				.containsExactly(AvailableAction.OTHER_CHOICES);
	}

	@Test
	void dbMemberWhoHasRetired_mayOnlyDoTheOtherChoices() {
		assertThat(AvailableActionsPolicy.actionsFor(ContractType.DB, LocalDate.of(2025, 1, 1), TODAY))
				.containsExactly(AvailableAction.OTHER_CHOICES);
	}

	@Test
	void retirementDateTomorrow_isNotRetiredYet() {
		assertThat(AvailableActionsPolicy.actionsFor(ContractType.DC, TODAY.plusDays(1), TODAY))
				.contains(AvailableAction.INVESTMENT_CHOICE, AvailableAction.RETIREMENT_AGE);
	}

	@Test
	void retirementDateToday_countsAsRetired_theDateMustBeStrictlyInTheFuture() {
		assertThat(AvailableActionsPolicy.actionsFor(ContractType.DC, TODAY, TODAY))
				.containsExactly(AvailableAction.OTHER_CHOICES);
	}

	@Test
	void retirementDateYesterday_isRetired() {
		assertThat(AvailableActionsPolicy.actionsFor(ContractType.DC, TODAY.minusDays(1), TODAY))
				.containsExactly(AvailableAction.OTHER_CHOICES);
	}

	@Test
	void otherChoices_areAlwaysThere() {
		for (ContractType type : ContractType.values()) {
			for (LocalDate retires : new LocalDate[] { TODAY.minusYears(5), TODAY, TODAY.plusYears(30) }) {
				assertThat(AvailableActionsPolicy.actionsFor(type, retires, TODAY)).contains(AvailableAction.OTHER_CHOICES);
			}
		}
	}

}
