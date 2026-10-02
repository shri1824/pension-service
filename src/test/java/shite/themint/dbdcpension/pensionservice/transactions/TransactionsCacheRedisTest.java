package shite.themint.dbdcpension.pensionservice.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import shite.themint.dbdcpension.pensionservice.participation.FakeCoreSupport;

/**
 * Against the real Redis from docker-compose (localhost:6380). Skipped, not failed, when Redis is not
 * running, so the build does not depend on Docker. Proves that a page is cached per member, page and
 * size, and that negative (signed) amounts survive the round trip through Redis.
 */
@SpringBootTest(properties = {
		"spring.security.oauth2.client.registration.core.client-secret=test-secret",
		"pension-cache.transactions-ttl=4m",
		// These tests are about caching, not timeouts: a cold JVM on a busy machine needs more than the production 300 ms.
		"spring.data.redis.timeout=5s",
		"spring.data.redis.connect-timeout=5s" })
class TransactionsCacheRedisTest {

	private static final String KEY_PAGE_0 = "pension:transactions::M1001:0:10";
	private static final String KEY_PAGE_1 = "pension:transactions::M1001:1:10";

	private static final FakeCoreSupport fake = new FakeCoreSupport();

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.client.provider.keycloak.token-uri", fake::tokenUri);
		registry.add("core-api.base-url", fake::coreBaseUrl);
	}

	@Autowired
	private TransactionsPort port;

	@Autowired
	private StringRedisTemplate redis;

	@BeforeAll
	static void redisMustBeRunning() {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("localhost", 6380), 500);
		}
		catch (Exception ex) {
			assumeTrue(false, "Redis is not running on localhost:6380");
		}
	}

	@AfterAll
	static void stop() {
		fake.stop();
	}

	/**
	 * The cache write can reach Redis a moment after the call that caused it has returned. Waiting for
	 * it makes the test about the cache, not about timing.
	 */
	private void waitUntilStored(String key) {
		org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> redis.hasKey(key));
	}

	@Test
	void aPageIsCachedPerMemberPageAndSize_withReadableJsonAndTheTransactionsTtl() {
		redis.delete(KEY_PAGE_0);
		redis.delete(KEY_PAGE_1);

		TransactionList first = port.getTransactions("M1001", 0, 10);
		waitUntilStored(KEY_PAGE_0);
		TransactionList second = port.getTransactions("M1001", 0, 10);

		assertThat(second).isEqualTo(first);
		assertThat(second.items().get(0).amount()).isEqualByComparingTo("-862.46");
		assertThat(fake.coreCalls.get()).as("calls that reached the core").isEqualTo(1);

		// Another page of the same member is another entry, so it must reach the core.
		port.getTransactions("M1001", 1, 10);
		waitUntilStored(KEY_PAGE_1);
		assertThat(fake.coreCalls.get()).as("after asking page 1").isEqualTo(2);

		assertThat(redis.opsForValue().get(KEY_PAGE_0))
				.contains("\"transactionId\":\"T20260914-001\"")
				.contains("\"type\":\"INVESTMENT_WITHDRAWAL\"")
				.contains("\"hasMore\":true")
				.contains("-862.46")
				.doesNotContain("class");
		assertThat(redis.getExpire(KEY_PAGE_0)).isBetween(Duration.ofMinutes(2).toSeconds(), Duration.ofMinutes(4).toSeconds());

		redis.delete(KEY_PAGE_0);
		redis.delete(KEY_PAGE_1);
	}

}
