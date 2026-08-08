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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserServiceClient userServiceClient;

	@Mock
	private JwtService jwtService;

	@Mock
	private RefreshSessionRepository refreshSessionRepository;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		JwtProperties jwtProperties = new JwtProperties("test-secret", 30, 10080);
		authService = new AuthService(userServiceClient, jwtService, refreshSessionRepository, jwtProperties);
	}

	@Test
	void returnsTokensWhenAccountIsActive() {
		when(userServiceClient.validateCredentials("user", "123456"))
				.thenReturn(new UserValidationResponse("user-id-1", "ACTIVE"));

		Instant accessExpiresAt = Instant.now().plusSeconds(1800);
		when(jwtService.generateAccessToken("user-id-1"))
				.thenReturn(new JwtService.GeneratedToken("access-token", accessExpiresAt));

		AuthResponse response = authService.authenticate("user", "123456");

		assertThat(response.access_token()).isEqualTo("access-token");
		assertThat(response.refresh_token()).isNotBlank();
		assertThat(response.expired_at()).isEqualTo(accessExpiresAt);
		verify(refreshSessionRepository).create(eq(response.refresh_token()), eq("user-id-1"), eq(Duration.ofMinutes(10080)));
	}

	@Test
	void throwsInvalidCredentialsWhenAccountIsNotActive() {
		when(userServiceClient.validateCredentials("user", "123456"))
				.thenReturn(new UserValidationResponse("user-id-1", "INACTIVE"));

		assertThatThrownBy(() -> authService.authenticate("user", "123456"))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void validateTokenReturnsClaimsForValidBearerHeader() {
		Instant expiresAt = Instant.now().plusSeconds(1800);
		when(jwtService.validate("valid-token"))
				.thenReturn(new JwtService.TokenClaims("user-id-1", "access", expiresAt));

		TokenValidationResponse response = authService.validateToken("Bearer valid-token");

		assertThat(response.user_id()).isEqualTo("user-id-1");
		assertThat(response.token_type()).isEqualTo("access");
		assertThat(response.expires_at()).isEqualTo(expiresAt);
	}

	@Test
	void validateTokenThrowsInvalidTokenWhenHeaderIsMissing() {
		assertThatThrownBy(() -> authService.validateToken(null))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void validateTokenThrowsInvalidTokenWhenHeaderHasNoBearerPrefix() {
		assertThatThrownBy(() -> authService.validateToken("valid-token"))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void refreshReturnsNewTokensWhenRotationSucceeds() {
		when(refreshSessionRepository.rotate(eq("old-refresh-token"), anyString(), eq(Duration.ofMinutes(10080))))
				.thenReturn(RefreshSessionRepository.RotationOutcome.rotated("user-id-1"));

		Instant accessExpiresAt = Instant.now().plusSeconds(1800);
		when(jwtService.generateAccessToken("user-id-1"))
				.thenReturn(new JwtService.GeneratedToken("new-access-token", accessExpiresAt));

		AuthResponse response = authService.refresh("old-refresh-token");

		assertThat(response.access_token()).isEqualTo("new-access-token");
		assertThat(response.refresh_token()).isNotBlank().isNotEqualTo("old-refresh-token");
		assertThat(response.expired_at()).isEqualTo(accessExpiresAt);
	}

	@Test
	void refreshThrowsInvalidTokenWhenSessionNotFound() {
		when(refreshSessionRepository.rotate(eq("unknown-token"), anyString(), any()))
				.thenReturn(RefreshSessionRepository.RotationOutcome.notFound());

		assertThatThrownBy(() -> authService.refresh("unknown-token"))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void refreshThrowsRefreshTokenReusedWhenTokenAlreadyUsed() {
		when(refreshSessionRepository.rotate(eq("reused-token"), anyString(), any()))
				.thenReturn(RefreshSessionRepository.RotationOutcome.reuseDetected());

		assertThatThrownBy(() -> authService.refresh("reused-token"))
				.isInstanceOf(RefreshTokenReusedException.class);
	}
}
