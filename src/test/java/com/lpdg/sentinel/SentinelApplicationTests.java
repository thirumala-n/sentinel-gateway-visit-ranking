package com.lpdg.sentinel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: proves the Spring Boot application context loads successfully.
 */
@SpringBootTest
@ActiveProfiles("test")
class SentinelApplicationTests {

    @Test
    void contextLoads() {
        // The application context must start without errors.
    }
}
