/*
 * Copyright 2025 Tomas Vanek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.zonky.test.db.postgres.embedded;

import org.apache.commons.lang3.reflect.MethodUtils;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import javax.sql.DataSource;
import java.util.*;

public final class FlywayPreparer implements DatabasePreparer {

    private final FluentConfiguration configuration;
    private final List<String> locations;
    private final Map<String, String> properties;

    /**
     * Creates a new instance of the preparer with the specified locations of migrations.
     */
    public static FlywayPreparer forClasspathLocation(String... locations) {
        FluentConfiguration config = Flyway.configure().locations(locations);
        return new FlywayPreparer(config, Arrays.asList(locations), null);
    }

    /**
     * Creates a new instance of the preparer with the specified configuration properties.
     *
     * <p>Example of use:
     * <pre> {@code
     *     FlywayPreparer preparer = FlywayPreparer.fromConfiguration(Map.of(
     *             "flyway.locations", "db/migration",
     *             "flyway.postgresql.transactional.lock", "false"));
     * }</pre>
     *
     * A list of all available configuration properties can be found <a href='https://flywaydb.org/documentation/configuration/configfile.html'>here</a>.
     */
    public static FlywayPreparer fromConfiguration(Map<String, String> configuration) {
        FluentConfiguration config = Flyway.configure().configuration(configuration);
        return new FlywayPreparer(config, null, new HashMap<>(configuration));
    }

    private FlywayPreparer(FluentConfiguration configuration, List<String> locations, Map<String, String> properties) {
        this.configuration = configuration;
        this.locations = locations;
        this.properties = properties;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public void prepare(DataSource ds) {
        configuration.dataSource(ds);
        Flyway flyway = configuration.load();
        try {
            MethodUtils.invokeMethod(flyway, "migrate");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlywayPreparer that = (FlywayPreparer) o;
        return Objects.equals(locations, that.locations) && Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locations, properties);
    }
}
