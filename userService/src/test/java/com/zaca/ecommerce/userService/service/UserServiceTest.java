package com.zaca.ecommerce.userService.service;

import com.zaca.ecommerce.userService.client.AuthServiceClient;
import com.zaca.ecommerce.userService.dto.TokenValidationResponse;
import com.zaca.ecommerce.userService.dto.UserResponse;
import com.zaca.ecommerce.userService.dto.UserVerificationResponse;
import com.zaca.ecommerce.userService.entity.Role;
import com.zaca.ecommerce.userService.entity.User;
import com.zaca.ecommerce.userService.exception.DuplicateEmailException;
import com.zaca.ecommerce.userService.exception.ForbiddenException;
import com.zaca.ecommerce.userService.exception.InvalidTokenException;
import com.zaca.ecommerce.userService.exception.NoUpdatableFieldsException;
import com.zaca.ecommerce.userService.exception.UserNotFoundException;
import com.zaca.ecommerce.userService.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private AuthServiceClient authServiceClient;

	private UserService userService;

	@BeforeEach
	void setUp() {
		userService = new UserService(userRepository, passwordEncoder, authServiceClient);
	}

	@Test
	void registersUserWithEncodedPassword() {
		when(userRepository.existsByEmail("user@test.com")).thenReturn(false);
		when(passwordEncoder.encode("senha123")).thenReturn("hashed-password");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserResponse response = userService.register("User", "user@test.com", "senha123");

		assertThat(response.name()).isEqualTo("User");
		assertThat(response.email()).isEqualTo("user@test.com");

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertThat(captor.getValue().getPassword()).isEqualTo("hashed-password");
	}

	@Test
	void throwsDuplicateEmailWhenEmailAlreadyExists() {
		when(userRepository.existsByEmail("user@test.com")).thenReturn(true);

		assertThatThrownBy(() -> userService.register("User", "user@test.com", "senha123"))
				.isInstanceOf(DuplicateEmailException.class);

		verify(userRepository, never()).save(any(User.class));
		verify(passwordEncoder, never()).encode(anyString());
	}

	@Test
	void returnsCurrentUserWhenTokenIsValid() {
		UUID userId = UUID.randomUUID();
		Instant now = Instant.now();
		when(authServiceClient.validate("Bearer valid-token"))
				.thenReturn(new TokenValidationResponse(userId.toString(), "access", now.plusSeconds(60), Role.USER));
		User user = User.builder()
				.id(userId)
				.name("User")
				.email("user@test.com")
				.password("hashed-password")
				.role(Role.USER)
				.createdAt(now)
				.updatedAt(now)
				.build();
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		UserResponse response = userService.getCurrentUser("Bearer valid-token");

		assertThat(response.id()).isEqualTo(userId);
		assertThat(response.name()).isEqualTo("User");
		assertThat(response.email()).isEqualTo("user@test.com");
	}

	@Test
	void throwsInvalidTokenWhenAuthorizationHeaderIsMissing() {
		assertThatThrownBy(() -> userService.getCurrentUser(null))
				.isInstanceOf(InvalidTokenException.class);

		verifyNoInteractions(authServiceClient);
	}

	@Test
	void throwsInvalidTokenWhenAuthorizationHeaderIsMalformed() {
		assertThatThrownBy(() -> userService.getCurrentUser("malformed-token"))
				.isInstanceOf(InvalidTokenException.class);

		verifyNoInteractions(authServiceClient);
	}

	@Test
	void throwsInvalidTokenWhenUserIsNotFoundAfterValidation() {
		UUID userId = UUID.randomUUID();
		when(authServiceClient.validate("Bearer valid-token"))
				.thenReturn(new TokenValidationResponse(userId.toString(), "access", Instant.now().plusSeconds(60), Role.USER));
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.getCurrentUser("Bearer valid-token"))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void updatesNameAndEmailWhenBothProvided() {
		UUID userId = UUID.randomUUID();
		Instant now = Instant.now();
		when(authServiceClient.validate("Bearer valid-token"))
				.thenReturn(new TokenValidationResponse(userId.toString(), "access", now.plusSeconds(60), Role.USER));
		User user = User.builder()
				.id(userId)
				.name("Old Name")
				.email("old@test.com")
				.password("hashed-password")
				.role(Role.USER)
				.createdAt(now)
				.updatedAt(now)
				.build();
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.existsByEmailAndIdNot("new@test.com", userId)).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		userService.updateCurrentUser("Bearer valid-token", "New Name", "new@test.com");

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertThat(captor.getValue().getName()).isEqualTo("New Name");
		assertThat(captor.getValue().getEmail()).isEqualTo("new@test.com");
		assertThat(captor.getValue().getUpdatedAt()).isAfter(now);
	}

	@Test
	void updatesOnlyNameWhenEmailNotProvided() {
		UUID userId = UUID.randomUUID();
		Instant now = Instant.now();
		when(authServiceClient.validate("Bearer valid-token"))
				.thenReturn(new TokenValidationResponse(userId.toString(), "access", now.plusSeconds(60), Role.USER));
		User user = User.builder()
				.id(userId)
				.name("Old Name")
				.email("old@test.com")
				.password("hashed-password")
				.role(Role.USER)
				.createdAt(now)
				.updatedAt(now)
				.build();
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		userService.updateCurrentUser("Bearer valid-token", "New Name", null);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertThat(captor.getValue().getName()).isEqualTo("New Name");
		assertThat(captor.getValue().getEmail()).isEqualTo("old@test.com");
		verify(userRepository, never()).existsByEmailAndIdNot(anyString(), any(UUID.class));
	}

	@Test
	void updatesOnlyEmailWhenNameNotProvided() {
		UUID userId = UUID.randomUUID();
		Instant now = Instant.now();
		when(authServiceClient.validate("Bearer valid-token"))
				.thenReturn(new TokenValidationResponse(userId.toString(), "access", now.plusSeconds(60), Role.USER));
		User user = User.builder()
				.id(userId)
				.name("Old Name")
				.email("old@test.com")
				.password("hashed-password")
				.role(Role.USER)
				.createdAt(now)
				.updatedAt(now)
				.build();
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.existsByEmailAndIdNot("new@test.com", userId)).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		userService.updateCurrentUser("Bearer valid-token", null, "new@test.com");

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertThat(captor.getValue().getName()).isEqualTo("Old Name");
		assertThat(captor.getValue().getEmail()).isEqualTo("new@test.com");
	}

	@Test
	void throwsDuplicateEmailWhenNewEmailUsedByAnotherUser() {
		UUID userId = UUID.randomUUID();
		Instant now = Instant.now();
		when(authServiceClient.validate("Bearer valid-token"))
				.thenReturn(new TokenValidationResponse(userId.toString(), "access", now.plusSeconds(60), Role.USER));
		User user = User.builder()
				.id(userId)
				.name("Old Name")
				.email("old@test.com")
				.password("hashed-password")
				.role(Role.USER)
				.createdAt(now)
				.updatedAt(now)
				.build();
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.existsByEmailAndIdNot("taken@test.com", userId)).thenReturn(true);

		assertThatThrownBy(() -> userService.updateCurrentUser("Bearer valid-token", null, "taken@test.com"))
				.isInstanceOf(DuplicateEmailException.class);

		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void doesNotThrowWhenReSubmittingOwnCurrentEmail() {
		UUID userId = UUID.randomUUID();
		Instant now = Instant.now();
		when(authServiceClient.validate("Bearer valid-token"))
				.thenReturn(new TokenValidationResponse(userId.toString(), "access", now.plusSeconds(60), Role.USER));
		User user = User.builder()
				.id(userId)
				.name("Old Name")
				.email("user@test.com")
				.password("hashed-password")
				.role(Role.USER)
				.createdAt(now)
				.updatedAt(now)
				.build();
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		userService.updateCurrentUser("Bearer valid-token", "New Name", "user@test.com");

		verify(userRepository, never()).existsByEmailAndIdNot(anyString(), any(UUID.class));
		verify(userRepository).save(any(User.class));
	}

	@Test
	void throwsNoUpdatableFieldsWhenNameAndEmailAreBlankOrNull() {
		UUID userId = UUID.randomUUID();
		Instant now = Instant.now();
		when(authServiceClient.validate("Bearer valid-token"))
				.thenReturn(new TokenValidationResponse(userId.toString(), "access", now.plusSeconds(60), Role.USER));
		User user = User.builder()
				.id(userId)
				.name("Old Name")
				.email("user@test.com")
				.password("hashed-password")
				.role(Role.USER)
				.createdAt(now)
				.updatedAt(now)
				.build();
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> userService.updateCurrentUser("Bearer valid-token", "", " "))
				.isInstanceOf(NoUpdatableFieldsException.class);

		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void throwsInvalidTokenWhenUpdateAuthorizationHeaderIsMissing() {
		assertThatThrownBy(() -> userService.updateCurrentUser(null, "New Name", null))
				.isInstanceOf(InvalidTokenException.class);

		verifyNoInteractions(authServiceClient);
	}

	@Test
	void throwsInvalidTokenWhenUpdateAuthorizationHeaderIsMalformed() {
		assertThatThrownBy(() -> userService.updateCurrentUser("malformed-token", "New Name", null))
				.isInstanceOf(InvalidTokenException.class);

		verifyNoInteractions(authServiceClient);
	}

	@Test
	void throwsInvalidTokenWhenUpdateUserIsNotFoundAfterValidation() {
		UUID userId = UUID.randomUUID();
		when(authServiceClient.validate("Bearer valid-token"))
				.thenReturn(new TokenValidationResponse(userId.toString(), "access", Instant.now().plusSeconds(60), Role.USER));
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.updateCurrentUser("Bearer valid-token", "New Name", null))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void returnsUserWhenAdminQueriesExistingUser() {
		UUID callerId = UUID.randomUUID();
		UUID targetId = UUID.randomUUID();
		Instant now = Instant.now();
		when(authServiceClient.validate("Bearer admin-token"))
				.thenReturn(new TokenValidationResponse(callerId.toString(), "access", now.plusSeconds(60), Role.ADMIN));
		User caller = User.builder()
				.id(callerId)
				.name("Admin")
				.email("admin@test.com")
				.password("hashed-password")
				.role(Role.ADMIN)
				.createdAt(now)
				.updatedAt(now)
				.build();
		User target = User.builder()
				.id(targetId)
				.name("Target User")
				.email("target@test.com")
				.password("hashed-password")
				.role(Role.USER)
				.createdAt(now)
				.updatedAt(now)
				.build();
		when(userRepository.findById(callerId)).thenReturn(Optional.of(caller));
		when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

		UserResponse response = userService.getUserForAdmin("Bearer admin-token", targetId);

		assertThat(response.id()).isEqualTo(targetId);
		assertThat(response.name()).isEqualTo("Target User");
		assertThat(response.email()).isEqualTo("target@test.com");
	}

	@Test
	void throwsUserNotFoundWhenAdminQueriesNonExistingUser() {
		UUID callerId = UUID.randomUUID();
		UUID targetId = UUID.randomUUID();
		Instant now = Instant.now();
		when(authServiceClient.validate("Bearer admin-token"))
				.thenReturn(new TokenValidationResponse(callerId.toString(), "access", now.plusSeconds(60), Role.ADMIN));
		User caller = User.builder()
				.id(callerId)
				.name("Admin")
				.email("admin@test.com")
				.password("hashed-password")
				.role(Role.ADMIN)
				.createdAt(now)
				.updatedAt(now)
				.build();
		when(userRepository.findById(callerId)).thenReturn(Optional.of(caller));
		when(userRepository.findById(targetId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.getUserForAdmin("Bearer admin-token", targetId))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void throwsForbiddenWhenNonAdminQueriesUser() {
		UUID callerId = UUID.randomUUID();
		UUID targetId = UUID.randomUUID();
		Instant now = Instant.now();
		when(authServiceClient.validate("Bearer user-token"))
				.thenReturn(new TokenValidationResponse(callerId.toString(), "access", now.plusSeconds(60), Role.USER));
		User caller = User.builder()
				.id(callerId)
				.name("Regular User")
				.email("user@test.com")
				.password("hashed-password")
				.role(Role.USER)
				.createdAt(now)
				.updatedAt(now)
				.build();
		when(userRepository.findById(callerId)).thenReturn(Optional.of(caller));

		assertThatThrownBy(() -> userService.getUserForAdmin("Bearer user-token", targetId))
				.isInstanceOf(ForbiddenException.class);

		verify(userRepository, never()).findById(targetId);
	}

	@Test
	void throwsInvalidTokenWhenAdminQueryAuthorizationHeaderIsMissing() {
		UUID targetId = UUID.randomUUID();

		assertThatThrownBy(() -> userService.getUserForAdmin(null, targetId))
				.isInstanceOf(InvalidTokenException.class);

		verifyNoInteractions(authServiceClient);
	}

	@Test
	void returnsValidTrueWithUserIdWhenEmailAndPasswordMatch() {
		UUID userId = UUID.randomUUID();
		Instant now = Instant.now();
		User user = User.builder()
				.id(userId)
				.name("User")
				.email("user@test.com")
				.password("hashed-password")
				.role(Role.USER)
				.createdAt(now)
				.updatedAt(now)
				.build();
		when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("senha123", "hashed-password")).thenReturn(true);

		UserVerificationResponse response = userService.verifyCredentials("user@test.com", "senha123");

		assertThat(response.exists()).isTrue();
		assertThat(response.valid()).isTrue();
		assertThat(response.user_id()).isEqualTo(userId);
	}

	@Test
	void returnsBothFalseWithNoUserIdWhenPasswordDoesNotMatch() {
		Instant now = Instant.now();
		User user = User.builder()
				.id(UUID.randomUUID())
				.name("User")
				.email("user@test.com")
				.password("hashed-password")
				.role(Role.USER)
				.createdAt(now)
				.updatedAt(now)
				.build();
		when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

		UserVerificationResponse response = userService.verifyCredentials("user@test.com", "wrong-password");

		assertThat(response.exists()).isFalse();
		assertThat(response.valid()).isFalse();
		assertThat(response.user_id()).isNull();
	}

	@Test
	void returnsBothFalseWithNoUserIdWhenUserDoesNotExist() {
		when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());
		when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

		UserVerificationResponse response = userService.verifyCredentials("missing@test.com", "any-password");

		assertThat(response.exists()).isFalse();
		assertThat(response.valid()).isFalse();
		assertThat(response.user_id()).isNull();

		ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
		verify(passwordEncoder).matches(anyString(), hashCaptor.capture());
		assertThat(hashCaptor.getValue()).isNotNull();
	}
}
