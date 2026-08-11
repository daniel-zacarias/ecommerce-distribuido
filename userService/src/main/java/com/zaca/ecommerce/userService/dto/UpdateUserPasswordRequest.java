package com.zaca.ecommerce.userService.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateUserPasswordRequest(
		@NotBlank(message = "password is required") String password,

		@NotBlank(message = "newPassword is required") @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
				message = "newPassword must be at least 8 characters and contain at least one letter and one number")
		String newPassword) {
}
