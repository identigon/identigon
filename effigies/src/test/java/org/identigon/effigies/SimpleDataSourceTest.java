package org.identigon.effigies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimpleDataSourceTest {

    @Test
    void getConnectionSucceedsAgainstARealDatabase() throws SQLException {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        SimpleDataSource ds = new SimpleDataSource(url, "sa", "");
        try (Connection conn = ds.getConnection()) {
            assertFalse(conn.isClosed());
        }
    }

    @Test
    void getConnectionWithExplicitCredentialsSucceeds() throws SQLException {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        SimpleDataSource ds = new SimpleDataSource(url, "wrong-user-baked-in", "wrong-password-baked-in");
        try (Connection conn = ds.getConnection("sa", "")) {
            assertFalse(conn.isClosed());
        }
    }

    @Test
    void getConnectionFailsForAnUnroutableUrl() {
        SimpleDataSource ds = new SimpleDataSource("jdbc:no-such-dialect://nowhere", "u", "p");
        assertThrows(SQLException.class, ds::getConnection);
    }

    @Test
    void unwrapAlwaysThrows() {
        SimpleDataSource ds = new SimpleDataSource("jdbc:h2:mem:unused", "u", "p");
        assertThrows(SQLException.class, () -> ds.unwrap(SimpleDataSource.class));
    }

    @Test
    void isWrapperForAlwaysFalse() throws SQLException {
        SimpleDataSource ds = new SimpleDataSource("jdbc:h2:mem:unused", "u", "p");
        assertFalse(ds.isWrapperFor(SimpleDataSource.class));
    }

    @Test
    void logWriterIsNullAndSettingItIsANoOp() throws SQLException {
        SimpleDataSource ds = new SimpleDataSource("jdbc:h2:mem:unused", "u", "p");
        assertNull(ds.getLogWriter());
        ds.setLogWriter(null); // must not throw
    }

    @Test
    void loginTimeoutIsZeroAndSettingItIsANoOp() throws SQLException {
        SimpleDataSource ds = new SimpleDataSource("jdbc:h2:mem:unused", "u", "p");
        assertEquals(0, ds.getLoginTimeout());
        ds.setLoginTimeout(30); // must not throw, and must not change getLoginTimeout()
        assertEquals(0, ds.getLoginTimeout());
    }

    @Test
    void parentLoggerIsUnsupported() {
        SimpleDataSource ds = new SimpleDataSource("jdbc:h2:mem:unused", "u", "p");
        assertThrows(SQLFeatureNotSupportedException.class, ds::getParentLogger);
    }
}
