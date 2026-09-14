package com.lpdg.sentinel.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lpdg.sentinel.application.explanation.RankingComparisonReport;
import com.lpdg.sentinel.application.model.WeeklyPredictionsResult;
import com.lpdg.sentinel.application.model.WeeklyPredictionsResult.PredictionRecord;
import com.lpdg.sentinel.application.service.GatewayPredictionApplicationService;
import com.lpdg.sentinel.common.errors.GatewayNotFoundException;
import com.lpdg.sentinel.config.SentinelProperties;
import com.lpdg.sentinel.domain.explanation.DecisionCategory;
import com.lpdg.sentinel.domain.explanation.DecisionExplanation;
import com.lpdg.sentinel.domain.explanation.FeatureContribution;
import com.lpdg.sentinel.domain.explanation.RiskLevel;
import com.lpdg.sentinel.domain.model.DataConfidence;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import com.lpdg.sentinel.web.exception.GlobalExceptionHandler;
import com.lpdg.sentinel.web.validation.PredictionWeekParser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Isolated WebMvc test validating controller routing, DTO mapping, and exception advice.
 */
@WebMvcTest(PredictionController.class)
@org.springframework.boot.context.properties.EnableConfigurationProperties(SentinelProperties.class)
@Import({PredictionWeekParser.class, GlobalExceptionHandler.class})
class PredictionControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GatewayPredictionApplicationService predictionService;

    private PredictionWeek validWeek;
    private GatewayId gw1;
    private GatewayId gw2;
    private WeeklyPredictionsResult mockResult;
    private DecisionExplanation mockExplanation1;

    @BeforeEach
    void setUp() {
        validWeek = new PredictionWeek(LocalDate.of(2026, 3, 9));
        gw1 = GatewayId.of("02D3289B907C");
        gw2 = GatewayId.of("029E65D7B701");

        mockExplanation1 = new DecisionExplanation(
                1,
                gw1,
                84.12,
                false,
                DecisionCategory.MISSING_TELEMETRY,
                RiskLevel.CRITICAL,
                List.of(new FeatureContribution("F01", "Flagged Offline Hours", "168h", "CRITICAL", true)),
                DataConfidence.MISSING_TELEMETRY,
                List.of("No telemetry received in scoring week"),
                List.of("Subject to baseline drift"),
                "168h continuous outage; 168 offline anomalies. 156 meters affected.",
                "Primary driver is sustained complete offline failure.");

        List<PredictionRecord> items = new ArrayList<>();
        items.add(new PredictionRecord(1, gw1, 84.12, mockExplanation1.csvReason(), mockExplanation1, false));

        for (int i = 2; i <= 15; i++) {
            GatewayId id = GatewayId.of(String.format("AAAAAAAA%04X", i));
            DecisionExplanation expl = new DecisionExplanation(
                    i, id, 80.0 - i, false, DecisionCategory.ACTIONABLE_RISK, RiskLevel.HIGH,
                    List.of(), DataConfidence.FULL, List.of(), List.of(),
                    "Actionable risk detected. " + (100 + i) + " meters affected.",
                    "Summary for rank " + i);
            items.add(new PredictionRecord(i, id, 80.0 - i, expl.csvReason(), expl, false));
        }

        mockResult = new WeeklyPredictionsResult(validWeek, items, "RISK_BASED", Instant.parse("2026-09-15T02:00:00Z"));
    }

    // ── 1. Successful Predictions Endpoint ─────────────────────

    @Test
    void getPredictions_validIsoDate_returns200And15Items() throws Exception {
        when(predictionService.getPredictions(any(PredictionWeek.class))).thenReturn(mockResult);

        mockMvc.perform(get("/api/v1/predictions/2026-03-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.week_start", is("2026-03-09")))
                .andExpect(jsonPath("$.total_selected", is(15)))
                .andExpect(jsonPath("$.capacity", is(15)))
                .andExpect(jsonPath("$.ranking_method", is("RISK_BASED")))
                .andExpect(jsonPath("$.predictions", hasSize(15)))
                .andExpect(jsonPath("$.predictions[0].rank", is(1)))
                .andExpect(jsonPath("$.predictions[0].gateway_id", is("02D3289B907C")))
                .andExpect(jsonPath("$.predictions[0].score", is(84.12)))
                .andExpect(jsonPath("$.predictions[0].decision_category", is("MISSING_TELEMETRY")))
                .andExpect(jsonPath("$.predictions[0].risk_level", is("CRITICAL")))
                .andExpect(jsonPath("$.predictions[0].fallback", is(false)));

        verify(predictionService).getPredictions(validWeek);
    }

    @Test
    void getPredictions_validIsoWeek_returns200AndParsesCorrectMonday() throws Exception {
        when(predictionService.getPredictions(any(PredictionWeek.class))).thenReturn(mockResult);

        // 2026-W11 corresponds to Monday 2026-03-09
        mockMvc.perform(get("/api/v1/predictions/2026-W11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.week_start", is("2026-03-09")))
                .andExpect(jsonPath("$.predictions", hasSize(15)));

        verify(predictionService).getPredictions(validWeek);
    }

    // ── 2. Explanation Endpoint ────────────────────────────────

    @Test
    void explainGateway_canonicalId_returns200WithFullEvidence() throws Exception {
        when(predictionService.getExplanation(eq(validWeek), eq(gw1))).thenReturn(mockExplanation1);

        mockMvc.perform(get("/api/v1/predictions/2026-03-09/02D3289B907C/explain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gateway_id", is("02D3289B907C")))
                .andExpect(jsonPath("$.rank", is(1)))
                .andExpect(jsonPath("$.score", is(84.12)))
                .andExpect(jsonPath("$.decision_category", is("MISSING_TELEMETRY")))
                .andExpect(jsonPath("$.risk_level", is("CRITICAL")))
                .andExpect(jsonPath("$.feature_contributions", hasSize(1)))
                .andExpect(jsonPath("$.feature_contributions[0].feature_code", is("F01")))
                .andExpect(jsonPath("$.feature_contributions[0].severity", is("CRITICAL")))
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.limitations", hasSize(1)));
    }

    @Test
    void explainGateway_colonDelimitedMac_normalizesAndReturns200() throws Exception {
        when(predictionService.getExplanation(eq(validWeek), eq(gw1))).thenReturn(mockExplanation1);

        mockMvc.perform(get("/api/v1/predictions/2026-03-09/02:D3:28:9B:90:7C/explain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gateway_id", is("02D3289B907C")));

        verify(predictionService).getExplanation(validWeek, gw1);
    }

    // ── 3. Comparison Endpoint ─────────────────────────────────

    @Test
    void compareGateways_validPair_returns200WithReport() throws Exception {
        RankingComparisonReport report = new RankingComparisonReport(
                gw1, gw2,
                List.of("Gateway 02D3289B907C has 168h continuous outage vs 0h.", "Score: 84.12 vs 65.40."),
                false);

        when(predictionService.compare(eq(validWeek), eq(gw1), eq(gw2))).thenReturn(report);

        mockMvc.perform(get("/api/v1/predictions/2026-03-09/02D3289B907C/compare/029E65D7B701"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.higher_gateway_id", is("02D3289B907C")))
                .andExpect(jsonPath("$.lower_gateway_id", is("029E65D7B701")))
                .andExpect(jsonPath("$.reasons", hasSize(2)))
                .andExpect(jsonPath("$.score_proximity_warning", is(false)));
    }

    // ── 4. Rerun Endpoint ──────────────────────────────────────

    @Test
    void rerunPredictions_validWeek_returns200() throws Exception {
        when(predictionService.rerun(eq(validWeek))).thenReturn(mockResult);

        mockMvc.perform(post("/api/v1/predictions/2026-03-09/rerun"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_selected", is(15)))
                .andExpect(jsonPath("$.ranking_method", is("RISK_BASED")));

        verify(predictionService).rerun(validWeek);
    }

    // ── 5. Validation and Error Cases ──────────────────────────

    @Test
    void invalidWeek_malformedString_returns400MalformedWeek() throws Exception {
        mockMvc.perform(get("/api/v1/predictions/invalid-date-string"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("MALFORMED_WEEK")))
                .andExpect(jsonPath("$.message", containsString("Expected ISO date")));
    }

    @Test
    void invalidWeek_tuesdayDate_returns400InvalidWeekDay() throws Exception {
        // 2026-03-10 is a Tuesday
        mockMvc.perform(get("/api/v1/predictions/2026-03-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("INVALID_WEEK_DAY")))
                .andExpect(jsonPath("$.message", containsString("must be anchored to a Monday")));
    }

    @Test
    void unsupportedWeek_pastDateBeforeData_returns422UnsupportedWeek() throws Exception {
        // 2025-08-18 is before 2025-09-01 (insufficient 28-day baseline)
        mockMvc.perform(get("/api/v1/predictions/2025-08-18"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status", is(422)))
                .andExpect(jsonPath("$.code", is("UNSUPPORTED_WEEK")))
                .andExpect(jsonPath("$.message", containsString("requires 28 days of baseline history")));
    }

    @Test
    void futureWeek_dateBeyondDataset_returns422FutureWeek() throws Exception {
        // 2026-04-06 is after 2026-03-30
        mockMvc.perform(get("/api/v1/predictions/2026-04-06"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status", is(422)))
                .andExpect(jsonPath("$.code", is("FUTURE_WEEK")))
                .andExpect(jsonPath("$.message", containsString("is in the future")));
    }

    @Test
    void malformedGatewayId_invalidHex_returns400MalformedId() throws Exception {
        mockMvc.perform(get("/api/v1/predictions/2026-03-09/NOT_A_VALID_HEX/explain"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("MALFORMED_GATEWAY_ID")))
                .andExpect(jsonPath("$.message", containsString("Expected 12-char uppercase hex")));
    }

    @Test
    void gatewayNotFound_validIdNotInTop15_returns404() throws Exception {
        GatewayId missingId = GatewayId.of("000000000099");
        when(predictionService.getExplanation(eq(validWeek), eq(missingId)))
                .thenThrow(new GatewayNotFoundException("Gateway 000000000099 is not in the top 15 recommendations"));

        mockMvc.perform(get("/api/v1/predictions/2026-03-09/000000000099/explain"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.code", is("GATEWAY_NOT_FOUND")))
                .andExpect(jsonPath("$.message", containsString("is not in the top 15 recommendations")));
    }

    @Test
    void compareGateways_selfComparison_returns400InvalidComparison() throws Exception {
        when(predictionService.compare(eq(validWeek), eq(gw1), eq(gw1)))
                .thenThrow(new com.lpdg.sentinel.common.errors.InvalidComparisonException("Cannot compare gateway to itself: " + gw1));

        mockMvc.perform(get("/api/v1/predictions/2026-03-09/02D3289B907C/compare/02D3289B907C"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("INVALID_COMPARISON")))
                .andExpect(jsonPath("$.message", containsString("Cannot compare gateway to itself")));
    }
}
