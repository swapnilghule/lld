package LLD2.ecommerce.cart;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/* =====================
   ENUM
   ===================== */
enum CartStatus {
    ACTIVE,
    CHECKED_OUT
}

/* =====================
   EXCEPTIONS
   ===================== */
class CartNotFoundException extends RuntimeException {
    CartNotFoundException(String userId) { super("No active cart for user: " + userId); }
}

class CartNotActiveException extends RuntimeException {
    CartNotActiveException(String cartId) { super("Cart is not ACTIVE, cannot modify: " + cartId); }
}

class InvalidQuantityException extends RuntimeException {
    InvalidQuantityException(int qty) { super("Quantity must be positive, got: " + qty); }
}

class EmptyCartException extends RuntimeException {
    EmptyCartException(String cartId) { super("Cannot checkout empty cart: " + cartId); }
}

class CartValidationException extends RuntimeException {
    CartValidationException(String reason) { super("Cart validation failed: " + reason); }
}

/* =====================
   ENTITIES
   ===================== */
class User {
    final String userId;
    User(String userId) { this.userId = userId; }
}

class Product {
    final String productId;
    final double price;

    Product(String productId, double price) {
        this.productId = productId;
        this.price = price;
    }
}

class CartItem {
    final Product product;
    private int quantity;

    CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    int getQuantity() { return quantity; }

    void addQuantity(int delta) {
        int newQty = quantity + delta;
        if (newQty <= 0) throw new InvalidQuantityException(newQty);
        quantity = newQty;
    }

    void setQuantity(int qty) {
        if (qty <= 0) throw new InvalidQuantityException(qty);
        quantity = qty;
    }
}

class Cart {
    final String cartId;
    final User user;
    volatile CartStatus status;
    final Map<String, CartItem> items = new LinkedHashMap<>();

    Cart(String cartId, User user) {
        this.cartId = cartId;
        this.user = user;
        this.status = CartStatus.ACTIVE;
    }

    void assertActive() {
        if (status != CartStatus.ACTIVE) throw new CartNotActiveException(cartId);
    }

    boolean isEmpty() { return items.isEmpty(); }
}

/* =====================
   REPOSITORY
   ===================== */
interface CartRepository {
    Optional<Cart> findByUserId(String userId);
    Cart getOrCreate(String userId, Supplier<Cart> factory);
    void save(Cart cart);
    void deleteByUserId(String userId);
}

/**
 * Thread-safe in-memory repository.
 * getOrCreate uses computeIfAbsent to atomically avoid the classic
 * "read cart -> mutate -> save" race between concurrent requests for
 * the same new user.
 */
class InMemoryCartRepository implements CartRepository {

    private final Map<String, Cart> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Cart> findByUserId(String userId) {
        return Optional.ofNullable(store.get(userId));
    }

    @Override
    public Cart getOrCreate(String userId, Supplier<Cart> factory) {
        return store.computeIfAbsent(userId, id -> factory.get());
    }

    @Override
    public void save(Cart cart) {
        store.put(cart.user.userId, cart);
    }

    @Override
    public void deleteByUserId(String userId) {
        store.remove(userId);
    }
}

/* =====================
   STRATEGY
   ===================== */
interface CartValidationStrategy {
    void validate(Cart cart); // throws CartValidationException on failure
}

interface InventoryService {
    boolean isInStock(String productId, int requestedQty);
}

class InMemoryInventoryService implements InventoryService {
    private final Map<String, Integer> stock = new ConcurrentHashMap<>();

    void setStock(String productId, int qty) { stock.put(productId, qty); }

    @Override
    public boolean isInStock(String productId, int requestedQty) {
        return stock.getOrDefault(productId, 0) >= requestedQty;
    }
}

class StockValidationStrategy implements CartValidationStrategy {
    private final InventoryService inventoryService;

    StockValidationStrategy(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    public void validate(Cart cart) {
        for (CartItem item : cart.items.values()) {
            if (!inventoryService.isInStock(item.product.productId, item.getQuantity())) {
                throw new CartValidationException("Insufficient stock for " + item.product.productId);
            }
        }
    }
}

class PriceValidationStrategy implements CartValidationStrategy {
    @Override
    public void validate(Cart cart) {
        for (CartItem item : cart.items.values()) {
            if (item.product.price <= 0) {
                throw new CartValidationException("Invalid price for " + item.product.productId);
            }
        }
    }
}

/* =====================
   SERVICES
   ===================== */
class CartValidationService {
    private final List<CartValidationStrategy> strategies;

    CartValidationService(List<CartValidationStrategy> strategies) {
        this.strategies = strategies;
    }

    void validate(Cart cart) {
        if (cart.isEmpty()) throw new EmptyCartException(cart.cartId);
        for (CartValidationStrategy s : strategies) {
            s.validate(cart); // throws on failure, short-circuits
        }
    }
}

class PricingService {
    double getTotal(Cart cart) {
        double total = 0.0;
        for (CartItem item : cart.items.values()) {
            total += item.product.price * item.getQuantity();
        }
        return total;
    }
}

interface IdGenerator {
    String generate();
}

class UuidGenerator implements IdGenerator {
    public String generate() { return UUID.randomUUID().toString(); }
}

class CartService {

    private final CartRepository cartRepo;
    private final PricingService pricingService;
    private final CartValidationService validationService;
    private final IdGenerator idGenerator;

    CartService(CartRepository cartRepo,
                PricingService pricingService,
                CartValidationService validationService,
                IdGenerator idGenerator) {
        this.cartRepo = cartRepo;
        this.pricingService = pricingService;
        this.validationService = validationService;
        this.idGenerator = idGenerator;
    }

    public synchronized void addItem(String userId, Product product, int qty) {
        if (qty <= 0) throw new InvalidQuantityException(qty);
        Objects.requireNonNull(product, "product must not be null");

        Cart cart = cartRepo.getOrCreate(userId, () -> new Cart(idGenerator.generate(), new User(userId)));
        cart.assertActive();

        CartItem existing = cart.items.get(product.productId);
        if (existing == null) {
            cart.items.put(product.productId, new CartItem(product, qty));
        } else {
            existing.addQuantity(qty);
        }

        cartRepo.save(cart);
    }

    public synchronized void updateItemQuantity(String userId, String productId, int newQty) {
        Cart cart = requireCart(userId);
        cart.assertActive();

        CartItem item = cart.items.get(productId);
        if (item == null) return;

        if (newQty <= 0) {
            cart.items.remove(productId);
        } else {
            item.setQuantity(newQty);
        }
        cartRepo.save(cart);
    }

    public synchronized void removeItem(String userId, String productId) {
        Cart cart = requireCart(userId);
        cart.assertActive();
        cart.items.remove(productId);
        cartRepo.save(cart);
    }

    public Cart viewCart(String userId) {
        return requireCart(userId);
    }

    public double getCartTotal(String userId) {
        return pricingService.getTotal(requireCart(userId));
    }

    public synchronized boolean checkout(String userId) {
        Cart cart = requireCart(userId);
        cart.assertActive();

        validationService.validate(cart); // throws on failure

        cart.status = CartStatus.CHECKED_OUT;
        cartRepo.save(cart);
        return true;
    }

    private Cart requireCart(String userId) {
        return cartRepo.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException(userId));
    }
}
