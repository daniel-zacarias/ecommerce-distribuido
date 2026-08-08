package com.zaca.ecommerce.authService.controller;

import com.zaca.ecommerce.authService.dto.AuthResponse;
import com.zaca.ecommerce.authService.dto.LoginRequest;
import com.zaca.ecommerce.authService.dto.RefreshRequest;
import com.zaca.ecommerce.authService.dto.TokenValidationResponse;
import com.zaca.ecommerce.authService.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/auth")
	public ResponseEntity<AuthResponse> authenticate(@Valid @RequestBody LoginRequest loginRequest) {
		AuthResponse response = authService.authenticate(loginRequest.username(), loginRequest.password());
		return ResponseEntity.ok(response);
	}

	@PostMapping("/auth/validate")
	public ResponseEntity<TokenValidationResponse> validate(
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
		TokenValidationResponse response = authService.validateToken(authorizationHeader);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/auth/refresh")
	public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest refreshRequest) {
		AuthResponse response = authService.refresh(refreshRequest.refresh_token());
		return ResponseEntity.ok(response);
	}
}
