package com.zaca.ecommerce.userService.dto;

import jakarta.validation.constraints.Email;

public record UpdateUserRequest(
		String name,

		@Email(message = "email must be valid") String email) {
}
