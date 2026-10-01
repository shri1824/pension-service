package shite.themint.dbdcpension.pensionservice.participation;

/**
 * The member has no participation. Becomes a 404 problem detail at the API.
 */
public class ParticipantNotFoundException extends RuntimeException {

	public ParticipantNotFoundException() {
		super("Participant not found");
	}

}
