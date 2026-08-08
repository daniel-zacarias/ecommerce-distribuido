package com.zaca.ecommerce.authService.service;

import com.zaca.ecommerce.authService.client.UserServiceClient;
import com.zaca.ecommerce.authService.config.JwtProperties;
import com.zaca.ecommerce.authService.dto.AuthResponse;
import com.zaca.ecommerce.authService.dto.TokenValidationResponse;
import com.zaca.ecommerce.authService.dto.UserValidationResponse;
import com.zaca.ecommerce.authService.exception.InvalidCredentialsException;
import com.zaca.ecommerce.authService.exception.InvalidTokenException;
import com.zaca.ecommerce.authService.exception.RefreshTokenReusedException;
import com.zaca.ecommerce.authService.repository.RefreshSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

@Service
public class AuthService {

	private final UserServiceClient userServiceClient;
	private final JwtService jwtService;
	private final RefreshSessionRepository refreshSessionRepository;
	private final JwtProperties jwtProperties;

	public AuthService(UserServiceClient userServiceClient, JwtService jwtService,
			RefreshSessionRepository refreshSessionRepository, JwtProperties jwtProperties) {
		this.userServiceClient = userServiceClient;
		this.jwtService = jwtService;
		this.refreshSessionRepository = refreshSessionRepository;
		this.jwtProperties = jwtProperties;
	}

	public AuthResponse authenticate(String username, String password) {
		UserValidationResponse validationResponse = userServiceClient.validateCredentials(username, password);

		if (!validationResponse.isActive()) {
			throw new InvalidCredentialsException("Account is not active");
		}

		JwtService.GeneratedToken accessToken = jwtService.generateAccessToken(validationResponse.id());
		String refreshToken = UUID.randomUUID().toString();
		refreshSessionRepository.create(refreshToken, validationResponse.id(), refreshTokenTtl());

		return new AuthResponse(accessToken.token(), refreshToken, accessToken.expiresAt());
	}

	public AuthResponse refresh(String refreshToken) {
		String newRefreshToken = UUID.randomUUID().toString();

		RefreshSessionRepository.RotationOutcome outcome = refreshSessionRepository.rotate(refreshToken,
				newRefreshToken, refreshTokenTtl());

		switch (outcome.result()) {
			case NOT_FOUND -> throw new InvalidTokenException("Refresh token is invalid or expired");
			case REUSE_DETECTED ->
				throw new RefreshTokenReusedException("Refresh token reuse detected; session revoked");
			case ROTATED -> {
			}
		}

		JwtService.GeneratedToken accessToken = jwtService.generateAccessToken(outcome.userId());
		return new AuthResponse(accessToken.token(), newRefreshToken, accessToken.expiresAt());
	}

	private Duration refreshTokenTtl() {
		return Duration.ofMinutes(jwtProperties.refreshTokenExpirationMinutes());
	}

	public TokenValidationResponse validateToken(String authorizationHeader) {
		if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
			throw new InvalidTokenException("Authorization header is missing or malformed");
		}

		String token = authorizationHeader.substring("Bearer ".length());
		JwtService.TokenClaims claims = jwtService.validate(token);

		return new TokenValidationResponse(claims.subject(), claims.tokenType(), claims.expiresAt());
	}
}
