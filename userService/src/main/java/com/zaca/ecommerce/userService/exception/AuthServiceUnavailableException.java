package com.zaca.ecommerce.userService.exception;

public class AuthServiceUnavailableException extends RuntimeException {

	public AuthServiceUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
