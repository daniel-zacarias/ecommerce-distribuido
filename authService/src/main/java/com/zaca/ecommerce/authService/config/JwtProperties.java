package com.zaca.ecommerce.authService.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
		String secret,
		long accessTokenExpirationMinutes,
		long refreshTokenExpirationMinutes) {
}
