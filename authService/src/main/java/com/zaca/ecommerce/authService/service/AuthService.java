package com.zaca.ecommerce.authService.service;

import com.zaca.ecommerce.authService.client.UserServiceClient;
import com.zaca.ecommerce.authService.dto.AuthResponse;
import com.zaca.ecommerce.authService.dto.TokenValidationResponse;
import com.zaca.ecommerce.authService.dto.UserValidationResponse;
import com.zaca.ecommerce.authService.exception.InvalidCredentialsException;
import com.zaca.ecommerce.authService.exception.InvalidTokenException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

	private final UserServiceClient userServiceClient;
	private final JwtService jwtService;

	public AuthService(UserServiceClient userServiceClient, JwtService jwtService) {
		this.userServiceClient = userServiceClient;
		this.jwtService = jwtService;
	}

	public AuthResponse authenticate(String username, String password) {
		UserValidationResponse validationResponse = userServiceClient.validateCredentials(username, password);

		if (!validationResponse.isActive()) {
			throw new InvalidCredentialsException("Account is not active");
		}

		JwtService.GeneratedToken accessToken = jwtService.generateAccessToken(validationResponse.id());
		JwtService.GeneratedToken refreshToken = jwtService.generateRefreshToken(validationResponse.id());

		return new AuthResponse(accessToken.token(), refreshToken.token(), accessToken.expiresAt());
	}

	public TokenValidationResponse validateToken(String authorizationHeader) {
		if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
			throw new InvalidTokenException("Authorization header is missing or malformed");
		}

		String token = authorizationHeader.substring("Bearer ".length());
		JwtService.TokenClaims claims = jwtService.validate(token);

		return new TokenValidationResponse(claims.subject(), claims.tokenType(), claims.expiresAt());
	}
}
