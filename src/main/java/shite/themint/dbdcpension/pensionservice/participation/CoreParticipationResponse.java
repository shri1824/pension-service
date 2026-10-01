package shite.themint.dbdcpension.pensionservice.participation;

import java.time.LocalDate;

/**
 * The core's own JSON for a participation. Package-private on purpose: only the adapter may
 * know the core's field names; everything else works with {@link Participation}.
 */
record CoreParticipationResponse(
		String participantId,
		String schemeCode,
		String employerName,
		String planType,
		LocalDate startDate,
		LocalDate endDate) {
}
