package shite.themint.dbdcpension.pensionservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.DispatcherType;

/**
 * Makes this service an OAuth2 Resource Server for service-to-service calls: every
 * /internal/v1/** call needs a token for the audience pension-service that carries the
 * {@code pension.read} scope. Audience, issuer and signature are checked from application.yaml.
 * <p>
 * Only the health probes are open, so Kubernetes can check the pod without a token.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(auth -> auth
						// Let Spring's internal /error forward through, so a failure keeps its real status.
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers("/actuator/health/**").permitAll()
						// Spring maps each JWT scope to an authority named "SCOPE_<scope>".
						.requestMatchers("/internal/v1/**").hasAuthority("SCOPE_pension.read")
						// Deny by default: anything not listed above is blocked.
						.anyRequest().denyAll())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// Bearer tokens are not sent automatically by browsers, so CSRF protection is not needed.
				.csrf(csrf -> csrf.disable());

		return http.build();
	}

}
