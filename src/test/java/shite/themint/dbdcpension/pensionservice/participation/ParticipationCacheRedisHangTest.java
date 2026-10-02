package shite.themint.dbdcpension.pensionservice.participation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Redis accepts the connection but never answers (what a stopped or overloaded Redis looks like
 * to a client that was already connected). Without short Redis timeouts every request would wait
 * for the 60 s default; with them the cache is skipped and the core answers.
 */
@SpringBootTest(properties = "spring.security.oauth2.client.registration.core.client-secret=test-secret")
class ParticipationCacheRedisHangTest {

	private static final FakeCoreSupport fake = new FakeCoreSupport();
	private static final ServerSocket blackHole = openBlackHole();
	private static final List<Socket> held = new CopyOnWriteArrayList<>();

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.client.provider.keycloak.token-uri", fake::tokenUri);
		registry.add("core-api.base-url", fake::coreBaseUrl);
		registry.add("spring.data.redis.port", () -> blackHole.getLocalPort());
	}

	@Autowired
	private ParticipationPort port;

	@AfterAll
	static void stop() throws IOException {
		fake.stop();
		blackHole.close();
		for (Socket socket : held) {
			socket.close();
		}
	}

	@Test
	void redisThatNeverAnswers_doesNotHoldTheRequest() {
		long started = System.nanoTime();

		Participation result = port.getParticipation("M1001");

		Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
		assertThat(result.schemeId()).isEqualTo("SCH-001");
		assertThat(fake.coreCalls.get()).isEqualTo(1);
		assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
	}

	private static ServerSocket openBlackHole() {
		try {
			ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
			Thread acceptor = new Thread(() -> {
				while (!server.isClosed()) {
					try {
						held.add(server.accept()); // accept, then say nothing
					}
					catch (IOException ex) {
						return;
					}
				}
			});
			acceptor.setDaemon(true);
			acceptor.start();
			return server;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
