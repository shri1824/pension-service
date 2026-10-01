package shite.themint.dbdcpension.pensionservice.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

class CoreClientSecretCheckTest {

	private static ClientRegistration registrationWithSecret(String secret) {
		return ClientRegistration.withRegistrationId("core")
				.clientId("pension-service")
				.clientSecret(secret)
				.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
				.tokenUri("http://localhost/token")
				.build();
	}

	@Test
	void unresolvedPlaceholder_isRejected() {
		assertThatThrownBy(() -> CoreClientConfig.requireClientSecret(registrationWithSecret("${PENSION_SERVICE_CLIENT_SECRET}")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("PENSION_SERVICE_CLIENT_SECRET");
	}

	@Test
	void blankSecret_isRejected() {
		assertThatThrownBy(() -> CoreClientConfig.requireClientSecret(registrationWithSecret(" ")))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void missingRegistration_isRejected() {
		assertThatThrownBy(() -> CoreClientConfig.requireClientSecret(null))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void realSecret_isAccepted() {
		assertThatCode(() -> CoreClientConfig.requireClientSecret(registrationWithSecret("a-real-secret")))
				.doesNotThrowAnyException();
	}

}
