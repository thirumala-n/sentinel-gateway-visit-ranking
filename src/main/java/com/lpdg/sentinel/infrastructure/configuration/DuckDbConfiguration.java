package com.lpdg.sentinel.infrastructure.configuration;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * Configures a DuckDB in-process {@link DataSource} bean.
 *
 * <p>DuckDB is embedded (in-process); it does not require a separate server. The connection
 * URL {@code jdbc:duckdb:} creates an in-memory database. All data is queried directly
 * from Parquet and CSV files on the local filesystem using DuckDB's file-reading
 * functions ({@code read_parquet}, {@code read_csv_auto}).
 *
 * <p>A single shared {@link DataSource} instance is used across all repository beans.
 * DuckDB supports concurrent reads from a single in-process connection.
 */
@Configuration
public class DuckDbConfiguration {

    /**
     * Creates a DuckDB-backed {@link DataSource} for in-process analytics queries.
     *
     * @return configured DataSource
     * @throws SQLException if the DuckDB driver cannot initialize
     */
    @Bean
    public DataSource duckDbDataSource() throws SQLException {
        SimpleDriverDataSource ds = new SimpleDriverDataSource();
        ds.setUrl("jdbc:duckdb:");
        ds.setDriver(new org.duckdb.DuckDBDriver());
        return ds;
    }
}
