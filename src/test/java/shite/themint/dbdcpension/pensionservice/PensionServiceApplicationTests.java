package shite.themint.dbdcpension.pensionservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Tests set the client secret explicitly, so they never depend on a developer's local config/application.yaml.
@SpringBootTest(properties = "spring.security.oauth2.client.registration.core.client-secret=test-secret")
class PensionServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
