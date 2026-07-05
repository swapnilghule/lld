package LLD2.coderamry.filesystem;

import java.util.*;

// =====================
// ENUMS
// =====================
enum FileType {
    FILE,
    FOLDER
}

// =====================
// ENTITIES
// =====================
class User {
    String userId;
    String name;

    User(String userId, String name) {
        this.userId = userId;
        this.name = name;
    }
}

class FSItem {
    String id;
    String name;
    FileType type;
    String ownerId;
    Folder parent;
    Date createdAt;
    Date modifiedAt;

    FSItem(String id, String name, FileType type, String ownerId, Folder parent) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.ownerId = ownerId;
        this.parent = parent;
        this.createdAt = new Date();
        this.modifiedAt = new Date();
    }
}

class File extends FSItem {
    String content;

    File(String id, String name, String ownerId, Folder parent) {
        super(id, name, FileType.FILE, ownerId, parent);
        this.content = "";
    }
}

class Folder extends FSItem {
    List<FSItem> children = new ArrayList<>();

    Folder(String id, String name, String ownerId, Folder parent) {
        super(id, name, FileType.FOLDER, ownerId, parent);
    }

    void addItem(FSItem item) { children.add(item); }
    void removeItem(FSItem item) { children.remove(item); }
}

// =====================
// REPOSITORIES
// =====================
class UserRepository {
    Map<String, User> users = new HashMap<>();
    void addUser(User u) { users.put(u.userId, u); }
    User findById(String id) { return users.get(id); }
}

class FSRepository {
    Map<String, FSItem> fsItems = new HashMap<>();
    void addItem(FSItem item) { fsItems.put(item.id, item); }
    FSItem findById(String id) { return fsItems.get(id); }
    List<FSItem> findAll() { return new ArrayList<>(fsItems.values()); }
}

// =====================
// STRATEGY PATTERN FOR PERMISSIONS
// =====================
interface PermissionStrategy {
    boolean hasAccess(User user, FSItem item, String action);
}

class OwnerPermissionStrategy implements PermissionStrategy {
    public boolean hasAccess(User user, FSItem item, String action) {
        return user.userId.equals(item.ownerId);
    }
}

// =====================
// OBSERVER PATTERN FOR NOTIFICATIONS
// =====================
interface FSObserver {
    void update(FSItem item, String action);
}

class ConsoleNotifier implements FSObserver {
    public void update(FSItem item, String action) {
        System.out.println("Notification: " + action + " on " + item.name);
    }
}

// =====================
// SERVICE LAYER
// =====================
class FSService {
    FSRepository repo;
    List<FSObserver> observers = new ArrayList<>();
    PermissionStrategy permStrategy;

    FSService(FSRepository repo, PermissionStrategy strategy) {
        this.repo = repo;
        this.permStrategy = strategy;
    }

    void registerObserver(FSObserver obs) { observers.add(obs); }

    void notifyObservers(FSItem item, String action) {
        for(FSObserver o : observers) o.update(item, action);
    }

    File createFile(String name, User owner, Folder parent) {
        File f = new File(UUID.randomUUID().toString(), name, owner.userId, parent);
        if(parent != null) parent.addItem(f);
        repo.addItem(f);
        notifyObservers(f, "File Created");
        return f;
    }

    Folder createFolder(String name, User owner, Folder parent) {
        Folder f = new Folder(UUID.randomUUID().toString(), name, owner.userId, parent);
        if(parent != null) parent.addItem(f);
        repo.addItem(f);
        notifyObservers(f, "Folder Created");
        return f;
    }

    void deleteItem(FSItem item, User user) {
        if(!permStrategy.hasAccess(user, item, "delete")) return;
        if(item.parent != null) item.parent.removeItem(item);
        repo.fsItems.remove(item.id);
        notifyObservers(item, "Deleted");
    }

    void writeFile(File file, String content, User user) {
        if(!permStrategy.hasAccess(user, file, "write")) return;
        file.content = content;
        file.modifiedAt = new Date();
        notifyObservers(file, "Modified");
    }

    String readFile(File file, User user) {
        if(!permStrategy.hasAccess(user, file, "read")) return null;
        return file.content;
    }
}

// =====================
// DEMO
// =====================
public class FileSystemDemo {
    public static void main(String[] args) {
        UserRepository userRepo = new UserRepository();
        FSRepository fsRepo = new FSRepository();

        User alice = new User("U1", "Alice");
        User bob = new User("U2", "Bob");
        userRepo.addUser(alice);
        userRepo.addUser(bob);

        FSService fsService = new FSService(fsRepo, new OwnerPermissionStrategy());
        fsService.registerObserver(new ConsoleNotifier());

        Folder root = fsService.createFolder("root", alice, null);
        File f1 = fsService.createFile("file1.txt", alice, root);

        fsService.writeFile(f1, "Hello World!", alice);
        System.out.println(fsService.readFile(f1, alice));

        fsService.deleteItem(f1, alice);
    }
}
