package shite.themint.dbdcpension.pensionservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How long data stays in Redis before the core is asked again.
 *
 * @param participationTtl default 15 minutes
 * @param projectionTtl    default 6 hours (a projection only changes when the core recalculates it)
 * @param valuationTtl     default 1 hour (a capital value changes with the daily fund prices)
 * @param holdingsTtl      default 1 hour (units and prices change with the daily fund prices and new contributions)
 */
@ConfigurationProperties(prefix = "pension-cache")
public record CacheProperties(Duration participationTtl, Duration projectionTtl, Duration valuationTtl,
		Duration holdingsTtl) {

	public CacheProperties {
		if (participationTtl == null) {
			participationTtl = Duration.ofMinutes(15);
		}
		if (projectionTtl == null) {
			projectionTtl = Duration.ofHours(6);
		}
		if (valuationTtl == null) {
			valuationTtl = Duration.ofHours(1);
		}
		if (holdingsTtl == null) {
			holdingsTtl = Duration.ofHours(1);
		}
	}

}
