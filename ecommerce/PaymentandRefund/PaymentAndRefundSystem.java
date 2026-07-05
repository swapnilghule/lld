package LLD2.ecommerce.PaymentandRefund;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Payment & Refund System — Interview-ready version
 *
 * Fixes vs original:
 *  1. State pattern is REAL now: each state enforces its own valid
 *     transitions. Payment holds a `state` object, not just an enum flag.
 *  2. Refund is blocked unless payment is in SUCCESS state (invariant).
 *  3. Double-refund is blocked (idempotency at the domain level).
 *  4. Partial refunds supported with a running `refundedAmount`.
 *  5. Thread-safe repos (ConcurrentHashMap) — mention this out loud even
 *     if not asked.
 *  6. Basic input validation (amount > 0).
 *
 * Explicitly OUT of scope (say this out loud in interview, don't build it):
 *  - Real gateway integration / retries / webhooks
 *  - Persistence layer (DB), transactions across services
 *  - Idempotency keys for the payment CREATE call itself (only refund is
 *    guarded here)
 */
public class PaymentAndRefundSystem {

    /* =====================
       ENUMS
       ===================== */
    enum PaymentStatus { INITIATED, SUCCESS, FAILED, REFUNDED, PARTIALLY_REFUNDED }
    enum PaymentMethod { CARD, UPI, WALLET }
    enum RefundStatus { REQUESTED, PROCESSED, FAILED }

    /* =====================
       ENTITIES
       ===================== */
    static class Payment {
        final String paymentId;
        final String orderId;
        final double amount;
        final PaymentMethod method;
        double refundedAmount = 0.0;
        PaymentState state;

        Payment(String paymentId, String orderId, double amount, PaymentMethod method) {
            if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
            this.paymentId = paymentId;
            this.orderId = orderId;
            this.amount = amount;
            this.method = method;
            this.state = InitiatedState.INSTANCE;
        }

        PaymentStatus status() { return state.status(); }

        void markSuccess() { this.state = state.onSuccess(this); }
        void markFailed() { this.state = state.onFailure(this); }
        void applyRefund(double amt) { this.state = state.onRefund(this, amt); }
    }

    static class Refund {
        final String refundId;
        final String paymentId;
        final double amount;
        RefundStatus status;

        Refund(String refundId, String paymentId, double amount) {
            this.refundId = refundId;
            this.paymentId = paymentId;
            this.amount = amount;
            this.status = RefundStatus.REQUESTED;
        }
    }

    /* =====================
       REPOSITORIES (thread-safe)
       ===================== */
    interface PaymentRepository {
        void save(Payment payment);
        Payment findById(String paymentId);
    }

    static class InMemoryPaymentRepository implements PaymentRepository {
        private final Map<String, Payment> store = new ConcurrentHashMap<>();
        public void save(Payment payment) { store.put(payment.paymentId, payment); }
        public Payment findById(String paymentId) {
            Payment p = store.get(paymentId);
            if (p == null) throw new NoSuchElementException("Payment not found: " + paymentId);
            return p;
        }
    }

    interface RefundRepository {
        void save(Refund refund);
        Refund findById(String refundId);
    }

    static class InMemoryRefundRepository implements RefundRepository {
        private final Map<String, Refund> store = new ConcurrentHashMap<>();
        public void save(Refund refund) { store.put(refund.refundId, refund); }
        public Refund findById(String refundId) { return store.get(refundId); }
    }

    /* =====================
       STRATEGY — payment method behavior
       (interface first — this is the contract external gateways plug into)
       ===================== */
    interface PaymentStrategy {
        boolean pay(double amount);
    }

    static class CardPaymentStrategy implements PaymentStrategy {
        public boolean pay(double amount) { return amount <= 10000; } // stand-in for real gateway call
    }

    static class UPIPaymentStrategy implements PaymentStrategy {
        public boolean pay(double amount) { return true; }
    }

    static class WalletPaymentStrategy implements PaymentStrategy {
        public boolean pay(double amount) { return amount <= 5000; }
    }

    /* =====================
       STATE — real state machine.
       Each state OWNS its legal transitions. Illegal transitions throw,
       rather than silently mutating status — this is the core invariant
       an interviewer will probe for.
       ===================== */
    interface PaymentState {
        PaymentStatus status();

        default PaymentState onSuccess(Payment p) {
            throw new IllegalStateException("Cannot mark SUCCESS from " + status());
        }
        default PaymentState onFailure(Payment p) {
            throw new IllegalStateException("Cannot mark FAILED from " + status());
        }
        default PaymentState onRefund(Payment p, double amt) {
            throw new IllegalStateException("Cannot refund a payment in state " + status());
        }
    }

    static class InitiatedState implements PaymentState {
        static final InitiatedState INSTANCE = new InitiatedState();
        public PaymentStatus status() { return PaymentStatus.INITIATED; }
        public PaymentState onSuccess(Payment p) { return SuccessState.INSTANCE; }
        public PaymentState onFailure(Payment p) { return FailedState.INSTANCE; }
    }

    static class SuccessState implements PaymentState {
        static final SuccessState INSTANCE = new SuccessState();
        public PaymentStatus status() { return PaymentStatus.SUCCESS; }
        public PaymentState onRefund(Payment p, double amt) {
            double totalAfter = p.refundedAmount + amt;
            if (totalAfter > p.amount) {
                throw new IllegalArgumentException("Refund exceeds payment amount");
            }
            p.refundedAmount = totalAfter;
            return (totalAfter == p.amount) ? RefundedState.INSTANCE : PartiallyRefundedState.INSTANCE;
        }
    }

    static class PartiallyRefundedState implements PaymentState {
        static final PartiallyRefundedState INSTANCE = new PartiallyRefundedState();
        public PaymentStatus status() { return PaymentStatus.PARTIALLY_REFUNDED; }
        public PaymentState onRefund(Payment p, double amt) {
            double totalAfter = p.refundedAmount + amt;
            if (totalAfter > p.amount) {
                throw new IllegalArgumentException("Refund exceeds remaining refundable amount");
            }
            p.refundedAmount = totalAfter;
            return (totalAfter == p.amount) ? RefundedState.INSTANCE : PartiallyRefundedState.INSTANCE;
        }
    }

    static class FailedState implements PaymentState {
        static final FailedState INSTANCE = new FailedState();
        public PaymentStatus status() { return PaymentStatus.FAILED; }
        // no legal transitions out of FAILED — refund/success both throw via defaults
    }

    static class RefundedState implements PaymentState {
        static final RefundedState INSTANCE = new RefundedState();
        public PaymentStatus status() { return PaymentStatus.REFUNDED; }
        // fully refunded — any further refund attempt throws via default onRefund
    }

    /* =====================
       SERVICES
       ===================== */
    static class PaymentService {
        private final PaymentRepository paymentRepo;
        private final Map<PaymentMethod, PaymentStrategy> strategies;

        PaymentService(PaymentRepository paymentRepo) {
            this.paymentRepo = paymentRepo;
            strategies = new HashMap<>();
            strategies.put(PaymentMethod.CARD, new CardPaymentStrategy());
            strategies.put(PaymentMethod.UPI, new UPIPaymentStrategy());
            strategies.put(PaymentMethod.WALLET, new WalletPaymentStrategy());
        }

        String makePayment(String orderId, double amount, PaymentMethod method) {
            Payment payment = new Payment(UUID.randomUUID().toString(), orderId, amount, method);
            boolean success = strategies.get(method).pay(amount);

            if (success) payment.markSuccess();
            else payment.markFailed();

            paymentRepo.save(payment);
            return payment.paymentId;
        }
    }

    static class RefundService {
        private final RefundRepository refundRepo;
        private final PaymentRepository paymentRepo;

        RefundService(RefundRepository refundRepo, PaymentRepository paymentRepo) {
            this.refundRepo = refundRepo;
            this.paymentRepo = paymentRepo;
        }

        // Full refund
        String refund(String paymentId) {
            Payment payment = paymentRepo.findById(paymentId);
            return refund(paymentId, payment.amount - payment.refundedAmount);
        }

        // Partial or full refund — invariant enforcement lives in PaymentState,
        // this method just orchestrates + persists.
        String refund(String paymentId, double amount) {
            Payment payment = paymentRepo.findById(paymentId);

            Refund refund = new Refund(UUID.randomUUID().toString(), paymentId, amount);
            try {
                payment.applyRefund(amount); // throws if invariant violated
                refund.status = RefundStatus.PROCESSED;
            } catch (IllegalStateException | IllegalArgumentException e) {
                refund.status = RefundStatus.FAILED;
                refundRepo.save(refund);
                throw e; // let caller know refund was rejected + why
            }

            refundRepo.save(refund);
            paymentRepo.save(payment);
            return refund.refundId;
        }
    }

    /* =====================
       MAIN (DEMO)
       ===================== */
    public static void main(String[] args) {
        PaymentRepository paymentRepo = new InMemoryPaymentRepository();
        RefundRepository refundRepo = new InMemoryRefundRepository();

        PaymentService paymentService = new PaymentService(paymentRepo);
        RefundService refundService = new RefundService(refundRepo, paymentRepo);

        String paymentId = paymentService.makePayment("ORDER1", 3000, PaymentMethod.WALLET);
        System.out.println("Payment status after creation: " + paymentRepo.findById(paymentId).status());

        String refundId = refundService.refund(paymentId, 1000); // partial refund
        System.out.println("Refund ID: " + refundId);
        System.out.println("Status after partial refund: " + paymentRepo.findById(paymentId).status());

        refundService.refund(paymentId); // refund the rest
        System.out.println("Status after full refund: " + paymentRepo.findById(paymentId).status());

        try {
            refundService.refund(paymentId, 100); // should fail — nothing left to refund
        } catch (IllegalStateException e) {
            System.out.println("Expected failure: " + e.getMessage());
        }
    }
}
