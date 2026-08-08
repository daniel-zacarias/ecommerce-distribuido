package com.zaca.ecommerce.authService.service;

import com.zaca.ecommerce.authService.config.JwtProperties;
import com.zaca.ecommerce.authService.exception.InvalidTokenException;
import com.zaca.ecommerce.authService.exception.TokenExpiredException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

	private final JwtProperties jwtProperties;
	private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
		this.jwtProperties = jwtProperties;
		this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
	}

	public GeneratedToken generateAccessToken(String subject) {
		return generateToken(subject, jwtProperties.accessTokenExpirationMinutes());
	}

	private GeneratedToken generateToken(String subject, long expirationMinutes) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);

		String jti = UUID.randomUUID().toString();
        String tokenType = "access";
        String token = Jwts.builder()
				.subject(subject)
				.id(jti)
				.claim("type", tokenType)
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.signWith(signingKey)
				.compact();

		return new GeneratedToken(token, jti, expiresAt);
	}

	public TokenClaims validate(String token) {
		if (!StringUtils.hasText(token)) {
			throw new InvalidTokenException("Token is missing");
		}

		try {
			Claims claims = Jwts.parser()
					.verifyWith(signingKey)
					.build()
					.parseSignedClaims(token)
					.getPayload();

			return new TokenClaims(claims.getSubject(), claims.get("type", String.class), claims.getId(),
					claims.getExpiration().toInstant());
		} catch (ExpiredJwtException ex) {
			throw new TokenExpiredException("Token has expired");
		} catch (JwtException | IllegalArgumentException ex) {
			throw new InvalidTokenException("Token is invalid");
		}
	}

	public record GeneratedToken(String token, String jti, Instant expiresAt) {
	}

	public record TokenClaims(String subject, String tokenType, String jti, Instant expiresAt) {
	}
}
