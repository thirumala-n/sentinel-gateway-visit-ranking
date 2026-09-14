package com.lpdg.sentinel.domain.model;

import java.util.Objects;

/**
 * Immutable value object representing a normalized gateway identifier.
 *
 * <p>The challenge data uses two MAC formats:
 *
 * <ul>
 *   <li>17-char colon-delimited: {@code 06:39:EA:56:02:C1} (gateway_master.csv,
 *       field_visits.csv, engineer_review.xlsx)
 *   <li>12-char uppercase hex: {@code 0639EA5602C1} (telemetry/*.parquet,
 *       meter_read_success.csv)
 * </ul>
 *
 * <p>All gateway identifiers are normalized to the 12-char uppercase hex form on
 * construction. This is the canonical identifier used throughout the system.
 *
 * <p>Normalization rule: {@code TRIM(UPPER(REPLACE(raw, ':', '')))}
 *
 * @param value normalized 12-character uppercase hexadecimal string
 */
public record GatewayId(String value) implements Comparable<GatewayId> {

    /** Expected length of a normalized gateway identifier. */
    private static final int NORMALIZED_LENGTH = 12;

    public GatewayId {
        Objects.requireNonNull(value, "Gateway ID value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Gateway ID must not be blank");
        }
        if (value.length() != NORMALIZED_LENGTH) {
            throw new IllegalArgumentException(
                    "Normalized gateway ID must be exactly 12 characters, got: '"
                            + value
                            + "' (length="
                            + value.length()
                            + ")");
        }
        if (!value.matches("[0-9A-F]{12}")) {
            throw new IllegalArgumentException(
                    "Normalized gateway ID must contain only uppercase hex characters [0-9A-F], got: '"
                            + value
                            + "'");
        }
    }

    /**
     * Normalizes a raw gateway identifier string to the canonical 12-char uppercase hex format.
     *
     * <p>Accepts both colon-delimited MACs (17 chars) and plain hex strings (12 chars). The
     * transformation is {@code TRIM(UPPER(REPLACE(raw, ':', '')))}.
     *
     * @param raw raw gateway identifier from any data source
     * @return normalized {@link GatewayId}
     * @throws IllegalArgumentException if the raw value is null, blank, or cannot be normalized to
     *     a valid 12-char hex string
     */
    public static GatewayId normalize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Cannot normalize a null gateway ID");
        }
        String normalized = raw.trim().toUpperCase().replace(":", "");
        return new GatewayId(normalized);
    }

    /**
     * Alias factory method for {@link #normalize(String)}.
     *
     * @param raw raw gateway identifier
     * @return normalized {@link GatewayId}
     */
    public static GatewayId of(String raw) {
        return normalize(raw);
    }

    @Override
    public int compareTo(GatewayId other) {
        return this.value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
