package LLD2.blockchain.supplychain;

import java.util.*;

/* =====================
   ENUMS
   ===================== */

enum ShipmentStatus {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    AT_WAREHOUSE,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED
}

enum LocationType {
    FACTORY,
    WAREHOUSE,
    HUB,
    CUSTOMER
}

/* =====================
   ENTITIES
   ===================== */

class Product {
    String productId;
    String name;

    Product(String productId, String name) {
        this.productId = productId;
        this.name = name;
    }
}

class Location {
    String locationId;
    LocationType type;
    String city;

    Location(String locationId, LocationType type, String city) {
        this.locationId = locationId;
        this.type = type;
        this.city = city;
    }
}

class Shipment {
    String shipmentId;
    String orderId;
    ShipmentStatus status;
    Location currentLocation;
    List<Location> routeHistory;

    Shipment(String shipmentId, String orderId) {
        this.shipmentId = shipmentId;
        this.orderId = orderId;
        this.status = ShipmentStatus.CREATED;
        this.routeHistory = new ArrayList<>();
    }
}

class TrackingEvent {
    String eventId;
    String shipmentId;
    ShipmentStatus status;
    Location location;
    Date timestamp;

    TrackingEvent(String eventId, String shipmentId, ShipmentStatus status, Location location) {
        this.eventId = eventId;
        this.shipmentId = shipmentId;
        this.status = status;
        this.location = location;
        this.timestamp = new Date();
    }
}

/* =====================
   REPOSITORIES
   ===================== */

class ShipmentRepository {
    private final Map<String, Shipment> shipments = new HashMap<>();

    void save(Shipment shipment) {
        shipments.put(shipment.shipmentId, shipment);
    }

    Shipment findById(String shipmentId) {
        return shipments.get(shipmentId);
    }
}

class TrackingRepository {
    private final Map<String, List<TrackingEvent>> events = new HashMap<>();

    void save(TrackingEvent event) {
        events.computeIfAbsent(event.shipmentId, k -> new ArrayList<>()).add(event);
    }

    List<TrackingEvent> findByShipmentId(String shipmentId) {
        return events.getOrDefault(shipmentId, new ArrayList<>());
    }
}

/* =====================
   STRATEGY (Routing)
   ===================== */

interface RoutingStrategy {
    List<Location> calculateRoute(Location source, Location destination);
}

class SimpleRoutingStrategy implements RoutingStrategy {
    public List<Location> calculateRoute(Location source, Location destination) {
        List<Location> route = new ArrayList<>();
        route.add(source);
        route.add(destination);
        return route;
    }
}

class HubBasedRoutingStrategy implements RoutingStrategy {
    public List<Location> calculateRoute(Location source, Location destination) {
        List<Location> route = new ArrayList<>();
        route.add(source);
        route.add(new Location("HUB-1", LocationType.HUB, "Central Hub"));
        route.add(destination);
        return route;
    }
}

/* =====================
   STATE (Shipment)
   ===================== */

interface ShipmentState {
    void next(Shipment shipment);
    ShipmentStatus getStatus();
}

class CreatedState implements ShipmentState {
    public void next(Shipment shipment) {
        shipment.status = ShipmentStatus.PICKED_UP;
    }
    public ShipmentStatus getStatus() {
        return ShipmentStatus.CREATED;
    }
}

class InTransitState implements ShipmentState {
    public void next(Shipment shipment) {
        shipment.status = ShipmentStatus.OUT_FOR_DELIVERY;
    }
    public ShipmentStatus getStatus() {
        return ShipmentStatus.IN_TRANSIT;
    }
}

class DeliveredState implements ShipmentState {
    public void next(Shipment shipment) {}
    public ShipmentStatus getStatus() {
        return ShipmentStatus.DELIVERED;
    }
}

/* =====================
   SERVICES
   ===================== */

class ShipmentService {
    private final ShipmentRepository shipmentRepo;
    private final RoutingStrategy routingStrategy;

    ShipmentService(ShipmentRepository shipmentRepo, RoutingStrategy routingStrategy) {
        this.shipmentRepo = shipmentRepo;
        this.routingStrategy = routingStrategy;
    }

    Shipment createShipment(String orderId, Location source, Location destination) {
        Shipment shipment = new Shipment(UUID.randomUUID().toString(), orderId);
        shipment.routeHistory = routingStrategy.calculateRoute(source, destination);
        shipmentRepo.save(shipment);
        return shipment;
    }

    Shipment getShipment(String shipmentId) {
        return shipmentRepo.findById(shipmentId);
    }
}

class TrackingService {
    private final ShipmentRepository shipmentRepo;
    private final TrackingRepository trackingRepo;

    TrackingService(ShipmentRepository shipmentRepo, TrackingRepository trackingRepo) {
        this.shipmentRepo = shipmentRepo;
        this.trackingRepo = trackingRepo;
    }

    void updateStatus(String shipmentId, ShipmentStatus status, Location location) {
        Shipment shipment = shipmentRepo.findById(shipmentId);
        if (shipment == null) return;

        shipment.status = status;
        shipment.currentLocation = location;
        shipment.routeHistory.add(location);

        TrackingEvent event = new TrackingEvent(
                UUID.randomUUID().toString(),
                shipmentId,
                status,
                location
        );
        trackingRepo.save(event);
    }

    List<TrackingEvent> getTrackingHistory(String shipmentId) {
        return trackingRepo.findByShipmentId(shipmentId);
    }
}

/* =====================
   DEMO
   ===================== */

public class SupplyChainTrackingApp {
    public static void main(String[] args) {
        ShipmentRepository shipmentRepo = new ShipmentRepository();
        TrackingRepository trackingRepo = new TrackingRepository();

        RoutingStrategy routingStrategy = new HubBasedRoutingStrategy();
        ShipmentService shipmentService = new ShipmentService(shipmentRepo, routingStrategy);
        TrackingService trackingService = new TrackingService(shipmentRepo, trackingRepo);

        Location factory = new Location("LOC1", LocationType.FACTORY, "Pune");
        Location customer = new Location("LOC2", LocationType.CUSTOMER, "Mumbai");

        Shipment shipment = shipmentService.createShipment("ORDER123", factory, customer);

        trackingService.updateStatus(shipment.shipmentId, ShipmentStatus.PICKED_UP, factory);
        trackingService.updateStatus(shipment.shipmentId, ShipmentStatus.IN_TRANSIT,
                new Location("WH1", LocationType.WAREHOUSE, "Navi Mumbai"));
        trackingService.updateStatus(shipment.shipmentId, ShipmentStatus.DELIVERED, customer);

        System.out.println("Final Status: " + shipment.status);
        System.out.println("Tracking Events: " +
                trackingService.getTrackingHistory(shipment.shipmentId).size());
    }
}

