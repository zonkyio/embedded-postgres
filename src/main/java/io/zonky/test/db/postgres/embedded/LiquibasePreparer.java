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

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.resource.ResourceAccessor;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import static liquibase.database.DatabaseFactory.getInstance;

public final class LiquibasePreparer implements DatabasePreparer {

    private final String location;
    private final ResourceAccessor accessor;
    private final Contexts contexts;

    public static LiquibasePreparer forClasspathLocation(String location) {
        return forClasspathLocation(location, null);
    }

    public static LiquibasePreparer forClasspathLocation(String location, Contexts contexts) {
        return new LiquibasePreparer(location, new ClassLoaderResourceAccessor(), contexts);
    }
    
    public static LiquibasePreparer forFile(File file) {
        return forFile(file, null);
    }

    public static LiquibasePreparer forFile(File file, Contexts contexts) {
        if (file == null)
            throw new IllegalArgumentException("Missing file");
        File dir = file.getParentFile();
        if (dir == null)
            throw new IllegalArgumentException("Cannot get parent dir from file");

        try {
            return new LiquibasePreparer(file.getName(), new DirectoryResourceAccessor(dir), contexts);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private LiquibasePreparer(String location, ResourceAccessor accessor, Contexts contexts) {
        this.location = location;
        this.accessor = accessor;
        this.contexts = contexts != null ? contexts : new Contexts();
    }

    @SuppressWarnings("PMD.CloseResource")
    @Override
    public void prepare(DataSource ds) throws SQLException {
        try (Connection connection = ds.getConnection();
                Database database = getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection))) {
            Liquibase liquibase = new Liquibase(location, accessor, database);
            liquibase.update(contexts);
        } catch (LiquibaseException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LiquibasePreparer that = (LiquibasePreparer) o;
        return Objects.equals(location, that.location)
                && Objects.equals(accessor, that.accessor)
                && Objects.equals(contexts.getContexts(), that.contexts.getContexts());
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, accessor, contexts.getContexts());
    }
}
