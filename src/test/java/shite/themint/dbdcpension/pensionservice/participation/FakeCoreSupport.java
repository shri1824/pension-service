package shite.themint.dbdcpension.pensionservice.participation;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Two tiny local HTTP servers playing Keycloak's token endpoint and the core, counting the core calls. */
public final class FakeCoreSupport {

	public final AtomicInteger coreCalls = new AtomicInteger();
	private final HttpServer tokenServer = start("/token",
			x -> respond(x, "{\"access_token\":\"fake-token\",\"token_type\":\"Bearer\",\"expires_in\":300}"));
	private final HttpServer coreServer = start("/core", x -> {
		coreCalls.incrementAndGet();
		if (x.getRequestURI().getPath().endsWith("/investment-profile")) {
			respond(x, """
					{"participantId":"M1001","riskProfile":"NEUTRAL","payoutPreference":"FIXED_ANNUITY","planType":"DC",
					 "retirementDate":"2056-06-01"}""");
			return;
		}
		if (x.getRequestURI().getPath().contains("/transactions/")) {
			respond(x, """
					{"transactionNumber":"T20260914-001","transactionDate":"2026-09-14","transactionKind":"INVESTMENT_WITHDRAWAL",
					 "currencyCode":"EUR","amount":-862.46,
					 "lines":[{"lineCode":"DEFINED_CONTRIBUTION","amount":-862.51},{"lineCode":"BONUS","amount":-0.01},
					          {"lineCode":"ADMINISTRATION_COSTS","amount":0.04}],
					 "fundMovements":[{"fundCode":"FUND-MIX-N","fundName":"Example Mixed Fund Neutral","unitCount":-6.502952,
					                   "priceDate":"2026-09-14","unitPrice":132.63,"movementValue":-862.46}]}""");
			return;
		}
		if (x.getRequestURI().getPath().endsWith("/transactions")) {
			respond(x, """
					{"participantId":"M1001","currencyCode":"EUR","page":0,"pageSize":10,"totalCount":14,"transactions":[
					 {"transactionNumber":"T20260914-001","transactionDate":"2026-09-14",
					  "transactionKind":"INVESTMENT_WITHDRAWAL","amount":-862.46}]}""");
			return;
		}
		if (x.getRequestURI().getPath().endsWith("/holdings")) {
			respond(x, """
					{"participantId":"M1001","currencyCode":"EUR","positions":[
					 {"fundCode":"FUND-MIX-N","fundName":"Example Mixed Fund Neutral","unitCount":195.228665,
					  "priceDate":"2026-09-24","unitPrice":134.19,"positionValue":26197.32}]}""");
			return;
		}
		if (x.getRequestURI().getPath().endsWith("/valuation")) {
			respond(x, """
					{"participantId":"M1001","currencyCode":"EUR","premiumsPaidTotal":20769.28,
					 "marketValue":25992.60,"valuationDate":"2026-09-24"}""");
			return;
		}
		if (x.getRequestURI().getPath().endsWith("/projection")) {
			respond(x, """
					{"participantId":"M1001","pensionAge":68,"curr":"EUR","calculationDate":"2026-09-30",
					 "middleScenario":369.42,"lowScenario":117.10,"highScenario":881.55}""");
			return;
		}
		respond(x, """
				{"participantId":"M1001","schemeCode":"SCH-001","employerName":"Example Employer B.V.",
				 "planType":"DC","startDate":"2019-09-01","endDate":null}""");
	});

	public String tokenUri() {
		return "http://localhost:" + tokenServer.getAddress().getPort() + "/token";
	}

	public String coreBaseUrl() {
		return "http://localhost:" + coreServer.getAddress().getPort();
	}

	public void stop() {
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
