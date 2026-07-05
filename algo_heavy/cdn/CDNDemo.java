package LLD2.algo_heavy.cdn;

import java.util.*;

// =====================
// ENUMS
// =====================
enum CacheStatus {
    CACHED,
    MISS
}

enum OriginType {
    S3,
    HTTP_SERVER,
    API_SERVER
}

// =====================
// ENTITIES
// =====================
class Request {
    String path;
    String clientId;

    Request(String path, String clientId) {
        this.path = path;
        this.clientId = clientId;
    }
}

class Response {
    int statusCode;
    String body;

    Response(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    @Override
    public String toString() {
        return "Status: " + statusCode + ", Body: " + body;
    }
}

class EdgeServer {
    String serverId;
    Map<String, Response> cache = new LinkedHashMap<>();
    int capacity;

    EdgeServer(String serverId, int capacity) {
        this.serverId = serverId;
        this.capacity = capacity;
    }

    Response getFromCache(String path) {
        return cache.get(path);
    }

    void putInCache(String path, Response res) {
        if (cache.size() >= capacity) {
            // simple LRU eviction
            String firstKey = cache.keySet().iterator().next();
            cache.remove(firstKey);
        }
        cache.put(path, res);
    }
}

class OriginServer {
    String originId;
    String url;
    OriginType type;

    OriginServer(String originId, String url, OriginType type) {
        this.originId = originId;
        this.url = url;
        this.type = type;
    }

    Response fetch(Request req) {
        // simulate fetching content
        return new Response(200, "Content from " + originId + " for " + req.path);
    }
}

// =====================
// REPOSITORY
// =====================
class ServerRegistry {
    private final List<EdgeServer> edgeServers = new ArrayList<>();
    private final List<OriginServer> originServers = new ArrayList<>();

    void registerEdge(EdgeServer edge) {
        edgeServers.add(edge);
    }

    void registerOrigin(OriginServer origin) {
        originServers.add(origin);
    }

    List<EdgeServer> getEdgeServers() {
        return edgeServers;
    }

    List<OriginServer> getOriginServers() {
        return originServers;
    }
}

// =====================
// STRATEGY
// =====================
abstract class CachingStrategy {
    abstract Response fetch(Request req, EdgeServer edge, List<OriginServer> origins);
}

class LRUCacheStrategy extends CachingStrategy {
    @Override
    Response fetch(Request req, EdgeServer edge, List<OriginServer> origins) {
        Response res = edge.getFromCache(req.path);
        if (res != null) {
            System.out.println("Cache HIT at edge: " + edge.serverId);
            return res;
        }
        System.out.println("Cache MISS at edge: " + edge.serverId + ", fetching from origin...");
        Response originRes = origins.get(0).fetch(req); // simple: pick first origin
        edge.putInCache(req.path, originRes);
        return originRes;
    }
}

class TTLBasedStrategy extends CachingStrategy {
    private final Map<String, Long> ttlMap = new HashMap<>();
    private final long ttlMillis;

    TTLBasedStrategy(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    @Override
    Response fetch(Request req, EdgeServer edge, List<OriginServer> origins) {
        Response res = edge.getFromCache(req.path);
        long now = System.currentTimeMillis();
        Long expiry = ttlMap.get(req.path);
        if (res != null && expiry != null && expiry > now) {
            System.out.println("Cache HIT at edge: " + edge.serverId);
            return res;
        }
        System.out.println("Cache MISS or expired at edge: " + edge.serverId + ", fetching from origin...");
        Response originRes = origins.get(0).fetch(req);
        edge.putInCache(req.path, originRes);
        ttlMap.put(req.path, now + ttlMillis);
        return originRes;
    }
}

// =====================
// SERVICE
// =====================
class CDNService {
    private final ServerRegistry registry;
    private final CachingStrategy strategy;

    CDNService(ServerRegistry registry, CachingStrategy strategy) {
        this.registry = registry;
        this.strategy = strategy;
    }

    Response serveRequest(Request req) {
        List<EdgeServer> edges = registry.getEdgeServers();
        if (edges.isEmpty()) return new Response(500, "No edge servers available");
        List<OriginServer> origins = registry.getOriginServers();
        if (origins.isEmpty()) return new Response(500, "No origin servers available");

        // simple: pick first edge for demo
        EdgeServer edge = edges.get(0);
        return strategy.fetch(req, edge, origins);
    }
}

// =====================
// DEMO
// =====================
public class CDNDemo {
    public static void main(String[] args) {
        ServerRegistry registry = new ServerRegistry();
        registry.registerEdge(new EdgeServer("Edge1", 2));
        registry.registerOrigin(new OriginServer("Origin1", "http://origin1.com", OriginType.S3));

        CDNService cdn = new CDNService(registry, new LRUCacheStrategy());

        Request req1 = new Request("/video.mp4", "client1");
        Request req2 = new Request("/video.mp4", "client2");
        Request req3 = new Request("/image.png", "client3");

        System.out.println(cdn.serveRequest(req1));
        System.out.println(cdn.serveRequest(req2));
        System.out.println(cdn.serveRequest(req3));
        System.out.println(cdn.serveRequest(req1)); // should hit cache
    }
}
