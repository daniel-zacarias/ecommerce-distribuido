package com.zaca.ecommerce.authService.repository;

import com.zaca.ecommerce.authService.dto.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mocked unit test for {@link AuthSessionRepositoryImpl}: verifies the Java-side
 * key building and result handling in isolation, without a real Redis. The atomic
 * CAS behavior of the Lua script itself is verified separately, against a real Redis,
 * in {@link AuthSessionRepositoryImplIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class AuthSessionRepositoryImplTest {

	private static final Duration TTL = Duration.ofMinutes(10);

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private HashOperations<String, Object, Object> hashOperations;

	private AuthSessionRepositoryImpl repository;

	@BeforeEach
	void setUp() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		lenient().doReturn(hashOperations).when(redisTemplate).opsForHash();
		repository = new AuthSessionRepositoryImpl(redisTemplate);
	}

	@Test
	void createStoresTheTokenMappingAndTheSessionUnderTheSameGeneratedSessionId() {
		String sessionId = repository.create("token-1", "user-1", Role.USER, TTL);

		ArgumentCaptor<String> generatedSessionId = ArgumentCaptor.forClass(String.class);
		verify(valueOperations).set(eq("refresh-token:token-1"), generatedSessionId.capture(), eq(TTL));
		assertThat(sessionId).isEqualTo(generatedSessionId.getValue());

		String expectedSessionKey = "refresh-session:" + generatedSessionId.getValue();
		verify(hashOperations).putAll(eq(expectedSessionKey),
				eq(Map.of("currentToken", "token-1", "userId", "user-1", "role", "USER")));
		verify(redisTemplate).expire(expectedSessionKey, TTL);
	}

	@Test
	void linkAccessTokenStoresTheJtiToSessionMapping() {
		repository.linkAccessToken("jti-1", "session-abc", TTL);

		verify(valueOperations).set("access-jti:jti-1", "session-abc", TTL);
	}

	@Test
	void revokeByAccessJtiReturnsFalseWhenJtiIsNotLinked() {
		when(valueOperations.get("access-jti:unknown-jti")).thenReturn(null);

		boolean revoked = repository.revokeByAccessJti("unknown-jti");

		assertThat(revoked).isFalse();
		verify(hashOperations, never()).put(anyString(), any(), any());
	}

	@Test
	void revokeByAccessJtiMarksTheLinkedSessionAsRevoked() {
		when(valueOperations.get("access-jti:jti-1")).thenReturn("session-abc");

		boolean revoked = repository.revokeByAccessJti("jti-1");

		assertThat(revoked).isTrue();
		verify(hashOperations).put("refresh-session:session-abc", "revoked", "1");
	}

	@Test
	void isAccessTokenRevokedReturnsFalseWhenJtiIsNotLinked() {
		when(valueOperations.get("access-jti:unknown-jti")).thenReturn(null);

		assertThat(repository.isAccessTokenRevoked("unknown-jti")).isFalse();
	}

	@Test
	void isAccessTokenRevokedReturnsFalseWhenLinkedSessionIsNotRevoked() {
		when(valueOperations.get("access-jti:jti-1")).thenReturn("session-abc");
		when(hashOperations.get("refresh-session:session-abc", "revoked")).thenReturn(null);

		assertThat(repository.isAccessTokenRevoked("jti-1")).isFalse();
	}

	@Test
	void isAccessTokenRevokedReturnsTrueWhenLinkedSessionIsRevoked() {
		when(valueOperations.get("access-jti:jti-1")).thenReturn("session-abc");
		when(hashOperations.get("refresh-session:session-abc", "revoked")).thenReturn("1");

		assertThat(repository.isAccessTokenRevoked("jti-1")).isTrue();
	}

	@Test
	void rotateReturnsNotFoundWithoutRunningTheScriptWhenTokenMappingIsMissing() {
		when(valueOperations.get("refresh-token:unknown-token")).thenReturn(null);

		AuthSessionRepository.RotationOutcome outcome = repository.rotate("unknown-token", "new-token", TTL);

		assertThat(outcome.result()).isEqualTo(AuthSessionRepository.RotationResult.NOT_FOUND);
		assertThat(outcome.userId()).isNull();
		verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(), any(), any());
	}

	@Test
	void rotateReadsUserIdAndPersistsTheNewTokenMappingWhenScriptRotates() {
		when(valueOperations.get("refresh-token:old-token")).thenReturn("session-abc");
		stubScriptResult("session-abc", "old-token", "new-token", "ROTATED");
		when(hashOperations.get("refresh-session:session-abc", "userId")).thenReturn("user-1");
		when(hashOperations.get("refresh-session:session-abc", "role")).thenReturn("ADMIN");

		AuthSessionRepository.RotationOutcome outcome = repository.rotate("old-token", "new-token", TTL);

		assertThat(outcome.result()).isEqualTo(AuthSessionRepository.RotationResult.ROTATED);
		assertThat(outcome.userId()).isEqualTo("user-1");
		assertThat(outcome.role()).isEqualTo(Role.ADMIN);
		verify(valueOperations).set("refresh-token:new-token", "session-abc", TTL);
	}

	@Test
	void rotateDoesNotPersistNewTokenMappingWhenScriptDetectsReuse() {
		when(valueOperations.get("refresh-token:old-token")).thenReturn("session-abc");
		stubScriptResult("session-abc", "old-token", "new-token", "REUSE_DETECTED");

		AuthSessionRepository.RotationOutcome outcome = repository.rotate("old-token", "new-token", TTL);

		assertThat(outcome.result()).isEqualTo(AuthSessionRepository.RotationResult.REUSE_DETECTED);
		assertThat(outcome.userId()).isNull();
		verify(hashOperations, never()).get(anyString(), any());
		verify(valueOperations, never()).set(eq("refresh-token:new-token"), anyString(), any(Duration.class));
	}

	@Test
	void rotateReturnsNotFoundWhenScriptFindsTheSessionAlreadyGone() {
		when(valueOperations.get("refresh-token:old-token")).thenReturn("session-abc");
		stubScriptResult("session-abc", "old-token", "new-token", "NOT_FOUND");

		AuthSessionRepository.RotationOutcome outcome = repository.rotate("old-token", "new-token", TTL);

		assertThat(outcome.result()).isEqualTo(AuthSessionRepository.RotationResult.NOT_FOUND);
		assertThat(outcome.userId()).isNull();
	}

	@SuppressWarnings("unchecked")
	private void stubScriptResult(String sessionId, String presentedToken, String newToken, String scriptResult) {
		when(redisTemplate.execute(any(RedisScript.class), eq(List.of("refresh-session:" + sessionId)),
				eq(presentedToken), eq(newToken), eq(String.valueOf(TTL.toSeconds()))))
				.thenReturn(scriptResult);
	}
}
