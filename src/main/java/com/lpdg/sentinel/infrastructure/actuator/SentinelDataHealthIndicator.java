package com.lpdg.sentinel.infrastructure.actuator;

import com.lpdg.sentinel.config.SentinelProperties;
import java.io.File;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Lightweight Actuator {@link HealthIndicator} that verifies source data directory availability.
 *
 * <p>Executes an O(1) filesystem presence check in &lt;1ms without running analytical queries
 * or scanning parquet partitions, ensuring readiness checks never incur heavy computational costs.
 */
@Component
public class SentinelDataHealthIndicator implements HealthIndicator {

    private final String dataDir;

    public SentinelDataHealthIndicator(SentinelProperties properties) {
        this.dataDir = properties.dataDir();
    }

    @Override
    public Health health() {
        File dir = new File(dataDir);
        if (!dir.exists()) {
            return Health.down()
                    .withDetail("dataDir", dataDir)
                    .withDetail("reason", "Data directory does not exist")
                    .build();
        }
        if (!dir.isDirectory()) {
            return Health.down()
                    .withDetail("dataDir", dataDir)
                    .withDetail("reason", "Data path is not a directory")
                    .build();
        }

        File telemetryDir = new File(dir, "telemetry");
        File masterCsv = new File(dir, "gateway_master.csv");

        boolean telemetryAvailable = telemetryDir.exists();
        boolean masterAvailable = masterCsv.exists();

        if (!telemetryAvailable && !masterAvailable) {
            return Health.down()
                    .withDetail("dataDir", dataDir)
                    .withDetail("reason", "No telemetry or gateway master data found — ensure data directory is mounted correctly")
                    .build();
        }

        return Health.up()
                .withDetail("dataDir", dataDir)
                .withDetail("telemetryAvailable", telemetryAvailable)
                .withDetail("masterDataAvailable", masterAvailable)
                .withDetail("status", "READY")
                .build();
    }
}
