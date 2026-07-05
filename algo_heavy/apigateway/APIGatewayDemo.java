package LLD2.algo_heavy.apigateway;

import java.util.*;

// =====================
// ENUMS
// =====================
enum HttpMethod {
    GET, POST, PUT, DELETE
}

enum RoutingStrategyType {
    ROUND_ROBIN, LEAST_CONNECTION, RANDOM
}

// =====================
// ENTITIES
// =====================
class Service {
    String serviceName;
    String url;
    int activeConnections; // for least connection

    Service(String serviceName, String url) {
        this.serviceName = serviceName;
        this.url = url;
        this.activeConnections = 0;
    }

    @Override
    public String toString() {
        return serviceName + "(" + url + ")";
    }
}

class Request {
    String path;
    HttpMethod method;
    Map<String, String> headers = new HashMap<>();
    String body;

    Request(String path, HttpMethod method) {
        this.path = path;
        this.method = method;
    }
}

class Response {
    int statusCode;
    String body;
    Map<String, String> headers = new HashMap<>();
}

// =====================
// REPOSITORY
// =====================
class ServiceRegistry {
    private final Map<String, Service> services = new HashMap<>();

    void registerService(Service service) {
        services.put(service.serviceName, service);
    }

    List<Service> getAllServices() {
        return new ArrayList<>(services.values());
    }

    Service getService(String name) {
        return services.get(name);
    }
}

// =====================
// STRATEGY
// =====================
abstract class RoutingStrategy {
    abstract Service selectService(Request req, List<Service> services);
}

class RoundRobinStrategy extends LLD2.algo_heavy.loadBalancer.RoutingStrategy {
    private final Map<String, Integer> counter = new HashMap<>();

    @Override
    Service selectService(Request req, List<Service> services) {
        if (services.isEmpty()) return null;
        int index = counter.getOrDefault(req.path, 0);
        Service service = services.get(index % services.size());
        counter.put(req.path, index + 1);
        return service;
    }
}

class LeastConnectionStrategy extends LLD2.algo_heavy.loadBalancer.RoutingStrategy {
    @Override
    Service selectService(Request req, List<Service> services) {
        return services.stream().min(Comparator.comparingInt(s -> s.activeConnections)).orElse(null);
    }
}

// =====================
// MIDDLEWARE
// =====================
abstract class Middleware {
    abstract void handle(Request req, Response res);
}

class AuthMiddleware extends Middleware {
    @Override
    void handle(Request req, Response res) {
        // simple auth simulation
        if (!req.headers.containsKey("Authorization")) {
            res.statusCode = 401;
            res.body = "Unauthorized";
        }
    }
}

class LoggingMiddleware extends Middleware {
    @Override
    void handle(Request req, Response res) {
        System.out.println("Logging request: " + req.path + " Method: " + req.method);
    }
}

class RateLimitMiddleware extends Middleware {
    private final Map<String, Integer> counter = new HashMap<>();
    private final int limit = 2;

    @Override
    void handle(Request req, Response res) {
        int count = counter.getOrDefault(req.path, 0);
        if (count >= limit) {
            res.statusCode = 429;
            res.body = "Too Many Requests";
        } else {
            counter.put(req.path, count + 1);
        }
    }
}

// =====================
// SERVICE
// =====================
class APIGateway {
    ServiceRegistry serviceRegistry;
    List<Middleware> middlewares = new ArrayList<>();
    LLD2.algo_heavy.loadBalancer.RoutingStrategy routingStrategy;

    APIGateway(ServiceRegistry registry, LLD2.algo_heavy.loadBalancer.RoutingStrategy strategy) {
        this.serviceRegistry = registry;
        this.routingStrategy = strategy;
    }

    void addMiddleware(Middleware middleware) {
        middlewares.add(middleware);
    }

    Response route(Request req) {
        Response res = new Response();

        // apply middlewares
        for (Middleware m : middlewares) {
            m.handle(req, res);
            if (res.statusCode != 0) return res; // stop if middleware sets response
        }

        // select service
        Service service = routingStrategy.selectService(req, serviceRegistry.getAllServices());
        if (service == null) {
            res.statusCode = 404;
            res.body = "Service Not Found";
            return res;
        }

        // simulate forwarding request
        service.activeConnections++;
        res.statusCode = 200;
        res.body = "Forwarded to service: " + service.serviceName;
        service.activeConnections--;
        return res;
    }
}

// =====================
// MANAGER
// =====================
class APIGatewayManager {
    APIGateway apiGateway;

    APIGatewayManager(APIGateway gateway) {
        this.apiGateway = gateway;
    }

    Response processRequest(Request req) {
        return apiGateway.route(req);
    }
}

// =====================
// DEMO
// =====================
public class APIGatewayDemo {
    public static void main(String[] args) {
        ServiceRegistry registry = new ServiceRegistry();
        registry.registerService(new Service("UserService", "http://localhost:8081"));
        registry.registerService(new Service("OrderService", "http://localhost:8082"));

        APIGateway gateway = new APIGateway(registry, new LLD2.algo_heavy.loadBalancer.RoundRobinStrategy());
        gateway.addMiddleware(new LoggingMiddleware());
        gateway.addMiddleware(new AuthMiddleware());
        gateway.addMiddleware(new RateLimitMiddleware());

        APIGatewayManager manager = new APIGatewayManager(gateway);

        Request req1 = new Request("/users", HttpMethod.GET);
        req1.headers.put("Authorization", "token123");

        Request req2 = new Request("/orders", HttpMethod.GET); // missing auth header

        Response r1 = manager.processRequest(req1);
        Response r2 = manager.processRequest(req2);

        System.out.println(r1.statusCode + " : " + r1.body);
        System.out.println(r2.statusCode + " : " + r2.body);
    }
}
