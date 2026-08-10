package com.zaca.ecommerce.userService.exception;

public class InvalidTokenException extends RuntimeException {

	public InvalidTokenException(String message) {
		super(message);
	}
}
