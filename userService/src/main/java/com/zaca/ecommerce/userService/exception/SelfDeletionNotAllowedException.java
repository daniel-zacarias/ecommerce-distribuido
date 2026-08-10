package com.zaca.ecommerce.userService.exception;

public class SelfDeletionNotAllowedException extends RuntimeException {

	public SelfDeletionNotAllowedException(String message) {
		super(message);
	}
}
