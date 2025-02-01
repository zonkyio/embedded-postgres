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

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

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
        AtomicBoolean called = new AtomicBoolean(false);
        Path dataDir = Files.createDirectories(tf.resolve("data-dir-parent").resolve("data-dir"));
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setDataDirectory(dataDir)
                .setDataDirectoryCustomizer(dd -> {
                    called.set(true);
                    assertEquals(dataDir, dd.toPath());
                    assertTrue(Files.isRegularFile(dd.toPath().resolve("pg_hba.conf")));
                })
                .start()) {
            // nothing to do
        }
        assertTrue(called.get());
    }
}
