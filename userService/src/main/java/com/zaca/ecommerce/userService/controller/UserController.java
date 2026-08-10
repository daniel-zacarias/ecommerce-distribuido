package com.zaca.ecommerce.userService.controller;

import com.zaca.ecommerce.userService.dto.CreateUserRequest;
import com.zaca.ecommerce.userService.dto.UserResponse;
import com.zaca.ecommerce.userService.dto.UserVerificationRequest;
import com.zaca.ecommerce.userService.dto.UserVerificationResponse;
import com.zaca.ecommerce.userService.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping("/users")
	public ResponseEntity<UserResponse> register(@Valid @RequestBody CreateUserRequest request) {
		UserResponse response = userService.register(request.name(), request.email(), request.password());
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/me")
	public ResponseEntity<UserResponse> me(
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
		UserResponse response = userService.getCurrentUser(authorizationHeader);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/internal/users/verify")
	public ResponseEntity<UserVerificationResponse> verify(@Valid @RequestBody UserVerificationRequest request) {
		UserVerificationResponse response = userService.verifyCredentials(request.email(), request.password());
		return ResponseEntity.ok(response);
	}
}
