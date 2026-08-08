package com.zaca.ecommerce.authService.dto;

import java.time.Instant;

public record AuthResponse(
		String access_token,
		String refresh_token,
		Instant expired_at) {
}
