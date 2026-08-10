package com.zaca.ecommerce.authService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "internal-api.key=test-internal-api-key")
class AuthServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
