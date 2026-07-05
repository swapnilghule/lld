package LLD2.ecommerce.pricingAndAffortability;

import java.util.*;

/**
 * Pricing & Affordability System
 * Single-file, interview-ready LLD
 */
public class PricingAndAffordabilitySystem {

    /* =====================
       ENUMS
       ===================== */
    enum PriceComponentType {
        BASE_PRICE, DISCOUNT, COUPON, TAX
    }

    enum AffordabilityStatus {
        AFFORDABLE, NOT_AFFORDABLE
    }

    /* =====================
       ENTITIES
       ===================== */
    static class Product {
        String productId;
        double basePrice;

        Product(String productId, double basePrice) {
            this.productId = productId;
            this.basePrice = basePrice;
        }
    }

    static class User {
        String userId;
        double walletBalance;
        double creditLimit;

        User(String userId, double walletBalance, double creditLimit) {
            this.userId = userId;
            this.walletBalance = walletBalance;
            this.creditLimit = creditLimit;
        }
    }

    static class PriceComponent {
        PriceComponentType type;
        double amount;

        PriceComponent(PriceComponentType type, double amount) {
            this.type = type;
            this.amount = amount;
        }
    }

    static class PriceBreakdown {
        List<PriceComponent> components = new ArrayList<>();
        double finalAmount;

        void addComponent(PriceComponent component) {
            components.add(component);
            finalAmount += component.amount;
        }
    }

    static class AffordabilityResult {
        AffordabilityStatus status;

        AffordabilityResult(AffordabilityStatus status) {
            this.status = status;
        }
    }

    /* =====================
       REPOSITORIES
       ===================== */
    interface ProductRepository {
        Product findById(String productId);
    }

    static class InMemoryProductRepository implements ProductRepository {
        private final Map<String, Product> store = new HashMap<>();

        void save(Product product) {
            store.put(product.productId, product);
        }

        public Product findById(String productId) {
            return store.get(productId);
        }
    }

    interface UserRepository {
        User findById(String userId);
    }

    static class InMemoryUserRepository implements UserRepository {
        private final Map<String, User> store = new HashMap<>();

        void save(User user) {
            store.put(user.userId, user);
        }

        public User findById(String userId) {
            return store.get(userId);
        }
    }

    /* =====================
       PRICING RULES (CHAIN)
       ===================== */
    static abstract class PricingRule {
        protected PricingRule next;

        PricingRule linkWith(PricingRule next) {
            this.next = next;
            return next;
        }

        void apply(PriceBreakdown breakdown, Product product) {
            execute(breakdown, product);
            if (next != null) next.apply(breakdown, product);
        }

        protected abstract void execute(PriceBreakdown breakdown, Product product);
    }

    static class BasePriceRule extends PricingRule {
        protected void execute(PriceBreakdown breakdown, Product product) {
            breakdown.addComponent(
                    new PriceComponent(PriceComponentType.BASE_PRICE, product.basePrice)
            );
        }
    }

    static class DiscountRule extends PricingRule {
        protected void execute(PriceBreakdown breakdown, Product product) {
            double discount = product.basePrice * 0.10;
            breakdown.addComponent(
                    new PriceComponent(PriceComponentType.DISCOUNT, -discount)
            );
        }
    }

    static class CouponRule extends PricingRule {
        protected void execute(PriceBreakdown breakdown, Product product) {
            breakdown.addComponent(
                    new PriceComponent(PriceComponentType.COUPON, -50)
            );
        }
    }

    static class TaxRule extends PricingRule {
        protected void execute(PriceBreakdown breakdown, Product product) {
            double tax = breakdown.finalAmount * 0.18;
            breakdown.addComponent(
                    new PriceComponent(PriceComponentType.TAX, tax)
            );
        }
    }

    static class RuleEngine {
        private final PricingRule chain;

        RuleEngine(PricingRule chain) {
            this.chain = chain;
        }

        void applyRules(PriceBreakdown breakdown, Product product) {
            chain.apply(breakdown, product);
        }
    }

    /* =====================
       AFFORDABILITY STRATEGY
       ===================== */
    interface AffordabilityStrategy {
        boolean isAffordable(User user, double amount);
    }

    static class WalletAffordabilityStrategy implements AffordabilityStrategy {
        public boolean isAffordable(User user, double amount) {
            return user.walletBalance >= amount;
        }
    }

    static class CreditAffordabilityStrategy implements AffordabilityStrategy {
        public boolean isAffordable(User user, double amount) {
            return user.creditLimit >= amount;
        }
    }

    static class EmiAffordabilityStrategy implements AffordabilityStrategy {
        public boolean isAffordable(User user, double amount) {
            return amount <= (user.walletBalance + user.creditLimit);
        }
    }

    static class AffordabilityService {
        private final List<AffordabilityStrategy> strategies;

        AffordabilityService(List<AffordabilityStrategy> strategies) {
            this.strategies = strategies;
        }

        AffordabilityResult check(User user, double amount) {
            for (AffordabilityStrategy strategy : strategies) {
                if (strategy.isAffordable(user, amount)) {
                    return new AffordabilityResult(AffordabilityStatus.AFFORDABLE);
                }
            }
            return new AffordabilityResult(AffordabilityStatus.NOT_AFFORDABLE);
        }
    }

    /* =====================
       PRICING SERVICE
       ===================== */
    static class PricingService {
        private final ProductRepository productRepo;
        private final UserRepository userRepo;
        private final RuleEngine ruleEngine;
        private final AffordabilityService affordabilityService;

        PricingService(ProductRepository productRepo,
                       UserRepository userRepo,
                       RuleEngine ruleEngine,
                       AffordabilityService affordabilityService) {
            this.productRepo = productRepo;
            this.userRepo = userRepo;
            this.ruleEngine = ruleEngine;
            this.affordabilityService = affordabilityService;
        }

        AffordabilityResult calculatePrice(String productId, String userId) {
            Product product = productRepo.findById(productId);
            User user = userRepo.findById(userId);

            PriceBreakdown breakdown = new PriceBreakdown();
            ruleEngine.applyRules(breakdown, product);

            return affordabilityService.check(user, breakdown.finalAmount);
        }
    }

    /* =====================
       MAIN (DEMO)
       ===================== */
    public static void main(String[] args) {

        InMemoryProductRepository productRepo = new InMemoryProductRepository();
        InMemoryUserRepository userRepo = new InMemoryUserRepository();

        productRepo.save(new Product("P1", 1000));
        userRepo.save(new User("U1", 300, 800));

        PricingRule chain = new BasePriceRule();
        chain.linkWith(new DiscountRule())
                .linkWith(new CouponRule())
                .linkWith(new TaxRule());

        RuleEngine ruleEngine = new RuleEngine(chain);

        AffordabilityService affordabilityService =
                new AffordabilityService(List.of(
                        new WalletAffordabilityStrategy(),
                        new CreditAffordabilityStrategy(),
                        new EmiAffordabilityStrategy()
                ));

        PricingService pricingService =
                new PricingService(productRepo, userRepo, ruleEngine, affordabilityService);

        AffordabilityResult result =
                pricingService.calculatePrice("P1", "U1");

        System.out.println("Affordability Status: " + result.status);
    }
}
