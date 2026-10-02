package shite.themint.dbdcpension.pensionservice.holdings;

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
 * running, so the build does not depend on Docker. Proves that the fund list, with its six-decimal
 * units and the core's own totals, survives the round trip through Redis.
 */
@SpringBootTest(properties = {
		"spring.security.oauth2.client.registration.core.client-secret=test-secret",
		"pension-cache.holdings-ttl=100m",
		// These tests are about caching, not timeouts: a cold JVM on a busy machine needs more than the production 300 ms.
		"spring.data.redis.timeout=5s",
		"spring.data.redis.connect-timeout=5s" })
class HoldingsCacheRedisTest {

	private static final String KEY = "pension:holdings::M1001";

	private static final FakeCoreSupport fake = new FakeCoreSupport();

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.client.provider.keycloak.token-uri", fake::tokenUri);
		registry.add("core-api.base-url", fake::coreBaseUrl);
	}

	@Autowired
	private HoldingsPort port;

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
	private void waitUntilStored() {
		org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> redis.hasKey(KEY));
	}

	@Test
	void secondCallIsServedFromRedis_asReadableJson_withTheHoldingsTtl() {
		redis.delete(KEY);

		Holdings first = port.getHoldings("M1001");
		waitUntilStored();
		Holdings second = port.getHoldings("M1001");

		assertThat(second).isEqualTo(first);
		assertThat(second.funds().get(0).units()).isEqualByComparingTo("195.228665");
		assertThat(second.funds().get(0).totalValue()).isEqualByComparingTo("26197.32");
		assertThat(fake.coreCalls.get()).as("calls that reached the core").isEqualTo(1);

		assertThat(redis.opsForValue().get(KEY))
				.contains("\"fundId\":\"FUND-MIX-N\"")
				.contains("\"priceDate\":\"2026-09-24\"")
				.doesNotContain("class");
		assertThat(redis.getExpire(KEY)).isBetween(Duration.ofMinutes(60).toSeconds(), Duration.ofMinutes(100).toSeconds());

		redis.delete(KEY);
	}

}
