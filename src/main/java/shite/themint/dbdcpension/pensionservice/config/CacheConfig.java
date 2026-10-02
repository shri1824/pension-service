package shite.themint.dbdcpension.pensionservice.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import shite.themint.dbdcpension.pensionservice.holdings.Holdings;
import shite.themint.dbdcpension.pensionservice.participation.Participation;
import shite.themint.dbdcpension.pensionservice.projection.Projection;
import shite.themint.dbdcpension.pensionservice.transactions.TransactionDetail;
import shite.themint.dbdcpension.pensionservice.transactions.TransactionList;
import shite.themint.dbdcpension.pensionservice.valuation.Valuation;

/**
 * Redis cache in front of the core. Redis is an optimisation, never a dependency: if it is
 * down, every cache error is logged and swallowed, and the request goes to the core as if
 * there were no cache.
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig implements CachingConfigurer {

	public static final String PARTICIPATION_CACHE = "participation";
	public static final String PROJECTION_CACHE = "projection";
	public static final String VALUATION_CACHE = "valuation";
	public static final String HOLDINGS_CACHE = "holdings";
	public static final String TRANSACTIONS_CACHE = "transactions";
	public static final String TRANSACTION_DETAIL_CACHE = "transaction-detail";

	private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

	@Bean
	RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, CacheProperties properties) {
		return RedisCacheManager.builder(connectionFactory)
				.withCacheConfiguration(PARTICIPATION_CACHE, cache(Participation.class, properties.participationTtl()))
				.withCacheConfiguration(PROJECTION_CACHE, cache(Projection.class, properties.projectionTtl()))
				.withCacheConfiguration(VALUATION_CACHE, cache(Valuation.class, properties.valuationTtl()))
				.withCacheConfiguration(HOLDINGS_CACHE, cache(Holdings.class, properties.holdingsTtl()))
				.withCacheConfiguration(TRANSACTIONS_CACHE, cache(TransactionList.class, properties.transactionsTtl()))
				.withCacheConfiguration(TRANSACTION_DETAIL_CACHE,
						cache(TransactionDetail.class, properties.transactionDetailTtl()))
				.build();
	}

	/** A typed JSON serializer (Jackson 3): readable in redis-cli and no class names stored in the data. */
	private static <T> RedisCacheConfiguration cache(Class<T> type, Duration ttl) {
		return RedisCacheConfiguration.defaultCacheConfig()
				.prefixCacheNameWith("pension:")
				.entryTtl(ttl)
				.disableCachingNullValues()
				.serializeKeysWith(SerializationPair.fromSerializer(StringRedisSerializer.UTF_8))
				.serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(type)));
	}

	@Override
	public CacheErrorHandler errorHandler() {
		return new LoggingCacheErrorHandler();
	}

	/** Logs the exception class only (a message could carry a key or a host name). */
	static class LoggingCacheErrorHandler implements CacheErrorHandler {

		@Override
		public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
			log.warn("Cache read failed for '{}', going to the source: {}", cache.getName(), ex.getClass().getSimpleName());
		}

		@Override
		public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
			log.warn("Cache write failed for '{}': {}", cache.getName(), ex.getClass().getSimpleName());
		}

		@Override
		public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
			log.warn("Cache evict failed for '{}': {}", cache.getName(), ex.getClass().getSimpleName());
		}

		@Override
		public void handleCacheClearError(RuntimeException ex, Cache cache) {
			log.warn("Cache clear failed for '{}': {}", cache.getName(), ex.getClass().getSimpleName());
		}

	}

}
