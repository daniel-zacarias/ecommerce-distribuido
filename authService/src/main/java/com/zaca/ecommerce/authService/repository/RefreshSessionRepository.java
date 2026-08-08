package com.zaca.ecommerce.authService.repository;

import java.time.Duration;

public interface RefreshSessionRepository {

	void create(String token, String userId, Duration ttl);

	RotationOutcome rotate(String presentedToken, String newToken, Duration ttl);

	record RotationOutcome(RotationResult result, String userId) {

		public static RotationOutcome notFound() {
			return new RotationOutcome(RotationResult.NOT_FOUND, null);
		}

		public static RotationOutcome reuseDetected() {
			return new RotationOutcome(RotationResult.REUSE_DETECTED, null);
		}

		public static RotationOutcome rotated(String userId) {
			return new RotationOutcome(RotationResult.ROTATED, userId);
		}
	}

	enum RotationResult {
		ROTATED, NOT_FOUND, REUSE_DETECTED
	}
}
