package com.lpdg.sentinel.infrastructure.duckdb;

import com.lpdg.sentinel.config.SentinelProperties;
import com.lpdg.sentinel.domain.model.Gateway;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.port.GatewayRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * DuckDB-backed implementation of {@link GatewayRepository}.
 *
 * <p>Reads from {@code gateway_master.csv} using ISO-8859-1 (Latin-1) encoding to handle
 * German umlauts and eszett in location data (DQ-02). All gateway identifiers are
 * normalized to 12-char uppercase hex on read (DQ-01).
 *
 * <h3>Eligibility Filter (DQ-04, DQ-05)</h3>
 *
 * <pre>
 *   WHERE (decommissioned_on IS NULL OR decommissioned_on >= :evalDate)
 *     AND (installed_on IS NULL OR installed_on <= :evalDate)
 * </pre>
 *
 * <p>The query uses a parameterized date to avoid SQL injection and ensure deterministic
 * results.
 */
@Repository
public class DuckDbGatewayRepository implements GatewayRepository {

    private static final String QUERY =
            """
            SELECT
                TRIM(UPPER(REPLACE(gateway_id, ':', ''))) AS gateway_id_norm,
                COALESCE(TRY_CAST(n_meters_installed AS INTEGER), 156) AS n_meters,
                TRY_CAST(installed_on AS DATE) AS installed_on,
                TRY_CAST(decommissioned_on AS DATE) AS decommissioned_on
            FROM read_csv_auto(?, encoding='latin-1', header=true)
            WHERE (TRY_CAST(decommissioned_on AS DATE) IS NULL
                   OR TRY_CAST(decommissioned_on AS DATE) >= CAST(? AS DATE))
              AND (TRY_CAST(installed_on AS DATE) IS NULL
                   OR TRY_CAST(installed_on AS DATE) <= CAST(? AS DATE))
            """;

    private final DataSource dataSource;
    private final String dataDir;

    public DuckDbGatewayRepository(DataSource dataSource, SentinelProperties properties) {
        this.dataSource = dataSource;
        this.dataDir = properties.dataDir();
    }

    @Override
    public List<Gateway> findEligible(LocalDate evalDate) {
        String csvPath = dataDir + "/gateway_master.csv";
        List<Gateway> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(QUERY)) {
            stmt.setString(1, csvPath);
            stmt.setString(2, evalDate.toString());
            stmt.setString(3, evalDate.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String rawId = rs.getString("gateway_id_norm");
                    if (rawId == null || rawId.isBlank()) {
                        continue; // Skip invalid rows
                    }

                    GatewayId id;
                    try {
                        id = new GatewayId(rawId);
                    } catch (IllegalArgumentException e) {
                        // Log and skip malformed IDs
                        continue;
                    }

                    int meters = Gateway.clampMeters(rs.getInt("n_meters"));
                    LocalDate installedOn = toLocalDate(rs, "installed_on");
                    LocalDate decommissionedOn = toLocalDate(rs, "decommissioned_on");

                    result.add(new Gateway(id, meters, installedOn, decommissionedOn));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to load gateway master data from: " + csvPath, e);
        }

        return result;
    }

    private LocalDate toLocalDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date date = rs.getDate(column);
        return date != null ? date.toLocalDate() : null;
    }
}
