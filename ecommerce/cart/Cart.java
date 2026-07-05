package LLD2.ecommerce.cart;

import java.util.*;

/* =====================
   ENUM
   ===================== */
enum CartStatus {
    ACTIVE,
    CHECKED_OUT
}

/* =====================
   ENTITIES
   ===================== */
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
    Map<String, CartItem> items = new HashMap<>();

    Cart(String cartId, User user) {
        this.cartId = cartId;
        this.user = user;
        this.status = CartStatus.ACTIVE;
    }
}

/* =====================
   REPOSITORY
   ===================== */
interface CartRepository {
    Cart findByUserId(String userId);
    void save(Cart cart);
}

class InMemoryCartRepository implements CartRepository {

    private Map<String, Cart> store = new HashMap<>();

    @Override
    public Cart findByUserId(String userId) {
        return store.get(userId);
    }

    @Override
    public void save(Cart cart) {
        store.put(cart.user.userId, cart);
    }
}

/* =====================
   STRATEGY
   ===================== */
interface CartValidationStrategy {
    boolean validate(Cart cart);
}

class StockValidationStrategy implements CartValidationStrategy {
    public boolean validate(Cart cart) {
        return true; // assume stock ok
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

/* =====================
   SERVICES
   ===================== */
class CartValidationService {
    List<CartValidationStrategy> strategies = List.of(
            new StockValidationStrategy(),
            new PriceValidationStrategy()
    );

    boolean validate(Cart cart) {
        for (CartValidationStrategy s : strategies) {
            if (!s.validate(cart)) return false;
        }
        return true;
    }
}

class PricingService {
    double getPrice(Product product) {
        return product.price;
    }
}

class CartService {

    private CartRepository cartRepo = new InMemoryCartRepository();
    private PricingService pricingService = new PricingService();
    private CartValidationService validationService = new CartValidationService();

    public void addItem(String userId, Product product, int qty) {
        Cart cart = cartRepo.findByUserId(userId);

        if (cart == null) {
            cart = new Cart(UUID.randomUUID().toString(), new User(userId));
        }

        cart.items.putIfAbsent(
                product.productId,
                new CartItem(product, 0)
        );

        CartItem item = cart.items.get(product.productId);
        item.quantity += qty;

        cartRepo.save(cart);
    }

    public void removeItem(String userId, String productId) {
        Cart cart = cartRepo.findByUserId(userId);
        if (cart != null) {
            cart.items.remove(productId);
            cartRepo.save(cart);
        }
    }

    public Cart viewCart(String userId) {
        return cartRepo.findByUserId(userId);
    }

    public boolean checkout(String userId) {
        Cart cart = cartRepo.findByUserId(userId);
        if (cart == null) return false;

        if (!validationService.validate(cart)) return false;

        cart.status = CartStatus.CHECKED_OUT;
        cartRepo.save(cart);
        return true;
    }
}
