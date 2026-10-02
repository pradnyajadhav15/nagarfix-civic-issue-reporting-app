package dev.nagarfix.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** The whole app starts: database migrations run and every part fits together. Background jobs stay off. */
@SpringBootTest(properties = "app.jobs.enabled=false")
class ApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
