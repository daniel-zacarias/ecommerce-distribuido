package com.zaca.ecommerce.userService.client;

import com.zaca.ecommerce.userService.dto.TokenValidationResponse;

public interface AuthServiceClient {

	TokenValidationResponse validate(String authorizationHeader);
}
