package com.zaca.ecommerce.authService.exception;

public class RefreshTokenReusedException extends RuntimeException {

	public RefreshTokenReusedException(String message) {
		super(message);
	}
}
