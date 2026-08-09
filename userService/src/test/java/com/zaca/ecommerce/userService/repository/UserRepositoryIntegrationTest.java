package com.zaca.ecommerce.userService.repository;

import com.zaca.ecommerce.userService.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
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
				.name("Daniel")
				.email("daniel@test.com")
				.password("hashed-password")
				.createdAt(now)
				.updatedAt(now)
				.build();
		userRepository.save(user);

		assertThat(userRepository.existsByEmail("daniel@test.com")).isTrue();
		assertThat(userRepository.existsByEmail("other@test.com")).isFalse();
	}
}
