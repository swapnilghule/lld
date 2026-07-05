package LLD2.blockchain.tamperProofLedger;

import java.util.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class TamperProofLedger {

    /* =====================
       ENUM
    ===================== */
    enum EntryType {
        CREDIT,
        DEBIT
    }

    /* =====================
       HASH STRATEGY
    ===================== */
    interface HashStrategy {
        String hash(String input);
    }

    static class SHA256HashStrategy implements HashStrategy {
        public String hash(String input) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] encoded = digest.digest(input.getBytes());
                StringBuilder hex = new StringBuilder();
                for (byte b : encoded) {
                    hex.append(String.format("%02x", b));
                }
                return hex.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /* =====================
       ENTITY (IMMUTABLE)
    ===================== */
    static final class LedgerEntry {
        final String entryId;
        final long timestamp;
        final String referenceId;
        final EntryType type;
        final double amount;
        final String previousHash;
        final String hash;

        LedgerEntry(String entryId,
                    long timestamp,
                    String referenceId,
                    EntryType type,
                    double amount,
                    String previousHash,
                    HashStrategy hashStrategy) {

            this.entryId = entryId;
            this.timestamp = timestamp;
            this.referenceId = referenceId;
            this.type = type;
            this.amount = amount;
            this.previousHash = previousHash;
            this.hash = calculateHash(hashStrategy);
        }

        private String calculateHash(HashStrategy strategy) {
            String data = entryId + timestamp + referenceId + type + amount + previousHash;
            return strategy.hash(data);
        }
    }

    /* =====================
       REPOSITORY
    ===================== */
    interface LedgerRepository {
        void save(LedgerEntry entry);
        List<LedgerEntry> findAll();
        LedgerEntry getLastEntry();
    }

    static class InMemoryLedgerRepository implements LedgerRepository {
        private final List<LedgerEntry> ledger = new ArrayList<>();

        public void save(LedgerEntry entry) {
            ledger.add(entry);
        }

        public List<LedgerEntry> findAll() {
            return new ArrayList<>(ledger);
        }

        public LedgerEntry getLastEntry() {
            return ledger.isEmpty() ? null : ledger.get(ledger.size() - 1);
        }
    }

    /* =====================
       SERVICE
    ===================== */
    static class LedgerService {
        private final LedgerRepository repository;
        private final HashStrategy hashStrategy;

        LedgerService(LedgerRepository repository, HashStrategy hashStrategy) {
            this.repository = repository;
            this.hashStrategy = hashStrategy;
        }

        public void addEntry(String referenceId, EntryType type, double amount) {
            LedgerEntry last = repository.getLastEntry();
            String previousHash = (last == null) ? "GENESIS" : last.hash;

            LedgerEntry entry = new LedgerEntry(
                    UUID.randomUUID().toString(),
                    System.currentTimeMillis(),
                    referenceId,
                    type,
                    amount,
                    previousHash,
                    hashStrategy
            );

            repository.save(entry);
        }

        public boolean verifyLedger() {
            List<LedgerEntry> entries = repository.findAll();

            for (int i = 1; i < entries.size(); i++) {
                LedgerEntry curr = entries.get(i);
                LedgerEntry prev = entries.get(i - 1);

                if (!curr.previousHash.equals(prev.hash)) {
                    return false;
                }

                String recalculated = hashStrategy.hash(
                        curr.entryId +
                                curr.timestamp +
                                curr.referenceId +
                                curr.type +
                                curr.amount +
                                curr.previousHash
                );

                if (!curr.hash.equals(recalculated)) {
                    return false;
                }
            }
            return true;
        }

        public List<LedgerEntry> getEntries() {
            return repository.findAll();
        }
    }

    /* =====================
       MAIN (DEMO)
    ===================== */
    public static void main(String[] args) {
        LedgerRepository repo = new InMemoryLedgerRepository();
        HashStrategy hashStrategy = new SHA256HashStrategy();

        LedgerService service = new LedgerService(repo, hashStrategy);

        service.addEntry("ORDER-1", EntryType.CREDIT, 1000);
        service.addEntry("ORDER-2", EntryType.DEBIT, 200);
        service.addEntry("ORDER-3", EntryType.CREDIT, 500);

        System.out.println("Ledger valid: " + service.verifyLedger());

        for (LedgerEntry entry : service.getEntries()) {
            System.out.println(entry.referenceId + " -> " + entry.hash);
        }
    }
}
