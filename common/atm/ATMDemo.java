package LLD2.common.atm;

import java.util.*;

// =====================
// ENUMS
// =====================
enum AccountStatus {
    ACTIVE, BLOCKED
}

enum TransactionType {
    DEPOSIT, WITHDRAWAL, BALANCE_INQUIRY
}

// =====================
// ENTITIES
// =====================
class Account {
    String accountId;
    String ownerName;
    double balance;
    AccountStatus status;

    Account(String accountId, String ownerName, double balance) {
        this.accountId = accountId;
        this.ownerName = ownerName;
        this.balance = balance;
        this.status = AccountStatus.ACTIVE;
    }
}

class Transaction {
    String transactionId;
    Account account;
    TransactionType type;
    double amount;
    Date date;

    Transaction(String transactionId, Account account, TransactionType type, double amount) {
        this.transactionId = transactionId;
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.date = new Date();
    }
}

// =====================
// REPOSITORIES
// =====================
class AccountRepository {
    Map<String, Account> accounts = new HashMap<>();
    void addAccount(Account a) { accounts.put(a.accountId, a); }
    Account findById(String accountId) { return accounts.get(accountId); }
}

class TransactionRepository {
    Map<String, Transaction> transactions = new HashMap<>();
    void addTransaction(Transaction t) { transactions.put(t.transactionId, t); }
    List<Transaction> findByAccount(String accountId) {
        List<Transaction> list = new ArrayList<>();
        for(Transaction t : transactions.values()) {
            if(t.account.accountId.equals(accountId)) list.add(t);
        }
        return list;
    }
}

// =====================
// STRATEGY FOR TRANSACTION LIMITS
// =====================
interface TransactionStrategy {
    boolean validate(Account account, double amount);
}

class WithdrawalLimitStrategy implements TransactionStrategy {
    double limit;
    WithdrawalLimitStrategy(double limit) { this.limit = limit; }
    public boolean validate(Account account, double amount) {
        return amount <= limit && amount <= account.balance;
    }
}

// =====================
// OBSERVER PATTERN
// =====================
interface ATMObserver {
    void notify(String message);
}

class ConsoleNotifier implements ATMObserver {
    public void notify(String message) { System.out.println("Notification: " + message); }
}

// =====================
// SERVICE LAYER
// =====================
class ATMService {
    AccountRepository accountRepo;
    TransactionRepository transactionRepo;
    TransactionStrategy transactionStrategy;
    List<ATMObserver> observers = new ArrayList<>();

    ATMService(AccountRepository ar, TransactionRepository tr, TransactionStrategy ts) {
        this.accountRepo = ar;
        this.transactionRepo = tr;
        this.transactionStrategy = ts;
    }

    void registerObserver(ATMObserver obs) { observers.add(obs); }
    void notifyObservers(String msg) { for(ATMObserver o : observers) o.notify(msg); }

    void deposit(String accountId, double amount) {
        Account account = accountRepo.findById(accountId);
        if(account == null || account.status != AccountStatus.ACTIVE) {
            notifyObservers("Deposit failed: invalid account");
            return;
        }
        account.balance += amount;
        Transaction t = new Transaction(UUID.randomUUID().toString(), account, TransactionType.DEPOSIT, amount);
        transactionRepo.addTransaction(t);
        notifyObservers("Deposit successful: $" + amount);
    }

    void withdraw(String accountId, double amount) {
        Account account = accountRepo.findById(accountId);
        if(account == null || account.status != AccountStatus.ACTIVE) {
            notifyObservers("Withdrawal failed: invalid account");
            return;
        }
        if(!transactionStrategy.validate(account, amount)) {
            notifyObservers("Withdrawal failed: limit exceeded or insufficient balance");
            return;
        }
        account.balance -= amount;
        Transaction t = new Transaction(UUID.randomUUID().toString(), account, TransactionType.WITHDRAWAL, amount);
        transactionRepo.addTransaction(t);
        notifyObservers("Withdrawal successful: $" + amount);
    }

    double checkBalance(String accountId) {
        Account account = accountRepo.findById(accountId);
        if(account == null || account.status != AccountStatus.ACTIVE) {
            notifyObservers("Balance check failed: invalid account");
            return -1;
        }
        Transaction t = new Transaction(UUID.randomUUID().toString(), account, TransactionType.BALANCE_INQUIRY, 0);
        transactionRepo.addTransaction(t);
        notifyObservers("Balance inquiry: $" + account.balance);
        return account.balance;
    }
}

// =====================
// DEMO
// =====================
public class ATMDemo {
    public static void main(String[] args) {
        AccountRepository accountRepo = new AccountRepository();
        TransactionRepository transactionRepo = new TransactionRepository();

        Account acc1 = new Account("A1", "Alice", 1000);
        accountRepo.addAccount(acc1);

        ATMService atmService = new ATMService(accountRepo, transactionRepo, new WithdrawalLimitStrategy(500));
        atmService.registerObserver(new ConsoleNotifier());

        atmService.deposit("A1", 200);
        atmService.withdraw("A1", 300);
        atmService.checkBalance("A1");
    }
}
