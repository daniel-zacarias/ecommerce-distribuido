package com.zaca.ecommerce.userService.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalApiKeyFilterTest {

	private static final String EXPECTED_KEY = "expected-key";

	@Mock
	private HttpServletRequest request;

	@Mock
	private HttpServletResponse response;

	@Mock
	private FilterChain filterChain;

	private InternalApiKeyFilter filter;

	@BeforeEach
	void setUp() {
		filter = new InternalApiKeyFilter(EXPECTED_KEY, new ObjectMapper());
	}

	@Test
	void shouldNotFilterReturnsTrueForNonInternalPaths() {
		when(request.getRequestURI()).thenReturn("/users");

		assertThat(filter.shouldNotFilter(request)).isTrue();
	}

	@Test
	void shouldNotFilterReturnsFalseForInternalPaths() {
		when(request.getRequestURI()).thenReturn("/internal/users/verify");

		assertThat(filter.shouldNotFilter(request)).isFalse();
	}

	@Test
	void passesRequestThroughWhenApiKeyMatches() throws Exception {
		when(request.getHeader("X-API-KEY")).thenReturn(EXPECTED_KEY);

		filter.doFilterInternal(request, response, filterChain);

		verify(filterChain).doFilter(request, response);
		verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
	}

	@Test
	void rejectsWithUnauthorizedWhenApiKeyHeaderIsMissing() throws Exception {
		when(request.getHeader("X-API-KEY")).thenReturn(null);
		StringWriter body = new StringWriter();
		when(response.getWriter()).thenReturn(new PrintWriter(body));

		filter.doFilterInternal(request, response, filterChain);

		verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		verify(filterChain, never()).doFilter(request, response);
		assertThat(body.toString()).contains("message");
	}

	@Test
	void rejectsWithUnauthorizedWhenApiKeyIsWrong() throws Exception {
		when(request.getHeader("X-API-KEY")).thenReturn("wrong-key");
		StringWriter body = new StringWriter();
		when(response.getWriter()).thenReturn(new PrintWriter(body));

		filter.doFilterInternal(request, response, filterChain);

		verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		verify(filterChain, never()).doFilter(request, response);
		assertThat(body.toString()).contains("message");
	}
}
