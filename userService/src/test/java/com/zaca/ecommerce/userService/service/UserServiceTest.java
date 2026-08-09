package com.zaca.ecommerce.userService.service;

import com.zaca.ecommerce.userService.dto.UserResponse;
import com.zaca.ecommerce.userService.entity.User;
import com.zaca.ecommerce.userService.exception.DuplicateEmailException;
import com.zaca.ecommerce.userService.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	private UserService userService;

	@org.junit.jupiter.api.BeforeEach
	void setUp() {
		userService = new UserService(userRepository, passwordEncoder);
	}

	@Test
	void registersUserWithEncodedPassword() {
		when(userRepository.existsByEmail("daniel@test.com")).thenReturn(false);
		when(passwordEncoder.encode("senha123")).thenReturn("hashed-password");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserResponse response = userService.register("Daniel", "daniel@test.com", "senha123");

		assertThat(response.name()).isEqualTo("Daniel");
		assertThat(response.email()).isEqualTo("daniel@test.com");

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertThat(captor.getValue().getPassword()).isEqualTo("hashed-password");
	}

	@Test
	void throwsDuplicateEmailWhenEmailAlreadyExists() {
		when(userRepository.existsByEmail("daniel@test.com")).thenReturn(true);

		assertThatThrownBy(() -> userService.register("Daniel", "daniel@test.com", "senha123"))
				.isInstanceOf(DuplicateEmailException.class);

		verify(userRepository, never()).save(any(User.class));
		verify(passwordEncoder, never()).encode(anyString());
	}
}
