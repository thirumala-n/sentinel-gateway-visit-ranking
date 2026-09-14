package com.lpdg.sentinel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lpdg.sentinel.domain.model.GatewayId;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GatewayId} normalization and validation.
 */
class GatewayIdTest {

    @Test
    void constructor_acceptsValidNormalizedId() {
        GatewayId id = new GatewayId("0639EA5602C1");
        assertThat(id.value()).isEqualTo("0639EA5602C1");
    }

    @Test
    void normalize_stripsColonsAndUppercases() {
        GatewayId id = GatewayId.normalize("06:39:ea:56:02:c1");
        assertThat(id.value()).isEqualTo("0639EA5602C1");
    }

    @Test
    void normalize_alreadyNormalizedPassesThrough() {
        GatewayId id = GatewayId.normalize("0639EA5602C1");
        assertThat(id.value()).isEqualTo("0639EA5602C1");
    }

    @Test
    void normalize_tripsLeadingTrailingWhitespace() {
        GatewayId id = GatewayId.normalize("  0639EA5602C1  ");
        assertThat(id.value()).isEqualTo("0639EA5602C1");
    }

    @Test
    void normalize_handlesUpperCaseColonMac() {
        GatewayId id = GatewayId.normalize("06:39:EA:56:02:C1");
        assertThat(id.value()).isEqualTo("0639EA5602C1");
    }

    @Test
    void constructor_rejectsNull() {
        assertThatThrownBy(() -> new GatewayId(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_rejectsBlank() {
        assertThatThrownBy(() -> new GatewayId("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsWrongLength() {
        assertThatThrownBy(() -> new GatewayId("0639EA56"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12");
    }

    @Test
    void constructor_rejectsLowercaseHex() {
        assertThatThrownBy(() -> new GatewayId("0639ea5602c1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uppercase");
    }

    @Test
    void normalize_nullThrows() {
        assertThatThrownBy(() -> GatewayId.normalize(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityBasedOnValue() {
        GatewayId a = GatewayId.normalize("06:39:EA:56:02:C1");
        GatewayId b = new GatewayId("0639EA5602C1");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
