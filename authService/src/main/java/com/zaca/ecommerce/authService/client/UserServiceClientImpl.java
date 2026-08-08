package com.zaca.ecommerce.authService.client;

import com.zaca.ecommerce.authService.dto.UserValidationRequest;
import com.zaca.ecommerce.authService.dto.UserValidationResponse;
import com.zaca.ecommerce.authService.exception.InvalidCredentialsException;
import com.zaca.ecommerce.authService.exception.UserServiceUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class UserServiceClientImpl implements UserServiceClient {

	private static final String VALIDATE_CREDENTIALS_PATH = "/internal/users/validate-credentials";

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
		} catch (HttpClientErrorException ex) {
			throw new InvalidCredentialsException("User service rejected the provided credentials");
		} catch (HttpServerErrorException | ResourceAccessException ex) {
			throw new UserServiceUnavailableException("User service did not respond successfully", ex);
		} catch (RestClientException ex) {
			throw new UserServiceUnavailableException("Unexpected error while calling user service", ex);
		}
	}
}
