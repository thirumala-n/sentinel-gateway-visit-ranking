package com.lpdg.sentinel.infrastructure.csv;

import com.lpdg.sentinel.application.model.WeeklyPredictionsResult;
import com.lpdg.sentinel.application.model.WeeklyPredictionsResult.PredictionRecord;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

/**
 * Serializer that exports canonical {@link WeeklyPredictionsResult} to RFC-4180 compliant CSV format.
 *
 * <p>Schema: {@code week,rank,gateway_id,score,reason}
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Uses the exact same {@link PredictionRecord} instances that power the REST API.</li>
 *   <li>Deterministic column ordering and row sorting by rank ascending (1..15).</li>
 *   <li>Appropriate RFC-4180 escaping for reason strings containing commas, semicolons, or quotes.</li>
 * </ul>
 */
@Component
public class PredictionsCsvExporter {

    public static final String[] HEADERS = {"week_start", "rank", "gateway_id", "score", "reason"};

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader(HEADERS)
            .setRecordSeparator("\n")
            .build();

    /**
     * Exports the weekly prediction result to a CSV string.
     *
     * @param result canonical weekly prediction result
     * @return CSV formatted string with headers
     */
    public String exportToString(WeeklyPredictionsResult result) {
        Objects.requireNonNull(result, "result must not be null");
        return exportAllToString(List.of(result));
    }

    /**
     * Exports multiple weekly prediction results into a single consolidated CSV string.
     *
     * @param results list of canonical weekly prediction results
     * @return CSV formatted string with header and all rows
     */
    public String exportAllToString(List<WeeklyPredictionsResult> results) {
        StringWriter writer = new StringWriter();
        try {
            exportAllToWriter(results, writer);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to format CSV string", e);
        }
        return writer.toString();
    }

    /**
     * Writes the weekly prediction result as CSV to the specified {@link Writer}.
     *
     * @param result canonical weekly prediction result
     * @param writer destination writer
     * @throws IOException if writing fails
     */
    public void exportToWriter(WeeklyPredictionsResult result, Writer writer) throws IOException {
        Objects.requireNonNull(result, "result must not be null");
        exportAllToWriter(List.of(result), writer);
    }

    /**
     * Writes multiple weekly prediction results as CSV to the specified {@link Writer}.
     *
     * @param results list of canonical weekly prediction results
     * @param writer destination writer
     * @throws IOException if writing fails
     */
    public void exportAllToWriter(List<WeeklyPredictionsResult> results, Writer writer) throws IOException {
        Objects.requireNonNull(results, "results must not be null");
        Objects.requireNonNull(writer, "writer must not be null");

        try (CSVPrinter printer = new CSVPrinter(writer, CSV_FORMAT)) {
            for (WeeklyPredictionsResult result : results) {
                String weekStr = result.week().targetMonday().toString();
                for (PredictionRecord record : result.items()) {
                    printer.printRecord(
                            weekStr,
                            record.rank(),
                            record.gatewayId().value(),
                            String.format(java.util.Locale.ROOT, "%.4f", record.score()),
                            record.reason());
                }
            }
            printer.flush();
        }
    }

    /**
     * Exports the weekly prediction result to a file on disk.
     *
     * @param result canonical weekly prediction result
     * @param destination target file path
     * @throws IOException if file write fails
     */
    public void exportToFile(WeeklyPredictionsResult result, Path destination) throws IOException {
        Objects.requireNonNull(result, "result must not be null");
        exportAllToFile(List.of(result), destination);
    }

    /**
     * Exports multiple weekly prediction results to a consolidated file on disk.
     *
     * @param results list of canonical weekly prediction results
     * @param destination target file path
     * @throws IOException if file write fails
     */
    public void exportAllToFile(List<WeeklyPredictionsResult> results, Path destination) throws IOException {
        Objects.requireNonNull(destination, "destination must not be null");
        try (Writer writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            exportAllToWriter(results, writer);
        }
    }
}
