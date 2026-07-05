package LLD2.common.HotelManagement;

import java.util.*;

// =====================
// ENUMS
// =====================
enum RoomType {
    SINGLE, DOUBLE, SUITE
}

enum BookingStatus {
    BOOKED, CANCELLED, COMPLETED
}

// =====================
// ENTITIES
// =====================
class Customer {
    String customerId;
    String name;

    Customer(String customerId, String name) {
        this.customerId = customerId;
        this.name = name;
    }
}

class Room {
    String roomId;
    RoomType type;
    boolean available;
    double basePrice;

    Room(String roomId, RoomType type, double basePrice) {
        this.roomId = roomId;
        this.type = type;
        this.available = true;
        this.basePrice = basePrice;
    }
}

class Booking {
    String bookingId;
    Customer customer;
    Room room;
    BookingStatus status;
    Date checkIn;
    Date checkOut;
    double amount;

    Booking(String bookingId, Customer customer, Room room, Date checkIn, Date checkOut) {
        this.bookingId = bookingId;
        this.customer = customer;
        this.room = room;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.status = BookingStatus.BOOKED;
        this.amount = 0.0;
    }
}

// =====================
// REPOSITORIES
// =====================
class RoomRepository {
    Map<String, Room> rooms = new HashMap<>();
    void addRoom(Room r) { rooms.put(r.roomId, r); }
    Room findAvailable(RoomType type) {
        return rooms.values().stream().filter(r -> r.type == type && r.available).findFirst().orElse(null);
    }
}

class BookingRepository {
    Map<String, Booking> bookings = new HashMap<>();
    void addBooking(Booking b) { bookings.put(b.bookingId, b); }
    Booking findById(String id) { return bookings.get(id); }
}

class CustomerRepository {
    Map<String, Customer> customers = new HashMap<>();
    void addCustomer(Customer c) { customers.put(c.customerId, c); }
    Customer findById(String id) { return customers.get(id); }
}

// =====================
// STRATEGY PATTERN FOR PRICING
// =====================
interface PricingStrategy {
    double calculatePrice(Room room, Date checkIn, Date checkOut);
}

class SimplePricingStrategy implements PricingStrategy {
    public double calculatePrice(Room room, Date checkIn, Date checkOut) {
        long millis = checkOut.getTime() - checkIn.getTime();
        double days = Math.ceil(millis / (1000.0 * 60 * 60 * 24));
        return days * room.basePrice;
    }
}

// =====================
// OBSERVER PATTERN FOR NOTIFICATIONS
// =====================
interface HotelObserver {
    void notify(String message);
}

class ConsoleNotifier implements HotelObserver {
    public void notify(String message) { System.out.println("Notification: " + message); }
}

// =====================
// SERVICE LAYER
// =====================
class BookingService {
    RoomRepository roomRepo;
    BookingRepository bookingRepo;
    PricingStrategy pricingStrategy;
    List<HotelObserver> observers = new ArrayList<>();

    BookingService(RoomRepository rr, BookingRepository br, PricingStrategy ps) {
        this.roomRepo = rr;
        this.bookingRepo = br;
        this.pricingStrategy = ps;
    }

    void registerObserver(HotelObserver obs) { observers.add(obs); }
    void notifyObservers(String msg) { for(HotelObserver o : observers) o.notify(msg); }

    Booking bookRoom(Customer customer, RoomType type, Date checkIn, Date checkOut) {
        Room room = roomRepo.findAvailable(type);
        if(room == null) {
            notifyObservers("No available room for type " + type);
            return null;
        }
        room.available = false;
        Booking booking = new Booking(UUID.randomUUID().toString(), customer, room, checkIn, checkOut);
        booking.amount = pricingStrategy.calculatePrice(room, checkIn, checkOut);
        bookingRepo.addBooking(booking);
        notifyObservers("Room booked: " + room.roomId + " for customer: " + customer.name);
        return booking;
    }

    void cancelBooking(Booking booking) {
        booking.status = BookingStatus.CANCELLED;
        booking.room.available = true;
        notifyObservers("Booking cancelled: " + booking.bookingId);
    }
}

// =====================
// DEMO
// =====================
public class HotelManagementDemo {
    public static void main(String[] args) {
        RoomRepository roomRepo = new RoomRepository();
        BookingRepository bookingRepo = new BookingRepository();
        CustomerRepository customerRepo = new CustomerRepository();

        Customer alice = new Customer("C1", "Alice");
        customerRepo.addCustomer(alice);

        Room r1 = new Room("R1", RoomType.SINGLE, 100);
        Room r2 = new Room("R2", RoomType.DOUBLE, 150);
        roomRepo.addRoom(r1);
        roomRepo.addRoom(r2);

        BookingService bookingService = new BookingService(roomRepo, bookingRepo, new SimplePricingStrategy());
        bookingService.registerObserver(new ConsoleNotifier());

        Calendar cal = Calendar.getInstance();
        Date checkIn = cal.getTime();
        cal.add(Calendar.DATE, 2);
        Date checkOut = cal.getTime();

        Booking b1 = bookingService.bookRoom(alice, RoomType.SINGLE, checkIn, checkOut);
        System.out.println("Amount: " + b1.amount);

        bookingService.cancelBooking(b1);
    }
}
