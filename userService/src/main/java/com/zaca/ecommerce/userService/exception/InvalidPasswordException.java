package com.zaca.ecommerce.userService.exception;

public class InvalidPasswordException extends RuntimeException {

	public InvalidPasswordException(String message) {
		super(message);
	}
}
