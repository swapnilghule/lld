package LLD2.ecommerce.orderPlacement;


import java.util.*;

/**
 * ORDER PLACEMENT SYSTEM
 */
public class OrderPlacementSystem {

    /* =====================
       ENUMS
       ===================== */
    enum OrderStatus {
        CREATED,
        PAYMENT_PENDING,
        CONFIRMED,
        CANCELLED
    }

    enum PaymentMode {
        CARD,
        UPI,
        WALLET
    }

    /* =====================
       ENTITIES
       ===================== */
    static class User {
        String userId;

        User(String userId) {
            this.userId = userId;
        }
    }

    static class Product {
        String productId;
        double price;

        Product(String productId, double price) {
            this.productId = productId;
            this.price = price;
        }
    }

    static class OrderItem {
        String productId;
        int quantity;
        double price;

        OrderItem(String productId, int quantity, double price) {
            this.productId = productId;
            this.quantity = quantity;
            this.price = price;
        }
    }

    static class Order {
        String orderId;
        String userId;
        List<OrderItem> items;
        double totalAmount;
        OrderStatus status;

        Order(String orderId, String userId, List<OrderItem> items, double totalAmount) {
            this.orderId = orderId;
            this.userId = userId;
            this.items = items;
            this.totalAmount = totalAmount;
            this.status = OrderStatus.CREATED;
        }
    }

    /* =====================
       REPOSITORY
       ===================== */
    interface OrderRepository {
        void save(Order order);
        Order findById(String orderId);
    }

    static class InMemoryOrderRepository implements OrderRepository {
        private final Map<String, Order> store = new HashMap<>();

        public void save(Order order) {
            store.put(order.orderId, order);
        }

        public Order findById(String orderId) {
            return store.get(orderId);
        }
    }

    /* =====================
       PAYMENT STRATEGY
       ===================== */
    interface PaymentStrategy {
        boolean pay(double amount);
    }

    static class CardPaymentStrategy implements PaymentStrategy {
        public boolean pay(double amount) {
            System.out.println("Paid " + amount + " using CARD");
            return true;
        }
    }

    static class UPIPaymentStrategy implements PaymentStrategy {
        public boolean pay(double amount) {
            System.out.println("Paid " + amount + " using UPI");
            return true;
        }
    }

    static class WalletPaymentStrategy implements PaymentStrategy {
        public boolean pay(double amount) {
            System.out.println("Paid " + amount + " using WALLET");
            return true;
        }
    }

    /* =====================
       PAYMENT SERVICE
       ===================== */
    static class PaymentService {
        private final Map<PaymentMode, PaymentStrategy> strategies = new HashMap<>();

        PaymentService() {
            strategies.put(PaymentMode.CARD, new CardPaymentStrategy());
            strategies.put(PaymentMode.UPI, new UPIPaymentStrategy());
            strategies.put(PaymentMode.WALLET, new WalletPaymentStrategy());
        }

        boolean processPayment(PaymentMode mode, double amount) {
            return strategies.get(mode).pay(amount);
        }
    }

    /* =====================
       ORDER SERVICE
       ===================== */
    static class OrderService {
        private final OrderRepository orderRepository;
        private final PaymentService paymentService;

        OrderService(OrderRepository orderRepository, PaymentService paymentService) {
            this.orderRepository = orderRepository;
            this.paymentService = paymentService;
        }

        String placeOrder(String userId, List<OrderItem> items, PaymentMode mode) {
            double total = calculateTotal(items);
            Order order = new Order(UUID.randomUUID().toString(), userId, items, total);

            order.status = OrderStatus.PAYMENT_PENDING;
            orderRepository.save(order);

            boolean paymentSuccess = paymentService.processPayment(mode, total);

            if (paymentSuccess) {
                order.status = OrderStatus.CONFIRMED;
            } else {
                order.status = OrderStatus.CANCELLED;
            }

            orderRepository.save(order);
            return order.orderId;
        }

        private double calculateTotal(List<OrderItem> items) {
            double sum = 0;
            for (OrderItem item : items) {
                sum += item.price * item.quantity;
            }
            return sum;
        }

        Order getOrder(String orderId) {
            return orderRepository.findById(orderId);
        }
    }

    /* =====================
       MAIN
       ===================== */
    public static void main(String[] args) {
        OrderRepository orderRepository = new InMemoryOrderRepository();
        PaymentService paymentService = new PaymentService();
        OrderService orderService = new OrderService(orderRepository, paymentService);

        List<OrderItem> items = List.of(
                new OrderItem("P1", 2, 500),
                new OrderItem("P2", 1, 1000)
        );

        String orderId = orderService.placeOrder("USER1", items, PaymentMode.UPI);
        Order order = orderService.getOrder(orderId);

        System.out.println("Order ID: " + order.orderId);
        System.out.println("Status: " + order.status);
        System.out.println("Amount: " + order.totalAmount);
    }
}
