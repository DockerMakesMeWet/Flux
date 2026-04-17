package org.owl.flux.data.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.owl.flux.data.model.AccountVisit;
import org.owl.flux.data.model.IpVisit;
import org.owl.flux.data.model.PlayerSnapshot;

public final class PlayerRepository {
    private final DataSource dataSource;

    public PlayerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void upsertPlayer(String uuid, String username, String ip) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE players SET username = ?, last_ip = ? WHERE uuid = ?")) {
                statement.setString(1, username);
                statement.setString(2, ip);
                statement.setString(3, uuid);
                int updated = statement.executeUpdate();
                if (updated == 0) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO players (uuid, username, last_ip) VALUES (?, ?, ?)")) {
                        insert.setString(1, uuid);
                        insert.setString(2, username);
                        insert.setString(3, ip);
                        insert.executeUpdate();
                    }
                }
            }

            try (PreparedStatement history = connection.prepareStatement(
                    "INSERT INTO ip_history (uuid, ip, last_seen) VALUES (?, ?, ?)")) {
                history.setString(1, uuid);
                history.setString(2, ip);
                history.setTimestamp(3, Timestamp.from(Instant.now()));
                history.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to upsert player state.", exception);
        }
    }

    public Optional<PlayerSnapshot> findByUsername(String username) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT uuid, username, last_ip FROM players WHERE LOWER(username) = LOWER(?) LIMIT 1")) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(new PlayerSnapshot(
                        resultSet.getString("uuid"),
                        resultSet.getString("username"),
                        resultSet.getString("last_ip")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to find player by username.", exception);
        }
    }

    public Optional<PlayerSnapshot> findByUuid(String uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT uuid, username, last_ip FROM players WHERE uuid = ? LIMIT 1")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(new PlayerSnapshot(
                        resultSet.getString("uuid"),
                        resultSet.getString("username"),
                        resultSet.getString("last_ip")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to find player by uuid.", exception);
        }
    }

    public List<String> findUsernamesByIp(String ip) {
        Set<String> usernames = new LinkedHashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT DISTINCT username FROM players WHERE last_ip = ?")) {
            statement.setString(1, ip);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String username = resultSet.getString("username");
                    if (username != null && !username.isBlank()) {
                        usernames.add(username);
                    }
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query usernames by IP.", exception);
        }
        return new ArrayList<>(usernames);
    }

    public List<IpVisit> findIpHistoryByUuid(String uuid) {
        List<IpVisit> visits = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT ip, MAX(last_seen) AS last_seen
                             FROM ip_history
                             WHERE uuid = ?
                             GROUP BY ip
                             ORDER BY MAX(last_seen) DESC
                             """)) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    visits.add(new IpVisit(
                            resultSet.getString("ip"),
                            resultSet.getTimestamp("last_seen").toInstant()
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load IP history.", exception);
        }
        return visits;
    }

    public List<AccountVisit> findAccountsByIp(String ip) {
        List<AccountVisit> accounts = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT h.uuid AS uuid, p.username AS username, MAX(h.last_seen) AS last_seen
                             FROM ip_history h
                             LEFT JOIN players p ON p.uuid = h.uuid
                             WHERE h.ip = ?
                             GROUP BY h.uuid, p.username
                             ORDER BY MAX(h.last_seen) DESC
                             """)) {
            statement.setString(1, ip);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String username = resultSet.getString("username");
                    String account = username == null || username.isBlank()
                            ? resultSet.getString("uuid")
                            : username;
                    if (account == null || account.isBlank()) {
                        continue;
                    }
                    accounts.add(new AccountVisit(
                            account,
                            resultSet.getTimestamp("last_seen").toInstant()
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load accounts by IP.", exception);
        }
        return accounts;
    }
}
