package shite.themint.dbdcpension.pensionservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * The RestClient used to call the core. It gets a client-credentials token for the
 * registration {@code core} (see application.yaml), caches it until it expires, and adds it
 * to every request. Timeouts come from {@code spring.http.clients.*}.
 */
@Configuration
@EnableConfigurationProperties(CoreApiProperties.class)
public class CoreClientConfig {

	/** Name of the OAuth2 client registration in application.yaml. */
	static final String CORE_REGISTRATION = "core";

	/**
	 * The token is always requested for this service itself, never for the caller. Without a fixed
	 * principal, the cached token would depend on whoever is logged in on the current thread
	 * (and on no one at all on a background thread).
	 */
	private static final Authentication SERVICE_PRINCIPAL = new AnonymousAuthenticationToken(
			"pension-service", "pension-service", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

	/** Not tied to an HTTP request, so it also works on background threads. */
	@Bean
	OAuth2AuthorizedClientManager coreAuthorizedClientManager(ClientRegistrationRepository registrations,
			OAuth2AuthorizedClientService authorizedClients) {
		requireClientSecret(registrations.findByRegistrationId(CORE_REGISTRATION));
		AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
				new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
		manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
		return manager;
	}

	@Bean
	RestClient coreRestClient(RestClient.Builder builder, CoreApiProperties properties,
			OAuth2AuthorizedClientManager authorizedClientManager, OAuth2AuthorizedClientService authorizedClients) {
		OAuth2ClientHttpRequestInterceptor tokenInterceptor = new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
		tokenInterceptor.setClientRegistrationIdResolver(request -> CORE_REGISTRATION);
		tokenInterceptor.setPrincipalResolver(request -> SERVICE_PRINCIPAL);
		// If the core rejects the token, forget it so the next call fetches a fresh one.
		tokenInterceptor.setAuthorizationFailureHandler(
				OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(authorizedClients));

		return builder
				.baseUrl(properties.baseUrl())
				.requestInterceptor(tokenInterceptor)
				.build();
	}

	/**
	 * Fail at startup, with a clear message, if the secret is missing. Spring Boot leaves an
	 * unresolved ${...} placeholder in configuration properties as literal text instead of
	 * failing, so without this check a missing environment variable would only show up later
	 * as a confusing "invalid client" error from the identity provider.
	 */
	static void requireClientSecret(ClientRegistration registration) {
		String secret = registration == null ? null : registration.getClientSecret();
		if (secret == null || secret.isBlank() || secret.startsWith("${")) {
			throw new IllegalStateException(
					"The client secret for the core registration is missing. Set the environment variable PENSION_SERVICE_CLIENT_SECRET.");
		}
	}

}
