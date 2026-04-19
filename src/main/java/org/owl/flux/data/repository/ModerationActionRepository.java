package org.owl.flux.data.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.owl.flux.data.model.ModerationActionRecord;
import org.owl.flux.data.model.ModerationActionType;

public final class ModerationActionRepository {
    private final DataSource dataSource;

    public ModerationActionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void save(
            ModerationActionType actionType,
            String targetReference,
            String punishmentId,
            String executorUuid,
            String reason,
            Instant createdAt
    ) {
        String sql = """
                INSERT INTO moderation_actions (
                    action_type,
                    target_reference,
                    punishment_id,
                    executor_uuid,
                    reason,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, actionType.name());
            statement.setString(2, targetReference);
            statement.setString(3, punishmentId);
            statement.setString(4, executorUuid);
            statement.setString(5, reason);
            statement.setTimestamp(6, Timestamp.from(createdAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save moderation action audit record.", exception);
        }
    }

    public List<ModerationActionRecord> historyByTarget(String targetUuid, String targetUsername) {
        String normalizedUuid = normalizeOptionalToken(targetUuid);
        String normalizedUsername = normalizeOptionalToken(targetUsername);
        if (normalizedUuid == null && normalizedUsername == null) {
            return List.of();
        }

        String sql = """
                SELECT * FROM moderation_actions
                WHERE (? IS NOT NULL AND target_reference = ?)
                   OR (? IS NOT NULL AND LOWER(target_reference) = LOWER(?))
                ORDER BY created_at DESC, id DESC
                """;

        List<ModerationActionRecord> list = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedUuid);
            statement.setString(2, normalizedUuid);
            statement.setString(3, normalizedUsername);
            statement.setString(4, normalizedUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    list.add(new ModerationActionRecord(
                            resultSet.getLong("id"),
                            ModerationActionType.valueOf(resultSet.getString("action_type")),
                            resultSet.getString("target_reference"),
                            resultSet.getString("punishment_id"),
                            resultSet.getString("executor_uuid"),
                            resultSet.getString("reason"),
                            resultSet.getTimestamp("created_at").toInstant()
                    ));
                }
            }
            return list;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load moderation action history.", exception);
        }
    }

    private static String normalizeOptionalToken(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
