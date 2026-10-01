package shite.themint.dbdcpension.pensionservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Where the core system lives, bound from {@code core-api.*}.
 * Validated, so the service fails at startup (not on the first request) if the URL is missing.
 */
@Validated
@ConfigurationProperties(prefix = "core-api")
public record CoreApiProperties(@NotBlank String baseUrl) {
}
