package shite.themint.dbdcpension.pensionservice.participation;

/**
 * What the rest of the service needs, said in our language and without saying where the data
 * comes from. Today the only implementation calls the core's REST API; a read store could
 * replace it later without touching any caller.
 */
public interface ParticipationPort {

	/**
	 * @throws ParticipantNotFoundException        the member has no participation
	 * @throws shite.themint.dbdcpension.pensionservice.error.CoreUnavailableException the core could not be reached or failed
	 * @throws shite.themint.dbdcpension.pensionservice.error.CoreResponseException    the core answered with data we cannot use
	 */
	Participation getParticipation(String participantId);

}
