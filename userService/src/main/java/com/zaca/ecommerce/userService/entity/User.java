package com.zaca.ecommerce.userService.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

	@Id
	private UUID id;

	private String name;

	private String email;

	private String password;

	@Enumerated(EnumType.STRING)
	private Role role;

	@Column(updatable = false)
	@Setter(AccessLevel.NONE)
	private Instant createdAt;

	private Instant updatedAt;

	private Instant deletedAt;
}
