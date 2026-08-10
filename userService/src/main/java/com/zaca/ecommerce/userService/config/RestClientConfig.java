package com.zaca.ecommerce.userService.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

	@Bean
	public RestClient authServiceRestClient(
			@Value("${auth-service.base-url}") String baseUrl,
			@Value("${auth-service.connect-timeout-ms}") long connectTimeoutMs,
			@Value("${auth-service.read-timeout-ms}") long readTimeoutMs) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
		requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

		return RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				.build();
	}
}
