package com.lpdg.sentinel.application.service;

import com.lpdg.sentinel.application.feature.BaselineStatsComputer;
import com.lpdg.sentinel.application.feature.F01FlaggedOfflineHoursComputer;
import com.lpdg.sentinel.application.feature.F02FlaggedRebootHoursComputer;
import com.lpdg.sentinel.application.feature.F03MaxConsecutiveOfflineComputer;
import com.lpdg.sentinel.application.feature.F04TrendTrajectoryComputer;
import com.lpdg.sentinel.application.feature.F06CumulativeExcessZComputer;
import com.lpdg.sentinel.domain.feature.GatewayFeatures;
import com.lpdg.sentinel.domain.model.BaselineStats;
import com.lpdg.sentinel.domain.model.DataConfidence;
import com.lpdg.sentinel.domain.model.Gateway;
import com.lpdg.sentinel.domain.model.GatewayId;
import com.lpdg.sentinel.domain.model.PredictionWeek;
import com.lpdg.sentinel.domain.port.GatewayRepository;
import com.lpdg.sentinel.domain.port.MeterReadRepository;
import com.lpdg.sentinel.domain.port.TelemetryRepository;
import com.lpdg.sentinel.domain.port.TelemetryRepository.TelemetryRow;
import com.lpdg.sentinel.domain.ranking.RankingContext;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Application-layer service that orchestrates feature extraction for a prediction week.
 *
 * <p>This service interacts with domain port interfaces ({@link GatewayRepository},
 * {@link TelemetryRepository}, {@link MeterReadRepository}) and builds a fully-populated
 * {@link RankingContext} for ranking strategies.
 */
@Service
public class FeatureExtractionService {

    // ── Feature computers ──────────────────────────────────────
    private static final F01FlaggedOfflineHoursComputer F01 = new F01FlaggedOfflineHoursComputer();
    private static final F02FlaggedRebootHoursComputer F02 = new F02FlaggedRebootHoursComputer();
    private static final F03MaxConsecutiveOfflineComputer F03 = new F03MaxConsecutiveOfflineComputer();
    private static final F04TrendTrajectoryComputer F04 = new F04TrendTrajectoryComputer();
    private static final F06CumulativeExcessZComputer F06 = new F06CumulativeExcessZComputer();

    // ── Ports ──────────────────────────────────────────────────
    private final GatewayRepository gatewayRepository;
    private final TelemetryRepository telemetryRepository;
    private final MeterReadRepository meterReadRepository;

    public FeatureExtractionService(
            GatewayRepository gatewayRepository,
            TelemetryRepository telemetryRepository,
            MeterReadRepository meterReadRepository) {
        this.gatewayRepository = gatewayRepository;
        this.telemetryRepository = telemetryRepository;
        this.meterReadRepository = meterReadRepository;
    }

    /**
     * Extracts all features for every eligible gateway in the given prediction week and
     * returns the assembled {@link RankingContext}.
     *
     * @param week the target prediction week
     * @return populated ranking context; never null
     */
    public RankingContext extractFeatures(PredictionWeek week) {
        List<Gateway> eligible = gatewayRepository.findEligible(week.targetMonday());
        List<GatewayFeatures> allFeatures = new ArrayList<>(eligible.size());

        Map<GatewayId, List<TelemetryRow>> fleetBaseline =
                telemetryRepository.findFleetByWindow(week.baselineStart(), week.baselineEnd());

        Instant scoringStartInstant = week.scoringStart().atStartOfDay(ZoneOffset.UTC).toInstant();

        for (Gateway gateway : eligible) {
            List<TelemetryRow> baselineRows = fleetBaseline.get(gateway.id());
            List<TelemetryRow> scoringRows;

            if (baselineRows != null) {
                scoringRows =
                        baselineRows.stream()
                                .filter(r -> !r.tsUtc().isBefore(scoringStartInstant))
                                .toList();
            } else if (!fleetBaseline.isEmpty()) {
                baselineRows = List.of();
                scoringRows = List.of();
            } else {
                baselineRows =
                        telemetryRepository.findByGatewayAndWindow(
                                gateway.id(), week.baselineStart(), week.baselineEnd());
                scoringRows =
                        telemetryRepository.findByGatewayAndWindow(
                                gateway.id(), week.scoringStart(), week.scoringEnd());
            }

            GatewayFeatures features = extractForGateway(gateway, week, baselineRows, scoringRows);
            allFeatures.add(features);
        }

        return new RankingContext(week, allFeatures);
    }

    // ── Private extraction per gateway ─────────────────────────

    private GatewayFeatures extractForGateway(
            Gateway gateway,
            PredictionWeek week,
            List<TelemetryRow> baselineRows,
            List<TelemetryRow> scoringRows) {

        // Compute per-metric baseline statistics
        BaselineStats offlineStats = BaselineStatsComputer.computeOfflineStats(baselineRows);
        BaselineStats rebootStats = BaselineStatsComputer.computeRebootStats(baselineRows);

        // Determine data confidence
        DataConfidence confidence = resolveConfidence(offlineStats, scoringRows);

        GatewayFeatures.Builder builder =
                GatewayFeatures.builder(gateway.id(), week)
                        .dataConfidence(confidence)
                        .f05MetersExposure(gateway.nMetersInstalled())
                        .f07HistoricReadSuccess(meterReadRepository.findSuccessRate(gateway.id()));

        if (confidence == DataConfidence.MISSING_TELEMETRY) {
            // Hard-dead gateway: treat as 100% offline for 168 hours
            return buildHardDeadFeatures(builder);
        }

        // Apply feature computer pipeline
        F01.compute(builder, scoringRows, baselineRows, offlineStats, rebootStats);
        F02.compute(builder, scoringRows, baselineRows, offlineStats, rebootStats);
        F03.compute(builder, scoringRows, baselineRows, offlineStats, rebootStats);
        F04.compute(builder, scoringRows, baselineRows, offlineStats, rebootStats);
        F06.compute(builder, scoringRows, baselineRows, offlineStats, rebootStats);

        // F08: sum of disconnection_cnt over scoring window
        int totalDisconnections =
                scoringRows.stream().mapToInt(TelemetryRow::disconnectionCnt).sum();
        builder.f08DisconnectionFrequency(totalDisconnections);

        return builder.build();
    }

    private DataConfidence resolveConfidence(
            BaselineStats offlineStats, List<TelemetryRow> scoringRows) {
        if (scoringRows.isEmpty()) {
            return DataConfidence.MISSING_TELEMETRY;
        }
        if (offlineStats.coldStart()) {
            return DataConfidence.LOW_HISTORY;
        }
        return DataConfidence.FULL;
    }

    private GatewayFeatures buildHardDeadFeatures(GatewayFeatures.Builder builder) {
        return builder.f01FlaggedOfflineHours(168)
                .f02FlaggedRebootHours(0)
                .f03MaxConsecutiveOfflineHours(168)
                .f04TrendTrajectoryRatio(F04TrendTrajectoryComputer.MAX_RATIO)
                .f06CumulativeExcessZ(0.0)
                .f08DisconnectionFrequency(0)
                .build();
    }
}
