package com.zaca.ecommerce.authService.controller;

import com.zaca.ecommerce.authService.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalController {

	private final AuthService authService;

	public InternalController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/internal/auth/users/{userId}/revoke-sessions")
	public ResponseEntity<Void> revokeSessions(@PathVariable String userId) {
		authService.revokeSessionsForUser(userId);
		return ResponseEntity.noContent().build();
	}
}
