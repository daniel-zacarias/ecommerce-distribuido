package com.zaca.ecommerce.authService.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

	@Bean
	public RestClient userServiceRestClient(
			@Value("${user-service.base-url}") String baseUrl,
			@Value("${user-service.connect-timeout-ms}") long connectTimeoutMs,
			@Value("${user-service.read-timeout-ms}") long readTimeoutMs,
			@Value("${internal-api.key}") String internalApiKey) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
		requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

		return RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				.defaultHeader("X-API-KEY", internalApiKey)
				.build();
	}
}
