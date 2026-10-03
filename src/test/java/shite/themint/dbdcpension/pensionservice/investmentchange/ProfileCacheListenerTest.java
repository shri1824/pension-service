package shite.themint.dbdcpension.pensionservice.investmentchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import shite.themint.dbdcpension.pensionservice.config.CacheConfig;
import tools.jackson.databind.json.JsonMapper;

/** The listener's decisions, without Kafka and Redis: what it evicts and what it leaves alone. */
class ProfileCacheListenerTest {

	private final CacheManager caches = new ConcurrentMapCacheManager(CacheConfig.INVESTMENT_PROFILE_CACHE,
			CacheConfig.PROJECTION_CACHE);

	private final ProfileCacheListener listener = new ProfileCacheListener(new ProfileCacheEviction(caches),
			JsonMapper.builder().build());

	private Cache profiles() {
		return caches.getCache(CacheConfig.INVESTMENT_PROFILE_CACHE);
	}

	@BeforeEach
	void fill() {
		profiles().put("M1001", "profile of M1001");
		profiles().put("M1002", "profile of M1002");
		caches.getCache(CacheConfig.PROJECTION_CACHE).put("M1001", "projection of M1001");
	}

	private static ConsumerRecord<String, String> event(String type, String member, String to) {
		return new ConsumerRecord<>("pension.change-request.status-changed", 0, 0L, member, """
				{"requestId": "b76f406a-2670-4556-b6b2-b69d108aa888", "participantId": "%s", "type": "%s",
				 "from": "APPROVED", "to": "%s", "reason": null, "occurredAt": "2026-10-03T13:00:00Z"}"""
				.formatted(member, type, to));
	}

	@Test
	void completedInvestmentChange_evictsThatMembersProfile_andNobodyElses() {
		listener.onStatusChanged(event("INVESTMENT_CHOICE", "M1001", "COMPLETED"));

		assertThat(profiles().get("M1001")).isNull();
		assertThat(profiles().get("M1002")).isNotNull();
	}

	@Test
	void theProjectionIsLeftAlone() {
		listener.onStatusChanged(event("INVESTMENT_CHOICE", "M1001", "COMPLETED"));

		assertThat(caches.getCache(CacheConfig.PROJECTION_CACHE).get("M1001")).isNotNull();
	}

	@Test
	void everyOtherStatus_evictsNothing() {
		for (String status : new String[] { "SUBMITTED", "IN_REVIEW", "ADVICE_GIVEN", "APPROVED", "REJECTED" }) {
			listener.onStatusChanged(event("INVESTMENT_CHOICE", "M1001", status));
		}

		assertThat(profiles().get("M1001")).isNotNull();
	}

	@Test
	void aCompletedChangeOfAnotherKind_evictsNothing() {
		listener.onStatusChanged(event("RETIREMENT_AGE", "M1001", "COMPLETED"));

		assertThat(profiles().get("M1001")).isNotNull();
	}

	@Test
	void theSameEventTwice_isHarmless() {
		listener.onStatusChanged(event("INVESTMENT_CHOICE", "M1001", "COMPLETED"));
		listener.onStatusChanged(event("INVESTMENT_CHOICE", "M1001", "COMPLETED"));

		assertThat(profiles().get("M1001")).isNull();
		assertThat(profiles().get("M1002")).isNotNull();
	}

	@Test
	void anUnreadableEvent_isRejected_andEvictsNothing() {
		assertThatThrownBy(() -> listener.onStatusChanged(
				new ConsumerRecord<>("pension.change-request.status-changed", 0, 0L, "M1001", "garbage")))
				.isInstanceOf(MalformedEventException.class);

		assertThat(profiles().get("M1001")).isNotNull();
	}

	@Test
	void whenTheCacheCannotBeReached_theFailureIsNotHidden_soTheEventIsRetriedAndParked() {
		CacheManager broken = new CacheManager() {
			@Override
			public Cache getCache(String name) {
				return new org.springframework.cache.support.NoOpCache(name) {
					@Override
					public void evict(Object key) {
						throw new IllegalStateException("Redis is down");
					}
				};
			}

			@Override
			public java.util.Collection<String> getCacheNames() {
				return java.util.List.of(CacheConfig.INVESTMENT_PROFILE_CACHE);
			}
		};
		ProfileCacheListener failing = new ProfileCacheListener(new ProfileCacheEviction(broken), JsonMapper.builder().build());

		assertThatThrownBy(() -> failing.onStatusChanged(event("INVESTMENT_CHOICE", "M1001", "COMPLETED")))
				.isInstanceOf(IllegalStateException.class).hasMessage("Redis is down");
	}

}
