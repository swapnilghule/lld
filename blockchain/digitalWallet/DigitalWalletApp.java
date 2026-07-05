package LLD2.blockchain.digitalWallet;

import java.util.*;

/* =====================
   ENUMS
   ===================== */

enum WalletStatus {
    ACTIVE, SUSPENDED, CLOSED
}

enum TransactionType {
    CREDIT, DEBIT, REFUND
}

enum TransactionStatus {
    INITIATED, PROCESSING, SUCCESS, FAILED, REVERSED
}

enum PaymentMethodType {
    UPI, CARD, NET_BANKING, WALLET_BALANCE
}

/* =====================
   ENTITIES
   ===================== */

class User {
    String userId;
    String name;
    String email;

    User(String userId, String name, String email) {
        this.userId = userId;
        this.name = name;
        this.email = email;
    }
}

class Wallet {
    String walletId;
    String userId;
    double balance;
    WalletStatus status;

    Wallet(String walletId, String userId) {
        this.walletId = walletId;
        this.userId = userId;
        this.balance = 0.0;
        this.status = WalletStatus.ACTIVE;
    }
}

class Transaction {
    String transactionId;
    String walletId;
    double amount;
    TransactionType type;
    TransactionStatus status;
    Date createdAt;

    Transaction(String transactionId, String walletId, double amount, TransactionType type) {
        this.transactionId = transactionId;
        this.walletId = walletId;
        this.amount = amount;
        this.type = type;
        this.status = TransactionStatus.INITIATED;
        this.createdAt = new Date();
    }
}

class PaymentMethod {
    String methodId;
    String userId;
    PaymentMethodType type;
    boolean active;

    PaymentMethod(String methodId, String userId, PaymentMethodType type) {
        this.methodId = methodId;
        this.userId = userId;
        this.type = type;
        this.active = true;
    }
}

/* =====================
   REPOSITORIES
   ===================== */

class UserRepository {
    private final Map<String, User> users = new HashMap<>();

    void save(User user) {
        users.put(user.userId, user);
    }

    User findById(String userId) {
        return users.get(userId);
    }
}

class WalletRepository {
    private final Map<String, Wallet> wallets = new HashMap<>();

    void save(Wallet wallet) {
        wallets.put(wallet.userId, wallet);
    }

    Wallet findByUserId(String userId) {
        return wallets.get(userId);
    }
}

class TransactionRepository {
    private final Map<String, Transaction> transactions = new HashMap<>();

    void save(Transaction txn) {
        transactions.put(txn.transactionId, txn);
    }

    Transaction findById(String txnId) {
        return transactions.get(txnId);
    }

    List<Transaction> findByWalletId(String walletId) {
        List<Transaction> result = new ArrayList<>();
        for (Transaction t : transactions.values()) {
            if (t.walletId.equals(walletId)) {
                result.add(t);
            }
        }
        return result;
    }
}

class PaymentMethodRepository {
    private final Map<String, List<PaymentMethod>> methods = new HashMap<>();

    void save(PaymentMethod method) {
        methods.computeIfAbsent(method.userId, k -> new ArrayList<>()).add(method);
    }

    List<PaymentMethod> findByUserId(String userId) {
        return methods.getOrDefault(userId, new ArrayList<>());
    }
}

/* =====================
   STRATEGY (Payment)
   ===================== */

interface PaymentStrategy {
    boolean pay(double amount);
}

class UpiPaymentStrategy implements PaymentStrategy {
    public boolean pay(double amount) {
        return true;
    }
}

class CardPaymentStrategy implements PaymentStrategy {
    public boolean pay(double amount) {
        return true;
    }
}

class NetBankingPaymentStrategy implements PaymentStrategy {
    public boolean pay(double amount) {
        return true;
    }
}

class WalletBalancePaymentStrategy implements PaymentStrategy {
    private final Wallet wallet;

    WalletBalancePaymentStrategy(Wallet wallet) {
        this.wallet = wallet;
    }

    public boolean pay(double amount) {
        return wallet.balance >= amount;
    }
}

/* =====================
   STATE (Transaction)
   ===================== */

interface TransactionState {
    void next(Transaction txn);
    TransactionStatus getStatus();
}

class InitiatedState implements TransactionState {
    public void next(Transaction txn) {
        txn.status = TransactionStatus.PROCESSING;
    }
    public TransactionStatus getStatus() {
        return TransactionStatus.INITIATED;
    }
}

class ProcessingState implements TransactionState {
    public void next(Transaction txn) {
        txn.status = TransactionStatus.SUCCESS;
    }
    public TransactionStatus getStatus() {
        return TransactionStatus.PROCESSING;
    }
}

class SuccessState implements TransactionState {
    public void next(Transaction txn) {}
    public TransactionStatus getStatus() {
        return TransactionStatus.SUCCESS;
    }
}

class FailedState implements TransactionState {
    public void next(Transaction txn) {}
    public TransactionStatus getStatus() {
        return TransactionStatus.FAILED;
    }
}

class ReversedState implements TransactionState {
    public void next(Transaction txn) {}
    public TransactionStatus getStatus() {
        return TransactionStatus.REVERSED;
    }
}

/* =====================
   SERVICES
   ===================== */

class LedgerService {
    void credit(Wallet wallet, double amount) {
        wallet.balance += amount;
    }

    void debit(Wallet wallet, double amount) {
        wallet.balance -= amount;
    }
}

class TransactionService {
    private final TransactionRepository repo;
    private final LedgerService ledgerService;

    TransactionService(TransactionRepository repo, LedgerService ledgerService) {
        this.repo = repo;
        this.ledgerService = ledgerService;
    }

    Transaction create(String walletId, double amount, TransactionType type) {
        Transaction txn = new Transaction(UUID.randomUUID().toString(), walletId, amount, type);
        repo.save(txn);
        return txn;
    }

    void complete(Transaction txn, Wallet wallet) {
        if (txn.type == TransactionType.DEBIT) {
            ledgerService.debit(wallet, txn.amount);
        } else {
            ledgerService.credit(wallet, txn.amount);
        }
        txn.status = TransactionStatus.SUCCESS;
        repo.save(txn);
    }
}

class WalletService {
    private final WalletRepository walletRepo;

    WalletService(WalletRepository walletRepo) {
        this.walletRepo = walletRepo;
    }

    Wallet createWallet(String userId) {
        Wallet wallet = new Wallet(UUID.randomUUID().toString(), userId);
        walletRepo.save(wallet);
        return wallet;
    }

    double getBalance(String userId) {
        return walletRepo.findByUserId(userId).balance;
    }
}

class PaymentService {
    private final WalletRepository walletRepo;
    private final TransactionService txnService;

    PaymentService(WalletRepository walletRepo, TransactionService txnService) {
        this.walletRepo = walletRepo;
        this.txnService = txnService;
    }

    Transaction pay(String userId, double amount, PaymentMethodType methodType) {
        Wallet wallet = walletRepo.findByUserId(userId);
        PaymentStrategy strategy = selectStrategy(methodType, wallet);

        Transaction txn = txnService.create(wallet.walletId, amount, TransactionType.DEBIT);

        if (strategy.pay(amount)) {
            txnService.complete(txn, wallet);
        } else {
            txn.status = TransactionStatus.FAILED;
        }
        return txn;
    }

    Transaction refund(Transaction originalTxn) {
        Wallet wallet = walletRepo.findByUserId(originalTxn.walletId);
        Transaction refundTxn = txnService.create(wallet.walletId, originalTxn.amount, TransactionType.REFUND);
        txnService.complete(refundTxn, wallet);
        originalTxn.status = TransactionStatus.REVERSED;
        return refundTxn;
    }

    private PaymentStrategy selectStrategy(PaymentMethodType type, Wallet wallet) {
        switch (type) {
            case UPI: return new UpiPaymentStrategy();
            case CARD: return new CardPaymentStrategy();
            case NET_BANKING: return new NetBankingPaymentStrategy();
            default: return new WalletBalancePaymentStrategy(wallet);
        }
    }
}

/* =====================
   DEMO (Optional)
   ===================== */

public class DigitalWalletApp {
    public static void main(String[] args) {
        WalletRepository walletRepo = new WalletRepository();
        TransactionRepository txnRepo = new TransactionRepository();

        WalletService walletService = new WalletService(walletRepo);
        LedgerService ledgerService = new LedgerService();
        TransactionService txnService = new TransactionService(txnRepo, ledgerService);
        PaymentService paymentService = new PaymentService(walletRepo, txnService);

        Wallet wallet = walletService.createWallet("user1");
        ledgerService.credit(wallet, 1000);

        Transaction txn = paymentService.pay("user1", 300, PaymentMethodType.WALLET_BALANCE);
        System.out.println("Transaction Status: " + txn.status);
        System.out.println("Balance: " + wallet.balance);
    }
}
