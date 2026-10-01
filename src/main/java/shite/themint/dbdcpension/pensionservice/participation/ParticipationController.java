package shite.themint.dbdcpension.pensionservice.participation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Pattern;

/**
 * Internal API, called only by portal-bff with a service token. The member id is in the path
 * here because the BFF has already taken it from the validated user token; a member can never
 * reach this service directly.
 */
@RestController
@RequestMapping("/internal/v1/participants")
public class ParticipationController {

	private final ParticipationPort participationPort;

	public ParticipationController(ParticipationPort participationPort) {
		this.participationPort = participationPort;
	}

	@GetMapping("/{participantId}/participation")
	public Participation getParticipation(
			// Defence in depth: only plain ids reach the core, whatever the caller sends.
			@PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,32}$") String participantId) {
		return participationPort.getParticipation(participantId);
	}

}
