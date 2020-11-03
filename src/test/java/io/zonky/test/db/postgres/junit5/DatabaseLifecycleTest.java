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
public class DatabaseLifecycleTest {

    @RegisterExtension
    public static PreparedDbExtension staticExtension = EmbeddedPostgresExtension.preparedDatabase(ds -> {});

    @RegisterExtension
    public PreparedDbExtension instanceExtension = EmbeddedPostgresExtension.preparedDatabase(ds -> {});

    @Test
    @Order(1)
    public void testCreate1() throws Exception
    {
        createTable(staticExtension, "table1");
        createTable(instanceExtension, "table2");
    }

    @Test
    @Order(2)
    public void testCreate2() throws Exception
    {
        assertTrue(existsTable(staticExtension, "table1"));
        assertFalse(existsTable(instanceExtension, "table2"));
    }

    @Test
    @Order(3)
    public void testCreate3() throws Exception
    {
        assertTrue(existsTable(staticExtension, "table1"));
        assertFalse(existsTable(instanceExtension, "table2"));
    }

    private void createTable(PreparedDbExtension extension, String table) throws SQLException
    {
        try (Connection connection = extension.getTestDatabase().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(String.format("CREATE TABLE public.%s (a INTEGER)", table));
        }
    }

    private boolean existsTable(PreparedDbExtension extension, String table) throws SQLException
    {
        try (Connection connection = extension.getTestDatabase().getConnection();
             Statement statement = connection.createStatement()) {
            String query = String.format("SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '%s')", table);
            ResultSet resultSet = statement.executeQuery(query);
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }
}
