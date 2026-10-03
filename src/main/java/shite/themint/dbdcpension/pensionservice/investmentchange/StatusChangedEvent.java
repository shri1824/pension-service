package shite.themint.dbdcpension.pensionservice.investmentchange;

import java.util.Set;

import tools.jackson.databind.json.JsonMapper;

/**
 * The part of the "request moved from A to B" event that this service needs. Unreadable messages are rejected;
 * events about other kinds of request are valid but of no interest here.
 *
 * @param investmentChoice false for any other type of request (for example a future retirement-age change)
 */
record StatusChangedEvent(String participantId, String to, boolean investmentChoice) {

	private static final Set<String> STATUSES = Set.of("SUBMITTED", "IN_REVIEW", "ADVICE_GIVEN", "APPROVED", "REJECTED",
			"COMPLETED");

	private record Raw(String participantId, String type, String to) {
	}

	boolean meansTheChangeIsApplied() {
		return investmentChoice && "COMPLETED".equals(to);
	}

	static StatusChangedEvent parse(JsonMapper json, String payload) {
		Raw raw;
		try {
			raw = json.readValue(payload, Raw.class);
		}
		catch (RuntimeException ex) {
			throw new MalformedEventException("Not valid JSON", ex);
		}
		if (raw == null) {
			throw new MalformedEventException("Empty message");
		}
		if (raw.participantId() == null || raw.participantId().isBlank()) {
			throw new MalformedEventException("No participantId");
		}
		if (raw.to() == null || !STATUSES.contains(raw.to())) {
			throw new MalformedEventException("Unknown status: " + raw.to());
		}
		if (raw.type() == null || raw.type().isBlank()) {
			throw new MalformedEventException("No type");
		}
		return new StatusChangedEvent(raw.participantId(), raw.to(), "INVESTMENT_CHOICE".equals(raw.type()));
	}

}
