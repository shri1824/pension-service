package shite.themint.dbdcpension.pensionservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** How long a participation stays in Redis before the core is asked again. */
@ConfigurationProperties(prefix = "pension-cache")
public record CacheProperties(Duration participationTtl) {

	public CacheProperties {
		if (participationTtl == null) {
			participationTtl = Duration.ofMinutes(15);
		}
	}

}
