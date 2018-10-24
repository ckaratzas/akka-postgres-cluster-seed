package akka.cluster.seed.postgres;

import akka.cluster.seed.model.Observation;
import akka.cluster.seed.settings.Settings;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author ckaratza
 * All the Database operations needed for managing cluster seed nodes.
 */
public final class DBOperations {

    private final Settings settings;
    private final ConnectionProvider connectionProvider;

    public DBOperations(Settings settings) throws SQLException, ClassNotFoundException {
        this.settings = settings;
        connectionProvider = new ConnectionProvider(settings.jdbcUrl());
    }

    public List<Observation> getCurrentViewOfJoinedMembers() throws SQLException {
        Connection connection = connectionProvider.get();
        try (PreparedStatement pStmt = connection.prepareStatement("SELECT address,status,heartBeatTs FROM akka_cluster_seed WHERE systemName=? AND heartBeatTs < ?")) {
            pStmt.setString(1, settings.systemName());
            pStmt.setTimestamp(2, new Timestamp(Instant.now().plus(settings.gracePeriod(), ChronoUnit.MILLIS).toEpochMilli()));
            ResultSet rs = pStmt.executeQuery();
            List<Observation> observations = new ArrayList<>();
            while (rs.next()) {
                observations.add(new Observation(rs.getString(1), Observation.Status.valueOf(rs.getString(2)), rs.getTimestamp(3)));
            }
            rs.close();
            return observations;
        }
    }

    public void deleteEntry() throws SQLException {
        Connection connection = connectionProvider.get();
        try (PreparedStatement pStmt = connection.prepareStatement("DELETE FROM akka_cluster_seed WHERE systemName=? AND address=?")) {
            pStmt.setString(1, settings.systemName());
            pStmt.setString(2, settings.address().toString());
            pStmt.execute();
        }
    }

    public void upsertEntry(final String status, final long ts) throws SQLException {
        Connection connection = connectionProvider.get();
        try (PreparedStatement pStmt = connection.prepareStatement("INSERT INTO akka_cluster_seed (systemName,address,status,heartBeatTs) values (?,?,?,?)  " +
                "ON CONFLICT ON CONSTRAINT pkey DO UPDATE SET (status,heartBeatTs) = (?,?)")) {
            pStmt.setString(1, settings.systemName());
            pStmt.setString(2, settings.address().toString());
            pStmt.setString(3, status);
            pStmt.setTimestamp(4, new Timestamp(ts));
            pStmt.setString(5, status);
            pStmt.setTimestamp(6, new Timestamp(ts));
            pStmt.execute();
        }
    }

    public List<String> pollForNotifications() throws SQLException {
        Connection connection = connectionProvider.getNotificationsConn();
        try (PreparedStatement pStmt = connection.prepareStatement("SELECT 1")) {
            ResultSet rs = pStmt.executeQuery();
            rs.close();
            PGNotification notifications[] = ((PGConnection) connection).getNotifications();
            if (notifications != null) {
                List<String> polled = new ArrayList<>(notifications.length);
                for (PGNotification notification : notifications) {
                    polled.add(notification.getParameter());
                }
                return polled;
            }
            return Collections.emptyList();
        }
    }

    public void pushNotification(final String notification) throws SQLException {
        Connection connection = connectionProvider.get();
        try (PreparedStatement pStmt = connection.prepareStatement("NOTIFY " + settings.systemName() + ", '" + notification + "'")) {
            pStmt.execute();
        }
    }

    public boolean tryAcquireSessionScopeAdvisoryLock() throws SQLException {
        Connection connection = connectionProvider.get();
        try (PreparedStatement pStmt = connection.prepareStatement("select pg_try_advisory_lock(?)")) {
            pStmt.setLong(1, settings.lockId());
            ResultSet rs = pStmt.executeQuery();
            rs.next();
            return rs.getBoolean(1);
        }
    }

    public void subscribeToNotifications() throws SQLException {
        Connection connection = connectionProvider.getNotificationsConn();
        try (PreparedStatement pStmt = connection.prepareStatement("LISTEN " + settings.systemName())) {
            pStmt.execute();
        }
    }

    public void unsubscribeFromNotifications() throws SQLException {
        Connection connection = connectionProvider.getNotificationsConn();
        try (PreparedStatement pStmt = connection.prepareStatement("UNLISTEN " + settings.systemName())) {
            pStmt.execute();
        }
    }
}
