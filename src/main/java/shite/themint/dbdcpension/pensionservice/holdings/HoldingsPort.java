package shite.themint.dbdcpension.pensionservice.holdings;

/**
 * What the rest of the service needs to know about the funds a member holds, without saying where it
 * comes from.
 */
public interface HoldingsPort {

	/**
	 * @throws shite.themint.dbdcpension.pensionservice.error.ParticipantNotFoundException the member is unknown
	 * @throws shite.themint.dbdcpension.pensionservice.error.CoreUnavailableException     the core could not be reached or failed
	 * @throws shite.themint.dbdcpension.pensionservice.error.CoreResponseException        the core answered with data we cannot use
	 */
	Holdings getHoldings(String participantId);

}
