package com.lpdg.sentinel.web.controller;

import com.lpdg.sentinel.application.explanation.RankingComparisonReport;
import com.lpdg.sentinel.application.model.WeeklyPredictionsResult;
import com.lpdg.sentinel.application.service.GatewayPredictionApplicationService;
import com.lpdg.sentinel.common.errors.MalformedGatewayIdException;
import com.lpdg.sentinel.config.SentinelProperties;
import com.lpdg.sentinel.domain.explanation.DecisionExplanation;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import com.lpdg.sentinel.web.dto.FeatureContributionDto;
import com.lpdg.sentinel.web.dto.GatewayComparisonResponse;
import com.lpdg.sentinel.web.dto.GatewayExplanationResponse;
import com.lpdg.sentinel.web.dto.PredictionItemDto;
import com.lpdg.sentinel.web.dto.WeeklyPredictionsResponse;
import com.lpdg.sentinel.web.validation.PredictionWeekParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing prediction, explainability, comparison, and rerun endpoints.
 *
 * <p>Remains thin: all business logic, feature calculations, and ranking orchestrations
 * are delegated to the application and domain layers.
 */
@RestController
@RequestMapping("/api/v1/predictions")
@Tag(name = "Predictions", description = "Endpoints for weekly gateway visit decisions and decision evidence")
public class PredictionController {

    private final GatewayPredictionApplicationService predictionService;
    private final PredictionWeekParser weekParser;
    private final SentinelProperties properties;

    public PredictionController(
            GatewayPredictionApplicationService predictionService,
            PredictionWeekParser weekParser,
            SentinelProperties properties) {
        this.predictionService = Objects.requireNonNull(predictionService, "predictionService must not be null");
        this.weekParser = Objects.requireNonNull(weekParser, "weekParser must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @GetMapping("/{week}")
    @Operation(summary = "Get weekly visit recommendations",
            description = "Returns the exactly 15 ranked gateway recommendations for the requested prediction week.")
    public ResponseEntity<WeeklyPredictionsResponse> getPredictions(
            @Parameter(description = "Target Monday in ISO format (YYYY-MM-DD or YYYY-Www)", example = "2026-03-09")
            @PathVariable("week") String rawWeek) {

        PredictionWeek week = weekParser.parse(rawWeek);
        WeeklyPredictionsResult result = predictionService.getPredictions(week);
        return ResponseEntity.ok(toWeeklyResponse(result));
    }

    @GetMapping("/{week}/{gatewayId}/explain")
    @Operation(summary = "Explain gateway ranking decision",
            description = "Returns structured evidence and feature contributions explaining why a gateway was ranked where it is.")
    public ResponseEntity<GatewayExplanationResponse> explainGateway(
            @Parameter(description = "Target prediction week", example = "2026-03-09")
            @PathVariable("week") String rawWeek,
            @Parameter(description = "Gateway identifier (12-char hex or 17-char colon MAC)", example = "02D3289B907C")
            @PathVariable("gatewayId") String rawGatewayId) {

        PredictionWeek week = weekParser.parse(rawWeek);
        GatewayId gatewayId = parseGatewayId(rawGatewayId);
        DecisionExplanation explanation = predictionService.getExplanation(week, gatewayId);
        return ResponseEntity.ok(toExplanationResponse(explanation));
    }

    @GetMapping("/{week}/{gatewayId}/compare/{otherGatewayId}")
    @Operation(summary = "Pairwise gateway ranking comparison",
            description = "Explains why one gateway outranked another based on observable feature contrasts.")
    public ResponseEntity<GatewayComparisonResponse> compareGateways(
            @Parameter(description = "Target prediction week", example = "2026-03-09")
            @PathVariable("week") String rawWeek,
            @Parameter(description = "First gateway identifier", example = "02D3289B907C")
            @PathVariable("gatewayId") String rawGatewayIdA,
            @Parameter(description = "Second gateway identifier", example = "029E65D7B701")
            @PathVariable("otherGatewayId") String rawGatewayIdB) {

        PredictionWeek week = weekParser.parse(rawWeek);
        GatewayId gatewayA = parseGatewayId(rawGatewayIdA);
        GatewayId gatewayB = parseGatewayId(rawGatewayIdB);

        RankingComparisonReport report = predictionService.compare(week, gatewayA, gatewayB);
        return ResponseEntity.ok(new GatewayComparisonResponse(
                report.higherGateway().value(),
                report.lowerGateway().value(),
                report.reasons(),
                report.scoreProximityWarning()));
    }

    @PostMapping("/{week}/rerun")
    @Operation(summary = "Rerun ranking for week",
            description = "Forces a fresh recalculation from source telemetry data, bypassing cached results.")
    public ResponseEntity<WeeklyPredictionsResponse> rerunPredictions(
            @Parameter(description = "Target prediction week", example = "2026-03-09")
            @PathVariable("week") String rawWeek) {

        PredictionWeek week = weekParser.parse(rawWeek);
        WeeklyPredictionsResult result = predictionService.rerun(week);
        return ResponseEntity.ok(toWeeklyResponse(result));
    }

    // ── Mapping Helpers ────────────────────────────────────────

    private GatewayId parseGatewayId(String raw) {
        try {
            return GatewayId.normalize(raw);
        } catch (IllegalArgumentException e) {
            throw new MalformedGatewayIdException(
                    "Invalid gateway ID '" + raw + "'. Expected 12-char uppercase hex or 17-char colon MAC.");
        }
    }

    private WeeklyPredictionsResponse toWeeklyResponse(WeeklyPredictionsResult result) {
        List<PredictionItemDto> dtoList = result.items().stream()
                .map(record -> new PredictionItemDto(
                        record.rank(),
                        record.gatewayId().value(),
                        record.score(),
                        record.reason(),
                        record.explanation().category().name(),
                        record.explanation().riskLevel().name(),
                        record.explanation().confidence().name(),
                        record.fallback()))
                .toList();

        return new WeeklyPredictionsResponse(
                result.week().targetMonday().toString(),
                dtoList.size(),
                properties.visitsPerWeek(),
                result.rankingMethod(),
                result.generatedAt(),
                dtoList);
    }

    private GatewayExplanationResponse toExplanationResponse(DecisionExplanation explanation) {
        List<FeatureContributionDto> contributions = explanation.featureContributions().stream()
                .map(fc -> new FeatureContributionDto(
                        fc.featureCode(),
                        fc.featureName(),
                        fc.valueFormatted(),
                        fc.severityLabel(),
                        fc.active()))
                .toList();

        return new GatewayExplanationResponse(
                explanation.gatewayId().value(),
                explanation.rank(),
                explanation.score(),
                explanation.category().name(),
                explanation.riskLevel().name(),
                explanation.confidence().name(),
                explanation.fallback(),
                explanation.csvReason(),
                explanation.summary(),
                contributions,
                explanation.warnings(),
                explanation.limitations());
    }
}
