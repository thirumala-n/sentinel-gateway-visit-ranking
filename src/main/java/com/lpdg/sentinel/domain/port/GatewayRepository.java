package com.lpdg.sentinel.domain.port;

import com.lpdg.sentinel.domain.model.Gateway;
import java.time.LocalDate;
import java.util.List;

/**
 * Port interface for loading gateway master data.
 *
 * <p>Implementations reside in the infrastructure layer and read from
 * {@code gateway_master.csv} (ISO-8859-1 encoded). All gateway identifiers must be
 * normalized via {@code TRIM(UPPER(REPLACE(gateway_id, ':', '')))} before returning.
 *
 * <p>The domain and application layers depend only on this interface; they never import
 * DuckDB, CSV, or Spring-JDBC classes.
 */
public interface GatewayRepository {

    /**
     * Returns all gateways that are eligible for visit scheduling during the prediction
     * week whose exclusive upper boundary is {@code evalDate} (the target Monday).
     *
     * <p>An eligible gateway satisfies both conditions:
     *
     * <ol>
     *   <li>{@code installed_on} is null or &le; {@code evalDate}
     *   <li>{@code decommissioned_on} is null or &ge; {@code evalDate}
     * </ol>
     *
     * @param evalDate exclusive upper bound of the prediction week (targetMonday)
     * @return list of eligible gateways; never null; may be empty
     */
    List<Gateway> findEligible(LocalDate evalDate);
}
