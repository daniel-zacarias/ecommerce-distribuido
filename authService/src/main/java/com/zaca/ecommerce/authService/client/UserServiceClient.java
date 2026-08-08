package com.zaca.ecommerce.authService.client;

import com.zaca.ecommerce.authService.dto.UserValidationResponse;

public interface UserServiceClient {

	UserValidationResponse validateCredentials(String username, String password);
}
