package com.zaca.ecommerce.authService.dto;

public record UserValidationResponse(boolean exists, boolean valid, String user_id, Role role) {
}
