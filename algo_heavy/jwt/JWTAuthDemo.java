package LLD2.algo_heavy.jwt;

import java.util.*;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// =====================
// ENUMS
// =====================
enum AuthStatus {
    SUCCESS,
    FAILURE,
    EXPIRED
}

enum Role {
    ADMIN,
    USER,
    GUEST
}

// =====================
// ENTITIES
// =====================
class User {
    String userId;
    String username;
    String password;
    Set<Role> roles;

    User(String userId, String username, String password, Set<Role> roles) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.roles = roles;
    }
}

class Session {
    String sessionId;
    User user;
    String jwtToken;
    Instant expiry;

    Session(String sessionId, User user, String jwtToken, Instant expiry) {
        this.sessionId = sessionId;
        this.user = user;
        this.jwtToken = jwtToken;
        this.expiry = expiry;
    }
}

// =====================
// REPOSITORY
// =====================
class UserRepository {
    Map<String, User> users = new HashMap<>();

    void addUser(User user) {
        users.put(user.userId, user);
    }

    User findByUsername(String username) {
        return users.values().stream()
                .filter(u -> u.username.equals(username))
                .findFirst().orElse(null);
    }
}

class SessionRepository {
    Map<String, Session> sessions = new HashMap<>();

    void save(Session session) {
        sessions.put(session.sessionId, session);
    }

    Session findByToken(String jwtToken) {
        return sessions.values().stream()
                .filter(s -> s.jwtToken.equals(jwtToken))
                .findFirst().orElse(null);
    }
}

// =====================
// STRATEGY
// =====================
interface AuthStrategy {
    AuthStatus authenticate(String username, String password, UserRepository userRepo);
}

class PasswordAuthStrategy implements AuthStrategy {
    @Override
    public AuthStatus authenticate(String username, String password, UserRepository userRepo) {
        User user = userRepo.findByUsername(username);
        if (user != null && user.password.equals(password)) return AuthStatus.SUCCESS;
        return AuthStatus.FAILURE;
    }
}

// =====================
// SERVICE
// =====================
class JWTService {
    private static final String SECRET = "my-secret-key";

    String generateJWT(User user, long durationSeconds) {
        try {
            String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
            long exp = Instant.now().getEpochSecond() + durationSeconds;
            String payloadStr = String.format("{\"sub\":\"%s\",\"exp\":%d}", user.userId, exp);
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadStr.getBytes());
            String signature = hmacSha256(header + "." + payload, SECRET);
            return header + "." + payload + "." + signature;
        } catch (Exception e) {
            return null;
        }
    }

    boolean validateJWT(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return false;
            String header = parts[0];
            String payload = parts[1];
            String signature = parts[2];

            String expectedSig = hmacSha256(header + "." + payload, SECRET);
            if (!expectedSig.equals(signature)) return false;

            String payloadDecoded = new String(Base64.getUrlDecoder().decode(payload));
            long expIndex = payloadDecoded.indexOf("\"exp\":") + 6;
            long exp = Long.parseLong(payloadDecoded.substring((int) expIndex, payloadDecoded.indexOf("}", (int) expIndex)));
            return Instant.now().getEpochSecond() < exp;
        } catch (Exception e) {
            return false;
        }
    }

    private String hmacSha256(String data, String secret) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        sha256_HMAC.init(secretKey);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256_HMAC.doFinal(data.getBytes()));
    }
}

class AuthService {
    private final UserRepository userRepo;
    private final SessionRepository sessionRepo;
    private final AuthStrategy authStrategy;
    private final JWTService jwtService;

    AuthService(UserRepository userRepo, SessionRepository sessionRepo, AuthStrategy authStrategy, JWTService jwtService) {
        this.userRepo = userRepo;
        this.sessionRepo = sessionRepo;
        this.authStrategy = authStrategy;
        this.jwtService = jwtService;
    }

    String login(String username, String password) {
        if (authStrategy.authenticate(username, password, userRepo) == AuthStatus.SUCCESS) {
            User user = userRepo.findByUsername(username);
            String jwt = jwtService.generateJWT(user, 3600);
            Session session = new Session(UUID.randomUUID().toString(), user, jwt, Instant.now().plusSeconds(3600));
            sessionRepo.save(session);
            return jwt;
        }
        return null;
    }

    AuthStatus validateToken(String jwtToken) {
        Session session = sessionRepo.findByToken(jwtToken);
        if (session == null) return AuthStatus.FAILURE;
        return jwtService.validateJWT(jwtToken) ? AuthStatus.SUCCESS : AuthStatus.EXPIRED;
    }
}

// =====================
// DEMO
// =====================
public class JWTAuthDemo {
    public static void main(String[] args) {
        UserRepository userRepo = new UserRepository();
        SessionRepository sessionRepo = new SessionRepository();
        JWTService jwtService = new JWTService();
        AuthStrategy passwordAuth = new PasswordAuthStrategy();

        userRepo.addUser(new User("U1", "alice", "password123", Set.of(Role.ADMIN, Role.USER)));

        AuthService authService = new AuthService(userRepo, sessionRepo, passwordAuth, jwtService);

        String token = authService.login("alice", "password123");
        System.out.println("JWT Token: " + token);

        System.out.println("Token validation: " + authService.validateToken(token));
    }
}
