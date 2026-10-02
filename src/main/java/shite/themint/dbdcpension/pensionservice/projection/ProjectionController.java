package shite.themint.dbdcpension.pensionservice.projection;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Pattern;

/** Internal API, called only by portal-bff with a service token (see ParticipationController). */
@RestController
@RequestMapping("/internal/v1/participants")
public class ProjectionController {

	private final ProjectionPort projectionPort;

	public ProjectionController(ProjectionPort projectionPort) {
		this.projectionPort = projectionPort;
	}

	@GetMapping("/{participantId}/projection")
	public Projection getProjection(
			// Defence in depth: only plain ids reach the core, whatever the caller sends.
			@PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,32}$") String participantId) {
		return projectionPort.getProjection(participantId);
	}

}
