package com.lpdg.sentinel.web.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lpdg.sentinel.common.errors.FutureWeekException;
import com.lpdg.sentinel.common.errors.InvalidWeekException;
import com.lpdg.sentinel.common.errors.UnsupportedWeekException;
import com.lpdg.sentinel.config.SentinelProperties;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PredictionWeekParserTest {

    @Test
    @DisplayName("Parse valid ISO date format (YYYY-MM-DD) on Monday")
    void parseIsoDateMonday() {
        PredictionWeekParser parser = new PredictionWeekParser();
        PredictionWeek week = parser.parse("2026-03-09");
        assertThat(week.targetMonday()).isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    @DisplayName("Parse valid ISO week format (YYYY-Www)")
    void parseIsoWeek() {
        PredictionWeekParser parser = new PredictionWeekParser();
        PredictionWeek week = parser.parse("2026-W11");
        assertThat(week.targetMonday()).isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    @DisplayName("Parse valid ISO week-day format (YYYY-Www-1)")
    void parseIsoWeekDay() {
        PredictionWeekParser parser = new PredictionWeekParser();
        PredictionWeek week = parser.parse("2026-W11-1");
        assertThat(week.targetMonday()).isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    @DisplayName("Reject blank or null input with MALFORMED_WEEK")
    void rejectBlankOrNull() {
        PredictionWeekParser parser = new PredictionWeekParser();
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(InvalidWeekException.class)
                .hasMessageContaining("Week parameter must not be blank");

        assertThatThrownBy(() -> parser.parse("   "))
                .isInstanceOf(InvalidWeekException.class)
                .hasMessageContaining("Week parameter must not be blank");
    }

    @Test
    @DisplayName("Reject non-Monday date with INVALID_WEEK_DAY")
    void rejectNonMonday() {
        PredictionWeekParser parser = new PredictionWeekParser();
        // 2026-03-10 is Tuesday
        assertThatThrownBy(() -> parser.parse("2026-03-10"))
                .isInstanceOf(InvalidWeekException.class)
                .hasMessageContaining("must be anchored to a Monday");
    }

    @Test
    @DisplayName("Reject date before earliest supported Monday with UNSUPPORTED_WEEK")
    void rejectBeforeEarliestSupported() {
        PredictionWeekParser parser = new PredictionWeekParser();
        // 2025-08-25 is before 2025-09-01
        assertThatThrownBy(() -> parser.parse("2025-08-25"))
                .isInstanceOf(UnsupportedWeekException.class)
                .hasMessageContaining("requires 28 days of baseline history");
    }

    @Test
    @DisplayName("Reject date in future when telemetry is not yet available with FUTURE_WEEK")
    void rejectFutureDate() {
        PredictionWeekParser parser = new PredictionWeekParser();
        assertThatThrownBy(() -> parser.parse("2026-04-06"))
                .isInstanceOf(FutureWeekException.class)
                .hasMessageContaining("is in the future");
    }

    @Test
    @DisplayName("Dynamic discovery: newly added telemetry partitions enable future weeks without JVM restart")
    void dynamicDiscoveryOfFutureMonths(@TempDir Path tempDir) throws IOException {
        Path telemetryDir = tempDir.resolve("telemetry");
        Files.createDirectories(telemetryDir);

        SentinelProperties properties = new SentinelProperties(
                tempDir.toString(), 15, 28, 7, 8, "risk-based");
        PredictionWeekParser parser = new PredictionWeekParser(properties);

        // Initially only March 2026 partition exists
        Files.createDirectories(telemetryDir.resolve("month=2026-03"));

        assertThat(parser.getLatestSupportedMonday()).isEqualTo(LocalDate.of(2026, 3, 30));
        // 2026-03-30 succeeds
        PredictionWeek mar30 = parser.parse("2026-03-30");
        assertThat(mar30.targetMonday()).isEqualTo(LocalDate.of(2026, 3, 30));

        // 2026-04-06 is rejected because April telemetry is not yet on disk
        assertThatThrownBy(() -> parser.parse("2026-04-06"))
                .isInstanceOf(FutureWeekException.class)
                .hasMessageContaining("is in the future");

        // Live session simulates adding unseen April telemetry partition at runtime:
        Files.createDirectories(telemetryDir.resolve("month=2026-04"));

        // Without JVM restart, the same parser instance dynamically discovers April telemetry
        assertThat(parser.getLatestSupportedMonday()).isEqualTo(LocalDate.of(2026, 4, 27));

        PredictionWeek apr06 = parser.parse("2026-04-06");
        assertThat(apr06.targetMonday()).isEqualTo(LocalDate.of(2026, 4, 6));

        PredictionWeek apr27 = parser.parse("2026-04-27");
        assertThat(apr27.targetMonday()).isEqualTo(LocalDate.of(2026, 4, 27));

        // 2026-05-04 is rejected until May telemetry arrives
        assertThatThrownBy(() -> parser.parse("2026-05-04"))
                .isInstanceOf(FutureWeekException.class);

        // Add May telemetry partition at runtime:
        Files.createDirectories(telemetryDir.resolve("month=2026-05"));
        assertThat(parser.getLatestSupportedMonday()).isEqualTo(LocalDate.of(2026, 6, 1));

        PredictionWeek may04 = parser.parse("2026-05-04");
        assertThat(may04.targetMonday()).isEqualTo(LocalDate.of(2026, 5, 4));
    }
}
