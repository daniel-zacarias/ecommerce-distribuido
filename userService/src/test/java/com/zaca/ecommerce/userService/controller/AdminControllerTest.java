package com.zaca.ecommerce.userService.controller;

import com.zaca.ecommerce.userService.dto.UserResponse;
import com.zaca.ecommerce.userService.exception.DuplicateEmailException;
import com.zaca.ecommerce.userService.exception.ForbiddenException;
import com.zaca.ecommerce.userService.exception.InvalidTokenException;
import com.zaca.ecommerce.userService.exception.SelfDeletionNotAllowedException;
import com.zaca.ecommerce.userService.exception.UserNotFoundException;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@Test
	void returns201WhenAdminCreatesAdmin() throws Exception {
		UUID id = UUID.randomUUID();
		when(userService.createAdminAsAdmin(anyString(), anyString(), anyString(), anyString()))
				.thenReturn(new UserResponse(id, "New Admin", "new-admin@test.com", Instant.now()));

		mockMvc.perform(post("/admin/users")
						.header("Authorization", "Bearer admin-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"New Admin","email":"new-admin@test.com","password":"senha123"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("new-admin@test.com"))
				.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void returns409WhenAdminCreatesUserWithExistingEmail() throws Exception {
		when(userService.createAdminAsAdmin(anyString(), anyString(), anyString(), anyString()))
				.thenThrow(new DuplicateEmailException("Email already in use"));

		mockMvc.perform(post("/admin/users")
						.header("Authorization", "Bearer admin-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"New Admin","email":"new-admin@test.com","password":"senha123"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Email already in use"));
	}

	@Test
	void returns403WhenNonAdminCreatesAdmin() throws Exception {
		when(userService.createAdminAsAdmin(anyString(), anyString(), anyString(), anyString()))
				.thenThrow(new ForbiddenException("Admin role required"));

		mockMvc.perform(post("/admin/users")
						.header("Authorization", "Bearer user-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"New Admin","email":"new-admin@test.com","password":"senha123"}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Admin role required"));
	}

	@Test
	void returns401WhenCreateAdminTokenIsInvalidOrMissing() throws Exception {
		when(userService.createAdminAsAdmin(anyString(), anyString(), anyString(), anyString()))
				.thenThrow(new InvalidTokenException("Invalid or expired token"));

		mockMvc.perform(post("/admin/users")
						.header("Authorization", "Bearer invalid-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"New Admin","email":"new-admin@test.com","password":"senha123"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid or expired token"));
	}

	@Test
	void returns400WhenCreateAdminRequestIsInvalid() throws Exception {
		mockMvc.perform(post("/admin/users")
						.header("Authorization", "Bearer admin-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"New Admin","email":"not-an-email","password":"senha123"}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void returns200WithUserDataWhenAdminQueriesExistingUser() throws Exception {
		UUID targetId = UUID.randomUUID();
		when(userService.getUserForAdmin(anyString(), any(UUID.class)))
				.thenReturn(new UserResponse(targetId, "Target User", "target@test.com", Instant.now()));

		mockMvc.perform(get("/admin/users/" + targetId).header("Authorization", "Bearer admin-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Target User"))
				.andExpect(jsonPath("$.email").value("target@test.com"))
				.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void returns404WhenAdminQueriesNonExistingUser() throws Exception {
		UUID targetId = UUID.randomUUID();
		when(userService.getUserForAdmin(anyString(), any(UUID.class)))
				.thenThrow(new UserNotFoundException("User not found"));

		mockMvc.perform(get("/admin/users/" + targetId).header("Authorization", "Bearer admin-token"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("User not found"));
	}

	@Test
	void returns403WhenNonAdminQueriesUser() throws Exception {
		UUID targetId = UUID.randomUUID();
		when(userService.getUserForAdmin(anyString(), any(UUID.class)))
				.thenThrow(new ForbiddenException("Admin role required"));

		mockMvc.perform(get("/admin/users/" + targetId).header("Authorization", "Bearer user-token"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Admin role required"));
	}

	@Test
	void returns401WhenAdminQueryTokenIsInvalidOrMissing() throws Exception {
		UUID targetId = UUID.randomUUID();
		when(userService.getUserForAdmin(anyString(), any(UUID.class)))
				.thenThrow(new InvalidTokenException("Invalid or expired token"));

		mockMvc.perform(get("/admin/users/" + targetId).header("Authorization", "Bearer invalid-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid or expired token"));
	}

	@Test
	void returns200WhenAdminDeletesExistingUser() throws Exception {
		UUID targetId = UUID.randomUUID();
		doNothing().when(userService).deleteUserAsAdmin(anyString(), any(UUID.class));

		mockMvc.perform(delete("/admin/users/" + targetId).header("Authorization", "Bearer admin-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("User deleted successfully"));
	}

	@Test
	void returns404WhenAdminDeletesNonExistingUser() throws Exception {
		UUID targetId = UUID.randomUUID();
		doThrow(new UserNotFoundException("User not found"))
				.when(userService).deleteUserAsAdmin(anyString(), any(UUID.class));

		mockMvc.perform(delete("/admin/users/" + targetId).header("Authorization", "Bearer admin-token"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("User not found"));
	}

	@Test
	void returns403WhenNonAdminDeletesUser() throws Exception {
		UUID targetId = UUID.randomUUID();
		doThrow(new ForbiddenException("Admin role required"))
				.when(userService).deleteUserAsAdmin(anyString(), any(UUID.class));

		mockMvc.perform(delete("/admin/users/" + targetId).header("Authorization", "Bearer user-token"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Admin role required"));
	}

	@Test
	void returns400WhenAdminDeletesOwnAccount() throws Exception {
		UUID targetId = UUID.randomUUID();
		doThrow(new SelfDeletionNotAllowedException("Admins cannot delete their own account"))
				.when(userService).deleteUserAsAdmin(anyString(), any(UUID.class));

		mockMvc.perform(delete("/admin/users/" + targetId).header("Authorization", "Bearer admin-token"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Admins cannot delete their own account"));
	}

	@Test
	void returns401WhenDeleteTokenIsInvalidOrMissing() throws Exception {
		UUID targetId = UUID.randomUUID();
		doThrow(new InvalidTokenException("Invalid or expired token"))
				.when(userService).deleteUserAsAdmin(anyString(), any(UUID.class));

		mockMvc.perform(delete("/admin/users/" + targetId).header("Authorization", "Bearer invalid-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid or expired token"));
	}
}
