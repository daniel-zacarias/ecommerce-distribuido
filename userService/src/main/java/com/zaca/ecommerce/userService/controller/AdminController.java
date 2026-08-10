package com.zaca.ecommerce.userService.controller;

import com.zaca.ecommerce.userService.dto.CreateUserRequest;
import com.zaca.ecommerce.userService.dto.MessageResponse;
import com.zaca.ecommerce.userService.dto.UserResponse;
import com.zaca.ecommerce.userService.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class AdminController {

	private final UserService userService;

	public AdminController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping("/admin/users")
	public ResponseEntity<UserResponse> createAdminAsAdmin(
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
			@Valid @RequestBody CreateUserRequest request) {
		UserResponse response = userService.createAdminAsAdmin(
				authorizationHeader, request.name(), request.email(), request.password());
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/admin/users/{id}")
	public ResponseEntity<UserResponse> getUserByIdAsAdmin(
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
			@PathVariable UUID id) {
		UserResponse response = userService.getUserForAdmin(authorizationHeader, id);
		return ResponseEntity.ok(response);
	}

	@DeleteMapping("/admin/users/{id}")
	public ResponseEntity<MessageResponse> deleteUserAsAdmin(
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
			@PathVariable UUID id) {
		userService.deleteUserAsAdmin(authorizationHeader, id);
		return ResponseEntity.ok(new MessageResponse("User deleted successfully"));
	}
}
