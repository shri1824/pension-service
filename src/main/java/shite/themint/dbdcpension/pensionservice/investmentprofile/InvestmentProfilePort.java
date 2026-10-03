package shite.themint.dbdcpension.pensionservice.investmentprofile;

/**
 * What the rest of the service needs to know about a member's investment choice, without saying where it
 * comes from.
 */
public interface InvestmentProfilePort {

	/**
	 * @throws shite.themint.dbdcpension.pensionservice.error.ParticipantNotFoundException the member is unknown
	 * @throws shite.themint.dbdcpension.pensionservice.error.CoreUnavailableException     the core could not be reached or failed
	 * @throws shite.themint.dbdcpension.pensionservice.error.CoreResponseException        the core answered with data we cannot use
	 */
	InvestmentProfileData getInvestmentProfile(String participantId);

}
