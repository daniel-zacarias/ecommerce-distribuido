package com.zaca.ecommerce.authService.dto;

import java.time.Instant;

public record TokenValidationResponse(
		String user_id,
		String token_type,
		Instant expires_at,
		Role role) {
}
