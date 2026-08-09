package com.zaca.ecommerce.userService.controller;

import com.zaca.ecommerce.userService.dto.CreateUserRequest;
import com.zaca.ecommerce.userService.dto.UserResponse;
import com.zaca.ecommerce.userService.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
}
