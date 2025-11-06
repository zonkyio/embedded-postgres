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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class EmbeddedPostgresTest
{
    @TempDir
    public Path tf;

    @Test
    public void testEmbeddedPg() throws Exception
    {
        try (EmbeddedPostgres pg = EmbeddedPostgres.start();
             Connection c = pg.getPostgresDatabase().getConnection()) {
            Statement s = c.createStatement();
            ResultSet rs = s.executeQuery("SELECT 1");
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
            assertFalse(rs.next());
        }
    }

    @Test
    public void testEmbeddedPgCreationWithNestedDataDirectory() throws Exception
    {
        Path dataDir = Files.createDirectories(tf.resolve("data-dir-parent").resolve("data-dir"));
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setDataDirectory(dataDir)
                .setDataDirectoryCustomizer(dd -> {
                    assertEquals(dataDir, dd.toPath());
                    Path pgConfigFile = dd.toPath().resolve("postgresql.conf");
                    assertTrue(Files.isRegularFile(pgConfigFile));
                    try {
                        String pgConfig = new String(Files.readAllBytes(pgConfigFile), StandardCharsets.UTF_8);
                        pgConfig = pgConfig.replaceFirst("#?listen_addresses\\s*=\\s*'localhost'", "listen_addresses = '*'");
                        Files.write(pgConfigFile, pgConfig.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException ioe) {
                        throw new RuntimeException(ioe);
                    }
                })
                .start()) {
            try (Connection connection = pg.getPostgresDatabase().getConnection()) {
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SHOW listen_addresses;");
                rs.next();
                assertEquals("*", rs.getString(1));
            }
        }
    }
}
