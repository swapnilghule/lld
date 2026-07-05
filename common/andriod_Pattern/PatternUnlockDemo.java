package LLD2.common.andriod_Pattern;

import java.util.*;

// =====================
// ENUMS
// =====================
enum UnlockStatus {
    SUCCESS, FAILED, LOCKED
}

// =====================
// ENTITIES
// =====================
class User {
    String userId;
    String name;
    String patternHash; // store hash of the unlock pattern

    User(String userId, String name, String patternHash) {
        this.userId = userId;
        this.name = name;
        this.patternHash = patternHash;
    }
}

class UnlockAttempt {
    String attemptId;
    User user;
    List<Integer> pattern; // pattern entered
    Date timestamp;
    UnlockStatus status;

    UnlockAttempt(String attemptId, User user, List<Integer> pattern) {
        this.attemptId = attemptId;
        this.user = user;
        this.pattern = pattern;
        this.timestamp = new Date();
    }
}

// =====================
// REPOSITORIES
// =====================
class UserRepository {
    Map<String, User> users = new HashMap<>();
    void addUser(User u) { users.put(u.userId, u); }
    User findById(String userId) { return users.get(userId); }
}

class UnlockAttemptRepository {
    Map<String, UnlockAttempt> attempts = new HashMap<>();
    void addAttempt(UnlockAttempt a) { attempts.put(a.attemptId, a); }
    List<UnlockAttempt> findByUser(String userId) {
        List<UnlockAttempt> list = new ArrayList<>();
        for(UnlockAttempt a : attempts.values())
            if(a.user.userId.equals(userId)) list.add(a);
        return list;
    }
}

// =====================
// STRATEGY FOR PATTERN CHECK
// =====================
interface PatternCheckStrategy {
    boolean validate(User user, List<Integer> inputPattern);
}

class SimplePatternStrategy implements PatternCheckStrategy {
    public boolean validate(User user, List<Integer> inputPattern) {
        // naive hash: sum of numbers
        int inputHash = inputPattern.stream().mapToInt(Integer::intValue).sum();
        int storedHash = Integer.parseInt(user.patternHash);
        return inputHash == storedHash;
    }
}

// =====================
// OBSERVER
// =====================
interface UnlockObserver {
    void notify(String message);
}

class ConsoleNotifier implements UnlockObserver {
    public void notify(String message) { System.out.println("Notification: " + message); }
}

// =====================
// SERVICE
// =====================
class PatternUnlockService {
    UserRepository userRepo;
    UnlockAttemptRepository attemptRepo;
    PatternCheckStrategy strategy;
    List<UnlockObserver> observers = new ArrayList<>();
    int maxAttempts = 5;
    Map<String, Integer> failedCount = new HashMap<>();

    PatternUnlockService(UserRepository ur, UnlockAttemptRepository ar, PatternCheckStrategy s) {
        this.userRepo = ur;
        this.attemptRepo = ar;
        this.strategy = s;
    }

    void registerObserver(UnlockObserver obs) { observers.add(obs); }
    void notifyObservers(String msg) { for(UnlockObserver o: observers) o.notify(msg); }

    UnlockStatus unlock(String userId, List<Integer> pattern) {
        User user = userRepo.findById(userId);
        if(user == null) return UnlockStatus.FAILED;

        int fails = failedCount.getOrDefault(userId, 0);
        if(fails >= maxAttempts) {
            notifyObservers("Account locked due to max failed attempts: " + userId);
            return UnlockStatus.LOCKED;
        }

        boolean success = strategy.validate(user, pattern);
        UnlockAttempt attempt = new UnlockAttempt(UUID.randomUUID().toString(), user, pattern);
        attempt.status = success ? UnlockStatus.SUCCESS : UnlockStatus.FAILED;
        attemptRepo.addAttempt(attempt);

        if(success) {
            failedCount.put(userId, 0);
            notifyObservers("Unlock successful for " + user.name);
            return UnlockStatus.SUCCESS;
        } else {
            failedCount.put(userId, fails+1);
            notifyObservers("Unlock failed for " + user.name);
            if(fails+1 >= maxAttempts) return UnlockStatus.LOCKED;
            return UnlockStatus.FAILED;
        }
    }
}

// =====================
// DEMO
// =====================
public class PatternUnlockDemo {
    public static void main(String[] args) {
        UserRepository userRepo = new UserRepository();
        UnlockAttemptRepository attemptRepo = new UnlockAttemptRepository();

        User u1 = new User("U1", "Alice", "15"); // pattern sum hash
        userRepo.addUser(u1);

        PatternUnlockService service = new PatternUnlockService(userRepo, attemptRepo, new SimplePatternStrategy());
        service.registerObserver(new ConsoleNotifier());

        System.out.println(service.unlock("U1", Arrays.asList(5,1,4))); // sum=10 -> fail
        System.out.println(service.unlock("U1", Arrays.asList(7,8)));   // sum=15 -> success
    }
}
