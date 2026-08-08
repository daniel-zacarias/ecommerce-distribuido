package com.zaca.ecommerce.authService.repository;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link RefreshSessionRepositoryImpl} against a real Redis instance
 * (via Testcontainers), since the atomic CAS rotation logic lives inside a Lua
 * script that a mocked {@code StringRedisTemplate} cannot meaningfully verify.
 * Skipped automatically when Docker isn't available.
 *
 * @see RefreshSessionRepositoryImplTest for the mocked, Docker-free unit test.
 */
@Testcontainers
@EnabledIfDockerAvailable
class RefreshSessionRepositoryImplIntegrationTest {

	private static final Duration TTL = Duration.ofMinutes(10);

	@Container
	private static final RedisContainer REDIS = new RedisContainer("redis:7-alpine");

	private static LettuceConnectionFactory connectionFactory;

	private RefreshSessionRepositoryImpl repository;

	@BeforeAll
	static void startConnectionFactory() {
		connectionFactory = new LettuceConnectionFactory(
				new RedisStandaloneConfiguration(REDIS.getRedisHost(), REDIS.getRedisPort()));
		connectionFactory.afterPropertiesSet();
	}

	@AfterAll
	static void stopConnectionFactory() {
		connectionFactory.destroy();
	}

	@BeforeEach
	void setUp() {
		StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
		repository = new RefreshSessionRepositoryImpl(redisTemplate);
	}

	@Test
	void rotateReturnsNotFoundForATokenThatWasNeverCreated() {
		RefreshSessionRepository.RotationOutcome outcome = repository.rotate(
				UUID.randomUUID().toString(), UUID.randomUUID().toString(), TTL);

		assertThat(outcome.result()).isEqualTo(RefreshSessionRepository.RotationResult.NOT_FOUND);
		assertThat(outcome.userId()).isNull();
	}

	@Test
	void rotateSucceedsForAFreshlyCreatedToken() {
		String token = UUID.randomUUID().toString();
		String newToken = UUID.randomUUID().toString();
		repository.create(token, "user-1", TTL);

		RefreshSessionRepository.RotationOutcome outcome = repository.rotate(token, newToken, TTL);

		assertThat(outcome.result()).isEqualTo(RefreshSessionRepository.RotationResult.ROTATED);
		assertThat(outcome.userId()).isEqualTo("user-1");
	}

	@Test
	void rotatedTokenCanBeUsedForTheNextRotation() {
		String token = UUID.randomUUID().toString();
		String rotatedToken = UUID.randomUUID().toString();
		String nextToken = UUID.randomUUID().toString();
		repository.create(token, "user-1", TTL);
		repository.rotate(token, rotatedToken, TTL);

		RefreshSessionRepository.RotationOutcome outcome = repository.rotate(rotatedToken, nextToken, TTL);

		assertThat(outcome.result()).isEqualTo(RefreshSessionRepository.RotationResult.ROTATED);
		assertThat(outcome.userId()).isEqualTo("user-1");
	}

	@Test
	void reusingAnAlreadyRotatedTokenIsDetected() {
		String token = UUID.randomUUID().toString();
		repository.create(token, "user-1", TTL);
		repository.rotate(token, UUID.randomUUID().toString(), TTL);

		RefreshSessionRepository.RotationOutcome replay = repository.rotate(token, UUID.randomUUID().toString(), TTL);

		assertThat(replay.result()).isEqualTo(RefreshSessionRepository.RotationResult.REUSE_DETECTED);
		assertThat(replay.userId()).isNull();
	}

	@Test
	void reuseDetectionRevokesTheWholeSessionIncludingTheNewestToken() {
		String token = UUID.randomUUID().toString();
		String rotatedToken = UUID.randomUUID().toString();
		repository.create(token, "user-1", TTL);
		repository.rotate(token, rotatedToken, TTL);

		// replay the already-used original token -> reuse detected, whole session revoked
		repository.rotate(token, UUID.randomUUID().toString(), TTL);

		// the token that was legitimately current at the time of the attack is burned too
		RefreshSessionRepository.RotationOutcome outcome = repository.rotate(rotatedToken, UUID.randomUUID().toString(), TTL);

		assertThat(outcome.result()).isEqualTo(RefreshSessionRepository.RotationResult.REUSE_DETECTED);
	}

	@Test
	void concurrentRotationOfTheSameTokenSucceedsExactlyOnce() throws Exception {
		String token = UUID.randomUUID().toString();
		repository.create(token, "user-1", TTL);

		int attempts = 12;
		ExecutorService executor = Executors.newFixedThreadPool(attempts);
		CountDownLatch ready = new CountDownLatch(attempts);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<RefreshSessionRepository.RotationOutcome>> futures = new ArrayList<>();

		try {
			for (int i = 0; i < attempts; i++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					go.await();
					return repository.rotate(token, UUID.randomUUID().toString(), TTL);
				}));
			}

			ready.await();
			go.countDown();

			AtomicLong rotated = new AtomicLong();
			AtomicLong reuseDetected = new AtomicLong();
			for (Future<RefreshSessionRepository.RotationOutcome> future : futures) {
				switch (future.get().result()) {
					case ROTATED -> rotated.incrementAndGet();
					case REUSE_DETECTED -> reuseDetected.incrementAndGet();
					case NOT_FOUND -> throw new AssertionError("Unexpected NOT_FOUND for a token that was created");
				}
			}

			assertThat(rotated.get()).isEqualTo(1);
			assertThat(reuseDetected.get()).isEqualTo(attempts - 1L);
		} finally {
			executor.shutdown();
		}
	}
}
