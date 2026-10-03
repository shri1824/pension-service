package shite.themint.dbdcpension.pensionservice.investmentprofile;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

/**
 * A member's investment choice and what they may change. The core's data comes from the (cached)
 * port; the available actions are worked out HERE, on every request, from today's date, so a cached
 * entry never shows a button for a member who retired in the meantime.
 */
@Service
public class InvestmentChoiceService {

	private final InvestmentProfilePort investmentProfilePort;
	private final Clock clock;

	public InvestmentChoiceService(InvestmentProfilePort investmentProfilePort, Clock clock) {
		this.investmentProfilePort = investmentProfilePort;
		this.clock = clock;
	}

	public InvestmentChoice getInvestmentChoice(String participantId) {
		InvestmentProfileData data = investmentProfilePort.getInvestmentProfile(participantId);
		return new InvestmentChoice(
				data.profile(),
				data.pensionTarget(),
				AvailableActionsPolicy.actionsFor(data.contractType(), data.retirementDate(), LocalDate.now(clock)));
	}

}
