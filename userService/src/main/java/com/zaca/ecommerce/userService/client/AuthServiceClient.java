package com.zaca.ecommerce.userService.client;

import com.zaca.ecommerce.userService.dto.TokenValidationResponse;

import java.util.UUID;

public interface AuthServiceClient {

	TokenValidationResponse validate(String authorizationHeader);

	void revokeSessions(UUID userId);
}
