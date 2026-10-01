package shite.themint.dbdcpension.pensionservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Proves the token flow without Keycloak or the real core: two tiny local HTTP servers play the
 * token endpoint and the core. The RestClient must fetch a client-credentials token ONCE,
 * send it as a Bearer token on every call, and authenticate itself to the token endpoint.
 */
@SpringBootTest(properties = "spring.security.oauth2.client.registration.core.client-secret=test-secret")
class CoreClientTokenTest {

	private static final List<String> tokenRequestAuth = new CopyOnWriteArrayList<>();
	private static final List<String> tokenRequestBody = new CopyOnWriteArrayList<>();
	private static final List<String> coreRequestAuth = new CopyOnWriteArrayList<>();

	private static final HttpServer tokenServer = startServer("/token", exchange -> {
		tokenRequestAuth.add(exchange.getRequestHeaders().getFirst("Authorization"));
		tokenRequestBody.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
		respond(exchange, "{\"access_token\":\"fake-token\",\"token_type\":\"Bearer\",\"expires_in\":300}");
	});

	private static final HttpServer coreServer = startServer("/ping", exchange -> {
		coreRequestAuth.add(exchange.getRequestHeaders().getFirst("Authorization"));
		respond(exchange, "{}");
	});

	@DynamicPropertySource
	static void fakeServers(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.client.provider.keycloak.token-uri",
				() -> "http://localhost:" + tokenServer.getAddress().getPort() + "/token");
		registry.add("core-api.base-url", () -> "http://localhost:" + coreServer.getAddress().getPort());
	}

	@Autowired
	private RestClient coreRestClient;

	@AfterAll
	static void stopServers() {
		tokenServer.stop(0);
		coreServer.stop(0);
	}

	@Test
	void fetchesOneToken_andSendsItOnEveryCall() {
		coreRestClient.get().uri("/ping").retrieve().toBodilessEntity();
		coreRestClient.get().uri("/ping").retrieve().toBodilessEntity();

		assertThat(coreRequestAuth).containsExactly("Bearer fake-token", "Bearer fake-token");
		assertThat(tokenRequestBody).hasSize(1);
		assertThat(tokenRequestBody.get(0)).contains("grant_type=client_credentials").contains("scope=core.read");
		assertThat(tokenRequestAuth.get(0)).isEqualTo(
				"Basic " + Base64.getEncoder().encodeToString("pension-service:test-secret".getBytes(StandardCharsets.UTF_8)));
	}

	private interface Handler {
		void handle(HttpExchange exchange) throws IOException;
	}

	private static HttpServer startServer(String path, Handler handler) {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			server.createContext(path, handler::handle);
			server.start();
			return server;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not start the fake server for " + path, ex);
		}
	}

	private static void respond(HttpExchange exchange, String json) throws IOException {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

}
