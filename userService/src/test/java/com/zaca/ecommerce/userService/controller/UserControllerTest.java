package com.zaca.ecommerce.userService.controller;

import com.zaca.ecommerce.userService.dto.UserResponse;
import com.zaca.ecommerce.userService.dto.UserVerificationResponse;
import com.zaca.ecommerce.userService.exception.DuplicateEmailException;
import com.zaca.ecommerce.userService.exception.InvalidTokenException;
import com.zaca.ecommerce.userService.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@Test
	void returns201WhenRegistrationSucceeds() throws Exception {
		UUID id = UUID.randomUUID();
		when(userService.register(anyString(), anyString(), anyString()))
				.thenReturn(new UserResponse(id, "Daniel", "user@test.com", Instant.now()));

		mockMvc.perform(post("/users")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Daniel","email":"user@test.com","password":"senha123"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("user@test.com"));
	}

	@Test
	void returns400WhenEmailIsInvalid() throws Exception {
		mockMvc.perform(post("/users")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Daniel","email":"not-an-email","password":"senha123"}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void returns400WhenPasswordIsWeak() throws Exception {
		mockMvc.perform(post("/users")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Daniel","email":"user@test.com","password":"abc"}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void returns409WhenEmailAlreadyExists() throws Exception {
		when(userService.register(anyString(), anyString(), anyString()))
				.thenThrow(new DuplicateEmailException("Email already in use"));

		mockMvc.perform(post("/users")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Daniel","email":"user@test.com","password":"senha123"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Email already in use"));
	}

	@Test
	void returns200WithOwnDataWhenTokenIsValid() throws Exception {
		UUID id = UUID.randomUUID();
		when(userService.getCurrentUser(anyString()))
				.thenReturn(new UserResponse(id, "Daniel", "user@test.com", Instant.now()));

		mockMvc.perform(get("/me").header("Authorization", "Bearer valid-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Daniel"))
				.andExpect(jsonPath("$.email").value("user@test.com"))
				.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void returns401WhenTokenIsInvalidOrMissing() throws Exception {
		when(userService.getCurrentUser(anyString()))
				.thenThrow(new InvalidTokenException("Invalid or expired token"));

		mockMvc.perform(get("/me").header("Authorization", "Bearer invalid-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid or expired token"));
	}

	@Test
	void returns200WithExistsTrueValidTrueWhenCredentialsMatch() throws Exception {
		UUID userId = UUID.randomUUID();
		when(userService.verifyCredentials(anyString(), anyString()))
				.thenReturn(new UserVerificationResponse(true, true, userId));

		mockMvc.perform(post("/internal/users/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"user@test.com","password":"senha123"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.exists").value(true))
				.andExpect(jsonPath("$.valid").value(true))
				.andExpect(jsonPath("$.user_id").value(userId.toString()));
	}

	@Test
	void returns200WithExistsFalseValidFalseWhenCredentialsDoNotMatch() throws Exception {
		when(userService.verifyCredentials(anyString(), anyString()))
				.thenReturn(new UserVerificationResponse(false, false, null));

		mockMvc.perform(post("/internal/users/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"user@test.com","password":"wrong-password"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.exists").value(false))
				.andExpect(jsonPath("$.valid").value(false))
				.andExpect(jsonPath("$.user_id").doesNotExist());
	}

	@Test
	void returns400WhenVerifyEmailIsMissingOrInvalid() throws Exception {
		mockMvc.perform(post("/internal/users/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"not-an-email","password":"senha123"}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void returns400WhenVerifyPasswordIsBlank() throws Exception {
		mockMvc.perform(post("/internal/users/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"user@test.com","password":""}
								"""))
				.andExpect(status().isBadRequest());
	}
}
