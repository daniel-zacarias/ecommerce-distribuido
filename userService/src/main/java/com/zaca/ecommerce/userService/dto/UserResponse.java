package com.zaca.ecommerce.userService.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, Instant created_at) {
}
