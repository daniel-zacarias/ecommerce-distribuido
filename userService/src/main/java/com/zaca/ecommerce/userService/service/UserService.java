package com.zaca.ecommerce.userService.service;

import com.zaca.ecommerce.userService.client.AuthServiceClient;
import com.zaca.ecommerce.userService.dto.TokenValidationResponse;
import com.zaca.ecommerce.userService.dto.UserResponse;
import com.zaca.ecommerce.userService.dto.UserVerificationResponse;
import com.zaca.ecommerce.userService.entity.Role;
import com.zaca.ecommerce.userService.entity.User;
import com.zaca.ecommerce.userService.exception.DuplicateEmailException;
import com.zaca.ecommerce.userService.exception.ForbiddenException;
import com.zaca.ecommerce.userService.exception.InvalidPasswordException;
import com.zaca.ecommerce.userService.exception.InvalidTokenException;
import com.zaca.ecommerce.userService.exception.NoUpdatableFieldsException;
import com.zaca.ecommerce.userService.exception.SelfDeletionNotAllowedException;
import com.zaca.ecommerce.userService.exception.UserNotFoundException;
import com.zaca.ecommerce.userService.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

	// Valid BCrypt hash with no known matching plaintext; used so the encoder
	// always performs real work even when no user is found, keeping the
	// "user not found" and "wrong password" response times indistinguishable.
	private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthServiceClient authServiceClient;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			AuthServiceClient authServiceClient) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.authServiceClient = authServiceClient;
	}

	public UserResponse register(String name, String email, String rawPassword) {
		return createUser(name, email, rawPassword, Role.USER);
	}

	public UserResponse createAdminAsAdmin(String authorizationHeader, String name, String email,
			String rawPassword) {
		User caller = resolveCurrentUser(authorizationHeader);
		if (caller.getRole() != Role.ADMIN) {
			throw new ForbiddenException("Admin role required");
		}

		return createUser(name, email, rawPassword, Role.ADMIN);
	}

	private UserResponse createUser(String name, String email, String rawPassword, Role role) {
		if (userRepository.existsByEmail(email)) {
			throw new DuplicateEmailException("Email already in use");
		}

		Instant now = Instant.now();
		User user = User.builder()
				.id(UUID.randomUUID())
				.name(name)
				.email(email)
				.password(passwordEncoder.encode(rawPassword))
				.role(role)
				.createdAt(now)
				.updatedAt(now)
				.build();

		User saved = userRepository.save(user);
		return new UserResponse(saved.getId(), saved.getName(), saved.getEmail(), saved.getCreatedAt());
	}

	public UserResponse getCurrentUser(String authorizationHeader) {
		User user = resolveCurrentUser(authorizationHeader);
		return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
	}

	public void updateCurrentUser(String authorizationHeader, String name, String email) {
		User user = resolveCurrentUser(authorizationHeader);

		boolean hasName = StringUtils.hasText(name);
		boolean hasEmail = StringUtils.hasText(email);

		if (!hasName && !hasEmail) {
			throw new NoUpdatableFieldsException("At least one field (name or email) must be provided");
		}

		if (hasEmail && !email.equalsIgnoreCase(user.getEmail())
				&& userRepository.existsByEmailAndIdNot(email, user.getId())) {
			throw new DuplicateEmailException("Email already in use");
		}

		if (hasName) {
			user.setName(name);
		}
		if (hasEmail) {
			user.setEmail(email);
		}
		user.setUpdatedAt(Instant.now());
		userRepository.save(user);
	}

	public UserResponse getUserForAdmin(String authorizationHeader, UUID targetId) {
		User caller = resolveCurrentUser(authorizationHeader);
		if (caller.getRole() != Role.ADMIN) {
			throw new ForbiddenException("Admin role required");
		}

		User target = userRepository.findById(targetId)
				.filter(user -> user.getDeletedAt() == null)
				.orElseThrow(() -> new UserNotFoundException("User not found"));

		return new UserResponse(target.getId(), target.getName(), target.getEmail(), target.getCreatedAt());
	}

	public void deleteUserAsAdmin(String authorizationHeader, UUID targetId) {
		User caller = resolveCurrentUser(authorizationHeader);
		if (caller.getRole() != Role.ADMIN) {
			throw new ForbiddenException("Admin role required");
		}
		if (caller.getId().equals(targetId)) {
			throw new SelfDeletionNotAllowedException("Admins cannot delete their own account");
		}

		User target = userRepository.findById(targetId)
				.filter(user -> user.getDeletedAt() == null)
				.orElseThrow(() -> new UserNotFoundException("User not found"));

		authServiceClient.revokeSessions(target.getId());

		target.setDeletedAt(Instant.now());
		userRepository.save(target);
	}

	private User resolveCurrentUser(String authorizationHeader) {
		if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
			throw new InvalidTokenException("Authorization header is missing or malformed");
		}

		TokenValidationResponse validation = authServiceClient.validate(authorizationHeader);
		UUID userId = UUID.fromString(validation.user_id());

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new InvalidTokenException("Invalid or expired token"));

		if (user.getDeletedAt() != null) {
			throw new InvalidTokenException("Invalid or expired token");
		}

		return user;
	}

	public UserVerificationResponse verifyCredentials(String email, String rawPassword) {
		Optional<User> userOpt = userRepository.findByEmail(email).filter(user -> user.getDeletedAt() == null);

		// Always run the encoder, even when the user doesn't exist, so response
		// timing doesn't itself leak whether the account exists.
		String hashToCompareAgainst = userOpt.map(User::getPassword).orElse(DUMMY_HASH);
		boolean matches = passwordEncoder.matches(rawPassword, hashToCompareAgainst);

		// "user not found" and "wrong password" must be indistinguishable to the
		// caller, so both collapse to exists=false, valid=false, user_id=null.
		boolean isValid = userOpt.isPresent() && matches;
		UUID userId = isValid ? userOpt.get().getId() : null;
		Role role = isValid ? userOpt.get().getRole() : null;
		return new UserVerificationResponse(isValid, isValid, userId, role);
	}

	public void updateCurrentUserPassword(String authorizationHeader, String password, String newPassword) {
		User user = resolveCurrentUser(authorizationHeader);

		if (!passwordEncoder.matches(password, user.getPassword())) {
			throw new InvalidPasswordException("Current password is incorrect");
		}

		user.setPassword(passwordEncoder.encode(newPassword));
		user.setUpdatedAt(Instant.now());
		userRepository.save(user);
	}
}
