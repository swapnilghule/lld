package LLD2.ecommerce.PaymentandRefund;

import java.util.*;

/**
 * Payment & Refund System
 * One-file LLD with Repo + State + Strategy
 */
public class PaymentAndRefundSystem {

    /* =====================
       ENUMS
       ===================== */
    enum PaymentStatus {
        INITIATED,
        SUCCESS,
        FAILED,
        REFUNDED
    }

    enum PaymentMethod {
        CARD,
        UPI,
        WALLET
    }

    enum RefundStatus {
        REQUESTED,
        PROCESSED,
        FAILED
    }

    /* =====================
       ENTITIES
       ===================== */
    static class Payment {
        String paymentId;
        String orderId;
        double amount;
        PaymentMethod method;
        PaymentStatus status;

        Payment(String paymentId, String orderId,
                double amount, PaymentMethod method) {
            this.paymentId = paymentId;
            this.orderId = orderId;
            this.amount = amount;
            this.method = method;
            this.status = PaymentStatus.INITIATED;
        }
    }

    static class Refund {
        String refundId;
        String paymentId;
        double amount;
        RefundStatus status;

        Refund(String refundId, String paymentId, double amount) {
            this.refundId = refundId;
            this.paymentId = paymentId;
            this.amount = amount;
            this.status = RefundStatus.REQUESTED;
        }
    }

    /* =====================
       REPOSITORIES
       ===================== */
    interface PaymentRepository {
        void save(Payment payment);
        Payment findById(String paymentId);
    }

    static class InMemoryPaymentRepository implements PaymentRepository {
        private final Map<String, Payment> store = new HashMap<>();

        public void save(Payment payment) {
            store.put(payment.paymentId, payment);
        }

        public Payment findById(String paymentId) {
            return store.get(paymentId);
        }
    }

    interface RefundRepository {
        void save(Refund refund);
        Refund findById(String refundId);
    }

    static class InMemoryRefundRepository implements RefundRepository {
        private final Map<String, Refund> store = new HashMap<>();

        public void save(Refund refund) {
            store.put(refund.refundId, refund);
        }

        public Refund findById(String refundId) {
            return store.get(refundId);
        }
    }

    /* =====================
       STRATEGY – PAYMENT
       ===================== */
    interface PaymentStrategy {
        boolean pay(double amount);
    }

    static class CardPaymentStrategy implements PaymentStrategy {
        public boolean pay(double amount) {
            return amount <= 10000;
        }
    }

    static class UPIPaymentStrategy implements PaymentStrategy {
        public boolean pay(double amount) {
            return true;
        }
    }

    static class WalletPaymentStrategy implements PaymentStrategy {
        public boolean pay(double amount) {
            return amount <= 5000;
        }
    }

    /* =====================
       STATE – PAYMENT
       ===================== */
    interface PaymentState {
        void handle(Payment payment);
    }

    static class PaymentSuccessState implements PaymentState {
        public void handle(Payment payment) {
            payment.status = PaymentStatus.SUCCESS;
        }
    }

    static class PaymentFailedState implements PaymentState {
        public void handle(Payment payment) {
            payment.status = PaymentStatus.FAILED;
        }
    }

    static class PaymentRefundedState implements PaymentState {
        public void handle(Payment payment) {
            payment.status = PaymentStatus.REFUNDED;
        }
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

        String makePayment(String orderId,
                           double amount,
                           PaymentMethod method) {

            Payment payment = new Payment(
                    UUID.randomUUID().toString(),
                    orderId,
                    amount,
                    method
            );

            boolean success = strategies.get(method).pay(amount);

            PaymentState state =
                    success ? new PaymentSuccessState()
                            : new PaymentFailedState();

            state.handle(payment);
            paymentRepo.save(payment);

            return payment.paymentId;
        }
    }

    static class RefundService {
        private final RefundRepository refundRepo;
        private final PaymentRepository paymentRepo;

        RefundService(RefundRepository refundRepo,
                      PaymentRepository paymentRepo) {
            this.refundRepo = refundRepo;
            this.paymentRepo = paymentRepo;
        }

        String refund(String paymentId) {
            Payment payment = paymentRepo.findById(paymentId);

            Refund refund = new Refund(
                    UUID.randomUUID().toString(),
                    paymentId,
                    payment.amount
            );

            refund.status = RefundStatus.PROCESSED;
            refundRepo.save(refund);

            new PaymentRefundedState().handle(payment);
            paymentRepo.save(payment);

            return refund.refundId;
        }
    }

    /* =====================
       MAIN (DEMO)
       ===================== */
    public static void main(String[] args) {

        PaymentRepository paymentRepo =
                new InMemoryPaymentRepository();
        RefundRepository refundRepo =
                new InMemoryRefundRepository();

        PaymentService paymentService =
                new PaymentService(paymentRepo);

        RefundService refundService =
                new RefundService(refundRepo, paymentRepo);

        String paymentId =
                paymentService.makePayment(
                        "ORDER1", 3000, PaymentMethod.WALLET);

        String refundId =
                refundService.refund(paymentId);

        System.out.println("Payment ID: " + paymentId);
        System.out.println("Refund ID: " + refundId);
        System.out.println("Final Payment Status: " +
                paymentRepo.findById(paymentId).status);
    }
}
