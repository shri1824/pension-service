package shite.themint.dbdcpension.pensionservice.investmentprofile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import shite.themint.dbdcpension.pensionservice.participation.ContractType;

/**
 * Which changes a member may make right now (assumption A1):
 * <ul>
 * <li>{@code INVESTMENT_CHOICE}: a DC member who has not retired yet</li>
 * <li>{@code RETIREMENT_AGE}: any member who has not retired yet</li>
 * <li>{@code OTHER_CHOICES}: always</li>
 * </ul>
 * "Not retired" means the retirement date is strictly after today. This is business logic, so it lives
 * here and nowhere else: the BFF and the front end only pass on or show the result.
 */
public final class AvailableActionsPolicy {

	private AvailableActionsPolicy() {
	}

	public static List<AvailableAction> actionsFor(ContractType contractType, LocalDate retirementDate, LocalDate today) {
		boolean notRetired = retirementDate.isAfter(today);
		List<AvailableAction> actions = new ArrayList<>();
		if (notRetired && contractType == ContractType.DC) {
			actions.add(AvailableAction.INVESTMENT_CHOICE);
		}
		if (notRetired) {
			actions.add(AvailableAction.RETIREMENT_AGE);
		}
		actions.add(AvailableAction.OTHER_CHOICES);
		return List.copyOf(actions);
	}

}
