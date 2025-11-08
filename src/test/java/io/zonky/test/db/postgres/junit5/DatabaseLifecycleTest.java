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

package io.zonky.test.db.postgres.junit5;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(OrderAnnotation.class)
class DatabaseLifecycleTest {

    @RegisterExtension
    static PreparedDbExtension staticExtension = EmbeddedPostgresExtension.preparedDatabase(ds -> {});

    @SuppressWarnings("JUnitMalformedDeclaration")
    @RegisterExtension
    PreparedDbExtension instanceExtension = EmbeddedPostgresExtension.preparedDatabase(ds -> {});

    @Test
    @Order(1)
    void testCreate1() throws Exception {
        createTable(staticExtension, "table1");
        createTable(instanceExtension, "table2");
    }

    @Test
    @Order(2)
    void testCreate2() throws Exception {
        assertTrue(existsTable(staticExtension, "table1"));
        assertFalse(existsTable(instanceExtension, "table2"));
    }

    @Test
    @Order(3)
    void testCreate3() throws Exception {
        assertTrue(existsTable(staticExtension, "table1"));
        assertFalse(existsTable(instanceExtension, "table2"));
    }

    private void createTable(PreparedDbExtension extension, String table) throws SQLException {
        try (Connection connection = extension.getTestDatabase().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(String.format("CREATE TABLE public.%s (a INTEGER)", table));
        }
    }

    private boolean existsTable(PreparedDbExtension extension, String table) throws SQLException {
        String query = String.format("SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '%s')", table);
        try (Connection connection = extension.getTestDatabase().getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(query)) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }
}
