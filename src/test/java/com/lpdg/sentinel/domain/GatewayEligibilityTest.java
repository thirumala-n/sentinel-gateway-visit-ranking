package com.lpdg.sentinel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lpdg.sentinel.domain.model.Gateway;
import com.lpdg.sentinel.domain.model.GatewayId;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Gateway#isEligible(LocalDate)} — DQ-04 (decommissioned) and
 * DQ-05 (future installation) eligibility rules.
 */
class GatewayEligibilityTest {

    private static final GatewayId ID = new GatewayId("0639EA5602C1");
    private static final LocalDate EVAL = LocalDate.of(2026, 3, 9); // targetMonday

    // ── Active (no decommission, installed before eval) ───────

    @Test
    void activeGateway_isEligible() {
        Gateway gw = new Gateway(ID, 200, LocalDate.of(2025, 1, 1), null);
        assertThat(gw.isEligible(EVAL)).isTrue();
    }

    @Test
    void nullInstalledOn_isEligible() {
        Gateway gw = new Gateway(ID, 200, null, null);
        assertThat(gw.isEligible(EVAL)).isTrue();
    }

    // ── Decommissioned (DQ-04) ────────────────────────────────

    @Test
    void decommissionedBeforeEval_isNotEligible() {
        // Decommissioned one day before targetMonday
        Gateway gw = new Gateway(ID, 200, LocalDate.of(2025, 1, 1), EVAL.minusDays(1));
        assertThat(gw.isEligible(EVAL)).isFalse();
    }

    @Test
    void decommissionedExactlyOnEvalDate_isEligible() {
        // decommissioned_on >= evalDate means still active at the start of the window
        Gateway gw = new Gateway(ID, 200, LocalDate.of(2025, 1, 1), EVAL);
        assertThat(gw.isEligible(EVAL)).isTrue();
    }

    @Test
    void decommissionedAfterEval_isEligible() {
        Gateway gw = new Gateway(ID, 200, LocalDate.of(2025, 1, 1), EVAL.plusDays(7));
        assertThat(gw.isEligible(EVAL)).isTrue();
    }

    // ── Future installation (DQ-05) ───────────────────────────

    @Test
    void futureInstallation_isNotEligible() {
        // installed_on is after evalDate: gateway doesn't exist yet
        Gateway gw = new Gateway(ID, 200, EVAL.plusDays(1), null);
        assertThat(gw.isEligible(EVAL)).isFalse();
    }

    @Test
    void installedExactlyOnEvalDate_isNotEligible() {
        // installed_on > evalDate triggers exclusion (installed on the Monday itself
        // means not yet available for that week's scoring)
        Gateway gw = new Gateway(ID, 200, EVAL, null);
        // installed_on == evalDate → installed_on.isAfter(evalDate) is FALSE → eligible
        // Per rule: installed_on <= evalDate → eligible
        assertThat(gw.isEligible(EVAL)).isTrue();
    }

    @Test
    void installedBeforeEval_isEligible() {
        Gateway gw = new Gateway(ID, 200, EVAL.minusDays(100), null);
        assertThat(gw.isEligible(EVAL)).isTrue();
    }

    // ── Meter clamping ────────────────────────────────────────

    @Test
    void clampMeters_nullReturnsDefault() {
        assertThat(Gateway.clampMeters(null)).isEqualTo(Gateway.DEFAULT_METERS);
    }

    @Test
    void clampMeters_zeroReturnsDefault() {
        assertThat(Gateway.clampMeters(0)).isEqualTo(Gateway.DEFAULT_METERS);
    }

    @Test
    void clampMeters_negativeReturnsDefault() {
        assertThat(Gateway.clampMeters(-5)).isEqualTo(Gateway.DEFAULT_METERS);
    }

    @Test
    void clampMeters_normalValuePassesThrough() {
        assertThat(Gateway.clampMeters(300)).isEqualTo(300);
    }

    @Test
    void clampMeters_exceedsMaxIsClamped() {
        assertThat(Gateway.clampMeters(2000)).isEqualTo(1000);
    }

    @Test
    void constructor_rejectsInvalidMeterCount() {
        assertThatThrownBy(() -> new Gateway(ID, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
