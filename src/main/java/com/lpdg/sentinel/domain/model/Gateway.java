package com.lpdg.sentinel.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable gateway master-data record.
 *
 * <p>Loaded from {@code gateway_master.csv} (Latin-1 encoded). Contains metadata
 * needed to determine fleet eligibility and business-impact weighting for ranking.
 *
 * <h3>Eligibility Rules (DQ-04, DQ-05)</h3>
 *
 * <ul>
 *   <li>A gateway decommissioned before the eval date must NOT be a visit candidate.
 *   <li>A gateway whose {@code installedOn} is after the eval date is a future install
 *       and must NOT be scored.
 * </ul>
 *
 * @param id normalized gateway identifier (12-char uppercase hex)
 * @param nMetersInstalled number of smart meters depending on this gateway (clamped to [1, 1000]);
 *     defaults to fleet median 156 when absent in master data
 * @param installedOn date the gateway was installed; {@code null} treated as very early (always
 *     installed)
 * @param decommissionedOn date the gateway was decommissioned; {@code null} means still active
 */
public record Gateway(
        GatewayId id,
        int nMetersInstalled,
        LocalDate installedOn,
        LocalDate decommissionedOn) {

    /** Fleet median meter count used when {@code n_meters_installed} is missing. */
    public static final int DEFAULT_METERS = 156;

    /** Minimum clamped meter count. */
    private static final int MIN_METERS = 1;

    /** Maximum clamped meter count. */
    private static final int MAX_METERS = 1000;

    public Gateway {
        Objects.requireNonNull(id, "Gateway id must not be null");
        if (nMetersInstalled < MIN_METERS || nMetersInstalled > MAX_METERS) {
            throw new IllegalArgumentException(
                    "nMetersInstalled must be in [1, 1000], got: " + nMetersInstalled);
        }
    }

    /**
     * Returns {@code true} if this gateway is a valid candidate for visit scheduling
     * during the week that starts on {@code evalDate} (exclusive upper bound for all data).
     *
     * <p>Eligibility conditions (both must hold):
     *
     * <ol>
     *   <li>Gateway was installed on or before {@code evalDate} (i.e., it exists in the target
     *       week).
     *   <li>Gateway was NOT decommissioned before {@code evalDate} (i.e., it was still active when
     *       the scoring window begins).
     * </ol>
     *
     * @param evalDate the exclusive upper boundary of the prediction week ({@code targetMonday})
     * @return {@code true} if the gateway is eligible for ranking
     */
    public boolean isEligible(LocalDate evalDate) {
        Objects.requireNonNull(evalDate, "evalDate must not be null");

        // Future installations: not yet in service
        if (installedOn != null && installedOn.isAfter(evalDate)) {
            return false;
        }

        // Decommissioned before the prediction window opens
        if (decommissionedOn != null && decommissionedOn.isBefore(evalDate)) {
            return false;
        }

        return true;
    }

    /**
     * Clamps a raw meter count to the valid range [1, 1000], substituting the fleet
     * median when the raw value is null or zero.
     *
     * @param raw raw meter count from master data (may be null)
     * @return clamped meter count in [1, 1000]
     */
    public static int clampMeters(Integer raw) {
        if (raw == null || raw <= 0) {
            return DEFAULT_METERS;
        }
        return Math.min(MAX_METERS, Math.max(MIN_METERS, raw));
    }
}
