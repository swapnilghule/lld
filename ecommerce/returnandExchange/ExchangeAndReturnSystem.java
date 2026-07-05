package LLD2.ecommerce.returnandExchange;

import java.util.*;

/**
 * Exchange & Return System
 * One-file LLD with Repo + Strategy + State
 */
public class ExchangeAndReturnSystem {

    /* =====================
       ENUMS
       ===================== */
    enum OrderItemStatus {
        DELIVERED,
        RETURN_REQUESTED,
        RETURN_APPROVED,
        RETURN_REJECTED,
        RETURNED,
        EXCHANGE_REQUESTED,
        EXCHANGE_COMPLETED
    }

    enum ReturnReason {
        DAMAGED,
        WRONG_ITEM,
        SIZE_ISSUE,
        OTHER
    }

    enum RefundMode {
        WALLET,
        ORIGINAL_PAYMENT
    }

    /* =====================
       ENTITIES
       ===================== */
    static class OrderItem {
        String orderItemId;
        String productId;
        double price;
        OrderItemStatus status;

        OrderItem(String orderItemId, String productId, double price) {
            this.orderItemId = orderItemId;
            this.productId = productId;
            this.price = price;
            this.status = OrderItemStatus.DELIVERED;
        }
    }

    static class ReturnRequest {
        String requestId;
        String orderItemId;
        ReturnReason reason;
        RefundMode refundMode;

        ReturnRequest(String requestId, String orderItemId,
                      ReturnReason reason, RefundMode refundMode) {
            this.requestId = requestId;
            this.orderItemId = orderItemId;
            this.reason = reason;
            this.refundMode = refundMode;
        }
    }

    /* =====================
       REPOSITORIES
       ===================== */
    interface OrderItemRepository {
        OrderItem findById(String orderItemId);
        void save(OrderItem item);
    }

    static class InMemoryOrderItemRepository implements OrderItemRepository {
        private final Map<String, OrderItem> store = new HashMap<>();

        public OrderItem findById(String orderItemId) {
            return store.get(orderItemId);
        }

        public void save(OrderItem item) {
            store.put(item.orderItemId, item);
        }
    }

    interface ReturnRepository {
        void save(ReturnRequest request);
        ReturnRequest findById(String requestId);
    }

    static class InMemoryReturnRepository implements ReturnRepository {
        private final Map<String, ReturnRequest> store = new HashMap<>();

        public void save(ReturnRequest request) {
            store.put(request.requestId, request);
        }

        public ReturnRequest findById(String requestId) {
            return store.get(requestId);
        }
    }

    /* =====================
       STRATEGY – REFUND
       ===================== */
    interface RefundStrategy {
        void refund(double amount);
    }

    static class WalletRefundStrategy implements RefundStrategy {
        public void refund(double amount) {
            System.out.println("Refunded " + amount + " to wallet");
        }
    }

    static class OriginalPaymentRefundStrategy implements RefundStrategy {
        public void refund(double amount) {
            System.out.println("Refunded " + amount + " to original payment");
        }
    }

    /* =====================
       STATE – RETURN FLOW
       ===================== */
    interface ReturnState {
        void handle(OrderItem item);
    }

    static class ReturnRequestedState implements ReturnState {
        public void handle(OrderItem item) {
            item.status = OrderItemStatus.RETURN_REQUESTED;
        }
    }

    static class ReturnApprovedState implements ReturnState {
        public void handle(OrderItem item) {
            item.status = OrderItemStatus.RETURN_APPROVED;
        }
    }

    static class ReturnedState implements ReturnState {
        public void handle(OrderItem item) {
            item.status = OrderItemStatus.RETURNED;
        }
    }

    /* =====================
       SERVICES
       ===================== */
    static class RefundService {
        private final Map<RefundMode, RefundStrategy> strategies;

        RefundService() {
            strategies = new HashMap<>();
            strategies.put(RefundMode.WALLET, new WalletRefundStrategy());
            strategies.put(RefundMode.ORIGINAL_PAYMENT,
                    new OriginalPaymentRefundStrategy());
        }

        void processRefund(double amount, RefundMode mode) {
            strategies.get(mode).refund(amount);
        }
    }

    static class ExchangeReturnService {
        private final OrderItemRepository orderRepo;
        private final ReturnRepository returnRepo;
        private final RefundService refundService;

        ExchangeReturnService(OrderItemRepository orderRepo,
                              ReturnRepository returnRepo,
                              RefundService refundService) {
            this.orderRepo = orderRepo;
            this.returnRepo = returnRepo;
            this.refundService = refundService;
        }

        String requestReturn(String orderItemId,
                             ReturnReason reason,
                             RefundMode refundMode) {

            OrderItem item = orderRepo.findById(orderItemId);
            new ReturnRequestedState().handle(item);

            ReturnRequest request = new ReturnRequest(
                    UUID.randomUUID().toString(),
                    orderItemId,
                    reason,
                    refundMode
            );

            returnRepo.save(request);
            orderRepo.save(item);
            return request.requestId;
        }

        void approveReturn(String requestId) {
            ReturnRequest request = returnRepo.findById(requestId);
            OrderItem item = orderRepo.findById(request.orderItemId);

            new ReturnApprovedState().handle(item);
            refundService.processRefund(item.price, request.refundMode);

            new ReturnedState().handle(item);
            orderRepo.save(item);
        }
    }

    /* =====================
       MAIN (DEMO)
       ===================== */
    public static void main(String[] args) {

        InMemoryOrderItemRepository orderRepo =
                new InMemoryOrderItemRepository();
        InMemoryReturnRepository returnRepo =
                new InMemoryReturnRepository();
        RefundService refundService = new RefundService();

        ExchangeReturnService service =
                new ExchangeReturnService(orderRepo, returnRepo, refundService);

        OrderItem item =
                new OrderItem("OI1", "P1", 999);
        orderRepo.save(item);

        String requestId =
                service.requestReturn("OI1",
                        ReturnReason.DAMAGED,
                        RefundMode.WALLET);

        service.approveReturn(requestId);
        System.out.println("Final Status: " +
                orderRepo.findById("OI1").status);
    }
}
