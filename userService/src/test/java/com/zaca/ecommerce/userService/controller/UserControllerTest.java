package com.zaca.ecommerce.userService.controller;

import com.zaca.ecommerce.userService.dto.UserResponse;
import com.zaca.ecommerce.userService.exception.DuplicateEmailException;
import com.zaca.ecommerce.userService.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@Test
	void returns201WhenRegistrationSucceeds() throws Exception {
		UUID id = UUID.randomUUID();
		when(userService.register(anyString(), anyString(), anyString()))
				.thenReturn(new UserResponse(id, "Daniel", "daniel@test.com", Instant.now()));

		mockMvc.perform(post("/users")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Daniel","email":"daniel@test.com","password":"senha123"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("daniel@test.com"));
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
								{"name":"Daniel","email":"daniel@test.com","password":"abc"}
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
								{"name":"Daniel","email":"daniel@test.com","password":"senha123"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Email already in use"));
	}
}
