package com.zaca.ecommerce.authService.client;

import com.zaca.ecommerce.authService.dto.UserValidationResponse;
import com.zaca.ecommerce.authService.exception.UserServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class UserServiceClientImplTest {

	private MockRestServiceServer mockServer;
	private UserServiceClientImpl client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://userservice.test");
		mockServer = MockRestServiceServer.bindTo(builder).build();
		client = new UserServiceClientImpl(builder.build());
	}

	@Test
	void returnsValidationResponseWhenUserServiceRespondsSuccessfully() {
		mockServer.expect(requestTo("http://userservice.test/internal/users/verify"))
				.andExpect(method(org.springframework.http.HttpMethod.POST))
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.email").value("user@test.com"))
				.andExpect(jsonPath("$.password").value("123456"))
				.andRespond(withSuccess(
						"""
								{"exists":true,"valid":true,"user_id":"11111111-1111-1111-1111-111111111111"}
								""",
						MediaType.APPLICATION_JSON));

		UserValidationResponse response = client.validateCredentials("user@test.com", "123456");

		assertThat(response.exists()).isTrue();
		assertThat(response.valid()).isTrue();
		assertThat(response.user_id()).isEqualTo("11111111-1111-1111-1111-111111111111");
		mockServer.verify();
	}

	@Test
	void throwsUserServiceUnavailableWhenUserServiceReturnsServerError() {
		mockServer.expect(requestTo("http://userservice.test/internal/users/verify"))
				.andRespond(withServerError());

		assertThatThrownBy(() -> client.validateCredentials("user@test.com", "123456"))
				.isInstanceOf(UserServiceUnavailableException.class);
	}

	@Test
	void throwsUserServiceUnavailableWhenApiKeyIsRejected() {
		mockServer.expect(requestTo("http://userservice.test/internal/users/verify"))
				.andRespond(withUnauthorizedRequest());

		assertThatThrownBy(() -> client.validateCredentials("user@test.com", "123456"))
				.isInstanceOf(UserServiceUnavailableException.class);
	}

	@Test
	void throwsUserServiceUnavailableWhenConnectionFails() {
		mockServer.expect(requestTo("http://userservice.test/internal/users/verify"))
				.andRespond(request -> {
					throw new IOException("connection refused");
				});

		assertThatThrownBy(() -> client.validateCredentials("user@test.com", "123456"))
				.isInstanceOf(UserServiceUnavailableException.class);
	}
}
