package LLD2.coderamry.notis;

import java.util.*;

// =====================
// ENUMS
// =====================
enum NotificationType {
    EMAIL, SMS, PUSH
}

enum NotificationStatus {
    PENDING, SENT, FAILED
}

// =====================
// ENTITIES
// =====================
class User {
    String userId;
    String name;
    String email;
    String phone;

    User(String userId, String name, String email, String phone) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }
}

class Notification {
    String notificationId;
    NotificationType type;
    NotificationStatus status;
    String message;
    String userId;

    Notification(String notificationId, NotificationType type, String message, String userId) {
        this.notificationId = notificationId;
        this.type = type;
        this.message = message;
        this.userId = userId;
        this.status = NotificationStatus.PENDING;
    }
}

// =====================
// REPOSITORY
// =====================
class NotificationRepository {
    Map<String, Notification> notifications = new HashMap<>();

    void save(Notification n) { notifications.put(n.notificationId, n); }
    Notification findById(String id) { return notifications.get(id); }
}

// =====================
// OBSERVER
// =====================
interface NotificationObserver {
    void update(Notification notification);
}

class DashboardObserver implements NotificationObserver {
    public void update(Notification notification) {
        System.out.println("Dashboard updated for notification: " + notification.notificationId);
    }
}

class AnalyticsObserver implements NotificationObserver {
    public void update(Notification notification) {
        System.out.println("Analytics recorded for notification: " + notification.notificationId);
    }
}

// =====================
// STRATEGY
// =====================
interface NotificationSender {
    void send(Notification notification);
}

class EmailSender implements NotificationSender {
    public void send(Notification notification) {
        System.out.println("Sending EMAIL to user " + notification.userId + ": " + notification.message);
        notification.status = NotificationStatus.SENT;
    }
}

class SMSSender implements NotificationSender {
    public void send(Notification notification) {
        System.out.println("Sending SMS to user " + notification.userId + ": " + notification.message);
        notification.status = NotificationStatus.SENT;
    }
}

class PushSender implements NotificationSender {
    public void send(Notification notification) {
        System.out.println("Sending PUSH to user " + notification.userId + ": " + notification.message);
        notification.status = NotificationStatus.SENT;
    }
}

// =====================
// DECORATOR
// =====================
abstract class NotificationDecorator implements NotificationSender {
    protected NotificationSender wrapped;

    NotificationDecorator(NotificationSender wrapped) {
        this.wrapped = wrapped;
    }
}

class LoggingDecorator extends NotificationDecorator {
    LoggingDecorator(NotificationSender wrapped) { super(wrapped); }

    public void send(Notification notification) {
        System.out.println("LOG: Sending notification " + notification.notificationId);
        wrapped.send(notification);
    }
}

class RetryDecorator extends NotificationDecorator {
    RetryDecorator(NotificationSender wrapped) { super(wrapped); }

    public void send(Notification notification) {
        try {
            wrapped.send(notification);
        } catch (Exception e) {
            System.out.println("Retrying notification " + notification.notificationId);
            wrapped.send(notification);
        }
    }
}

// =====================
// SERVICE
// =====================
class NotificationService {
    private final NotificationRepository repo;
    private final Map<NotificationType, NotificationSender> senderMap = new HashMap<>();
    private final List<NotificationObserver> observers = new ArrayList<>();

    NotificationService(NotificationRepository repo) {
        this.repo = repo;
        // base senders wrapped with decorators
        senderMap.put(NotificationType.EMAIL, new LoggingDecorator(new EmailSender()));
        senderMap.put(NotificationType.SMS, new LoggingDecorator(new SMSSender()));
        senderMap.put(NotificationType.PUSH, new LoggingDecorator(new PushSender()));
    }

    void registerObserver(NotificationObserver observer) {
        observers.add(observer);
    }

    void notifyObservers(Notification notification) {
        for (NotificationObserver obs : observers) {
            obs.update(notification);
        }
    }

    void sendNotification(Notification notification) {
        repo.save(notification);
        NotificationSender sender = senderMap.get(notification.type);
        if (sender != null) {
            sender.send(notification);
            notifyObservers(notification);
        }
    }

    NotificationStatus getStatus(String notificationId) {
        Notification n = repo.findById(notificationId);
        return n != null ? n.status : null;
    }
}

// =====================
// DEMO
// =====================
public class NotificationEngineDemo {
    public static void main(String[] args) {
        NotificationRepository repo = new NotificationRepository();
        NotificationService service = new NotificationService(repo);

        // register observers
        service.registerObserver(new DashboardObserver());
        service.registerObserver(new AnalyticsObserver());

        Notification n1 = new Notification("N1", NotificationType.EMAIL, "Welcome!", "U1");
        Notification n2 = new Notification("N2", NotificationType.SMS, "Order shipped!", "U2");

        service.sendNotification(n1);
        service.sendNotification(n2);

        System.out.println("N1 status: " + service.getStatus("N1"));
        System.out.println("N2 status: " + service.getStatus("N2"));
    }
}
