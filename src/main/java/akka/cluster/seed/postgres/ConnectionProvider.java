package akka.cluster.seed.postgres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author ckaratza
 */
final class ConnectionProvider {

    private final String jdbcUrl;
    private String userName;
    private String password;
    private Connection conn;
    private boolean reCreateConnection = false;

    ConnectionProvider(String jdbcUrl) throws SQLException, ClassNotFoundException {
        this.jdbcUrl = jdbcUrl;
        int startQueryIdx = jdbcUrl.indexOf("?");
        String[] params = jdbcUrl.substring(startQueryIdx).split("&");
        for (String param : params) {
            if (param.startsWith("user")) userName = param.split("=")[1];
            if (param.startsWith("password")) password = param.split("=")[1];
        }
        Class.forName("org.postgresql.Driver");
        conn = DriverManager.getConnection(jdbcUrl, userName, password);
    }

    Connection getNotificationsConn() {
        return conn;
    }

    Connection get() throws SQLException {
        try {
            if (conn != null && conn.isClosed()) reCreateConnection = true;
            if (reCreateConnection) {
                conn = DriverManager.getConnection(jdbcUrl, userName, password);
            }
            reCreateConnection = false;
            return conn;
        } catch (SQLException e) {
            reCreateConnection = true;
            throw e;
        }
    }
}
