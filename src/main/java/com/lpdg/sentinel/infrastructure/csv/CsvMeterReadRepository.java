package com.lpdg.sentinel.infrastructure.csv;

import com.lpdg.sentinel.config.SentinelProperties;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.port.MeterReadRepository;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Repository;

/**
 * CSV-backed implementation of {@link MeterReadRepository}.
 *
 * <p>Reads from {@code meter_read_success.csv} (UTF-8, comma-separated) which contains
 * a 26-week historic snapshot ending on 2026-01-26. This data is intentionally used only
 * as an explanation feature (F07); it is NOT used as a real-time failure detector because
 * it may be up to 5 weeks stale relative to the scoring week (DQ-07).
 *
 * <h3>Fallback</h3>
 *
 * <p>If the gateway is absent from the file, {@link #FLEET_MEDIAN_SUCCESS_RATE} (91.0%)
 * is returned. If the file itself is absent or unreadable, all lookups return the fleet
 * median without throwing.
 *
 * <h3>Calculation</h3>
 *
 * <pre>
 *   success_rate = 100.0 * SUM(meters_read) / SUM(meters_expected)
 * </pre>
 *
 * <p>Aggregated per gateway across all 26 weeks.
 */
@Repository
public class CsvMeterReadRepository implements MeterReadRepository {

    private final Map<String, Double> successRateByGateway;

    public CsvMeterReadRepository(SentinelProperties properties) {
        this.successRateByGateway = loadFromCsv(properties.dataDir());
    }

    @Override
    public double findSuccessRate(GatewayId gatewayId) {
        return successRateByGateway.getOrDefault(gatewayId.value(), FLEET_MEDIAN_SUCCESS_RATE);
    }

    // ── Private loading ────────────────────────────────────────

    private Map<String, Double> loadFromCsv(String dataDir) {
        Path csvPath = Paths.get(dataDir, "meter_read_success.csv");
        if (!Files.exists(csvPath)) {
            return Map.of();
        }

        // Accumulators: gateway_id -> (totalRead, totalExpected)
        Map<String, long[]> accumulators = new HashMap<>();

        try (Reader reader = new InputStreamReader(Files.newInputStream(csvPath), StandardCharsets.UTF_8)) {
            CSVFormat format = CSVFormat.DEFAULT
                    .builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setTrim(true)
                    .build();

            for (CSVRecord record : format.parse(reader)) {
                String rawId = record.get("gateway_id");
                if (rawId == null || rawId.isBlank()) {
                    continue;
                }

                String normalizedId;
                try {
                    normalizedId = GatewayId.normalize(rawId).value();
                } catch (IllegalArgumentException e) {
                    continue; // Skip malformed IDs
                }

                long metersRead = parseLong(record, "meters_read");
                long metersExpected = parseLong(record, "meters_expected");

                accumulators.computeIfAbsent(normalizedId, k -> new long[2]);
                accumulators.get(normalizedId)[0] += metersRead;
                accumulators.get(normalizedId)[1] += metersExpected;
            }
        } catch (IOException e) {
            // File unreadable: return empty map; all lookups fall back to fleet median
            return Map.of();
        }

        // Compute success rates
        Map<String, Double> rates = new HashMap<>(accumulators.size());
        for (Map.Entry<String, long[]> entry : accumulators.entrySet()) {
            long totalRead = entry.getValue()[0];
            long totalExpected = entry.getValue()[1];
            double rate = totalExpected > 0
                    ? 100.0 * totalRead / totalExpected
                    : FLEET_MEDIAN_SUCCESS_RATE;
            // Clamp to [0, 100]
            rates.put(entry.getKey(), Math.min(100.0, Math.max(0.0, rate)));
        }

        return Map.copyOf(rates);
    }

    private long parseLong(CSVRecord record, String column) {
        try {
            String raw = record.get(column);
            return raw != null && !raw.isBlank() ? Long.parseLong(raw.trim()) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
