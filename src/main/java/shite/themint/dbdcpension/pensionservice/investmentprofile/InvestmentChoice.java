package shite.themint.dbdcpension.pensionservice.investmentprofile;

import java.util.List;

/**
 * A member's current investment choice and what they may change, in OUR model (the "Your current
 * situation" card). Only codes: the front end writes the sentences and shows the buttons.
 *
 * @param pensionTarget    FIXED when the member never made a choice (assumption A4)
 * @param availableActions the changes allowed right now, worked out per request from today's date
 */
public record InvestmentChoice(
		InvestmentProfile profile,
		PensionTarget pensionTarget,
		List<AvailableAction> availableActions) {
}
