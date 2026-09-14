package com.lpdg.sentinel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.lpdg.sentinel.domain.model.BaselineStats;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BaselineStats}: sigma floor enforcement, threshold computation,
 * and handling of zero-variance gateways (DQ-08).
 */
class BaselineStatsTest {

    // ── Constructor validation ────────────────────────────────

    @Test
    void constructor_acceptsValidStats() {
        BaselineStats stats = new BaselineStats(100.0, 20.0, 672, false);
        assertThat(stats.mean()).isEqualTo(100.0);
        assertThat(stats.stdDev()).isEqualTo(20.0);
        assertThat(stats.observationCount()).isEqualTo(672);
        assertThat(stats.coldStart()).isFalse();
    }

    @Test
    void constructor_rejectsNanMean() {
        assertThatThrownBy(() -> new BaselineStats(Double.NaN, 1.0, 100, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsInfiniteMean() {
        assertThatThrownBy(() -> new BaselineStats(Double.POSITIVE_INFINITY, 1.0, 100, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsNanStdDev() {
        assertThatThrownBy(() -> new BaselineStats(0.0, Double.NaN, 100, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsNegativeStdDev() {
        assertThatThrownBy(() -> new BaselineStats(0.0, -1.0, 100, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── sigmaEff floor ────────────────────────────────────────

    @Test
    void sigmaEff_returnsStdDevWhenAboveFloor() {
        BaselineStats stats = new BaselineStats(0.0, 100.0, 672, false);
        assertThat(stats.sigmaEff(60.0)).isEqualTo(100.0);
    }

    @Test
    void sigmaEff_returnsFloorWhenStdDevIsZero() {
        // DQ-08: zero-variance gateway — floor prevents division-by-zero
        BaselineStats stats = new BaselineStats(0.0, 0.0, 672, false);
        assertThat(stats.sigmaEff(60.0)).isEqualTo(60.0);
    }

    @Test
    void sigmaEff_returnsFloorWhenStdDevIsVerySmall() {
        BaselineStats stats = new BaselineStats(0.0, 0.001, 672, false);
        assertThat(stats.sigmaEff(60.0)).isEqualTo(60.0);
    }

    @Test
    void sigmaEff_exactlyAtFloor_returnsFloor() {
        BaselineStats stats = new BaselineStats(0.0, 60.0, 672, false);
        assertThat(stats.sigmaEff(60.0)).isEqualTo(60.0);
    }

    @Test
    void sigmaEff_rejectsZeroSigmaMin() {
        BaselineStats stats = new BaselineStats(0.0, 50.0, 672, false);
        assertThatThrownBy(() -> stats.sigmaEff(0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sigmaEff_rejectsNegativeSigmaMin() {
        BaselineStats stats = new BaselineStats(0.0, 50.0, 672, false);
        assertThatThrownBy(() -> stats.sigmaEff(-1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── threshold computation ─────────────────────────────────

    @Test
    void threshold_normalGateway() {
        // mu=100, sigma=20, sigmaMin=10 → sigmaEff=20, threshold=100+3*20=160
        BaselineStats stats = new BaselineStats(100.0, 20.0, 672, false);
        assertThat(stats.threshold(10.0)).isCloseTo(160.0, within(0.001));
    }

    @Test
    void threshold_zeroVarianceGateway() {
        // mu=0, sigma=0, sigmaMin=60 → sigmaEff=60, threshold=0+3*60=180
        BaselineStats stats = new BaselineStats(0.0, 0.0, 672, false);
        assertThat(stats.threshold(60.0)).isCloseTo(180.0, within(0.001));
    }

    @Test
    void threshold_rebootSigmaFloor() {
        // mu=0, sigma=0, sigmaMin=0.5 → threshold=0+3*0.5=1.5
        BaselineStats stats = new BaselineStats(0.0, 0.0, 672, false);
        assertThat(stats.threshold(0.5)).isCloseTo(1.5, within(0.001));
    }

    // ── cold start ────────────────────────────────────────────

    @Test
    void coldStart_flaggedWhenBelowThreshold() {
        BaselineStats stats = new BaselineStats(0.0, 0.0, 100, true);
        assertThat(stats.coldStart()).isTrue();
    }

    @Test
    void empty_hasColdStartFlag() {
        BaselineStats stats = BaselineStats.empty();
        assertThat(stats.coldStart()).isTrue();
        assertThat(stats.observationCount()).isZero();
        assertThat(stats.mean()).isZero();
        assertThat(stats.stdDev()).isZero();
    }
}
