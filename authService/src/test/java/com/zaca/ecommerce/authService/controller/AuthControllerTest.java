package com.zaca.ecommerce.authService.controller;

import com.zaca.ecommerce.authService.config.SecurityConfig;
import com.zaca.ecommerce.authService.dto.AuthResponse;
import com.zaca.ecommerce.authService.dto.Role;
import com.zaca.ecommerce.authService.dto.TokenValidationResponse;
import com.zaca.ecommerce.authService.exception.InvalidCredentialsException;
import com.zaca.ecommerce.authService.exception.InvalidTokenException;
import com.zaca.ecommerce.authService.exception.RefreshTokenReusedException;
import com.zaca.ecommerce.authService.exception.TokenExpiredException;
import com.zaca.ecommerce.authService.exception.UserServiceUnavailableException;
import com.zaca.ecommerce.authService.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@Test
	void returnsTokensWhenCredentialsAreValid() throws Exception {
		Instant expiresAt = Instant.parse("2026-08-07T23:00:00Z");
		when(authService.authenticate(eq("user"), eq("123456")))
				.thenReturn(new AuthResponse("access-token", "refresh-token", expiresAt));

		mockMvc.perform(post("/auth")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"user\",\"password\":\"123456\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access_token").value("access-token"))
				.andExpect(jsonPath("$.refresh_token").value("refresh-token"));
	}

	@Test
	void returns401WhenCredentialsAreInvalid() throws Exception {
		when(authService.authenticate(eq("user"), eq("wrong-password")))
				.thenThrow(new InvalidCredentialsException("Invalid credentials"));

		mockMvc.perform(post("/auth")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"user\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void returns503WhenUserServiceIsUnavailable() throws Exception {
		when(authService.authenticate(any(), any()))
				.thenThrow(new UserServiceUnavailableException("down", new RuntimeException()));

		mockMvc.perform(post("/auth")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"user\",\"password\":\"123456\"}"))
				.andExpect(status().isServiceUnavailable());
	}

	@Test
	void returns400AndSkipsAuthServiceWhenUsernameIsBlank() throws Exception {
		mockMvc.perform(post("/auth")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"\",\"password\":\"123456\"}"))
				.andExpect(status().isBadRequest());

		verify(authService, never()).authenticate(any(), any());
	}

	@Test
	void returns400WhenBodyIsMalformedJson() throws Exception {
		mockMvc.perform(post("/auth")
						.contentType(MediaType.APPLICATION_JSON)
						.content("not-json"))
				.andExpect(status().isBadRequest());

		verify(authService, never()).authenticate(any(), any());
	}

	@Test
	void returnsClaimsWhenTokenIsValid() throws Exception {
		Instant expiresAt = Instant.parse("2026-08-07T23:00:00Z");
		when(authService.validateToken(eq("Bearer valid-token")))
				.thenReturn(new TokenValidationResponse("user-id-1", "access", expiresAt, Role.USER));

		mockMvc.perform(post("/auth/validate")
						.header("Authorization", "Bearer valid-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user_id").value("user-id-1"))
				.andExpect(jsonPath("$.token_type").value("access"));
	}

	@Test
	void returns401WhenTokenIsInvalid() throws Exception {
		when(authService.validateToken(eq("Bearer bad-token")))
				.thenThrow(new InvalidTokenException("Token is invalid"));

		mockMvc.perform(post("/auth/validate")
						.header("Authorization", "Bearer bad-token"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void returns401WhenTokenIsExpired() throws Exception {
		when(authService.validateToken(eq("Bearer expired-token")))
				.thenThrow(new TokenExpiredException("Token has expired"));

		mockMvc.perform(post("/auth/validate")
						.header("Authorization", "Bearer expired-token"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void returns401WhenAuthorizationHeaderIsMissing() throws Exception {
		when(authService.validateToken(isNull()))
				.thenThrow(new InvalidTokenException("Authorization header is missing or malformed"));

		mockMvc.perform(post("/auth/validate"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void returnsNewTokensWhenRefreshTokenIsValid() throws Exception {
		Instant expiresAt = Instant.parse("2026-08-07T23:00:00Z");
		when(authService.refresh(eq("valid-refresh-token")))
				.thenReturn(new AuthResponse("new-access-token", "new-refresh-token", expiresAt));

		mockMvc.perform(post("/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"refresh_token\":\"valid-refresh-token\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access_token").value("new-access-token"))
				.andExpect(jsonPath("$.refresh_token").value("new-refresh-token"));
	}

	@Test
	void returns401WhenRefreshTokenIsInvalidOrExpired() throws Exception {
		when(authService.refresh(eq("bad-refresh-token")))
				.thenThrow(new InvalidTokenException("Refresh token is invalid or expired"));

		mockMvc.perform(post("/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"refresh_token\":\"bad-refresh-token\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void returns401WhenRefreshTokenWasAlreadyUsed() throws Exception {
		when(authService.refresh(eq("reused-refresh-token")))
				.thenThrow(new RefreshTokenReusedException("Refresh token reuse detected; session revoked"));

		mockMvc.perform(post("/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"refresh_token\":\"reused-refresh-token\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void returns400AndSkipsAuthServiceWhenRefreshTokenIsBlank() throws Exception {
		mockMvc.perform(post("/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"refresh_token\":\"\"}"))
				.andExpect(status().isBadRequest());

		verify(authService, never()).refresh(any());
	}

	@Test
	void returns400WhenRefreshBodyIsMalformedJson() throws Exception {
		mockMvc.perform(post("/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("not-json"))
				.andExpect(status().isBadRequest());

		verify(authService, never()).refresh(any());
	}

	@Test
	void returnsNoContentWhenLoggingOutWithAValidBearerHeader() throws Exception {
		mockMvc.perform(post("/auth/logout")
						.header("Authorization", "Bearer valid-token"))
				.andExpect(status().isNoContent());

		verify(authService).logout(eq("Bearer valid-token"));
	}

	@Test
	void returnsNoContentWhenLoggingOutWithoutAnAuthorizationHeader() throws Exception {
		mockMvc.perform(post("/auth/logout"))
				.andExpect(status().isNoContent());

		verify(authService).logout(isNull());
	}
}
