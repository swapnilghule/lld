import java.util.*;

// =====================
// ENUMS
// =====================
enum VehicleType {
    CAR,
    BIKE,
    TRUCK
}

enum SlotType {
    COMPACT,
    REGULAR,
    LARGE
}

// =====================
// ENTITIES
// =====================
class Vehicle {
    String vehicleId;
    VehicleType type;
    Vehicle(String vehicleId, VehicleType type) {
        this.vehicleId = vehicleId;
        this.type = type;
    }
}

class ParkingSlot {
    String slotId;
    SlotType type;
    boolean occupied;
    Vehicle currentVehicle;

    ParkingSlot(String slotId, SlotType type) {
        this.slotId = slotId;
        this.type = type;
        this.occupied = false;
    }
}

class Ticket {
    String ticketId;
    Vehicle vehicle;
    ParkingSlot slot;
    Date entryTime;
    Date exitTime;
    double amount;

    Ticket(String ticketId, Vehicle vehicle, ParkingSlot slot) {
        this.ticketId = ticketId;
        this.vehicle = vehicle;
        this.slot = slot;
        this.entryTime = new Date();
    }
}

class User {
    String userId;
    String name;
    User(String userId, String name) {
        this.userId = userId;
        this.name = name;
    }
}

// =====================
// REPOSITORIES
// =====================
class SlotRepository {
    Map<String, ParkingSlot> slots = new HashMap<>();
    void addSlot(ParkingSlot s) { slots.put(s.slotId, s); }
    ParkingSlot findAvailable(SlotType type) {
        return slots.values().stream()
                .filter(s -> s.type == type && !s.occupied)
                .findFirst().orElse(null);
    }
}

class TicketRepository {
    Map<String, Ticket> tickets = new HashMap<>();
    void addTicket(Ticket t) { tickets.put(t.ticketId, t); }
    Ticket findByVehicle(String vehicleId) {
        return tickets.values().stream()
                .filter(t -> t.vehicle.vehicleId.equals(vehicleId) && t.exitTime == null)
                .findFirst().orElse(null);
    }
}

// =====================
// STRATEGY PATTERN FOR PRICING
// =====================
interface PricingStrategy {
    double calculateFee(Ticket ticket);
}

class HourlyPricingStrategy implements PricingStrategy {
    double ratePerHour;
    HourlyPricingStrategy(double rate) { this.ratePerHour = rate; }
    public double calculateFee(Ticket ticket) {
        long millis = new Date().getTime() - ticket.entryTime.getTime();
        double hours = Math.ceil(millis / (1000.0 * 60 * 60));
        return hours * ratePerHour;
    }
}

// =====================
// OBSERVER PATTERN FOR NOTIFICATIONS
// =====================
interface ParkingObserver {
    void notify(String message);
}

class ConsoleNotifier implements ParkingObserver {
    public void notify(String message) { System.out.println("Notification: " + message); }
}

// =====================
// SERVICE LAYER
// =====================
class ParkingService {
    SlotRepository slotRepo;
    TicketRepository ticketRepo;
    PricingStrategy pricingStrategy;
    List<ParkingObserver> observers = new ArrayList<>();

    ParkingService(SlotRepository sr, TicketRepository tr, PricingStrategy ps) {
        this.slotRepo = sr;
        this.ticketRepo = tr;
        this.pricingStrategy = ps;
    }

    void registerObserver(ParkingObserver obs) { observers.add(obs); }
    void notifyObservers(String msg) { for(ParkingObserver o: observers) o.notify(msg); }

    Ticket parkVehicle(Vehicle v, SlotType type) {
        ParkingSlot slot = slotRepo.findAvailable(type);
        if(slot == null) {
            notifyObservers("No available slot for " + v.vehicleId);
            return null;
        }
        slot.occupied = true;
        slot.currentVehicle = v;
        Ticket ticket = new Ticket(UUID.randomUUID().toString(), v, slot);
        ticketRepo.addTicket(ticket);
        notifyObservers("Vehicle parked: " + v.vehicleId + " at slot: " + slot.slotId);
        return ticket;
    }

    double unparkVehicle(String vehicleId) {
        Ticket ticket = ticketRepo.findByVehicle(vehicleId);
        if(ticket == null) return 0;
        ticket.exitTime = new Date();
        ticket.amount = pricingStrategy.calculateFee(ticket);
        ParkingSlot slot = ticket.slot;
        slot.occupied = false;
        slot.currentVehicle = null;
        notifyObservers("Vehicle unparked: " + vehicleId + ", Fee: " + ticket.amount);
        return ticket.amount;
    }
}

// =====================
// DEMO
// =====================
public class ParkingLotDemo {
    public static void main(String[] args) {
        SlotRepository slotRepo = new SlotRepository();
        TicketRepository ticketRepo = new TicketRepository();

        slotRepo.addSlot(new ParkingSlot("S1", SlotType.COMPACT));
        slotRepo.addSlot(new ParkingSlot("S2", SlotType.REGULAR));

        ParkingService service = new ParkingService(slotRepo, ticketRepo, new HourlyPricingStrategy(10));
        service.registerObserver(new ConsoleNotifier());

        Vehicle car = new Vehicle("V1", VehicleType.CAR);
        Vehicle bike = new Vehicle("V2", VehicleType.BIKE);

        service.parkVehicle(car, SlotType.REGULAR);
        service.parkVehicle(bike, SlotType.COMPACT);

        try { Thread.sleep(2000); } catch(Exception e){}

        service.unparkVehicle(car.vehicleId);
        service.unparkVehicle(bike.vehicleId);
    }
}
