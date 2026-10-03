package shite.themint.dbdcpension.pensionservice.investmentchange;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * What happens when an event cannot be handled (decision D9, dead-letter topic):
 * <ul>
 * <li>an unreadable event is parked at once on {@code <topic>.dlt}, same partition number;</li>
 * <li>any other failure (for example Redis is down) is tried three more times, one second apart, and then parked
 * the same way. Waiting a little helps for a short Redis outage and costs nothing for the others.</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "pension-service.investment-change.kafka-enabled", havingValue = "true",
		matchIfMissing = true)
class InvestmentChangeKafkaConfig {

	@Bean
	CommonErrorHandler investmentChangeErrorHandler(KafkaTemplate<String, String> kafka) {
		DeadLetterPublishingRecoverer toDeadLetterTopic = new DeadLetterPublishingRecoverer(kafka,
				(record, failure) -> new TopicPartition(record.topic() + ".dlt", record.partition()));
		DefaultErrorHandler handler = new DefaultErrorHandler(toDeadLetterTopic, new FixedBackOff(1000L, 3));
		handler.addNotRetryableExceptions(MalformedEventException.class);
		return handler;
	}

}
