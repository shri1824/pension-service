package shite.themint.dbdcpension.pensionservice.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Full application context, so the real SecurityConfig is exercised. If SecurityConfig is not
 * picked up (for example a wrong package), the 403 tests fail.
 */
@SpringBootTest(properties = "spring.security.oauth2.client.registration.core.client-secret=test-secret")
@AutoConfigureMockMvc
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	private static RequestPostProcessor pensionRead() {
		return jwt().authorities(new SimpleGrantedAuthority("SCOPE_pension.read"));
	}

	@Test
	void withoutToken_isUnauthorized() throws Exception {
		mockMvc.perform(get("/internal/v1/anything")).andExpect(status().isUnauthorized());
	}

	@Test
	void tokenWithoutPensionReadScope_isForbidden() throws Exception {
		mockMvc.perform(get("/internal/v1/anything").with(jwt())).andExpect(status().isForbidden());
	}

	@Test
	void tokenWithScope_isLetInToAnUnmappedPath() throws Exception {
		mockMvc.perform(get("/internal/v1/anything").with(pensionRead())).andExpect(status().isNotFound());
	}

	@Test
	void pathOutsideInternalApi_isForbiddenEvenWithScope() throws Exception {
		mockMvc.perform(get("/core/v1/anything").with(pensionRead())).andExpect(status().isForbidden());
	}

	@Test
	void livenessProbe_isOpenWithoutToken() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
	}

}
