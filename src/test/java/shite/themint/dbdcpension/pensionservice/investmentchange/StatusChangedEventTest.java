package shite.themint.dbdcpension.pensionservice.investmentchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;

/** What this service reads from the status-changed topic, with the exact JSON core-mock writes. */
class StatusChangedEventTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static String json(String type, String member, String to) {
		return """
				{"requestId": "b76f406a-2670-4556-b6b2-b69d108aa888", "participantId": %s, "type": %s, "from": "APPROVED",
				 "to": %s, "reason": null, "occurredAt": "2026-10-03T13:00:00Z"}"""
				.formatted(member == null ? "null" : "\"" + member + "\"", type == null ? "null" : "\"" + type + "\"",
						to == null ? "null" : "\"" + to + "\"");
	}

	@Test
	void completedInvestmentChange_meansTheChangeIsApplied() {
		StatusChangedEvent event = StatusChangedEvent.parse(JSON, json("INVESTMENT_CHOICE", "M1007", "COMPLETED"));

		assertThat(event.participantId()).isEqualTo("M1007");
		assertThat(event.meansTheChangeIsApplied()).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "SUBMITTED", "IN_REVIEW", "ADVICE_GIVEN", "APPROVED", "REJECTED" })
	void everyOtherStatus_isNotAnApplication(String status) {
		assertThat(StatusChangedEvent.parse(JSON, json("INVESTMENT_CHOICE", "M1007", status)).meansTheChangeIsApplied())
				.isFalse();
	}

	@Test
	void anotherKindOfRequest_isValidButNotOurs() {
		StatusChangedEvent event = StatusChangedEvent.parse(JSON, json("RETIREMENT_AGE", "M1007", "COMPLETED"));

		assertThat(event.meansTheChangeIsApplied()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "not json", "[]", "{}", "{\"participantId\": 5" })
	void unreadableMessages_areRejected(String payload) {
		assertThatThrownBy(() -> StatusChangedEvent.parse(JSON, payload)).isInstanceOf(MalformedEventException.class);
	}

	@Test
	void missingOrUnknownParts_areRejected_notGuessed() {
		assertThatThrownBy(() -> StatusChangedEvent.parse(JSON, json("INVESTMENT_CHOICE", null, "COMPLETED")))
				.isInstanceOf(MalformedEventException.class);
		assertThatThrownBy(() -> StatusChangedEvent.parse(JSON, json("INVESTMENT_CHOICE", " ", "COMPLETED")))
				.isInstanceOf(MalformedEventException.class);
		assertThatThrownBy(() -> StatusChangedEvent.parse(JSON, json("INVESTMENT_CHOICE", "M1", null)))
				.isInstanceOf(MalformedEventException.class);
		assertThatThrownBy(() -> StatusChangedEvent.parse(JSON, json("INVESTMENT_CHOICE", "M1", "DONE")))
				.isInstanceOf(MalformedEventException.class);
		assertThatThrownBy(() -> StatusChangedEvent.parse(JSON, json(null, "M1", "COMPLETED")))
				.isInstanceOf(MalformedEventException.class);
	}

}
