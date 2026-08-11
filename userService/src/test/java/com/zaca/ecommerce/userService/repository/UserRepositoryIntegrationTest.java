package com.zaca.ecommerce.userService.repository;

import com.zaca.ecommerce.userService.entity.Role;
import com.zaca.ecommerce.userService.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@EnabledIfDockerAvailable
class UserRepositoryIntegrationTest {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@org.springframework.test.context.DynamicPropertySource
	static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private UserRepository userRepository;

	@Test
	void existsByEmailReturnsTrueWhenUserExists() {
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
		userRepository.save(user);

		assertThat(userRepository.existsByEmail("user@test.com")).isTrue();
		assertThat(userRepository.existsByEmail("other@test.com")).isFalse();
	}

	@Test
	void existsByEmailAndIdNotReturnsTrueWhenEmailBelongsToDifferentUser() {
		Instant now = Instant.now();
		User owner = User.builder()
				.id(UUID.randomUUID())
				.name("Owner")
				.email("owner@test.com")
				.password("hashed-password")
				.role(Role.USER)
				.createdAt(now)
				.updatedAt(now)
				.build();
		User other = User.builder()
				.id(UUID.randomUUID())
				.name("Other")
				.email("other@test.com")
				.password("hashed-password")
				.role(Role.USER)
				.createdAt(now)
				.updatedAt(now)
				.build();
		userRepository.save(owner);
		userRepository.save(other);

		assertThat(userRepository.existsByEmailAndIdNot("other@test.com", owner.getId())).isTrue();
	}

	@Test
	void existsByEmailAndIdNotReturnsFalseWhenEmailBelongsToSameUser() {
		Instant now = Instant.now();
		User owner = User.builder()
				.id(UUID.randomUUID())
				.name("Owner")
				.email("owner@test.com")
				.password("hashed-password")
				.role(Role.USER)
				.createdAt(now)
				.updatedAt(now)
				.build();
		userRepository.save(owner);

		assertThat(userRepository.existsByEmailAndIdNot("owner@test.com", owner.getId())).isFalse();
	}

	@Test
	void findByEmailReturnsUserWhenPresent() {
		Instant now = Instant.now();
		User user = User.builder()
				.id(UUID.randomUUID())
				.name("User")
				.email("user2@test.com")
				.password("hashed-password")
				.role(Role.USER)
				.createdAt(now)
				.updatedAt(now)
				.build();
		userRepository.save(user);

		Optional<User> found = userRepository.findByEmail("user2@test.com");

		assertThat(found).isPresent();
		assertThat(found.get().getEmail()).isEqualTo("user2@test.com");
	}

	@Test
	void findByEmailReturnsEmptyWhenAbsent() {
		Optional<User> found = userRepository.findByEmail("missing@test.com");

		assertThat(found).isEmpty();
	}
}
