package LLD2.resume.logger;

import java.util.*;

/* =====================
   ENUMS
   ===================== */
enum LogLevel {
    INFO, WARN, ERROR, CRITICAL
}

enum LogStatus {
    SUCCESS, FAILURE
}

/* =====================
   ENTITIES
   ===================== */
class LogMessage {
    String logId;
    String userId;
    String component;
    String message;
    LogLevel level;
    LogStatus status;
    Date timestamp;

    LogMessage(String userId, String component, String message, LogLevel level, LogStatus status) {
        this.logId = UUID.randomUUID().toString();
        this.userId = userId;
        this.component = component;
        this.message = message;
        this.level = level;
        this.status = status;
        this.timestamp = new Date();
    }
}

/* =====================
   REPOSITORY
   ===================== */
class LoggerRepository {
    List<LogMessage> logs = new ArrayList<>();

    void save(LogMessage log) {
        logs.add(log);
    }

    List<LogMessage> findAll() {
        return logs;
    }
}

/* =====================
   FILTER INTERFACE
   ===================== */
interface LogFilter {
    boolean filter(LogMessage log);
}

/* Example filters */
class LevelFilter implements LogFilter {
    LogLevel minLevel;

    LevelFilter(LogLevel minLevel) {
        this.minLevel = minLevel;
    }

    public boolean filter(LogMessage log) {
        return log.level.ordinal() >= minLevel.ordinal();
    }
}

class ComponentFilter implements LogFilter {
    String component;

    ComponentFilter(String component) {
        this.component = component;
    }

    public boolean filter(LogMessage log) {
        return log.component.equals(component);
    }
}

/* =====================
   APPENDER INTERFACE
   ===================== */
interface LogAppender {
    void append(LogMessage log);
}

/* Example appenders */
class ConsoleAppender implements LogAppender {
    public void append(LogMessage log) {
        System.out.println("[" + log.level + "] " + log.component + " - " + log.message);
    }
}

class MemoryAppender implements LogAppender {
    LoggerRepository repository;

    MemoryAppender(LoggerRepository repository) {
        this.repository = repository;
    }

    public void append(LogMessage log) {
        repository.save(log);
    }
}

/* =====================
   CHAIN OF RESPONSIBILITY HANDLER
   ===================== */
abstract class LogHandler {
    protected LogHandler next;
    List<LogFilter> filters = new ArrayList<>();
    List<LogAppender> appenders = new ArrayList<>();

    public LogHandler setNext(LogHandler next) {
        this.next = next;
        return next;
    }

    public void addFilter(LogFilter filter) {
        filters.add(filter);
    }

    public void addAppender(LogAppender appender) {
        appenders.add(appender);
    }

    public void handle(LogMessage log) {
        boolean pass = true;
        for (LogFilter filter : filters) {
            if (!filter.filter(log)) {
                pass = false;
                break;
            }
        }
        if (pass) {
            for (LogAppender appender : appenders) {
                appender.append(log);
            }
        }
        if (next != null) next.handle(log);
    }
}

/* =====================
   HANDLERS
   ===================== */
class InfoLogHandler extends LogHandler {}
class WarnLogHandler extends LogHandler {}
class ErrorLogHandler extends LogHandler {}
class CriticalLogHandler extends LogHandler {}

/* =====================
   LOGGER SERVICE
   ===================== */
class LoggerService {
    private LogHandler chain;

    LoggerService(LogHandler chain) {
        this.chain = chain;
    }

    public void log(String userId, String component, String message, LogLevel level, LogStatus status) {
        LogMessage log = new LogMessage(userId, component, message, level, status);
        chain.handle(log);
    }
}

/* =====================
   DEMO
   ===================== */
public class LoggerDemo {
    public static void main(String[] args) {
        LoggerRepository repo = new LoggerRepository();

        // Create handlers
        InfoLogHandler infoHandler = new InfoLogHandler();
        WarnLogHandler warnHandler = new WarnLogHandler();
        ErrorLogHandler errorHandler = new ErrorLogHandler();
        CriticalLogHandler criticalHandler = new CriticalLogHandler();

        // Chain: INFO -> WARN -> ERROR -> CRITICAL
        infoHandler.setNext(warnHandler).setNext(errorHandler).setNext(criticalHandler);

        // Add filters & appenders
        infoHandler.addFilter(new LevelFilter(LogLevel.INFO));
        infoHandler.addAppender(new ConsoleAppender());
        errorHandler.addAppender(new MemoryAppender(repo));
        criticalHandler.addAppender(new MemoryAppender(repo));
        criticalHandler.addAppender(new ConsoleAppender());

        LoggerService logger = new LoggerService(infoHandler);

        // Log examples
        logger.log("user1", "AuthService", "User login success", LogLevel.INFO, LogStatus.SUCCESS);
        logger.log("user2", "PaymentService", "Payment failed", LogLevel.ERROR, LogStatus.FAILURE);
        logger.log("user3", "OrderService", "Order delayed", LogLevel.WARN, LogStatus.SUCCESS);
        logger.log("user4", "SystemService", "Critical failure!", LogLevel.CRITICAL, LogStatus.FAILURE);

        System.out.println("Logs stored in memory: " + repo.findAll().size()); // ERROR + CRITICAL
    }
}
