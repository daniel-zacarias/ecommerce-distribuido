package com.zaca.ecommerce.authService.client;

import com.zaca.ecommerce.authService.dto.UserValidationRequest;
import com.zaca.ecommerce.authService.dto.UserValidationResponse;
import com.zaca.ecommerce.authService.exception.UserServiceUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class UserServiceClientImpl implements UserServiceClient {

	private static final String VALIDATE_CREDENTIALS_PATH = "/internal/users/verify";

	private final RestClient userServiceRestClient;

	public UserServiceClientImpl(RestClient userServiceRestClient) {
		this.userServiceRestClient = userServiceRestClient;
	}

	@Override
	public UserValidationResponse validateCredentials(String username, String password) {
		try {
			return userServiceRestClient.post()
					.uri(VALIDATE_CREDENTIALS_PATH)
					.body(new UserValidationRequest(username, password))
					.retrieve()
					.body(UserValidationResponse.class);
		} catch (RestClientException ex) {
			throw new UserServiceUnavailableException("User service did not respond successfully", ex);
		}
	}
}
