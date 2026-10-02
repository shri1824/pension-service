package shite.themint.dbdcpension.pensionservice.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The core's projection JSON, with the core's own names. Package-private on purpose: only the
 * adapter may know it, so a change in the core touches this package and nothing else.
 */
record CoreProjectionResponse(
		String participantId,
		Integer pensionAge,
		String curr,
		LocalDate calculationDate,
		BigDecimal middleScenario,
		BigDecimal lowScenario,
		BigDecimal highScenario) {
}
