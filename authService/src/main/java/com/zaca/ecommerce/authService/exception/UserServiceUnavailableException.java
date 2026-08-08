package com.zaca.ecommerce.authService.exception;

public class UserServiceUnavailableException extends RuntimeException {

	public UserServiceUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
