package LLD2.coderamry.vending;

import java.util.*;

// =====================
// ENUMS
// =====================
enum ProductType {
    SNACK, DRINK
}

enum VendingState {
    IDLE, SELECTION, PAYMENT, DISPENSE
}

// =====================
// ENTITIES
// =====================
class Product {
    String productId;
    String name;
    ProductType type;
    double price;
    int quantity;

    Product(String productId, String name, ProductType type, double price, int quantity) {
        this.productId = productId;
        this.name = name;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
    }
}

// =====================
// REPOSITORY
// =====================
class ProductRepository {
    Map<String, Product> products = new HashMap<>();

    void addProduct(Product product) { products.put(product.productId, product); }
    Product findById(String id) { return products.get(id); }
    List<Product> findAll() { return new ArrayList<>(products.values()); }
}

// =====================
// STATE PATTERN
// =====================
interface VendingStateStrategy {
    void handle(VendingMachine vm);
}

class IdleState implements VendingStateStrategy {
    public void handle(VendingMachine vm) {
        System.out.println("Machine is idle. Waiting for selection...");
    }
}

class SelectionState implements VendingStateStrategy {
    public void handle(VendingMachine vm) {
        System.out.println("Product selected: " + vm.selectedProductId);
    }
}

class PaymentState implements VendingStateStrategy {
    public void handle(VendingMachine vm) {
        System.out.println("Payment received: $" + vm.insertedAmount);
    }
}

class DispenseState implements VendingStateStrategy {
    public void handle(VendingMachine vm) {
        Product p = vm.productRepo.findById(vm.selectedProductId);
        if(p != null && p.quantity > 0 && vm.insertedAmount >= p.price) {
            p.quantity--;
            System.out.println("Dispensed: " + p.name);
            vm.insertedAmount -= p.price;
        } else {
            System.out.println("Cannot dispense. Check stock or payment.");
        }
        vm.setState(new IdleState());
    }
}

// =====================
// VENDING MACHINE SERVICE
// =====================
class VendingMachine {
    VendingStateStrategy state;
    ProductRepository productRepo;
    String selectedProductId;
    double insertedAmount;

    VendingMachine(ProductRepository repo) {
        this.productRepo = repo;
        this.state = new IdleState();
        this.insertedAmount = 0;
    }

    void setState(VendingStateStrategy state) { this.state = state; }
    void selectProduct(String productId) {
        this.selectedProductId = productId;
        setState(new SelectionState());
        state.handle(this);
    }

    void insertMoney(double amount) {
        this.insertedAmount += amount;
        setState(new PaymentState());
        state.handle(this);
    }

    void dispense() {
        setState(new DispenseState());
        state.handle(this);
    }

    void showProducts() {
        System.out.println("Available products:");
        for(Product p: productRepo.findAll()) {
            System.out.println(p.productId + " - " + p.name + " $" + p.price + " Qty:" + p.quantity);
        }
    }
}

// =====================
// DEMO
// =====================
public class VendingMachineDemo {
    public static void main(String[] args) {
        ProductRepository repo = new ProductRepository();
        repo.addProduct(new Product("P1", "Coke", ProductType.DRINK, 1.5, 10));
        repo.addProduct(new Product("P2", "Chips", ProductType.SNACK, 2.0, 5));

        VendingMachine vm = new VendingMachine(repo);

        vm.showProducts();
        vm.selectProduct("P1");
        vm.insertMoney(2.0);
        vm.dispense();

        vm.selectProduct("P2");
        vm.insertMoney(1.0); // insufficient
        vm.dispense();
    }
}
