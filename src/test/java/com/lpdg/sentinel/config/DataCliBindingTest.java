package com.lpdg.sentinel.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.lpdg.sentinel.SentinelApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

class DataCliBindingTest {

    @Configuration
    @EnableConfigurationProperties(SentinelProperties.class)
    static class TestConfig {}

    @Nested
    @SpringBootTest(
            classes = TestConfig.class,
            args = {"--data=/custom/submission/data"})
    class EqualsFormatTest {
        @Autowired
        private SentinelProperties properties;

        @Test
        @DisplayName("CLI argument --data=/path binds directly to SentinelProperties dataDir")
        void cliDataArgumentEqualsFormat() {
            assertThat(properties.dataDir()).isEqualTo("/custom/submission/data");
        }
    }

    @Nested
    @SpringBootTest(classes = TestConfig.class)
    class DefaultFallbackTest {
        @Autowired
        private SentinelProperties properties;

        @Test
        @DisplayName("Default data-dir falls back to 'data'")
        void defaultDataDirFallback() {
            assertThat(properties.dataDir()).isEqualTo("data");
        }
    }

    @Test
    @DisplayName("SentinelApplication.normalizeArgs transforms space-separated --data to equals-separated")
    void testNormalizeArgs() {
        String[] input = {"--server.port=8080", "--data", "/somewhere/else", "--foo=bar"};
        String[] normalized = SentinelApplication.normalizeArgs(input);
        assertThat(normalized).containsExactly("--server.port=8080", "--data=/somewhere/else", "--foo=bar");
    }

    @Test
    @DisplayName("SentinelApplication.normalizeArgs leaves already normalized args intact")
    void testNormalizeArgsAlreadyNormalized() {
        String[] input = {"--data=/somewhere/else", "--server.port=8080"};
        String[] normalized = SentinelApplication.normalizeArgs(input);
        assertThat(normalized).containsExactly("--data=/somewhere/else", "--server.port=8080");
    }
}
