package com.zaca.ecommerce.authService.service;

import com.zaca.ecommerce.authService.config.JwtProperties;
import com.zaca.ecommerce.authService.dto.Role;
import com.zaca.ecommerce.authService.exception.InvalidTokenException;
import com.zaca.ecommerce.authService.exception.TokenExpiredException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

	private static final String SECRET = "test-secret-with-at-least-32-bytes-of-length";

	private final JwtProperties jwtProperties = new JwtProperties(SECRET, 30, 10080);
	private final JwtService jwtService = new JwtService(jwtProperties);

	@Test
	void accessTokenContainsExpirationAndUniqueId() {
		JwtService.GeneratedToken token = jwtService.generateAccessToken("user-id-1", Role.USER);

		Claims claims = parseClaims(token.token());

		assertThat(claims.getSubject()).isEqualTo("user-id-1");
		assertThat(claims.getId()).isNotBlank();
		assertThat(claims.getId()).isEqualTo(token.jti());
		assertThat(claims.get("type", String.class)).isEqualTo("access");
		assertThat(claims.get("role", String.class)).isEqualTo("USER");
		assertThat(claims.getExpiration().toInstant()).isEqualTo(token.expiresAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
		assertThat(token.expiresAt()).isAfter(Instant.now().plusSeconds(1700));
		assertThat(token.expiresAt()).isBefore(Instant.now().plusSeconds(1900));
	}

	@Test
	void validateReturnsClaimsForValidToken() {
		JwtService.GeneratedToken token = jwtService.generateAccessToken("user-id-1", Role.ADMIN);

		JwtService.TokenClaims claims = jwtService.validate(token.token());

		assertThat(claims.subject()).isEqualTo("user-id-1");
		assertThat(claims.tokenType()).isEqualTo("access");
		assertThat(claims.jti()).isEqualTo(token.jti());
		assertThat(claims.expiresAt()).isEqualTo(token.expiresAt().truncatedTo(ChronoUnit.SECONDS));
		assertThat(claims.role()).isEqualTo(Role.ADMIN);
	}

	@Test
	void validateThrowsTokenExpiredForExpiredToken() {
		SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
		Instant past = Instant.now().minusSeconds(60);
		String expiredToken = Jwts.builder()
				.subject("user-id-1")
				.id(UUID.randomUUID().toString())
				.claim("type", "access")
				.issuedAt(Date.from(past.minusSeconds(60)))
				.expiration(Date.from(past))
				.signWith(key)
				.compact();

		assertThatThrownBy(() -> jwtService.validate(expiredToken))
				.isInstanceOf(TokenExpiredException.class);
	}

	@Test
	void validateThrowsInvalidTokenForBadSignature() {
		SecretKey otherKey = Keys.hmacShaKeyFor("a-completely-different-secret-of-32-bytes".getBytes(StandardCharsets.UTF_8));
		String tokenSignedWithOtherKey = Jwts.builder()
				.subject("user-id-1")
				.claim("type", "access")
				.expiration(Date.from(Instant.now().plusSeconds(60)))
				.signWith(otherKey)
				.compact();

		assertThatThrownBy(() -> jwtService.validate(tokenSignedWithOtherKey))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void validateThrowsInvalidTokenForMalformedToken() {
		assertThatThrownBy(() -> jwtService.validate("not-a-jwt"))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void validateThrowsInvalidTokenForBlankToken() {
		assertThatThrownBy(() -> jwtService.validate(""))
				.isInstanceOf(InvalidTokenException.class);
		assertThatThrownBy(() -> jwtService.validate(null))
				.isInstanceOf(InvalidTokenException.class);
	}

	private Claims parseClaims(String token) {
		SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
		return Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
}
