package LLD2.resume_based.vault;

import java.util.*;

// =====================
// ENUMS
// =====================
enum SecretType {
    API_KEY, PASSWORD, TOKEN, CERTIFICATE
}

enum AccessLevel {
    READ, WRITE, ADMIN
}

// =====================
// ENTITIES
// =====================
class Secret {
    String secretId;
    String name;
    SecretType type;
    String value; // stored encrypted in real system
    Date createdAt;

    Secret(String secretId, String name, SecretType type, String value) {
        this.secretId = secretId;
        this.name = name;
        this.type = type;
        this.value = value;
        this.createdAt = new Date();
    }
}

class VaultUser {
    String userId;
    String name;
    AccessLevel accessLevel;

    VaultUser(String userId, String name, AccessLevel accessLevel) {
        this.userId = userId;
        this.name = name;
        this.accessLevel = accessLevel;
    }
}

// =====================
// REPOSITORIES
// =====================
class SecretRepository {
    Map<String, Secret> secrets = new HashMap<>();
    void addSecret(Secret s) { secrets.put(s.secretId, s); }
    Secret getSecret(String id) { return secrets.get(id); }
    List<Secret> getAllSecrets() { return new ArrayList<>(secrets.values()); }
}

class UserRepository {
    Map<String, VaultUser> users = new HashMap<>();
    void addUser(VaultUser u) { users.put(u.userId, u); }
    VaultUser getUser(String userId) { return users.get(userId); }
}

// =====================
// STRATEGY
// =====================
interface EncryptionStrategy {
    String encrypt(String value);
    String decrypt(String encrypted);
}

class SimpleEncryptionStrategy implements EncryptionStrategy {
    public String encrypt(String value) {
        return new StringBuilder(value).reverse().toString(); // dummy
    }
    public String decrypt(String encrypted) {
        return new StringBuilder(encrypted).reverse().toString();
    }
}

// =====================
// OBSERVER
// =====================
interface SecretObserver {
    void notify(String message);
}

class ConsoleNotifier implements SecretObserver {
    public void notify(String message) { System.out.println("Notification: " + message); }
}

// =====================
// SERVICE
// =====================
class VaultService {
    SecretRepository secretRepo;
    UserRepository userRepo;
    EncryptionStrategy encryptionStrategy;
    List<SecretObserver> observers = new ArrayList<>();

    VaultService(SecretRepository sr, UserRepository ur, EncryptionStrategy es) {
        this.secretRepo = sr;
        this.userRepo = ur;
        this.encryptionStrategy = es;
    }

    void registerObserver(SecretObserver obs) { observers.add(obs); }
    void notifyObservers(String msg) { for(SecretObserver o : observers) o.notify(msg); }

    void addSecret(String name, SecretType type, String value) {
        String encrypted = encryptionStrategy.encrypt(value);
        Secret secret = new Secret(UUID.randomUUID().toString(), name, type, encrypted);
        secretRepo.addSecret(secret);
        notifyObservers("Secret added: " + name);
    }

    String getSecret(String userId, String secretId) {
        VaultUser user = userRepo.getUser(userId);
        if(user == null || user.accessLevel == AccessLevel.READ) {
            notifyObservers("Access denied for user: " + userId);
            return null;
        }
        Secret secret = secretRepo.getSecret(secretId);
        if(secret == null) return null;
        notifyObservers("Secret retrieved by user: " + user.name);
        return encryptionStrategy.decrypt(secret.value);
    }
}

// =====================
// DEMO
// =====================
public class VaultDemo {
    public static void main(String[] args) {
        SecretRepository secretRepo = new SecretRepository();
        UserRepository userRepo = new UserRepository();

        VaultUser admin = new VaultUser("U1", "Alice", AccessLevel.ADMIN);
        VaultUser reader = new VaultUser("U2", "Bob", AccessLevel.READ);
        userRepo.addUser(admin);
        userRepo.addUser(reader);

        VaultService vaultService = new VaultService(secretRepo, userRepo, new SimpleEncryptionStrategy());
        vaultService.registerObserver(new ConsoleNotifier());

        vaultService.addSecret("DB_PASSWORD", SecretType.PASSWORD, "mysecret123");
        Secret stored = secretRepo.getAllSecrets().get(0);

        System.out.println("Admin read: " + vaultService.getSecret("U1", stored.secretId));
        System.out.println("Reader read: " + vaultService.getSecret("U2", stored.secretId));
    }
}
