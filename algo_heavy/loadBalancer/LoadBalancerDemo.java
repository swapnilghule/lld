package LLD2.algo_heavy.loadBalancer;

import java.util.*;

// =====================
// ENUMS
// =====================
enum RoutingStrategyType {
    ROUND_ROBIN,
    LEAST_CONNECTION,
    RANDOM
}

enum ServerStatus {
    ACTIVE,
    INACTIVE
}

// =====================
// ENTITIES
// =====================
class Server {
    String serverId;
    String url;
    int activeConnections;
    ServerStatus status;

    Server(String serverId, String url) {
        this.serverId = serverId;
        this.url = url;
        this.activeConnections = 0;
        this.status = ServerStatus.ACTIVE;
    }

    @Override
    public String toString() {
        return serverId + "(" + url + ")";
    }
}

class LBRequest {
    String path;
    String clientId;

    LBRequest(String path, String clientId) {
        this.path = path;
        this.clientId = clientId;
    }
}

// =====================
// REPOSITORY
// =====================
class ServerRegistry {
    private final Map<String, Server> servers = new HashMap<>();

    void registerServer(Server server) {
        servers.put(server.serverId, server);
    }

    List<Server> getAllActiveServers() {
        List<Server> active = new ArrayList<>();
        for (Server s : servers.values()) {
            if (s.status == ServerStatus.ACTIVE) active.add(s);
        }
        return active;
    }
}

// =====================
// STRATEGY
// =====================
abstract class RoutingStrategy {
    abstract Server selectServer(LBRequest request, List<Server> servers);
}

class RoundRobinStrategy extends RoutingStrategy {
    private final Map<String, Integer> counter = new HashMap<>();

    @Override
    Server selectServer(LBRequest request, List<Server> servers) {
        if (servers.isEmpty()) return null;
        int index = counter.getOrDefault(request.path, 0);
        Server server = servers.get(index % servers.size());
        counter.put(request.path, index + 1);
        return server;
    }
}

class LeastConnectionStrategy extends RoutingStrategy {
    @Override
    Server selectServer(LBRequest request, List<Server> servers) {
        return servers.stream().min(Comparator.comparingInt(s -> s.activeConnections)).orElse(null);
    }
}

class RandomStrategy extends RoutingStrategy {
    private final Random random = new Random();

    @Override
    Server selectServer(LBRequest request, List<Server> servers) {
        if (servers.isEmpty()) return null;
        return servers.get(random.nextInt(servers.size()));
    }
}

// =====================
// SERVICE
// =====================
class LoadBalancer {
    private final ServerRegistry registry;
    private final RoutingStrategy strategy;

    LoadBalancer(ServerRegistry registry, RoutingStrategy strategy) {
        this.registry = registry;
        this.strategy = strategy;
    }

    Server route(LBRequest request) {
        List<Server> servers = registry.getAllActiveServers();
        Server selected = strategy.selectServer(request, servers);
        if (selected != null) selected.activeConnections++;
        return selected;
    }
}

// =====================
// DEMO
// =====================
public class LoadBalancerDemo {
    public static void main(String[] args) {
        ServerRegistry registry = new ServerRegistry();
        registry.registerServer(new Server("S1", "http://10.0.0.1"));
        registry.registerServer(new Server("S2", "http://10.0.0.2"));
        registry.registerServer(new Server("S3", "http://10.0.0.3"));

        LoadBalancer lb = new LoadBalancer(registry, new RoundRobinStrategy());

        LBRequest req1 = new LBRequest("/api/users", "client1");
        LBRequest req2 = new LBRequest("/api/orders", "client2");
        LBRequest req3 = new LBRequest("/api/users", "client3");

        System.out.println("Request 1 routed to: " + lb.route(req1));
        System.out.println("Request 2 routed to: " + lb.route(req2));
        System.out.println("Request 3 routed to: " + lb.route(req3));
    }
}

