/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zonky.test.db.postgres.embedded;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

import org.apache.commons.lang3.reflect.MethodUtils;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

// TODO: Detect missing migration files.
// cf. https://github.com/flyway/flyway/issues/1496
// There is also a related @Ignored test in otj-sql.

public final class FlywayPreparer implements DatabasePreparer {

    private final FluentConfiguration configuration;
    private final List<String> locations;

    public static FlywayPreparer forClasspathLocation(String... locations) {
        FluentConfiguration config = Flyway.configure().locations(locations);
        return new FlywayPreparer(config, Arrays.asList(locations));
    }

    private FlywayPreparer(FluentConfiguration configuration, List<String> locations) {
        this.configuration = configuration;
        this.locations = locations;
    }

    @Override
    public void prepare(DataSource ds) throws SQLException {
        configuration.dataSource(ds);
        Flyway flyway = configuration.load();
        try {
            MethodUtils.invokeMethod(flyway, "migrate");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (! (obj instanceof FlywayPreparer)) {
            return false;
        }
        return Objects.equals(locations, ((FlywayPreparer) obj).locations);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(locations);
    }
}
