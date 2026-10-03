package shite.themint.dbdcpension.pensionservice.investmentchange;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the back office's status changes. When an investment-choice request is COMPLETED, the member's cached
 * profile is evicted. The offset is committed only after this method returned, so a crash means "read again",
 * which is safe because evicting twice does no harm.
 */
@Component
@ConditionalOnProperty(name = "pension-service.investment-change.kafka-enabled", havingValue = "true",
		matchIfMissing = true)
class ProfileCacheListener {

	private static final Logger log = LoggerFactory.getLogger(ProfileCacheListener.class);

	private final ProfileCacheEviction eviction;

	private final JsonMapper json;

	ProfileCacheListener(ProfileCacheEviction eviction, JsonMapper json) {
		this.eviction = eviction;
		this.json = json;
	}

	@KafkaListener(topics = "${pension-service.investment-change.status-changed-topic}",
			groupId = "${pension-service.investment-change.group-id}")
	void onStatusChanged(ConsumerRecord<String, String> record) {
		StatusChangedEvent event = StatusChangedEvent.parse(json, record.value());
		if (event.meansTheChangeIsApplied()) {
			eviction.evict(event.participantId());
			log.info("Investment change completed for member {}: cached profile evicted", event.participantId());
		}
	}

}
