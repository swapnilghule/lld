package LLD2.ecommerce.delierveyTracking;

import java.util.*;


// =====================
// ENUMS
// =====================
enum ShipmentStatus {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED
}

// =====================
// CORE ENTITIES
// =====================
class Order {
    String orderId;

    Order(String orderId) {
        this.orderId = orderId;
    }
}

class DeliveryAgent {
    String agentId;

    DeliveryAgent(String agentId) {
        this.agentId = agentId;
    }
}

class Location {
    double lat;
    double lon;

    Location(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }
}

class Shipment {
    String shipmentId;
    ShipmentStatus status;
    Location location;
    Order order;
    DeliveryAgent agent;

    List<TrackingObserver> observers = new ArrayList<>();

    Shipment(String shipmentId, Order order) {
        this.shipmentId = shipmentId;
        this.order = order;
        this.status = ShipmentStatus.CREATED;
    }

    void addObserver(TrackingObserver observer) {
        observers.add(observer);
    }

    void updateStatus(ShipmentStatus status) {
        this.status = status;
        notifyObservers();
    }

    void updateLocation(Location location) {
        this.location = location;
    }

    private void notifyObservers() {
        for (TrackingObserver observer : observers) {
            observer.update(status);
        }
    }
}

// =====================
// REPOSITORY
// =====================
class ShipmentRepository {
    private final Map<String, Shipment> store = new HashMap<>();

    void save(Shipment shipment) {
        store.put(shipment.shipmentId, shipment);
    }

    Shipment findById(String shipmentId) {
        return store.get(shipmentId);
    }
}

// =====================
// STRATEGY
// =====================
interface ETAStrategy {
    int calculate(Shipment shipment);
}

class DistanceBasedETAStrategy implements ETAStrategy {
    public int calculate(Shipment shipment) {
        return 120;
    }
}

class TrafficAwareETAStrategy implements ETAStrategy {
    public int calculate(Shipment shipment) {
        return 180;
    }
}

// =====================
// OBSERVER
// =====================
interface TrackingObserver {
    void update(ShipmentStatus status);
}

class UserNotificationObserver implements TrackingObserver {
    public void update(ShipmentStatus status) {
        System.out.println("User notified: " + status);
    }
}

class OpsDashboardObserver implements TrackingObserver {
    public void update(ShipmentStatus status) {
        System.out.println("Ops dashboard updated: " + status);
    }
}

// =====================
// SERVICES
// =====================
class ETAService {
    private final ETAStrategy strategy;

    ETAService(ETAStrategy strategy) {
        this.strategy = strategy;
    }

    int calculateETA(Shipment shipment) {
        return strategy.calculate(shipment);
    }
}

class NotificationService {
    void notify(ShipmentStatus status) {
        System.out.println("Notification sent for status: " + status);
    }
}

class TrackingService {
    private final ShipmentRepository repository;
    private final NotificationService notificationService;
    private final ETAService etaService;

    TrackingService(ShipmentRepository repository,
                    NotificationService notificationService,
                    ETAService etaService) {
        this.repository = repository;
        this.notificationService = notificationService;
        this.etaService = etaService;
    }

    Shipment createShipment(String orderId) {
        Shipment shipment = new Shipment(
                UUID.randomUUID().toString(),
                new Order(orderId)
        );
        repository.save(shipment);
        return shipment;
    }

    void updateLocation(String shipmentId, Location location) {
        Shipment shipment = repository.findById(shipmentId);
        if (shipment != null) {
            shipment.updateLocation(location);
        }
    }

    void updateStatus(String shipmentId, ShipmentStatus status) {
        Shipment shipment = repository.findById(shipmentId);
        if (shipment != null) {
            shipment.updateStatus(status);
            notificationService.notify(status);
        }
    }

    ShipmentStatus getStatus(String shipmentId) {
        Shipment shipment = repository.findById(shipmentId);
        return shipment != null ? shipment.status : null;
    }

    int getETA(String shipmentId) {
        Shipment shipment = repository.findById(shipmentId);
        return shipment != null ? etaService.calculateETA(shipment) : -1;
    }
}

// =====================
// MAIN
// =====================
public class DeliveryTrackingSystem {

    public static void main(String[] args) {

        ShipmentRepository repository = new ShipmentRepository();
        ETAService etaService = new ETAService(new TrafficAwareETAStrategy());
        NotificationService notificationService = new NotificationService();

        TrackingService trackingService =
                new TrackingService(repository, notificationService, etaService);

        Shipment shipment = trackingService.createShipment("ORDER-123");

        shipment.addObserver(new UserNotificationObserver());
        shipment.addObserver(new OpsDashboardObserver());

        trackingService.updateStatus(shipment.shipmentId, ShipmentStatus.PICKED_UP);
        trackingService.updateLocation(shipment.shipmentId, new Location(12.97, 77.59));
        trackingService.updateStatus(shipment.shipmentId, ShipmentStatus.OUT_FOR_DELIVERY);

        System.out.println("ETA: " + trackingService.getETA(shipment.shipmentId));
        System.out.println("Final Status: " + trackingService.getStatus(shipment.shipmentId));
    }
}


