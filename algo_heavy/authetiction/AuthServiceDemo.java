package LLD2.algo_heavy.authetiction;

import java.util.*;
import java.time.Instant;

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
    Set<LLD2.algo_heavy.jwt.Role> roles;

    User(String userId, String username, String password, Set<LLD2.algo_heavy.jwt.Role> roles) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.roles = roles;
    }
}

class Token {
    String tokenValue;
    Instant expiry;

    Token(String tokenValue, Instant expiry) {
        this.tokenValue = tokenValue;
        this.expiry = expiry;
    }
}

class Session {
    String sessionId;
    LLD2.algo_heavy.jwt.User user;
    Token token;

    Session(String sessionId, LLD2.algo_heavy.jwt.User user, Token token) {
        this.sessionId = sessionId;
        this.user = user;
        this.token = token;
    }
}

// =====================
// REPOSITORY
// =====================
class UserRepository {
    Map<String, LLD2.algo_heavy.jwt.User> users = new HashMap<>();

    void addUser(LLD2.algo_heavy.jwt.User user) {
        users.put(user.userId, user);
    }

    LLD2.algo_heavy.jwt.User findByUsername(String username) {
        return users.values().stream()
                .filter(u -> u.username.equals(username))
                .findFirst().orElse(null);
    }
}

class SessionRepository {
    Map<String, LLD2.algo_heavy.jwt.Session> sessions = new HashMap<>();

    void save(LLD2.algo_heavy.jwt.Session session) {
        sessions.put(session.sessionId, session);
    }

    LLD2.algo_heavy.jwt.Session findByToken(String tokenValue) {
        return sessions.values().stream()
                .filter(s -> s.token.tokenValue.equals(tokenValue))
                .findFirst().orElse(null);
    }
}

// =====================
// STRATEGY / AUTH POLICY
// =====================
interface AuthStrategy {
    LLD2.algo_heavy.jwt.AuthStatus authenticate(String username, String password, LLD2.algo_heavy.jwt.UserRepository userRepo);
}

class PasswordAuthStrategy implements LLD2.algo_heavy.jwt.AuthStrategy {
    @Override
    public LLD2.algo_heavy.jwt.AuthStatus authenticate(String username, String password, LLD2.algo_heavy.jwt.UserRepository userRepo) {
        LLD2.algo_heavy.jwt.User user = userRepo.findByUsername(username);
        if (user != null && user.password.equals(password)) return LLD2.algo_heavy.jwt.AuthStatus.SUCCESS;
        return LLD2.algo_heavy.jwt.AuthStatus.FAILURE;
    }
}

// OAuth / SSO strategies can be added by implementing AuthStrategy

// =====================
// SERVICE
// =====================
class TokenService {
    Token generateToken(LLD2.algo_heavy.jwt.User user, long durationSeconds) {
        String tokenValue = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plusSeconds(durationSeconds);
        return new Token(tokenValue, expiry);
    }

    boolean validateToken(Token token) {
        return Instant.now().isBefore(token.expiry);
    }
}

class AuthService {
    private final LLD2.algo_heavy.jwt.UserRepository userRepo;
    private final LLD2.algo_heavy.jwt.SessionRepository sessionRepo;
    private final LLD2.algo_heavy.jwt.AuthStrategy authStrategy;
    private final TokenService tokenService;

    AuthService(LLD2.algo_heavy.jwt.UserRepository userRepo, LLD2.algo_heavy.jwt.SessionRepository sessionRepo,
                LLD2.algo_heavy.jwt.AuthStrategy authStrategy, TokenService tokenService) {
        this.userRepo = userRepo;
        this.sessionRepo = sessionRepo;
        this.authStrategy = authStrategy;
        this.tokenService = tokenService;
    }

    Token login(String username, String password) {
        if (authStrategy.authenticate(username, password, userRepo) == LLD2.algo_heavy.jwt.AuthStatus.SUCCESS) {
            LLD2.algo_heavy.jwt.User user = userRepo.findByUsername(username);
            Token token = tokenService.generateToken(user, 3600); // 1 hour token
            LLD2.algo_heavy.jwt.Session session = new LLD2.algo_heavy.jwt.Session(UUID.randomUUID().toString(), user, token);
            sessionRepo.save(session);
            return token;
        }
        return null;
    }

    LLD2.algo_heavy.jwt.AuthStatus validateToken(String tokenValue) {
        LLD2.algo_heavy.jwt.Session session = sessionRepo.findByToken(tokenValue);
        if (session == null) return LLD2.algo_heavy.jwt.AuthStatus.FAILURE;
        return tokenService.validateToken(session.token) ? LLD2.algo_heavy.jwt.AuthStatus.SUCCESS : LLD2.algo_heavy.jwt.AuthStatus.EXPIRED;
    }

    boolean hasRole(String tokenValue, LLD2.algo_heavy.jwt.Role role) {
        LLD2.algo_heavy.jwt.Session session = sessionRepo.findByToken(tokenValue);
        if (session == null || !tokenService.validateToken(session.token)) return false;
        return session.user.roles.contains(role);
    }
}

// =====================
// DEMO
// =====================
public class AuthServiceDemo {
    public static void main(String[] args) {
        LLD2.algo_heavy.jwt.UserRepository userRepo = new LLD2.algo_heavy.jwt.UserRepository();
        LLD2.algo_heavy.jwt.SessionRepository sessionRepo = new LLD2.algo_heavy.jwt.SessionRepository();
        TokenService tokenService = new TokenService();
        LLD2.algo_heavy.jwt.AuthStrategy passwordAuth = new LLD2.algo_heavy.jwt.PasswordAuthStrategy();

        userRepo.addUser(new LLD2.algo_heavy.jwt.User("U1", "alice", "password123", Set.of(LLD2.algo_heavy.jwt.Role.ADMIN, LLD2.algo_heavy.jwt.Role.USER)));
        userRepo.addUser(new LLD2.algo_heavy.jwt.User("U2", "bob", "pass456", Set.of(LLD2.algo_heavy.jwt.Role.USER)));

        LLD2.algo_heavy.jwt.AuthService authService = new LLD2.algo_heavy.jwt.AuthService(userRepo, sessionRepo, passwordAuth, tokenService);

        Token token = authService.login("alice", "password123");
        System.out.println("Token: " + (token != null ? token.tokenValue : "Login failed"));

        System.out.println("Validate token: " + authService.validateToken(token.tokenValue));
        System.out.println("Has ADMIN role: " + authService.hasRole(token.tokenValue, LLD2.algo_heavy.jwt.Role.ADMIN));
        System.out.println("Has GUEST role: " + authService.hasRole(token.tokenValue, LLD2.algo_heavy.jwt.Role.GUEST));
    }
}
