package shite.themint.dbdcpension.pensionservice.investmentprofile;

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
 * running, so the build does not depend on Docker. Proves that only what does NOT depend on today's date
 * is cached (the retirement date, not the available actions), and that the actions are still worked out
 * on every request.
 */
@SpringBootTest(properties = {
		"spring.security.oauth2.client.registration.core.client-secret=test-secret",
		"pension-cache.investment-profile-ttl=50m",
		// These tests are about caching, not timeouts: a cold JVM on a busy machine needs more than the production 300 ms.
		"spring.data.redis.timeout=5s",
		"spring.data.redis.connect-timeout=5s" })
class InvestmentProfileCacheRedisTest {

	private static final String KEY = "pension:investment-profile::M1001";

	private static final FakeCoreSupport fake = new FakeCoreSupport();

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.client.provider.keycloak.token-uri", fake::tokenUri);
		registry.add("core-api.base-url", fake::coreBaseUrl);
	}

	@Autowired
	private InvestmentChoiceService service;

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
	void onlyTheDateIndependentDataIsCached_andTheActionsAreStillComputedPerRequest() {
		redis.delete(KEY);

		InvestmentChoice first = service.getInvestmentChoice("M1001");
		waitUntilStored();
		InvestmentChoice second = service.getInvestmentChoice("M1001");

		assertThat(second).isEqualTo(first);
		assertThat(second.profile()).isEqualTo(InvestmentProfile.NEUTRAL);
		assertThat(second.availableActions()).contains(AvailableAction.INVESTMENT_CHOICE);
		assertThat(fake.coreCalls.get()).as("calls that reached the core").isEqualTo(1);

		assertThat(redis.opsForValue().get(KEY))
				.contains("\"profile\":\"NEUTRAL\"")
				.contains("\"pensionTarget\":\"FIXED\"")
				.contains("\"contractType\":\"DC\"")
				.contains("\"retirementDate\":\"2056-06-01\"")
				.doesNotContain("availableActions")
				.doesNotContain("class");
		assertThat(redis.getExpire(KEY)).isBetween(Duration.ofMinutes(30).toSeconds(), Duration.ofMinutes(50).toSeconds());

		redis.delete(KEY);
	}

}
