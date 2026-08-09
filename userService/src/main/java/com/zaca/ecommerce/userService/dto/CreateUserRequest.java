package com.zaca.ecommerce.userService.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateUserRequest(
		@NotBlank(message = "name is required") String name,

		@NotBlank(message = "email is required") @Email(message = "email must be valid") String email,

		@NotBlank(message = "password is required") @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
				message = "password must be at least 8 characters and contain at least one letter and one number")
		String password) {
}
