package com.zaca.ecommerce.userService.dto;

import com.zaca.ecommerce.userService.entity.Role;

import java.util.UUID;

public record UserVerificationResponse(boolean exists, boolean valid, UUID user_id, Role role) {
}
