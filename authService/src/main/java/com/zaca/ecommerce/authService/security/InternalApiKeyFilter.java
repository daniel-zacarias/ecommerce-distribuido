package com.zaca.ecommerce.authService.security;

import com.zaca.ecommerce.authService.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

	private static final String HEADER_NAME = "X-API-KEY";
	private static final String PROTECTED_PATH_PREFIX = "/internal/";

	private final String expectedApiKey;
	private final ObjectMapper objectMapper;

	public InternalApiKeyFilter(@Value("${internal-api.key}") String expectedApiKey, ObjectMapper objectMapper) {
		this.expectedApiKey = expectedApiKey;
		this.objectMapper = objectMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith(PROTECTED_PATH_PREFIX);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String providedKey = request.getHeader(HEADER_NAME);

		if (providedKey == null || !MessageDigest.isEqual(
				providedKey.getBytes(StandardCharsets.UTF_8), expectedApiKey.getBytes(StandardCharsets.UTF_8))) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse("Invalid or missing API key")));
			return;
		}

		filterChain.doFilter(request, response);
	}
}
