package com.lpdg.sentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sentinel — Gateway Visit Prioritization Service.
 *
 * <p>Entry point for the NEXORA 2026 challenge application.
 * Ranks gateway visits to optimise field-engineer scheduling.</p>
 */
@SpringBootApplication
public class SentinelApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentinelApplication.class, args);
    }
}
