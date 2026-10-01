package shite.themint.dbdcpension.pensionservice.participation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Redis is unreachable: the cache must be invisible, and every call must still be answered by the core. */
@SpringBootTest(properties = {
		"spring.security.oauth2.client.registration.core.client-secret=test-secret",
		"spring.data.redis.port=1" })
class ParticipationCacheRedisDownTest {

	private static final FakeCoreSupport fake = new FakeCoreSupport();

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.client.provider.keycloak.token-uri", fake::tokenUri);
		registry.add("core-api.base-url", fake::coreBaseUrl);
	}

	@Autowired
	private ParticipationPort port;

	@AfterAll
	static void stop() {
		fake.stop();
	}

	@Test
	void redisDown_requestsStillWork_andGoToTheCoreEveryTime() {
		assertThat(port.getParticipation("M1001").schemeId()).isEqualTo("SCH-001");
		assertThat(port.getParticipation("M1001").schemeId()).isEqualTo("SCH-001");

		assertThat(fake.coreCalls.get()).isEqualTo(2);
	}

}
