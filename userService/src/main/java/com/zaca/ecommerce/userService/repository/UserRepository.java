package com.zaca.ecommerce.userService.repository;

import com.zaca.ecommerce.userService.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

	boolean existsByEmail(String email);

	boolean existsByEmailAndIdNot(String email, UUID id);

	Optional<User> findByEmail(String email);
}
