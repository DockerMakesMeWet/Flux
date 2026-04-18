package org.owl.flux.data.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
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
}
