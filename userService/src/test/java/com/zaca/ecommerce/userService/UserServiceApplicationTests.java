package com.zaca.ecommerce.userService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "internal-api.key=test-internal-api-key")
class UserServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
