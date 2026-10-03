package shite.themint.dbdcpension.pensionservice.investmentchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import shite.themint.dbdcpension.pensionservice.investmentprofile.InvestmentChoice;
import shite.themint.dbdcpension.pensionservice.investmentprofile.InvestmentChoiceService;
import shite.themint.dbdcpension.pensionservice.participation.FakeCoreSupport;

/**
 * The whole step 7 in pension-service, with the real Kafka and the real Redis (skipped when either is not running):
 * a profile is really cached (the fake core is asked once), a COMPLETED event arrives on Kafka, the cache entry is
 * gone, and the next request asks the core again. Own topic, so the real topic is never touched.
 */
@SpringBootTest(properties = {
		"spring.security.oauth2.client.registration.core.client-secret=test-secret",
		// These tests are about caching, not timeouts: a cold JVM on a busy machine needs more than the production 300 ms.
		"spring.data.redis.timeout=5s",
		"spring.data.redis.connect-timeout=5s" })
class CompletedChangeEvictsCacheTest {

	private static final String BROKER = "localhost:9094";

	private static final String TOPIC = "test.profilecache." + UUID.randomUUID();

	private static final String DEAD_LETTER_TOPIC = TOPIC + ".dlt";

	private static final String KEY = "pension:investment-profile::M1001";

	private static final FakeCoreSupport fake = new FakeCoreSupport();

	@BeforeAll
	static void kafkaAndRedisMustBeRunning() throws Exception {
		assumeTrue(listening(6380), "Redis is not running on localhost:6380");
		assumeTrue(listening(9094), "Kafka is not running on localhost:9094");
		try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", BROKER))) {
			admin.createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1), new NewTopic(DEAD_LETTER_TOPIC, 3, (short) 1)))
					.all().get();
		}
	}

	private static boolean listening(int port) {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("localhost", port), 500);
			return true;
		}
		catch (Exception ex) {
			return false;
		}
	}

	@AfterAll
	static void stop() {
		fake.stop();
		try (AdminClient admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", BROKER))) {
			admin.deleteTopics(List.of(TOPIC, DEAD_LETTER_TOPIC)).all().get();
		}
		catch (Exception ex) {
			// Kafka was not there: nothing to clean.
		}
	}

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.client.provider.keycloak.token-uri", fake::tokenUri);
		registry.add("core-api.base-url", fake::coreBaseUrl);
		registry.add("pension-service.investment-change.kafka-enabled", () -> "true");
		registry.add("pension-service.investment-change.status-changed-topic", () -> TOPIC);
		registry.add("pension-service.investment-change.group-id", () -> "test-" + UUID.randomUUID());
	}

	@Autowired
	private InvestmentChoiceService service;

	@Autowired
	private StringRedisTemplate redis;

	private static String event(String member, String from, String to) {
		return """
				{"requestId": "%s", "participantId": "%s", "type": "INVESTMENT_CHOICE", "from": "%s", "to": "%s",
				 "reason": null, "occurredAt": "2026-10-03T13:00:00Z"}""".formatted(UUID.randomUUID(), member, from, to);
	}

	private static void send(String key, String value) throws Exception {
		Properties props = new Properties();
		props.put("bootstrap.servers", BROKER);
		props.put("key.serializer", StringSerializer.class.getName());
		props.put("value.serializer", StringSerializer.class.getName());
		try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
			producer.send(new ProducerRecord<>(TOPIC, key, value)).get();
		}
	}

	private void cacheTheProfile() {
		redis.delete(KEY);
		service.getInvestmentChoice("M1001");
		Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> redis.hasKey(KEY));
	}

	private static List<String> deadLetters(int expected) {
		Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BROKER);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		List<String> values = new ArrayList<>();
		try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
			List<TopicPartition> partitions = List.of(new TopicPartition(DEAD_LETTER_TOPIC, 0),
					new TopicPartition(DEAD_LETTER_TOPIC, 1), new TopicPartition(DEAD_LETTER_TOPIC, 2));
			consumer.assign(partitions);
			consumer.seekToBeginning(partitions);
			long deadline = System.currentTimeMillis() + 15_000;
			while (values.size() < expected && System.currentTimeMillis() < deadline) {
				for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
					values.add(record.value());
				}
			}
		}
		return values;
	}

	@Test
	void aCompletedChange_removesTheCachedProfile_soTheNextRequestAsksTheCoreAgain() throws Exception {
		cacheTheProfile();
		int callsBefore = fake.coreCalls.get();
		service.getInvestmentChoice("M1001");
		assertThat(fake.coreCalls.get()).as("served from the cache, the core is not asked").isEqualTo(callsBefore);

		// Other statuses first: they must not remove anything. Then the completion.
		send("M1001", event("M1001", "SUBMITTED", "IN_REVIEW"));
		send("M1001", event("M1001", "APPROVED", "COMPLETED"));

		Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> !redis.hasKey(KEY));
		InvestmentChoice fresh = service.getInvestmentChoice("M1001");
		assertThat(fake.coreCalls.get()).as("the core was asked again").isGreaterThan(callsBefore);
		assertThat(fresh.profile()).isNotNull();
	}

	@Test
	void unreadableEvents_goToTheDeadLetterTopic_andTheCompletionBehindThemStillWorks() throws Exception {
		cacheTheProfile();
		String garbage = "this is not json at all";
		send("M1001", garbage);
		send("M1001", event("M1001", "APPROVED", "COMPLETED"));

		Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> !redis.hasKey(KEY));
		assertThat(deadLetters(1)).contains(garbage);
	}

}
