package shite.themint.dbdcpension.pensionservice.participation;

import static org.assertj.core.api.Assertions.assertThat;

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

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Against the real Redis from docker-compose (localhost:6380). Skipped, not failed, when Redis
 * is not running, so the build does not depend on Docker.
 */
@SpringBootTest(properties = {
		"spring.security.oauth2.client.registration.core.client-secret=test-secret",
		"pension-cache.participation-ttl=2m" })
class ParticipationCacheRedisTest {

	private static final String KEY = "pension:participation::M1001";

	private static final FakeCoreSupport fake = new FakeCoreSupport();

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.client.provider.keycloak.token-uri", fake::tokenUri);
		registry.add("core-api.base-url", fake::coreBaseUrl);
	}

	@Autowired
	private ParticipationPort port;

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

	@Test
	void secondCallIsServedFromRedis_asReadableJson_withATtl() {
		redis.delete(KEY);

		Participation first = port.getParticipation("M1001");
		Participation second = port.getParticipation("M1001");

		assertThat(second).isEqualTo(first);
		assertThat(fake.coreCalls.get()).isEqualTo(1);

		assertThat(redis.opsForValue().get(KEY))
				.contains("\"schemeId\":\"SCH-001\"")
				.contains("\"participatedFrom\":\"2019-09-01\"")
				.doesNotContain("class");
		assertThat(redis.getExpire(KEY)).isBetween(1L, Duration.ofMinutes(2).toSeconds());

		redis.delete(KEY);
	}

}
