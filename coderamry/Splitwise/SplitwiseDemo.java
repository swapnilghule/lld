package LLD2.coderamry.Splitwise;

import java.util.*;

// =====================
// ENUMS
// =====================
enum SplitType {
    EQUAL,
    EXACT,
    PERCENT
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

class Expense {
    String expenseId;
    String description;
    double amount;
    String paidBy; // userId
    SplitType splitType;
    Map<String, Double> splits = new HashMap<>(); // userId -> amount

    Expense(String expenseId, String description, double amount, String paidBy, SplitType splitType) {
        this.expenseId = expenseId;
        this.description = description;
        this.amount = amount;
        this.paidBy = paidBy;
        this.splitType = splitType;
    }
}

// =====================
// REPOSITORIES
// =====================
class UserRepository {
    Map<String, User> users = new HashMap<>();
    void addUser(User u) { users.put(u.userId, u); }
    User findById(String id) { return users.get(id); }
    List<User> findAll() { return new ArrayList<>(users.values()); }
}

class ExpenseRepository {
    Map<String, Expense> expenses = new HashMap<>();
    void addExpense(Expense e) { expenses.put(e.expenseId, e); }
    Expense findById(String id) { return expenses.get(id); }
    List<Expense> findAll() { return new ArrayList<>(expenses.values()); }
}

// =====================
// STRATEGY PATTERN FOR SPLIT
// =====================
interface SplitStrategy {
    void split(Expense expense, List<String> involvedUserIds);
}

class EqualSplitStrategy implements SplitStrategy {
    public void split(Expense expense, List<String> involvedUserIds) {
        double perUser = expense.amount / involvedUserIds.size();
        for(String u : involvedUserIds) {
            expense.splits.put(u, perUser);
        }
    }
}

class ExactSplitStrategy implements SplitStrategy {
    Map<String, Double> exactMap;
    ExactSplitStrategy(Map<String, Double> exactMap) { this.exactMap = exactMap; }
    public void split(Expense expense, List<String> involvedUserIds) {
        for(String u : involvedUserIds) {
            expense.splits.put(u, exactMap.getOrDefault(u, 0.0));
        }
    }
}

class PercentSplitStrategy implements SplitStrategy {
    Map<String, Double> percentMap;
    PercentSplitStrategy(Map<String, Double> percentMap) { this.percentMap = percentMap; }
    public void split(Expense expense, List<String> involvedUserIds) {
        for(String u : involvedUserIds) {
            double amt = expense.amount * (percentMap.getOrDefault(u, 0.0)/100.0);
            expense.splits.put(u, amt);
        }
    }
}

// =====================
// OBSERVER PATTERN FOR NOTIFICATIONS
// =====================
interface NotificationObserver {
    void update(Expense expense, String message);
}

class EmailNotifier implements NotificationObserver {
    public void update(Expense expense, String message) {
        System.out.println("Email: " + message + " -> " + expense.description);
    }
}

class PushNotifier implements NotificationObserver {
    public void update(Expense expense, String message) {
        System.out.println("Push: " + message + " -> " + expense.description);
    }
}

// =====================
// SERVICE LAYER
// =====================
class ExpenseService {
    UserRepository userRepo;
    ExpenseRepository expenseRepo;
    List<NotificationObserver> observers = new ArrayList<>();

    ExpenseService(UserRepository uRepo, ExpenseRepository eRepo) {
        this.userRepo = uRepo;
        this.expenseRepo = eRepo;
    }

    void registerObserver(NotificationObserver obs) { observers.add(obs); }
    void notifyObservers(Expense e, String msg) {
        for(NotificationObserver o : observers) o.update(e, msg);
    }

    void addExpense(String desc, double amount, String paidBy, SplitType type,
                    List<String> involvedUserIds, SplitStrategy strategy) {
        Expense e = new Expense(UUID.randomUUID().toString(), desc, amount, paidBy, type);
        strategy.split(e, involvedUserIds);
        expenseRepo.addExpense(e);
        notifyObservers(e, "Expense Added");
    }

    void showExpenseBreakdown() {
        for(Expense e : expenseRepo.findAll()) {
            System.out.println("Expense: " + e.description + " Paid By: " + e.paidBy);
            for(Map.Entry<String, Double> entry : e.splits.entrySet()) {
                System.out.println("  User: " + entry.getKey() + " owes: " + entry.getValue());
            }
        }
    }
}

// =====================
// DEMO
// =====================
public class SplitwiseDemo {
    public static void main(String[] args) {
        UserRepository userRepo = new UserRepository();
        ExpenseRepository expenseRepo = new ExpenseRepository();

        userRepo.addUser(new User("U1", "Alice"));
        userRepo.addUser(new User("U2", "Bob"));
        userRepo.addUser(new User("U3", "Charlie"));

        ExpenseService expenseService = new ExpenseService(userRepo, expenseRepo);
        expenseService.registerObserver(new EmailNotifier());
        expenseService.registerObserver(new PushNotifier());

        List<String> users = List.of("U1", "U2", "U3");

        expenseService.addExpense("Dinner", 300, "U1", SplitType.EQUAL, users, new EqualSplitStrategy());

        Map<String, Double> exactMap = Map.of("U1", 100.0, "U2", 150.0, "U3", 50.0);
        expenseService.addExpense("Groceries", 300, "U2", SplitType.EXACT, users, new ExactSplitStrategy(exactMap));

        Map<String, Double> percentMap = Map.of("U1", 50.0, "U2", 30.0, "U3", 20.0);
        expenseService.addExpense("Taxi", 200, "U3", SplitType.PERCENT, users, new PercentSplitStrategy(percentMap));

        expenseService.showExpenseBreakdown();
    }
}
