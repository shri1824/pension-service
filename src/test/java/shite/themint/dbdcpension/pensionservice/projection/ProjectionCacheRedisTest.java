package shite.themint.dbdcpension.pensionservice.projection;

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
 * Against the real Redis from docker-compose (localhost:6380). Skipped, not failed, when Redis
 * is not running, so the build does not depend on Docker. Proves that the projection survives
 * the round trip through Redis with its cents, dates and codes intact.
 */
@SpringBootTest(properties = {
		"spring.security.oauth2.client.registration.core.client-secret=test-secret",
		"pension-cache.projection-ttl=3h",
		// These tests are about caching, not timeouts: a cold JVM on a busy machine needs more than the production 300 ms.
		"spring.data.redis.timeout=5s",
		"spring.data.redis.connect-timeout=5s" })
class ProjectionCacheRedisTest {

	private static final String KEY = "pension:projection::M1001";

	private static final FakeCoreSupport fake = new FakeCoreSupport();

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.client.provider.keycloak.token-uri", fake::tokenUri);
		registry.add("core-api.base-url", fake::coreBaseUrl);
	}

	@Autowired
	private ProjectionPort port;

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
	 * The cache write can reach Redis a moment after the call that caused it has returned (seen with the
	 * Redis MONITOR on a busy machine). Waiting for it makes the test about the cache, not about timing.
	 */
	private void waitUntilStored() {
		org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> redis.hasKey(KEY));
	}

	@Test
	void secondCallIsServedFromRedis_asReadableJson_withTheProjectionTtl() {
		redis.delete(KEY);

		Projection first = port.getProjection("M1001");
		waitUntilStored();
		Projection second = port.getProjection("M1001");

		assertThat(second).isEqualTo(first);
		assertThat(second.scenarios().get(0).grossMonthlyAmount()).isEqualByComparingTo("369.42");
		assertThat(fake.coreCalls.get()).as("calls that reached the core").isEqualTo(1);

		assertThat(redis.opsForValue().get(KEY))
				.contains("\"retirementAge\":68")
				.contains("\"amountBasis\":\"TODAYS_PRICES\"")
				.contains("\"calculatedOn\":\"2026-09-30\"")
				.contains("\"type\":\"EXPECTED\"")
				.doesNotContain("class");
		assertThat(redis.getExpire(KEY)).isBetween(Duration.ofHours(2).toSeconds(), Duration.ofHours(3).toSeconds());

		redis.delete(KEY);
	}

}
