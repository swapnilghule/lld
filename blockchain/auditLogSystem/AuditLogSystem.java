package LLD2.blockchain.auditLogSystem;

import java.util.*;

/* =====================
   ENUMS
   ===================== */

enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    READ,
    LOGIN,
    LOGOUT
}

enum AuditStatus {
    SUCCESS,
    FAILURE
}

enum SeverityLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/* =====================
   ENTITIES
   ===================== */

class AuditLog {
    String auditId;
    String userId;
    String entityName;
    String entityId;
    AuditAction action;
    AuditStatus status;
    SeverityLevel severity;
    String message;
    Date timestamp;

    AuditLog(String auditId, String userId, String entityName, String entityId,
             AuditAction action, AuditStatus status, SeverityLevel severity, String message) {
        this.auditId = auditId;
        this.userId = userId;
        this.entityName = entityName;
        this.entityId = entityId;
        this.action = action;
        this.status = status;
        this.severity = severity;
        this.message = message;
        this.timestamp = new Date();
    }
}

/* =====================
   REPOSITORY
   ===================== */

class AuditLogRepository {
    private final List<AuditLog> logs = new ArrayList<>();

    void save(AuditLog log) {
        logs.add(log);
    }

    List<AuditLog> findAll() {
        return logs;
    }

    List<AuditLog> findByUserId(String userId) {
        List<AuditLog> result = new ArrayList<>();
        for (AuditLog log : logs) {
            if (log.userId.equals(userId)) {
                result.add(log);
            }
        }
        return result;
    }
}

/* =====================
   STRATEGY (Filtering)
   ===================== */

interface AuditFilterStrategy {
    boolean filter(AuditLog log);
}

class SeverityFilterStrategy implements AuditFilterStrategy {
    private final SeverityLevel minLevel;

    SeverityFilterStrategy(SeverityLevel minLevel) {
        this.minLevel = minLevel;
    }

    public boolean filter(AuditLog log) {
        return log.severity.ordinal() >= minLevel.ordinal();
    }
}

class ActionFilterStrategy implements AuditFilterStrategy {
    private final AuditAction action;

    ActionFilterStrategy(AuditAction action) {
        this.action = action;
    }

    public boolean filter(AuditLog log) {
        return log.action == action;
    }
}

/* =====================
   SERVICES
   ===================== */

class AuditLogService {
    private final AuditLogRepository repository;

    AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    void log(String userId, String entityName, String entityId,
             AuditAction action, AuditStatus status,
             SeverityLevel severity, String message) {

        AuditLog auditLog = new AuditLog(
                UUID.randomUUID().toString(),
                userId,
                entityName,
                entityId,
                action,
                status,
                severity,
                message
        );
        repository.save(auditLog);
    }

    List<AuditLog> getLogs(AuditFilterStrategy filterStrategy) {
        List<AuditLog> result = new ArrayList<>();
        for (AuditLog log : repository.findAll()) {
            if (filterStrategy.filter(log)) {
                result.add(log);
            }
        }
        return result;
    }
}

/* =====================
   DEMO
   ===================== */

public class AuditLogSystem {
    public static void main(String[] args) {
        AuditLogRepository repo = new AuditLogRepository();
        AuditLogService service = new AuditLogService(repo);

        service.log("user1", "Order", "ORD123",
                AuditAction.CREATE, AuditStatus.SUCCESS,
                SeverityLevel.MEDIUM, "Order created");

        service.log("user2", "Payment", "PAY456",
                AuditAction.DELETE, AuditStatus.FAILURE,
                SeverityLevel.CRITICAL, "Unauthorized delete attempt");

        List<AuditLog> criticalLogs =
                service.getLogs(new SeverityFilterStrategy(SeverityLevel.HIGH));

        System.out.println("High severity logs count: " + criticalLogs.size());
    }
}
