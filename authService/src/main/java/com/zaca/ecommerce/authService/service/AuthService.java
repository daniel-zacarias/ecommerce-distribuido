package com.zaca.ecommerce.authService.service;

import com.zaca.ecommerce.authService.client.UserServiceClient;
import com.zaca.ecommerce.authService.config.JwtProperties;
import com.zaca.ecommerce.authService.dto.AuthResponse;
import com.zaca.ecommerce.authService.dto.TokenValidationResponse;
import com.zaca.ecommerce.authService.dto.UserValidationResponse;
import com.zaca.ecommerce.authService.exception.InvalidCredentialsException;
import com.zaca.ecommerce.authService.exception.InvalidTokenException;
import com.zaca.ecommerce.authService.exception.RefreshTokenReusedException;
import com.zaca.ecommerce.authService.exception.TokenExpiredException;
import com.zaca.ecommerce.authService.repository.AuthSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

@Service
public class AuthService {

	private final UserServiceClient userServiceClient;
	private final JwtService jwtService;
	private final AuthSessionRepository authSessionRepository;
	private final JwtProperties jwtProperties;

	public AuthService(UserServiceClient userServiceClient, JwtService jwtService,
			AuthSessionRepository authSessionRepository, JwtProperties jwtProperties) {
		this.userServiceClient = userServiceClient;
		this.jwtService = jwtService;
		this.authSessionRepository = authSessionRepository;
		this.jwtProperties = jwtProperties;
	}

	public AuthResponse authenticate(String username, String password) {
		UserValidationResponse validationResponse = userServiceClient.validateCredentials(username, password);

		if (!validationResponse.valid()) {
			throw new InvalidCredentialsException("Invalid credentials");
		}

		JwtService.GeneratedToken accessToken = jwtService.generateAccessToken(validationResponse.user_id());
		String refreshToken = UUID.randomUUID().toString();
		String sessionId = authSessionRepository.create(refreshToken, validationResponse.user_id(), refreshTokenTtl());
		authSessionRepository.linkAccessToken(accessToken.jti(), sessionId, accessTokenTtl());

		return new AuthResponse(accessToken.token(), refreshToken, accessToken.expiresAt());
	}

	public AuthResponse refresh(String refreshToken) {
		String newRefreshToken = UUID.randomUUID().toString();

		AuthSessionRepository.RotationOutcome outcome = authSessionRepository.rotate(refreshToken,
				newRefreshToken, refreshTokenTtl());

		switch (outcome.result()) {
			case NOT_FOUND -> throw new InvalidTokenException("Refresh token is invalid or expired");
			case REUSE_DETECTED ->
				throw new RefreshTokenReusedException("Refresh token reuse detected; session revoked");
			case ROTATED -> {
			}
		}

		JwtService.GeneratedToken accessToken = jwtService.generateAccessToken(outcome.userId());
		authSessionRepository.linkAccessToken(accessToken.jti(), outcome.sessionId(), accessTokenTtl());
		return new AuthResponse(accessToken.token(), newRefreshToken, accessToken.expiresAt());
	}

	private Duration refreshTokenTtl() {
		return Duration.ofMinutes(jwtProperties.refreshTokenExpirationMinutes());
	}

	private Duration accessTokenTtl() {
		return Duration.ofMinutes(jwtProperties.accessTokenExpirationMinutes());
	}

	public TokenValidationResponse validateToken(String authorizationHeader) {
		String token = extractBearerToken(authorizationHeader);
		if (token == null) {
			throw new InvalidTokenException("Authorization header is missing or malformed");
		}

		JwtService.TokenClaims claims = jwtService.validate(token);
		if (authSessionRepository.isAccessTokenRevoked(claims.jti())) {
			throw new InvalidTokenException("Token has been revoked");
		}

		return new TokenValidationResponse(claims.subject(), claims.tokenType(), claims.expiresAt());
	}

	public void logout(String authorizationHeader) {
		String token = extractBearerToken(authorizationHeader);
		if (token == null) {
			return;
		}

		try {
			JwtService.TokenClaims claims = jwtService.validate(token);
			authSessionRepository.revokeByAccessJti(claims.jti());
		} catch (TokenExpiredException | InvalidTokenException ex) {
			// idempotent: nothing to revoke for an expired/invalid access token
		}
	}

	private String extractBearerToken(String authorizationHeader) {
		if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
			return null;
		}

		return authorizationHeader.substring("Bearer ".length());
	}
}
