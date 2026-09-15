package com.lpdg.sentinel;

import java.util.ArrayList;
import java.util.List;
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
        SpringApplication.run(SentinelApplication.class, normalizeArgs(args));
    }

    /**
     * Normalizes CLI arguments to ensure seamless Spring Boot binding for both
     * {@code --data /path} (space-separated) and {@code --data=/path} (equals-separated).
     */
    public static String[] normalizeArgs(String[] args) {
        if (args == null || args.length == 0) {
            return args;
        }
        List<String> normalized = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if ("--data".equals(args[i]) && i + 1 < args.length && !args[i + 1].startsWith("--")) {
                normalized.add("--data=" + args[i + 1]);
                i++;
            } else {
                normalized.add(args[i]);
            }
        }
        return normalized.toArray(new String[0]);
    }
}
