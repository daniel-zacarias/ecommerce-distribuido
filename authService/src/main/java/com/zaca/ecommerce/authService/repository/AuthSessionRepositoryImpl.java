package com.zaca.ecommerce.authService.repository;

import com.zaca.ecommerce.authService.dto.Role;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class AuthSessionRepositoryImpl implements AuthSessionRepository {

	private static final String TOKEN_KEY_PREFIX = "refresh-token:";
	private static final String SESSION_KEY_PREFIX = "refresh-session:";
	private static final String ACCESS_JTI_KEY_PREFIX = "access-jti:";
	private static final String USER_SESSIONS_KEY_PREFIX = "user-sessions:";

	private static final RedisScript<String> ROTATE_SCRIPT = new DefaultRedisScript<>("""
			if redis.call('EXISTS', KEYS[1]) == 0 then
			  return 'NOT_FOUND'
			end
			if redis.call('HGET', KEYS[1], 'revoked') == '1' then
			  return 'REUSE_DETECTED'
			end
			if redis.call('HGET', KEYS[1], 'currentToken') ~= ARGV[1] then
			  redis.call('HSET', KEYS[1], 'revoked', '1')
			  redis.call('EXPIRE', KEYS[1], ARGV[3])
			  return 'REUSE_DETECTED'
			end
			redis.call('HSET', KEYS[1], 'currentToken', ARGV[2])
			redis.call('EXPIRE', KEYS[1], ARGV[3])
			return 'ROTATED'
			""", String.class);

	private final StringRedisTemplate redisTemplate;

	public AuthSessionRepositoryImpl(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public String create(String token, String userId, Role role, Duration ttl) {
		String sessionId = UUID.randomUUID().toString();
		String sessionKey = sessionKey(sessionId);

		redisTemplate.opsForValue().set(tokenKey(token), sessionId, ttl);
		redisTemplate.opsForHash().putAll(sessionKey,
				Map.of("currentToken", token, "userId", userId, "role", role.name()));
		redisTemplate.expire(sessionKey, ttl);

		String userSessionsKey = userSessionsKey(userId);
		redisTemplate.opsForSet().add(userSessionsKey, sessionId);
		redisTemplate.expire(userSessionsKey, ttl);

		return sessionId;
	}

	@Override
	public RotationOutcome rotate(String presentedToken, String newToken, Duration ttl) {
		String sessionId = redisTemplate.opsForValue().get(tokenKey(presentedToken));
		if (sessionId == null) {
			return RotationOutcome.notFound();
		}

		String sessionKey = sessionKey(sessionId);
		String result = redisTemplate.execute(ROTATE_SCRIPT, List.of(sessionKey), presentedToken, newToken,
				String.valueOf(ttl.toSeconds()));

		return switch (RotationResult.valueOf(result)) {
			case NOT_FOUND -> RotationOutcome.notFound();
			case REUSE_DETECTED -> RotationOutcome.reuseDetected();
			case ROTATED -> {
				String userId = (String) redisTemplate.opsForHash().get(sessionKey, "userId");
				Role role = Role.valueOf((String) redisTemplate.opsForHash().get(sessionKey, "role"));
				redisTemplate.opsForValue().set(tokenKey(newToken), sessionId, ttl);
				yield RotationOutcome.rotated(userId, role, sessionId);
			}
		};
	}

	@Override
	public void linkAccessToken(String jti, String sessionId, Duration ttl) {
		redisTemplate.opsForValue().set(accessJtiKey(jti), sessionId, ttl);
	}

	@Override
	public boolean revokeByAccessJti(String jti) {
		String sessionId = redisTemplate.opsForValue().get(accessJtiKey(jti));
		if (sessionId == null) {
			return false;
		}

		redisTemplate.opsForHash().put(sessionKey(sessionId), "revoked", "1");
		return true;
	}

	@Override
	public boolean isAccessTokenRevoked(String jti) {
		String sessionId = redisTemplate.opsForValue().get(accessJtiKey(jti));
		if (sessionId == null) {
			return false;
		}

		return "1".equals(redisTemplate.opsForHash().get(sessionKey(sessionId), "revoked"));
	}

	@Override
	public void revokeAllForUser(String userId) {
		Set<String> sessionIds = redisTemplate.opsForSet().members(userSessionsKey(userId));
		if (sessionIds == null) {
			return;
		}

		for (String sessionId : sessionIds) {
			redisTemplate.opsForHash().put(sessionKey(sessionId), "revoked", "1");
		}
	}

	private String tokenKey(String token) {
		return TOKEN_KEY_PREFIX + token;
	}

	private String sessionKey(String sessionId) {
		return SESSION_KEY_PREFIX + sessionId;
	}

	private String accessJtiKey(String jti) {
		return ACCESS_JTI_KEY_PREFIX + jti;
	}

	private String userSessionsKey(String userId) {
		return USER_SESSIONS_KEY_PREFIX + userId;
	}
}
