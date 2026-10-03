package shite.themint.dbdcpension.pensionservice.investmentchange;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import shite.themint.dbdcpension.pensionservice.config.CacheConfig;

/**
 * Throws away one member's cached investment profile. The key is the member id, exactly as
 * {@code CoreInvestmentProfileAdapter} stores it. If Redis cannot be reached this throws (it is deliberately NOT
 * silenced like the cache reads): a lost eviction means a stale profile, and the caller must retry or park the event.
 */
@Component
class ProfileCacheEviction {

	private final CacheManager caches;

	ProfileCacheEviction(CacheManager caches) {
		this.caches = caches;
	}

	void evict(String participantId) {
		Cache cache = caches.getCache(CacheConfig.INVESTMENT_PROFILE_CACHE);
		if (cache == null) {
			throw new IllegalStateException("No cache named " + CacheConfig.INVESTMENT_PROFILE_CACHE);
		}
		cache.evict(participantId);
	}

}
