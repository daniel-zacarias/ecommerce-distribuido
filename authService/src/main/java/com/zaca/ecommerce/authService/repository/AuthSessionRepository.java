package com.zaca.ecommerce.authService.repository;

import com.zaca.ecommerce.authService.dto.Role;

import java.time.Duration;

public interface AuthSessionRepository {

	String create(String token, String userId, Role role, Duration ttl);

	RotationOutcome rotate(String presentedToken, String newToken, Duration ttl);

	void linkAccessToken(String jti, String sessionId, Duration ttl);

	boolean revokeByAccessJti(String jti);

	boolean isAccessTokenRevoked(String jti);

	record RotationOutcome(RotationResult result, String userId, Role role, String sessionId) {

		public static RotationOutcome notFound() {
			return new RotationOutcome(RotationResult.NOT_FOUND, null, null, null);
		}

		public static RotationOutcome reuseDetected() {
			return new RotationOutcome(RotationResult.REUSE_DETECTED, null, null, null);
		}

		public static RotationOutcome rotated(String userId, Role role, String sessionId) {
			return new RotationOutcome(RotationResult.ROTATED, userId, role, sessionId);
		}
	}

	enum RotationResult {
		ROTATED, NOT_FOUND, REUSE_DETECTED
	}
}
