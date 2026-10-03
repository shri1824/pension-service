package shite.themint.dbdcpension.pensionservice.investmentprofile;

import java.time.LocalDate;

import shite.themint.dbdcpension.pensionservice.participation.ContractType;

/**
 * What the core knows about a member's investment choice, in OUR model. This is what is cached: it does
 * not depend on today's date, so a cached entry can never be wrong when a retirement date passes. The
 * available actions are worked out from it on every request ({@link AvailableActionsPolicy}).
 *
 * @param pensionTarget  never null: FIXED when the member never made a choice (assumption A4)
 * @param retirementDate the day the member retires, as the core knows it
 */
public record InvestmentProfileData(
		InvestmentProfile profile,
		PensionTarget pensionTarget,
		ContractType contractType,
		LocalDate retirementDate) {
}
