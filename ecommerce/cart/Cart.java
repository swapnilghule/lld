package LLD2.ecommerce.cart;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

enum CartStatus { ACTIVE, CHECKED_OUT }

class User {
    String userId;
    User(String userId) { this.userId = userId; }
}

class Product {
    String productId;
    double price;
    Product(String productId, double price) {
        this.productId = productId;
        this.price = price;
    }
}

class CartItem {
    Product product;
    int quantity;
    CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }
}

class Cart {
    String cartId;
    User user;
    CartStatus status;
    Map<String, CartItem> items = new LinkedHashMap<>();

    Cart(String cartId, User user) {
        this.cartId = cartId;
        this.user = user;
        this.status = CartStatus.ACTIVE;
    }
}

interface CartRepository {
    Cart findByUserId(String userId);
    void save(Cart cart);
}

class InMemoryCartRepository implements CartRepository {
    // ConcurrentHashMap: avoids corrupting the map itself under concurrent access
    private Map<String, Cart> store = new ConcurrentHashMap<>();

    public Cart findByUserId(String userId) {
        return store.get(userId);
    }

    public void save(Cart cart) {
        store.put(cart.user.userId, cart);
    }
}

interface CartValidationStrategy {
    boolean validate(Cart cart);
}

class StockValidationStrategy implements CartValidationStrategy {
    public boolean validate(Cart cart) {
        return true; // assume stock ok — would call InventoryService in real system
    }
}

class PriceValidationStrategy implements CartValidationStrategy {
    public boolean validate(Cart cart) {
        for (CartItem item : cart.items.values()) {
            if (item.product.price <= 0) return false;
        }
        return true;
    }
}

class CartValidationService {
    List<CartValidationStrategy> strategies = List.of(
            new StockValidationStrategy(),
            new PriceValidationStrategy()
    );

    boolean validate(Cart cart) {
        if (cart.items.isEmpty()) return false;
        for (CartValidationStrategy s : strategies) {
            if (!s.validate(cart)) return false;
        }
        return true;
    }
}

class PricingService {
    double getTotal(Cart cart) {
        double total = 0.0;
        for (CartItem item : cart.items.values()) {
            total += item.product.price * item.quantity;
        }
        return total;
    }
}

class CartService {

    private CartRepository cartRepo = new InMemoryCartRepository();
    private PricingService pricingService = new PricingService();
    private CartValidationService validationService = new CartValidationService();

    // synchronized: simple fix for the read-modify-write race on the same cart.
    // In a real system: per-cart lock or DB optimistic locking (version column).
    public synchronized void addItem(String userId, Product product, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be positive");

        Cart cart = cartRepo.findByUserId(userId);
        if (cart == null) {
            cart = new Cart(UUID.randomUUID().toString(), new User(userId));
        }
        if (cart.status != CartStatus.ACTIVE) {
            throw new IllegalStateException("Cannot modify a checked-out cart");
        }

        CartItem item = cart.items.get(product.productId);
        if (item == null) {
            cart.items.put(product.productId, new CartItem(product, qty));
        } else {
            item.quantity += qty;
        }
        cartRepo.save(cart);
    }

    public synchronized void removeItem(String userId, String productId) {
        Cart cart = cartRepo.findByUserId(userId);
        if (cart == null) return;
        if (cart.status != CartStatus.ACTIVE) {
            throw new IllegalStateException("Cannot modify a checked-out cart");
        }
        cart.items.remove(productId);
        cartRepo.save(cart);
    }

    public Cart viewCart(String userId) {
        return cartRepo.findByUserId(userId);
    }

    public double getCartTotal(String userId) {
        Cart cart = cartRepo.findByUserId(userId);
        return cart == null ? 0.0 : pricingService.getTotal(cart);
    }

    public synchronized boolean checkout(String userId) {
        Cart cart = cartRepo.findByUserId(userId);
        if (cart == null) return false;
        if (cart.status != CartStatus.ACTIVE) return false;

        if (!validationService.validate(cart)) return false;

        cart.status = CartStatus.CHECKED_OUT;
        cartRepo.save(cart);
        return true;
    }
}
