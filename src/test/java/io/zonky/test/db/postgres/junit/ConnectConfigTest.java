package io.zonky.test.db.postgres.junit;

import io.zonky.test.db.postgres.embedded.ConnectionInfo;
import io.zonky.test.db.postgres.embedded.DatabasePreparer;
import org.junit.Rule;
import org.junit.Test;
import org.postgresql.ds.common.BaseDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ConnectConfigTest {

    private final CapturingDatabasePreparer preparer = new CapturingDatabasePreparer();

    @Rule
    public PreparedDbRule db = EmbeddedPostgresRules.preparedDatabase(preparer)
            .customize(builder -> builder.setConnectConfig("connectTimeout", "20"));

    @Test
    public void test() throws SQLException {
        ConnectionInfo connectionInfo = db.getConnectionInfo();

        Map<String, String> properties = connectionInfo.getProperties();
        assertEquals(1, properties.size());
        assertEquals("20", properties.get("connectTimeout"));

        BaseDataSource testDatabase = (BaseDataSource) db.getTestDatabase();
        assertEquals("20", testDatabase.getProperty("connectTimeout"));

        BaseDataSource preparerDataSource = (BaseDataSource) preparer.getDataSource();
        assertEquals("20", preparerDataSource.getProperty("connectTimeout"));
    }

    private class CapturingDatabasePreparer implements DatabasePreparer {

        private DataSource dataSource;

        @Override
        public void prepare(DataSource ds) {
            if (dataSource != null)
                throw new IllegalStateException("database preparer has been called multiple times");
            dataSource = ds;
        }

        public DataSource getDataSource() {
            return dataSource;
        }
    }
}
