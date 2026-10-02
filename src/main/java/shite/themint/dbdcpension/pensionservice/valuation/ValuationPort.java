package shite.themint.dbdcpension.pensionservice.valuation;

/**
 * What the rest of the service needs to know about a member's valuation, without saying where it
 * comes from.
 */
public interface ValuationPort {

	/**
	 * @throws shite.themint.dbdcpension.pensionservice.error.ParticipantNotFoundException the member is unknown
	 * @throws shite.themint.dbdcpension.pensionservice.error.CoreUnavailableException     the core could not be reached or failed
	 * @throws shite.themint.dbdcpension.pensionservice.error.CoreResponseException        the core answered with data we cannot use
	 */
	Valuation getValuation(String participantId);

}
