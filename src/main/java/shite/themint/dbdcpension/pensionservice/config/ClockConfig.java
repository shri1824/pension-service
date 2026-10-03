package shite.themint.dbdcpension.pensionservice.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One clock for the whole service, so tests can fix the time (for example around a retirement date). */
@Configuration
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}

}
