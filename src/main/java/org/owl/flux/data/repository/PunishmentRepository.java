package org.owl.flux.data.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.owl.flux.data.JsonMetadata;
import org.owl.flux.data.model.PunishmentRecord;
import org.owl.flux.data.model.PunishmentType;

public final class PunishmentRepository {
    private static final int MAX_ID_SUGGESTION_LIMIT = 100;
    private static final String IP_PUNISHMENT_METADATA_KEY = "ip_punishment";

    private final DataSource dataSource;

    public PunishmentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean existsId(String id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM punishments WHERE id = ? LIMIT 1")) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to check punishment ID collision.", exception);
        }
    }

    public void save(PunishmentRecord punishment) {
        try (Connection connection = dataSource.getConnection()) {
            String sql = insertSqlForDatabaseProduct(connection.getMetaData().getDatabaseProductName());
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, punishment.id());
                statement.setString(2, punishment.type().name());
                statement.setString(3, punishment.targetUuid());
                statement.setString(4, punishment.targetIp());
                statement.setString(5, punishment.targetUsername());
                statement.setString(6, punishment.executorUuid());
                statement.setString(7, punishment.reason());
                statement.setTimestamp(8, Timestamp.from(punishment.startTime()));
                if (punishment.endTime() == null) {
                    statement.setTimestamp(9, null);
                } else {
                    statement.setTimestamp(9, Timestamp.from(punishment.endTime()));
                }
                statement.setBoolean(10, punishment.active());
                statement.setBoolean(11, punishment.voided());
                statement.setString(12, punishment.voidReason());
                statement.setBoolean(13, punishment.issuedOffline());
                statement.setBoolean(14, punishment.joinNoticeDelivered());
                statement.setString(15, JsonMetadata.write(punishment.metadata()));
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            if (isDuplicateKeyViolation(exception)) {
                throw new DuplicatePunishmentIdException("Punishment ID already exists.", exception);
            }
            throw new IllegalStateException("Failed to save punishment.", exception);
        }
    }

    public Optional<PunishmentRecord> findById(String id) {
        expireEndedPunishments();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM punishments WHERE id = ? LIMIT 1")) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readPunishment(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to find punishment by id.", exception);
        }
    }

    public List<String> findRecentIdsByPrefix(String prefix, int limit) {
        int cappedLimit = Math.max(0, Math.min(MAX_ID_SUGGESTION_LIMIT, limit));
        if (cappedLimit == 0) {
            return List.of();
        }

        String normalizedPrefix = normalizePrefix(prefix);
        List<String> ids = new ArrayList<>();
        String sql = normalizedPrefix.isEmpty()
                ? """
                        SELECT id FROM punishments
                        ORDER BY start_time DESC
                        LIMIT ?
                        """
                : """
                        SELECT id FROM punishments
                        WHERE id >= ?
                          AND id < ?
                        ORDER BY start_time DESC
                        LIMIT ?
                        """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (normalizedPrefix.isEmpty()) {
                statement.setInt(1, cappedLimit);
            } else {
                statement.setString(1, normalizedPrefix);
                statement.setString(2, normalizedPrefix + '\uffff');
                statement.setInt(3, cappedLimit);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(resultSet.getString("id"));
                }
            }
            return ids;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query punishment IDs by prefix.", exception);
        }
    }

    public List<String> findRecentActiveIdsByTypePrefix(PunishmentType type, String prefix, int limit) {
        expireEndedPunishments();
        int cappedLimit = Math.max(0, Math.min(MAX_ID_SUGGESTION_LIMIT, limit));
        if (cappedLimit == 0) {
            return List.of();
        }

        String normalizedPrefix = normalizePrefix(prefix);
        List<String> ids = new ArrayList<>();
        String sql = normalizedPrefix.isEmpty()
                ? """
                        SELECT id FROM punishments
                        WHERE type = ?
                          AND active = TRUE
                          AND voided = FALSE
                        ORDER BY start_time DESC
                        LIMIT ?
                        """
                : """
                        SELECT id FROM punishments
                        WHERE type = ?
                          AND active = TRUE
                          AND voided = FALSE
                          AND id >= ?
                          AND id < ?
                        ORDER BY start_time DESC
                        LIMIT ?
                        """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            if (normalizedPrefix.isEmpty()) {
                statement.setInt(2, cappedLimit);
            } else {
                statement.setString(2, normalizedPrefix);
                statement.setString(3, normalizedPrefix + '\uffff');
                statement.setInt(4, cappedLimit);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(resultSet.getString("id"));
                }
            }
            return ids;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query active punishment IDs by type and prefix.", exception);
        }
    }

    public Optional<PunishmentRecord> findActivePunishment(String targetUuid, String targetUsername, String targetIp, PunishmentType type) {
        expireEndedPunishments();
        String sql = """
                SELECT * FROM punishments
                WHERE type = ?
                  AND active = TRUE
                  AND voided = FALSE
                  AND (
                        (target_uuid IS NOT NULL AND target_uuid = ?)
                     OR (target_username IS NOT NULL AND LOWER(target_username) = LOWER(?))
                     OR (target_ip IS NOT NULL AND target_ip = ?)
                  )
                ORDER BY start_time DESC
                """;

        String normalizedUsername = targetUsername == null ? "" : targetUsername.trim();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setString(2, targetUuid);
            statement.setString(3, normalizedUsername);
            statement.setString(4, targetIp);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    PunishmentRecord punishment = readPunishment(resultSet);
                    if (matchesAccountTarget(punishment, targetUuid, normalizedUsername)
                            || matchesIpTarget(punishment, targetIp)) {
                        return Optional.of(punishment);
                    }
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query active punishment.", exception);
        }
    }

    public List<PunishmentRecord> findPendingJoinNotices(String targetUuid, String targetUsername) {
        String normalizedUsername = targetUsername == null ? "" : targetUsername.trim();
        if ((targetUuid == null || targetUuid.isBlank()) && normalizedUsername.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT * FROM punishments
                WHERE issued_offline = TRUE
                  AND join_notice_delivered = FALSE
                  AND voided = FALSE
                  AND (
                        (target_uuid IS NOT NULL AND target_uuid = ?)
                     OR (target_uuid IS NULL AND target_username IS NOT NULL AND LOWER(target_username) = LOWER(?))
                  )
                ORDER BY start_time ASC
                """;

        List<PunishmentRecord> list = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetUuid);
            statement.setString(2, normalizedUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    list.add(readPunishment(resultSet));
                }
            }
            return list;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query pending join notices.", exception);
        }
    }

    public int markJoinNoticesDelivered(List<String> punishmentIds) {
        if (punishmentIds == null || punishmentIds.isEmpty()) {
            return 0;
        }

        String sql = "UPDATE punishments SET join_notice_delivered = TRUE WHERE id = ? AND join_notice_delivered = FALSE";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (String punishmentId : punishmentIds) {
                statement.setString(1, punishmentId);
                statement.addBatch();
            }
            int updated = 0;
            for (int count : statement.executeBatch()) {
                if (count > 0) {
                    updated += count;
                }
            }
            return updated;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to mark join notices as delivered.", exception);
        }
    }

    public List<PunishmentRecord> findPendingMuteExpiryNotices(String targetUuid, String targetUsername) {
        String normalizedUsername = targetUsername == null ? "" : targetUsername.trim();
        if ((targetUuid == null || targetUuid.isBlank()) && normalizedUsername.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT * FROM punishments
                WHERE type = 'MUTE'
                  AND mute_expiry_notice_pending = TRUE
                  AND mute_expiry_notice_delivered = FALSE
                  AND voided = FALSE
                  AND (
                        (target_uuid IS NOT NULL AND target_uuid = ?)
                     OR (target_uuid IS NULL AND target_username IS NOT NULL AND LOWER(target_username) = LOWER(?))
                  )
                ORDER BY end_time ASC, start_time ASC
                """;

        List<PunishmentRecord> list = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetUuid);
            statement.setString(2, normalizedUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    list.add(readPunishment(resultSet));
                }
            }
            return list;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to query pending mute-expiry notices.", exception);
        }
    }

    public int markMuteExpiryNoticesDelivered(List<String> punishmentIds) {
        if (punishmentIds == null || punishmentIds.isEmpty()) {
            return 0;
        }

        String sql = """
                UPDATE punishments
                SET mute_expiry_notice_delivered = TRUE, mute_expiry_notice_pending = FALSE
                WHERE id = ?
                  AND type = 'MUTE'
                  AND mute_expiry_notice_pending = TRUE
                  AND mute_expiry_notice_delivered = FALSE
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (String punishmentId : punishmentIds) {
                statement.setString(1, punishmentId);
                statement.addBatch();
            }
            int updated = 0;
            for (int count : statement.executeBatch()) {
                if (count > 0) {
                    updated += count;
                }
            }
            return updated;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to mark mute-expiry notices as delivered.", exception);
        }
    }

    public int countTemplateHistory(String targetUuid, PunishmentType type, String templateName) {
        String sql = """
                SELECT metadata FROM punishments
                WHERE target_uuid = ?
                  AND type = ?
                  AND voided = FALSE
                """;

        int count = 0;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetUuid);
            statement.setString(2, type.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Map<String, String> metadata = JsonMetadata.read(resultSet.getString("metadata"));
                    String template = metadata.getOrDefault("template", "");
                    if (template.equalsIgnoreCase(templateName)) {
                        count++;
                    }
                }
            }
            return count;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to count template history.", exception);
        }
    }

    public boolean deactivateActiveByTarget(String targetUuid, String targetUsername, PunishmentType type) {
        return deactivateActiveByTargetWithReason(targetUuid, targetUsername, type, null, null);
    }

    public boolean voidSupersededByTarget(
            String targetUuid,
            String targetUsername,
            PunishmentType type,
            String voidReason,
            String excludedPunishmentId
    ) {
        expireEndedPunishments();
        String normalizedUsername = targetUsername == null ? "" : targetUsername.trim();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             UPDATE punishments
                             SET active = FALSE,
                                 voided = TRUE,
                                 void_reason = ?
                             WHERE type = ?
                               AND voided = FALSE
                               AND (active = TRUE OR type = 'WARN')
                               AND (target_uuid = ? OR LOWER(target_username) = LOWER(?))
                               AND (? IS NULL OR id <> ?)
                             """)) {
            statement.setString(1, normalizeOptionalText(voidReason));
            statement.setString(2, type.name());
            statement.setString(3, targetUuid);
            statement.setString(4, normalizedUsername);
            statement.setString(5, normalizeOptionalText(excludedPunishmentId));
            statement.setString(6, normalizeOptionalText(excludedPunishmentId));
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to void superseded punishments by target.", exception);
        }
    }

    public boolean deactivateActiveByTargetWithReason(
            String targetUuid,
            String targetUsername,
            PunishmentType type,
            String reason,
            String excludedPunishmentId
    ) {
        expireEndedPunishments();
        String normalizedUsername = targetUsername == null ? "" : targetUsername.trim();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             UPDATE punishments
                             SET active = FALSE,
                                 void_reason = COALESCE(?, void_reason)
                             WHERE type = ?
                               AND active = TRUE
                               AND voided = FALSE
                               AND (target_uuid = ? OR LOWER(target_username) = LOWER(?))
                               AND (? IS NULL OR id <> ?)
                             """)) {
            statement.setString(1, normalizeOptionalText(reason));
            statement.setString(2, type.name());
            statement.setString(3, targetUuid);
            statement.setString(4, normalizedUsername);
            statement.setString(5, normalizeOptionalText(excludedPunishmentId));
            statement.setString(6, normalizeOptionalText(excludedPunishmentId));
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to deactivate active punishment by target UUID.", exception);
        }
    }

    public boolean deactivateActiveById(String id, PunishmentType type) {
        expireEndedPunishments();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE punishments SET active = FALSE WHERE id = ? AND type = ? AND active = TRUE AND voided = FALSE")) {
            statement.setString(1, id);
            statement.setString(2, type.name());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to deactivate active punishment by ID.", exception);
        }
    }

    public boolean deactivateActiveBanByIp(String targetIp) {
        return deactivateActiveBanByIpWithReason(targetIp, null, null);
    }

    public boolean voidSupersededBanByIp(String targetIp, String voidReason, String excludedPunishmentId) {
        expireEndedPunishments();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             UPDATE punishments
                             SET active = FALSE,
                                 voided = TRUE,
                                 void_reason = ?
                             WHERE target_ip = ?
                               AND type = 'BAN'
                               AND active = TRUE
                               AND voided = FALSE
                               AND (? IS NULL OR id <> ?)
                             """)) {
            statement.setString(1, normalizeOptionalText(voidReason));
            statement.setString(2, targetIp);
            statement.setString(3, normalizeOptionalText(excludedPunishmentId));
            statement.setString(4, normalizeOptionalText(excludedPunishmentId));
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to void superseded IP bans.", exception);
        }
    }

    public boolean deactivateActiveBanByIpWithReason(String targetIp, String reason, String excludedPunishmentId) {
        expireEndedPunishments();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             UPDATE punishments
                             SET active = FALSE,
                                 void_reason = COALESCE(?, void_reason)
                             WHERE target_ip = ?
                               AND type = 'BAN'
                               AND active = TRUE
                               AND voided = FALSE
                               AND (? IS NULL OR id <> ?)
                             """)) {
            statement.setString(1, normalizeOptionalText(reason));
            statement.setString(2, targetIp);
            statement.setString(3, normalizeOptionalText(excludedPunishmentId));
            statement.setString(4, normalizeOptionalText(excludedPunishmentId));
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to deactivate active IP bans.", exception);
        }
    }

    public boolean voidById(String id, String voidReason) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE punishments SET active = FALSE, voided = TRUE, void_reason = ? WHERE id = ?")) {
            statement.setString(1, normalizeOptionalText(voidReason));
            statement.setString(2, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to void punishment.", exception);
        }
    }

    public List<PunishmentRecord> historyByTarget(String targetUuid, String targetUsername) {
        String normalizedUsername = targetUsername == null ? "" : targetUsername.trim();
        List<PunishmentRecord> list = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM punishments WHERE target_uuid = ? OR LOWER(target_username) = LOWER(?) ORDER BY start_time DESC")) {
            statement.setString(1, targetUuid);
            statement.setString(2, normalizedUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    list.add(readPunishment(resultSet));
                }
            }
            return list;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load target history.", exception);
        }
    }

    public List<PunishmentRecord> historyByIp(String ip) {
        List<PunishmentRecord> list = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM punishments WHERE target_ip = ? ORDER BY start_time DESC")) {
            statement.setString(1, ip);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    list.add(readPunishment(resultSet));
                }
            }
            return list;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load IP history.", exception);
        }
    }

    public List<PunishmentRecord> activeByTarget(String targetUuid, String targetUsername) {
        expireEndedPunishments();
        String normalizedUsername = targetUsername == null ? "" : targetUsername.trim();
        List<PunishmentRecord> list = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM punishments WHERE active = TRUE AND voided = FALSE AND (target_uuid = ? OR LOWER(target_username) = LOWER(?)) ORDER BY start_time DESC")) {
            statement.setString(1, targetUuid);
            statement.setString(2, normalizedUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    list.add(readPunishment(resultSet));
                }
            }
            return list;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load active punishments by target UUID.", exception);
        }
    }

    public List<PunishmentRecord> voidAllCandidatesByTarget(String targetUuid, String targetUsername) {
        expireEndedPunishments();
        String normalizedUsername = targetUsername == null ? "" : targetUsername.trim();
        List<PunishmentRecord> list = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT * FROM punishments
                             WHERE voided = FALSE
                               AND (active = TRUE OR type = 'WARN')
                               AND (target_uuid = ? OR LOWER(target_username) = LOWER(?))
                             ORDER BY start_time DESC
                             """)) {
            statement.setString(1, targetUuid);
            statement.setString(2, normalizedUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    list.add(readPunishment(resultSet));
                }
            }
            return list;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load void-all candidates by target.", exception);
        }
    }

    public List<PunishmentRecord> activeByIp(String ip) {
        expireEndedPunishments();
        List<PunishmentRecord> list = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM punishments WHERE target_ip = ? AND active = TRUE AND voided = FALSE ORDER BY start_time DESC")) {
            statement.setString(1, ip);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    list.add(readPunishment(resultSet));
                }
            }
            return list;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load active punishments by IP.", exception);
        }
    }

    public List<PunishmentRecord> voidAllCandidatesByIp(String ip) {
        expireEndedPunishments();
        List<PunishmentRecord> list = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT * FROM punishments
                             WHERE target_ip = ?
                               AND voided = FALSE
                               AND (active = TRUE OR type = 'WARN')
                             ORDER BY start_time DESC
                             """)) {
            statement.setString(1, ip);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    list.add(readPunishment(resultSet));
                }
            }
            return list;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load void-all candidates by IP.", exception);
        }
    }

    public void expireEndedPunishments() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             UPDATE punishments
                             SET active = FALSE,
                                 mute_expiry_notice_pending = CASE
                                     WHEN type = 'MUTE' AND voided = FALSE AND mute_expiry_notice_delivered = FALSE
                                         THEN TRUE
                                     ELSE mute_expiry_notice_pending
                                 END
                             WHERE active = TRUE AND end_time IS NOT NULL AND end_time <= ?
                             """)) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to expire punishments.", exception);
        }
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        return prefix.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean matchesAccountTarget(PunishmentRecord record, String targetUuid, String normalizedUsername) {
        boolean uuidMatch = targetUuid != null
                && !targetUuid.isBlank()
                && targetUuid.equals(record.targetUuid());
        boolean usernameMatch = normalizedUsername != null
                && !normalizedUsername.isEmpty()
                && record.targetUsername() != null
                && record.targetUsername().equalsIgnoreCase(normalizedUsername);
        return uuidMatch || usernameMatch;
    }

    private static boolean matchesIpTarget(PunishmentRecord record, String targetIp) {
        if (targetIp == null || targetIp.isBlank()) {
            return false;
        }
        if (record.targetIp() == null || !record.targetIp().equals(targetIp)) {
            return false;
        }
        return isIpPunishment(record);
    }

    private static boolean isIpPunishment(PunishmentRecord record) {
        if (record == null || record.metadata() == null) {
            return false;
        }
        return Boolean.parseBoolean(record.metadata().getOrDefault(IP_PUNISHMENT_METADATA_KEY, "false"));
    }

    static String insertSqlForDatabaseProduct(String databaseProductName) {
        boolean isPostgreSql = databaseProductName != null
                && databaseProductName.toLowerCase(Locale.ROOT).contains("postgresql");
        String metadataValue = isPostgreSql ? "?::jsonb" : "?";
        return """
                INSERT INTO punishments (
                    id, type, target_uuid, target_ip, target_username, executor_uuid, reason, start_time, end_time, active, voided, void_reason, issued_offline, join_notice_delivered, metadata
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, %s)
                """.formatted(metadataValue);
    }

    private static boolean isDuplicateKeyViolation(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            if ("23505".equals(current.getSQLState()) || current.getErrorCode() == 23505) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalizedMessage = message.toLowerCase(Locale.ROOT);
                if (normalizedMessage.contains("duplicate")
                        && (normalizedMessage.contains("primary key") || normalizedMessage.contains("unique"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static PunishmentRecord readPunishment(ResultSet resultSet) throws SQLException {
        Timestamp endTimestamp = resultSet.getTimestamp("end_time");
        return new PunishmentRecord(
                resultSet.getString("id"),
                PunishmentType.valueOf(resultSet.getString("type")),
                resultSet.getString("target_uuid"),
                resultSet.getString("target_ip"),
                resultSet.getString("target_username"),
                resultSet.getString("executor_uuid"),
                resultSet.getString("reason"),
                resultSet.getTimestamp("start_time").toInstant(),
                endTimestamp == null ? null : endTimestamp.toInstant(),
                resultSet.getBoolean("active"),
                resultSet.getBoolean("voided"),
                resultSet.getString("void_reason"),
                resultSet.getBoolean("issued_offline"),
                resultSet.getBoolean("join_notice_delivered"),
                JsonMetadata.read(resultSet.getString("metadata"))
        );
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
