package com.zaca.ecommerce.authService.controller;

import com.zaca.ecommerce.authService.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalController.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@Test
	void returns204WhenRevokingSessionsForUser() throws Exception {
		doNothing().when(authService).revokeSessionsForUser("user-1");

		mockMvc.perform(post("/internal/auth/users/user-1/revoke-sessions"))
				.andExpect(status().isNoContent());

		verify(authService).revokeSessionsForUser(eq("user-1"));
	}
}
