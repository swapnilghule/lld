package LLD2.ecommerce.productCatalog;

import java.util.*;
import java.util.stream.Collectors;

public class ProductCatalogSystem {

    /* =====================
       ENUMS
    ===================== */
    enum ProductStatus {
        ACTIVE, INACTIVE, OUT_OF_STOCK
    }

    enum SortOrder {
        ASC, DESC
    }

    /* =====================
       ENTITIES
    ===================== */
    static class Product {
        String productId;
        String name;
        String description;
        double price;
        String categoryId;
        ProductStatus status;
        Map<String, String> attributes = new HashMap<>();

        Product(String productId, String name, String description,
                double price, String categoryId, ProductStatus status) {
            this.productId = productId;
            this.name = name;
            this.description = description;
            this.price = price;
            this.categoryId = categoryId;
            this.status = status;
        }
    }

    static class Category {
        String categoryId;
        String name;

        Category(String categoryId, String name) {
            this.categoryId = categoryId;
            this.name = name;
        }
    }

    /* =====================
       REPOSITORIES
    ===================== */
    interface ProductRepository {
        void save(Product product);
        Product findById(String productId);
        List<Product> findAll();
        List<Product> findByCategory(String categoryId);
    }

    static class InMemoryProductRepository implements ProductRepository {
        private final Map<String, Product> store = new HashMap<>();

        public void save(Product product) {
            store.put(product.productId, product);
        }

        public Product findById(String productId) {
            return store.get(productId);
        }

        public List<Product> findAll() {
            return new ArrayList<>(store.values());
        }

        public List<Product> findByCategory(String categoryId) {
            return store.values()
                    .stream()
                    .filter(p -> p.categoryId.equals(categoryId))
                    .collect(Collectors.toList());
        }
    }

    interface CategoryRepository {
        void save(Category category);
        Category findById(String categoryId);
    }

    static class InMemoryCategoryRepository implements CategoryRepository {
        private final Map<String, Category> store = new HashMap<>();

        public void save(Category category) {
            store.put(category.categoryId, category);
        }

        public Category findById(String categoryId) {
            return store.get(categoryId);
        }
    }

    /* =====================
       SEARCH STRATEGY
    ===================== */
    interface SearchStrategy {
        List<Product> search(List<Product> products, String keyword);
    }

    static class NameSearchStrategy implements SearchStrategy {
        public List<Product> search(List<Product> products, String keyword) {
            return products.stream()
                    .filter(p -> p.name.toLowerCase().contains(keyword.toLowerCase()))
                    .collect(Collectors.toList());
        }
    }

    static class DescriptionSearchStrategy implements SearchStrategy {
        public List<Product> search(List<Product> products, String keyword) {
            return products.stream()
                    .filter(p -> p.description.toLowerCase().contains(keyword.toLowerCase()))
                    .collect(Collectors.toList());
        }
    }

    /* =====================
       SORT STRATEGY
    ===================== */
    interface SortStrategy {
        List<Product> sort(List<Product> products, SortOrder order);
    }

    static class PriceSortStrategy implements SortStrategy {
        public List<Product> sort(List<Product> products, SortOrder order) {
            Comparator<Product> comp = Comparator.comparingDouble(p -> p.price);
            if (order == SortOrder.DESC) comp = comp.reversed();
            return products.stream().sorted(comp).collect(Collectors.toList());
        }
    }

    static class NameSortStrategy implements SortStrategy {
        public List<Product> sort(List<Product> products, SortOrder order) {
            Comparator<Product> comp = Comparator.comparing(p -> p.name);
            if (order == SortOrder.DESC) comp = comp.reversed();
            return products.stream().sorted(comp).collect(Collectors.toList());
        }
    }

    /* =====================
       SERVICE
    ===================== */
    static class CatalogService {
        private final ProductRepository productRepo;
        private final CategoryRepository categoryRepo;
        private SearchStrategy searchStrategy;
        private SortStrategy sortStrategy;

        CatalogService(ProductRepository productRepo, CategoryRepository categoryRepo) {
            this.productRepo = productRepo;
            this.categoryRepo = categoryRepo;
        }

        void addCategory(Category category) {
            categoryRepo.save(category);
        }

        void addProduct(Product product) {
            productRepo.save(product);
        }

        Product getProduct(String productId) {
            return productRepo.findById(productId);
        }

        List<Product> listByCategory(String categoryId) {
            return productRepo.findByCategory(categoryId);
        }

        List<Product> search(String keyword, SearchStrategy strategy) {
            this.searchStrategy = strategy;
            return searchStrategy.search(productRepo.findAll(), keyword);
        }

        List<Product> sort(List<Product> products, SortStrategy strategy, SortOrder order) {
            this.sortStrategy = strategy;
            return sortStrategy.sort(products, order);
        }
    }

    /* =====================
       MAIN (DEMO)
    ===================== */
    public static void main(String[] args) {
        ProductRepository productRepo = new InMemoryProductRepository();
        CategoryRepository categoryRepo = new InMemoryCategoryRepository();
        CatalogService catalogService = new CatalogService(productRepo, categoryRepo);

        Category electronics = new Category("C1", "Electronics");
        catalogService.addCategory(electronics);

        Product p1 = new Product("P1", "iPhone", "Apple smartphone", 80000, "C1", ProductStatus.ACTIVE);
        Product p2 = new Product("P2", "Samsung Galaxy", "Android phone", 60000, "C1", ProductStatus.ACTIVE);
        Product p3 = new Product("P3", "Pixel", "Google phone", 70000, "C1", ProductStatus.ACTIVE);

        catalogService.addProduct(p1);
        catalogService.addProduct(p2);
        catalogService.addProduct(p3);

        System.out.println("Search 'phone':");
        List<Product> searchResult =
                catalogService.search("phone", new DescriptionSearchStrategy());
        searchResult.forEach(p -> System.out.println(p.name));

        System.out.println("\nSorted by price DESC:");
        List<Product> sorted =
                catalogService.sort(searchResult, new PriceSortStrategy(), SortOrder.DESC);
        sorted.forEach(p -> System.out.println(p.name + " " + p.price));
    }
}
