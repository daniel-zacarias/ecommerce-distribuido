package com.zaca.ecommerce.authService.dto;

public record UserValidationResponse(String id, String status) {

	private static final String ACTIVE_STATUS = "ACTIVE";

	public boolean isActive() {
		return ACTIVE_STATUS.equalsIgnoreCase(status);
	}
}
