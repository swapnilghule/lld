package LLD2.common.CarRental;

import java.util.*;
import java.text.*;

// =====================
// ENUMS
// =====================
enum CarStatus {
    AVAILABLE, RENTED, MAINTENANCE
}

enum RentalStatus {
    ACTIVE, COMPLETED, CANCELLED
}

// =====================
// ENTITIES
// =====================
class Car {
    String carId;
    String model;
    CarStatus status;

    Car(String carId, String model) {
        this.carId = carId;
        this.model = model;
        this.status = CarStatus.AVAILABLE;
    }
}

class Customer {
    String customerId;
    String name;

    Customer(String customerId, String name) {
        this.customerId = customerId;
        this.name = name;
    }
}

class Rental {
    String rentalId;
    Car car;
    Customer customer;
    Date startDate;
    Date endDate;
    RentalStatus status;

    Rental(String rentalId, Car car, Customer customer, Date startDate, Date endDate) {
        this.rentalId = rentalId;
        this.car = car;
        this.customer = customer;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = RentalStatus.ACTIVE;
    }
}

// =====================
// REPOSITORIES
// =====================
class CarRepository {
    Map<String, Car> cars = new HashMap<>();
    void addCar(Car c) { cars.put(c.carId, c); }
    Car findAvailable() {
        return cars.values().stream().filter(c -> c.status == CarStatus.AVAILABLE).findFirst().orElse(null);
    }
    Car findById(String carId) { return cars.get(carId); }
}

class CustomerRepository {
    Map<String, Customer> customers = new HashMap<>();
    void addCustomer(Customer c) { customers.put(c.customerId, c); }
    Customer findById(String id) { return customers.get(id); }
}

class RentalRepository {
    Map<String, Rental> rentals = new HashMap<>();
    void addRental(Rental r) { rentals.put(r.rentalId, r); }
    Rental findActiveRentalByCar(String carId) {
        return rentals.values().stream()
                .filter(r -> r.car.carId.equals(carId) && r.status == RentalStatus.ACTIVE)
                .findFirst().orElse(null);
    }
}

// =====================
// STRATEGY FOR PRICING
// =====================
interface PricingStrategy {
    double calculatePrice(Rental rental);
}

class DailyRateStrategy implements PricingStrategy {
    double ratePerDay;
    DailyRateStrategy(double rate) { this.ratePerDay = rate; }
    public double calculatePrice(Rental rental) {
        long diff = rental.endDate.getTime() - rental.startDate.getTime();
        long days = Math.max(1, diff / (1000*60*60*24));
        return days * ratePerDay;
    }
}

// =====================
// OBSERVER PATTERN
// =====================
interface RentalObserver {
    void notify(String message);
}

class ConsoleNotifier implements RentalObserver {
    public void notify(String message) { System.out.println("Notification: " + message); }
}

// =====================
// SERVICE LAYER
// =====================
class CarRentalService {
    CarRepository carRepo;
    CustomerRepository customerRepo;
    RentalRepository rentalRepo;
    PricingStrategy pricingStrategy;
    List<RentalObserver> observers = new ArrayList<>();

    CarRentalService(CarRepository cr, CustomerRepository csr, RentalRepository rr, PricingStrategy ps) {
        this.carRepo = cr;
        this.customerRepo = csr;
        this.rentalRepo = rr;
        this.pricingStrategy = ps;
    }

    void registerObserver(RentalObserver obs) { observers.add(obs); }
    void notifyObservers(String msg) { for(RentalObserver o : observers) o.notify(msg); }

    Rental rentCar(String carId, String customerId, Date start, Date end) {
        Car car = carRepo.findById(carId);
        Customer customer = customerRepo.findById(customerId);

        if(car == null || car.status != CarStatus.AVAILABLE) {
            notifyObservers("Car not available: " + carId);
            return null;
        }

        car.status = CarStatus.RENTED;
        Rental rental = new Rental(UUID.randomUUID().toString(), car, customer, start, end);
        rentalRepo.addRental(rental);

        double price = pricingStrategy.calculatePrice(rental);
        notifyObservers("Car rented: " + car.model + " to " + customer.name + " | Price: $" + price);
        return rental;
    }

    void returnCar(String carId) {
        Rental rental = rentalRepo.findActiveRentalByCar(carId);
        if(rental == null) return;
        rental.status = RentalStatus.COMPLETED;
        rental.car.status = CarStatus.AVAILABLE;
        notifyObservers("Car returned: " + rental.car.model);
    }
}

// =====================
// DEMO
// =====================
public class CarRentalDemo {
    public static void main(String[] args) {
        CarRepository carRepo = new CarRepository();
        CustomerRepository customerRepo = new CustomerRepository();
        RentalRepository rentalRepo = new RentalRepository();

        Car car1 = new Car("C1", "Tesla Model 3");
        carRepo.addCar(car1);
        Customer cust1 = new Customer("CU1", "Alice");
        customerRepo.addCustomer(cust1);

        CarRentalService rentalService = new CarRentalService(carRepo, customerRepo, rentalRepo, new DailyRateStrategy(50));
        rentalService.registerObserver(new ConsoleNotifier());

        Calendar cal = Calendar.getInstance();
        Date start = cal.getTime();
        cal.add(Calendar.DATE, 3);
        Date end = cal.getTime();

        rentalService.rentCar("C1", "CU1", start, end);
        rentalService.returnCar("C1");
    }
}
