package com.zaca.ecommerce.authService.service;

import com.zaca.ecommerce.authService.client.UserServiceClient;
import com.zaca.ecommerce.authService.config.JwtProperties;
import com.zaca.ecommerce.authService.dto.AuthResponse;
import com.zaca.ecommerce.authService.dto.Role;
import com.zaca.ecommerce.authService.dto.TokenValidationResponse;
import com.zaca.ecommerce.authService.dto.UserValidationResponse;
import com.zaca.ecommerce.authService.exception.InvalidCredentialsException;
import com.zaca.ecommerce.authService.exception.InvalidTokenException;
import com.zaca.ecommerce.authService.exception.RefreshTokenReusedException;
import com.zaca.ecommerce.authService.exception.TokenExpiredException;
import com.zaca.ecommerce.authService.repository.AuthSessionRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserServiceClient userServiceClient;

	@Mock
	private JwtService jwtService;

	@Mock
	private AuthSessionRepository authSessionRepository;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		JwtProperties jwtProperties = new JwtProperties("test-secret", 30, 10080);
		authService = new AuthService(userServiceClient, jwtService, authSessionRepository, jwtProperties);
	}

	@Test
	void returnsTokensWhenCredentialsAreValid() {
		when(userServiceClient.validateCredentials("user", "123456"))
				.thenReturn(new UserValidationResponse(true, true, "user-id-1", Role.USER));

		Instant accessExpiresAt = Instant.now().plusSeconds(1800);
		when(jwtService.generateAccessToken("user-id-1", Role.USER))
				.thenReturn(new JwtService.GeneratedToken("access-token", "jti-1", accessExpiresAt));
		when(authSessionRepository.create(anyString(), eq("user-id-1"), eq(Role.USER), eq(Duration.ofMinutes(10080))))
				.thenReturn("session-1");

		AuthResponse response = authService.authenticate("user", "123456");

		assertThat(response.access_token()).isEqualTo("access-token");
		assertThat(response.refresh_token()).isNotBlank();
		assertThat(response.expired_at()).isEqualTo(accessExpiresAt);
		verify(authSessionRepository).create(eq(response.refresh_token()), eq("user-id-1"), eq(Role.USER),
				eq(Duration.ofMinutes(10080)));
		verify(authSessionRepository).linkAccessToken("jti-1", "session-1", Duration.ofMinutes(30));
	}

	@Test
	void throwsInvalidCredentialsWhenCredentialsAreInvalid() {
		when(userServiceClient.validateCredentials("user", "123456"))
				.thenReturn(new UserValidationResponse(false, false, null, null));

		assertThatThrownBy(() -> authService.authenticate("user", "123456"))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void validateTokenReturnsClaimsForValidBearerHeader() {
		Instant expiresAt = Instant.now().plusSeconds(1800);
		when(jwtService.validate("valid-token"))
				.thenReturn(new JwtService.TokenClaims("user-id-1", "access", "jti-1", expiresAt, Role.USER));
		when(authSessionRepository.isAccessTokenRevoked("jti-1")).thenReturn(false);

		TokenValidationResponse response = authService.validateToken("Bearer valid-token");

		assertThat(response.user_id()).isEqualTo("user-id-1");
		assertThat(response.token_type()).isEqualTo("access");
		assertThat(response.expires_at()).isEqualTo(expiresAt);
		assertThat(response.role()).isEqualTo(Role.USER);
	}

	@Test
	void validateTokenThrowsInvalidTokenWhenAccessTokenWasRevoked() {
		Instant expiresAt = Instant.now().plusSeconds(1800);
		when(jwtService.validate("revoked-token"))
				.thenReturn(new JwtService.TokenClaims("user-id-1", "access", "jti-1", expiresAt, Role.USER));
		when(authSessionRepository.isAccessTokenRevoked("jti-1")).thenReturn(true);

		assertThatThrownBy(() -> authService.validateToken("Bearer revoked-token"))
				.isInstanceOf(InvalidTokenException.class);
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
		when(authSessionRepository.rotate(eq("old-refresh-token"), anyString(), eq(Duration.ofMinutes(10080))))
				.thenReturn(AuthSessionRepository.RotationOutcome.rotated("user-id-1", Role.USER, "session-1"));

		Instant accessExpiresAt = Instant.now().plusSeconds(1800);
		when(jwtService.generateAccessToken("user-id-1", Role.USER))
				.thenReturn(new JwtService.GeneratedToken("new-access-token", "jti-2", accessExpiresAt));

		AuthResponse response = authService.refresh("old-refresh-token");

		assertThat(response.access_token()).isEqualTo("new-access-token");
		assertThat(response.refresh_token()).isNotBlank().isNotEqualTo("old-refresh-token");
		assertThat(response.expired_at()).isEqualTo(accessExpiresAt);
		verify(authSessionRepository).linkAccessToken("jti-2", "session-1", Duration.ofMinutes(30));
	}

	@Test
	void refreshThrowsInvalidTokenWhenSessionNotFound() {
		when(authSessionRepository.rotate(eq("unknown-token"), anyString(), any()))
				.thenReturn(AuthSessionRepository.RotationOutcome.notFound());

		assertThatThrownBy(() -> authService.refresh("unknown-token"))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void refreshThrowsRefreshTokenReusedWhenTokenAlreadyUsed() {
		when(authSessionRepository.rotate(eq("reused-token"), anyString(), any()))
				.thenReturn(AuthSessionRepository.RotationOutcome.reuseDetected());

		assertThatThrownBy(() -> authService.refresh("reused-token"))
				.isInstanceOf(RefreshTokenReusedException.class);
	}

	@Test
	void revokeSessionsForUserDelegatesToRepository() {
		authService.revokeSessionsForUser("user-1");

		verify(authSessionRepository).revokeAllForUser("user-1");
	}

	@Test
	void logoutRevokesTheSessionForAValidAccessToken() {
		Instant expiresAt = Instant.now().plusSeconds(1800);
		when(jwtService.validate("valid-token"))
				.thenReturn(new JwtService.TokenClaims("user-id-1", "access", "jti-1", expiresAt, Role.USER));

		authService.logout("Bearer valid-token");

		verify(authSessionRepository).revokeByAccessJti("jti-1");
	}

	@Test
	void logoutIsIdempotentWhenAuthorizationHeaderIsMissing() {
		authService.logout(null);

		verify(authSessionRepository, never()).revokeByAccessJti(any());
	}

	@Test
	void logoutIsIdempotentWhenAuthorizationHeaderHasNoBearerPrefix() {
		authService.logout("not-a-bearer-header");

		verify(authSessionRepository, never()).revokeByAccessJti(any());
	}

	@Test
	void logoutIsIdempotentWhenAccessTokenIsExpired() {
		when(jwtService.validate("expired-token")).thenThrow(new TokenExpiredException("Token has expired"));

		authService.logout("Bearer expired-token");

		verify(authSessionRepository, never()).revokeByAccessJti(any());
	}

	@Test
	void logoutIsIdempotentWhenAccessTokenIsInvalid() {
		when(jwtService.validate("bad-token")).thenThrow(new InvalidTokenException("Token is invalid"));

		authService.logout("Bearer bad-token");

		verify(authSessionRepository, never()).revokeByAccessJti(any());
	}
}
