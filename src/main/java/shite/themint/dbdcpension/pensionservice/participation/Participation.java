package shite.themint.dbdcpension.pensionservice.participation;

import java.time.LocalDate;

/**
 * A member's participation in a pension scheme, in OUR model. Nothing outside the adapter
 * ever sees the core's own names (schemeCode, planType, startDate, endDate).
 *
 * @param participatedUntil null while the member still participates
 */
public record Participation(
		String schemeId,
		String employerName,
		ContractType contractType,
		LocalDate participatedFrom,
		LocalDate participatedUntil) {
}
