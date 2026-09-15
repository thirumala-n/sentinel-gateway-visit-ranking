package com.lpdg.sentinel.web.validation;

import com.lpdg.sentinel.common.errors.FutureWeekException;
import com.lpdg.sentinel.common.errors.InvalidWeekException;
import com.lpdg.sentinel.common.errors.UnsupportedWeekException;
import com.lpdg.sentinel.config.SentinelProperties;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Strict parser and temporal validator for prediction week inputs.
 *
 * <p>Supports:
 * <ul>
 *   <li>ISO date strings: {@code YYYY-MM-DD} (e.g., {@code 2026-03-09})</li>
 *   <li>ISO week strings: {@code YYYY-Www} (e.g., {@code 2026-W11}) or {@code YYYY-Www-e} (e.g., {@code 2026-W11-1})</li>
 * </ul>
 *
 * <p>Validates:
 * <ul>
 *   <li>Day of week must be Monday.</li>
 *   <li>Earliest supported week is {@code 2025-09-01} (telemetry begins 2025-08-01, requiring 28-day baseline).</li>
 *   <li>Latest supported week is dynamically discovered from available telemetry partitions on disk,
 *       allowing unseen future months (e.g. April/May 2026) added during Round Two live evaluation
 *       to be accepted and processed without restarting the JVM.</li>
 * </ul>
 */
@Component
public class PredictionWeekParser {

    /** Earliest Monday with complete 28-day historical baseline in competition data. */
    public static final LocalDate EARLIEST_SUPPORTED_MONDAY = LocalDate.of(2025, 9, 1);

    /** Default fallback latest Monday when telemetry partitions cannot be discovered from disk. */
    public static final LocalDate DEFAULT_LATEST_MONDAY = LocalDate.of(2026, 3, 30);

    /** Compatibility alias for default latest Monday. */
    public static final LocalDate LATEST_SUPPORTED_MONDAY = DEFAULT_LATEST_MONDAY;

    private static final Pattern ISO_WEEK_PATTERN = Pattern.compile("^\\d{4}-W\\d{2}$");
    private static final Pattern ISO_WEEK_DAY_PATTERN = Pattern.compile("^\\d{4}-W\\d{2}-\\d$");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern MONTH_PARTITION_PATTERN = Pattern.compile("(\\d{4})-(\\d{1,2})");

    private final SentinelProperties properties;

    public PredictionWeekParser() {
        this(null);
    }

    @Autowired
    public PredictionWeekParser(@Autowired(required = false) SentinelProperties properties) {
        this.properties = properties;
    }

    /**
     * Dynamically discovers the latest Monday supported by currently available telemetry partitions.
     *
     * <p>Scans {@code data/telemetry/} for partition folders/files (e.g. {@code month=YYYY-MM}).
     * When new monthly partitions (e.g. {@code month=2026-04}) are added at runtime, this method
     * dynamically extends the supported horizon without requiring a JVM or container restart.</p>
     *
     * @return latest Monday covered by available telemetry; never null
     */
    public LocalDate getLatestSupportedMonday() {
        String dataDirPath = properties != null ? properties.dataDir() : "data";
        File telemetryDir = new File(dataDirPath, "telemetry");
        if (telemetryDir.exists() && telemetryDir.isDirectory()) {
            File[] files = telemetryDir.listFiles();
            if (files != null && files.length > 0) {
                YearMonth latestYm = null;
                for (File file : files) {
                    Matcher m = MONTH_PARTITION_PATTERN.matcher(file.getName());
                    if (m.find()) {
                        try {
                            int year = Integer.parseInt(m.group(1));
                            int month = Integer.parseInt(m.group(2));
                            if (month >= 1 && month <= 12) {
                                YearMonth ym = YearMonth.of(year, month);
                                if (latestYm == null || ym.isAfter(latestYm)) {
                                    latestYm = ym;
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                if (latestYm != null) {
                    LocalDate endOfMonth = latestYm.atEndOfMonth();
                    LocalDate dayAfter = endOfMonth.plusDays(1);
                    return dayAfter.getDayOfWeek() == DayOfWeek.MONDAY
                            ? dayAfter
                            : endOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                }
            }
        }
        return DEFAULT_LATEST_MONDAY;
    }

    /**
     * Parses and validates a raw week string into an immutable {@link PredictionWeek}.
     *
     * @param raw input string from path variable
     * @return validated prediction week
     * @throws InvalidWeekException if string is unparseable or date is not a Monday
     * @throws UnsupportedWeekException if date lacks 28-day baseline history
     * @throws FutureWeekException if date falls beyond available telemetry data
     */
    public PredictionWeek parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidWeekException("MALFORMED_WEEK", "Week parameter must not be blank.");
        }

        String trimmed = raw.trim();
        LocalDate targetMonday;

        if (ISO_WEEK_PATTERN.matcher(trimmed).matches()) {
            try {
                targetMonday = LocalDate.parse(trimmed + "-1", DateTimeFormatter.ISO_WEEK_DATE);
            } catch (DateTimeParseException e) {
                throw new InvalidWeekException("MALFORMED_WEEK", "Invalid ISO week format: '" + trimmed + "'.", e);
            }
        } else if (ISO_WEEK_DAY_PATTERN.matcher(trimmed).matches()) {
            try {
                targetMonday = LocalDate.parse(trimmed, DateTimeFormatter.ISO_WEEK_DATE);
            } catch (DateTimeParseException e) {
                throw new InvalidWeekException("MALFORMED_WEEK", "Invalid ISO week format: '" + trimmed + "'.", e);
            }
        } else if (ISO_DATE_PATTERN.matcher(trimmed).matches()) {
            try {
                targetMonday = LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                throw new InvalidWeekException("MALFORMED_WEEK", "Invalid ISO date format: '" + trimmed + "'.", e);
            }
        } else {
            throw new InvalidWeekException(
                    "MALFORMED_WEEK",
                    "Invalid week format '" + trimmed + "'. Expected ISO date (YYYY-MM-DD) or ISO week (YYYY-Www).");
        }

        if (targetMonday.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new InvalidWeekException(
                    "INVALID_WEEK_DAY",
                    String.format("Prediction week must be anchored to a Monday (got %s, %s).",
                            targetMonday, targetMonday.getDayOfWeek()));
        }

        if (targetMonday.isBefore(EARLIEST_SUPPORTED_MONDAY)) {
            throw new UnsupportedWeekException(
                    String.format("Requested week %s is before the earliest supported week (%s). "
                                    + "Telemetry starts on 2025-08-01 and requires 28 days of baseline history.",
                            targetMonday, EARLIEST_SUPPORTED_MONDAY));
        }

        LocalDate latestSupportedMonday = getLatestSupportedMonday();
        if (targetMonday.isAfter(latestSupportedMonday)) {
            throw new FutureWeekException(
                    String.format("Requested week %s is in the future. Telemetry is available up to %s.",
                            targetMonday, latestSupportedMonday));
        }

        return new PredictionWeek(targetMonday);
    }
}
