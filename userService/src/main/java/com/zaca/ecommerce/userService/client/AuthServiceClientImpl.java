package com.zaca.ecommerce.userService.client;

import com.zaca.ecommerce.userService.dto.TokenValidationResponse;
import com.zaca.ecommerce.userService.exception.AuthServiceUnavailableException;
import com.zaca.ecommerce.userService.exception.InvalidTokenException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class AuthServiceClientImpl implements AuthServiceClient {

	private static final String VALIDATE_PATH = "/auth/validate";
	private static final String REVOKE_SESSIONS_PATH = "/internal/auth/users/{userId}/revoke-sessions";

	private final RestClient authServiceRestClient;

	public AuthServiceClientImpl(RestClient authServiceRestClient) {
		this.authServiceRestClient = authServiceRestClient;
	}

	@Override
	public TokenValidationResponse validate(String authorizationHeader) {
		try {
			return authServiceRestClient.post()
					.uri(VALIDATE_PATH)
					.header(HttpHeaders.AUTHORIZATION, authorizationHeader)
					.retrieve()
					.body(TokenValidationResponse.class);
		} catch (HttpClientErrorException ex) {
			throw new InvalidTokenException("Invalid or expired token");
		} catch (HttpServerErrorException | ResourceAccessException ex) {
			throw new AuthServiceUnavailableException("Auth service did not respond successfully", ex);
		} catch (RestClientException ex) {
			throw new AuthServiceUnavailableException("Unexpected error while calling auth service", ex);
		}
	}

	@Override
	public void revokeSessions(UUID userId) {
		try {
			authServiceRestClient.post()
					.uri(REVOKE_SESSIONS_PATH, userId)
					.retrieve()
					.toBodilessEntity();
		} catch (HttpServerErrorException | ResourceAccessException ex) {
			throw new AuthServiceUnavailableException("Auth service did not respond successfully", ex);
		} catch (RestClientException ex) {
			throw new AuthServiceUnavailableException("Unexpected error while calling auth service", ex);
		}
	}
}
