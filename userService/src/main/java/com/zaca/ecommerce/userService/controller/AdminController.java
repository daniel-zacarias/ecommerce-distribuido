package com.zaca.ecommerce.userService.controller;

import com.zaca.ecommerce.userService.dto.MessageResponse;
import com.zaca.ecommerce.userService.dto.UserResponse;
import com.zaca.ecommerce.userService.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class AdminController {

	private final UserService userService;

	public AdminController(UserService userService) {
		this.userService = userService;
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
