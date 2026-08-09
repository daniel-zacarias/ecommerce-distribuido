package com.zaca.ecommerce.userService.service;

import com.zaca.ecommerce.userService.dto.UserResponse;
import com.zaca.ecommerce.userService.entity.User;
import com.zaca.ecommerce.userService.exception.DuplicateEmailException;
import com.zaca.ecommerce.userService.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public UserResponse register(String name, String email, String rawPassword) {
		if (userRepository.existsByEmail(email)) {
			throw new DuplicateEmailException("Email already in use");
		}

		Instant now = Instant.now();
		User user = User.builder()
				.id(UUID.randomUUID())
				.name(name)
				.email(email)
				.password(passwordEncoder.encode(rawPassword))
				.createdAt(now)
				.updatedAt(now)
				.build();

		User saved = userRepository.save(user);
		return new UserResponse(saved.getId(), saved.getName(), saved.getEmail(), saved.getCreatedAt());
	}
}
