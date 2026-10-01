package shite.themint.dbdcpension.pensionservice.participation;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Two tiny local HTTP servers playing Keycloak's token endpoint and the core, counting the core calls. */
final class FakeCoreSupport {

	final AtomicInteger coreCalls = new AtomicInteger();
	private final HttpServer tokenServer = start("/token",
			x -> respond(x, "{\"access_token\":\"fake-token\",\"token_type\":\"Bearer\",\"expires_in\":300}"));
	private final HttpServer coreServer = start("/core", x -> {
		coreCalls.incrementAndGet();
		respond(x, """
				{"participantId":"M1001","schemeCode":"SCH-001","employerName":"Example Employer B.V.",
				 "planType":"DC","startDate":"2019-09-01","endDate":null}""");
	});

	String tokenUri() {
		return "http://localhost:" + tokenServer.getAddress().getPort() + "/token";
	}

	String coreBaseUrl() {
		return "http://localhost:" + coreServer.getAddress().getPort();
	}

	void stop() {
		tokenServer.stop(0);
		coreServer.stop(0);
	}

	private static HttpServer start(String path, Handler handler) {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			server.createContext(path, handler::handle);
			server.start();
			return server;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void respond(HttpExchange exchange, String json) throws IOException {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, body.length);
		exchange.getResponseBody().write(body);
		exchange.close();
	}

	private interface Handler {
		void handle(HttpExchange exchange) throws IOException;
	}

}
