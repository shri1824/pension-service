package shite.themint.dbdcpension.pensionservice.investmentprofile;

import java.time.LocalDate;

/**
 * The core's investment profile JSON, with the core's own names and codes. Package-private on purpose:
 * only the adapter may know it, so a change in the core touches this package and nothing else.
 */
record CoreInvestmentProfileResponse(
		String participantId,
		String riskProfile,
		String payoutPreference,
		String planType,
		LocalDate retirementDate) {
}
