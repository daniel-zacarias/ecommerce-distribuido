package com.zaca.ecommerce.authService.service;

import com.zaca.ecommerce.authService.client.UserServiceClient;
import com.zaca.ecommerce.authService.dto.AuthResponse;
import com.zaca.ecommerce.authService.dto.TokenValidationResponse;
import com.zaca.ecommerce.authService.dto.UserValidationResponse;
import com.zaca.ecommerce.authService.exception.InvalidCredentialsException;
import com.zaca.ecommerce.authService.exception.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserServiceClient userServiceClient;

	@Mock
	private JwtService jwtService;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(userServiceClient, jwtService);
	}

	@Test
	void returnsTokensWhenAccountIsActive() {
		when(userServiceClient.validateCredentials("user", "123456"))
				.thenReturn(new UserValidationResponse("user-id-1", "ACTIVE"));

		Instant accessExpiresAt = Instant.now().plusSeconds(1800);
		when(jwtService.generateAccessToken("user-id-1"))
				.thenReturn(new JwtService.GeneratedToken("access-token", accessExpiresAt));
		when(jwtService.generateRefreshToken("user-id-1"))
				.thenReturn(new JwtService.GeneratedToken("refresh-token", Instant.now().plusSeconds(604800)));

		AuthResponse response = authService.authenticate("user", "123456");

		assertThat(response.access_token()).isEqualTo("access-token");
		assertThat(response.refresh_token()).isEqualTo("refresh-token");
		assertThat(response.expired_at()).isEqualTo(accessExpiresAt);
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
}
