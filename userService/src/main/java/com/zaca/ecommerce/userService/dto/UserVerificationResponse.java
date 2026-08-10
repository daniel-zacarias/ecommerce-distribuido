package com.zaca.ecommerce.userService.dto;

import java.util.UUID;

public record UserVerificationResponse(boolean exists, boolean valid, UUID user_id) {
}
