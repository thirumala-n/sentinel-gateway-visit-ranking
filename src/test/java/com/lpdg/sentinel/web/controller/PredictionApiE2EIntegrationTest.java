package com.lpdg.sentinel.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lpdg.sentinel.web.dto.WeeklyPredictionsResponse;
import java.io.File;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Full end-to-end integration test exercising the complete production path:
 * HTTP -> Controller -> Application Service -> Repository -> DuckDB -> Parquet -> Feature Engine -> Ranking -> Explanation -> HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "sentinel.data-dir=data",
        "sentinel.visits-per-week=15",
        "sentinel.ranking-strategy=risk-based"
})
class PredictionApiE2EIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void verifyDataAvailable() {
        File dataDir = new File("data");
        Assumptions.assumeTrue(
                dataDir.exists() && new File(dataDir, "telemetry").exists(),
                "Skipping E2E test: real data directory not available");
    }

    @Test
    void endToEnd_fullPipeline_predictionsExplanationComparisonAndRerun() throws Exception {
        String targetWeek = "2026-03-09";

        // 1. GET /api/v1/predictions/{week}
        MvcResult predResult = mockMvc.perform(get("/api/v1/predictions/" + targetWeek))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.week_start", is(targetWeek)))
                .andExpect(jsonPath("$.total_selected", is(15)))
                .andExpect(jsonPath("$.capacity", is(15)))
                .andExpect(jsonPath("$.ranking_method", is("RISK_BASED")))
                .andExpect(jsonPath("$.predictions", hasSize(15)))
                .andReturn();

        WeeklyPredictionsResponse predictionsResponse = objectMapper.readValue(
                predResult.getResponse().getContentAsString(), WeeklyPredictionsResponse.class);

        assertThat(predictionsResponse.predictions()).hasSize(15);

        // Verify ordering, unique IDs, and finite scores
        for (int i = 0; i < 15; i++) {
            var p = predictionsResponse.predictions().get(i);
            assertThat(p.rank()).isEqualTo(i + 1);
            assertThat(Double.isNaN(p.score())).isFalse();
            assertThat(Double.isInfinite(p.score())).isFalse();
            assertThat(p.reason()).isNotBlank();
            assertThat(p.decisionCategory()).isNotBlank();
            assertThat(p.riskLevel()).isNotBlank();
        }

        long uniqueGateways = predictionsResponse.predictions().stream()
                .map(com.lpdg.sentinel.web.dto.PredictionItemDto::gatewayId).distinct().count();
        assertThat(uniqueGateways).isEqualTo(15);

        String topGateway = predictionsResponse.predictions().get(0).gatewayId();
        String secondGateway = predictionsResponse.predictions().get(1).gatewayId();

        // 2. GET /api/v1/predictions/{week}/{gatewayId}/explain
        mockMvc.perform(get("/api/v1/predictions/" + targetWeek + "/" + topGateway + "/explain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gateway_id", is(topGateway)))
                .andExpect(jsonPath("$.rank", is(1)))
                .andExpect(jsonPath("$.score", is(predictionsResponse.predictions().get(0).score())))
                .andExpect(jsonPath("$.primary_reason", is(predictionsResponse.predictions().get(0).reason())))
                .andExpect(jsonPath("$.summary").isString())
                .andExpect(jsonPath("$.feature_contributions").isArray());

        // 3. GET /api/v1/predictions/{week}/{gatewayId}/compare/{otherGatewayId}
        mockMvc.perform(get("/api/v1/predictions/" + targetWeek + "/" + topGateway + "/compare/" + secondGateway))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.higher_gateway_id", is(topGateway)))
                .andExpect(jsonPath("$.lower_gateway_id", is(secondGateway)))
                .andExpect(jsonPath("$.reasons").isArray())
                .andExpect(jsonPath("$.score_proximity_warning").isBoolean());

        // 4. POST /api/v1/predictions/{week}/rerun
        mockMvc.perform(post("/api/v1/predictions/" + targetWeek + "/rerun"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_selected", is(15)))
                .andExpect(jsonPath("$.predictions[0].gateway_id", is(topGateway)))
                .andExpect(jsonPath("$.predictions[0].score", is(predictionsResponse.predictions().get(0).score())));

        // 5. GET /actuator/health
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }
}
