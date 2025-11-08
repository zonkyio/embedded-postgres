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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public interface DatabaseConnectionPreparer extends DatabasePreparer {

    @Override
    default void prepare(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection()) {
            prepare(c);
        }
    }

    void prepare(Connection conn) throws SQLException;
}
