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

package io.zonky.test.db.postgres.junit;

import io.zonky.test.db.postgres.embedded.LiquibasePreparer;
import liquibase.Contexts;
import org.junit.Rule;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;

public class LiquibasePreparerClasspathContextTest {

    @Rule
    public PreparedDbRule db = EmbeddedPostgresRules.preparedDatabase(LiquibasePreparer.forClasspathLocation("liqui/master-test.xml", new Contexts("test")));

    @SuppressWarnings("SqlNoDataSourceInspection")
    @Test
    public void testEmptyTables() throws Exception {
        try (Connection c = db.getTestDatabase().getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM foo")) {
            rs.next();
            assertEquals(0, rs.getInt(1));
        }
    }
}
