package LLD2.resume.ErrorHandling;

import java.util.*;

/* =====================
   ENUMS
   ===================== */
enum ErrorCode {
    NOT_FOUND,
    BAD_REQUEST,
    UNAUTHORIZED,
    INTERNAL_SERVER_ERROR
}

/* =====================
   ENTITIES
   ===================== */
class ApiError {
    String errorId;
    ErrorCode code;
    String message;
    Date timestamp;
    String component;

    ApiError(ErrorCode code, String message, String component) {
        this.errorId = UUID.randomUUID().toString();
        this.code = code;
        this.message = message;
        this.component = component;
        this.timestamp = new Date();
    }

    @Override
    public String toString() {
        return "[" + code + "] " + component + " - " + message;
    }
}

/* =====================
   REPOSITORY (for audit)
   ===================== */
class ErrorRepository {
    List<ApiError> errorLogs = new ArrayList<>();

    void save(ApiError error) {
        errorLogs.add(error);
    }

    List<ApiError> findAll() {
        return errorLogs;
    }
}

/* =====================
   HANDLER INTERFACE
   ===================== */
interface ExceptionHandler {
    boolean canHandle(Exception ex);
    void handle(Exception ex, String component);
}

/* =====================
   CONCRETE HANDLERS
   ===================== */
class NotFoundExceptionHandler implements ExceptionHandler {
    ErrorRepository repo;

    NotFoundExceptionHandler(ErrorRepository repo) {
        this.repo = repo;
    }

    public boolean canHandle(Exception ex) {
        return ex instanceof NoSuchElementException;
    }

    public void handle(Exception ex, String component) {
        ApiError error = new ApiError(ErrorCode.NOT_FOUND, ex.getMessage(), component);
        repo.save(error);
        System.out.println(error);
    }
}

class BadRequestExceptionHandler implements ExceptionHandler {
    ErrorRepository repo;

    BadRequestExceptionHandler(ErrorRepository repo) {
        this.repo = repo;
    }

    public boolean canHandle(Exception ex) {
        return ex instanceof IllegalArgumentException;
    }

    public void handle(Exception ex, String component) {
        ApiError error = new ApiError(ErrorCode.BAD_REQUEST, ex.getMessage(), component);
        repo.save(error);
        System.out.println(error);
    }
}

class InternalServerExceptionHandler implements ExceptionHandler {
    ErrorRepository repo;

    InternalServerExceptionHandler(ErrorRepository repo) {
        this.repo = repo;
    }

    public boolean canHandle(Exception ex) {
        return true; // fallback handler
    }

    public void handle(Exception ex, String component) {
        ApiError error = new ApiError(ErrorCode.INTERNAL_SERVER_ERROR, ex.getMessage(), component);
        repo.save(error);
        System.out.println(error);
    }
}

/* =====================
   GLOBAL HANDLER
   ===================== */
class GlobalExceptionHandler {
    List<ExceptionHandler> handlers = new ArrayList<>();

    void addHandler(ExceptionHandler handler) {
        handlers.add(handler);
    }

    void handle(Exception ex, String component) {
        for (ExceptionHandler handler : handlers) {
            if (handler.canHandle(ex)) {
                handler.handle(ex, component);
                return;
            }
        }
    }
}

/* =====================
   DEMO SERVICE
   ===================== */
class UserService {
    GlobalExceptionHandler globalHandler;

    UserService(GlobalExceptionHandler globalHandler) {
        this.globalHandler = globalHandler;
    }

    void getUser(String userId) {
        try {
            if (userId == null) throw new IllegalArgumentException("UserId cannot be null");
            if (userId.equals("404")) throw new NoSuchElementException("User not found");
            System.out.println("User fetched: " + userId);
        } catch (Exception ex) {
            globalHandler.handle(ex, "UserService");
        }
    }
}

/* =====================
   DEMO
   ===================== */
public class GlobalExceptionDemo {
    public static void main(String[] args) {
        ErrorRepository repo = new ErrorRepository();

        GlobalExceptionHandler globalHandler = new GlobalExceptionHandler();
        globalHandler.addHandler(new NotFoundExceptionHandler(repo));
        globalHandler.addHandler(new BadRequestExceptionHandler(repo));
        globalHandler.addHandler(new InternalServerExceptionHandler(repo));

        UserService userService = new UserService(globalHandler);

        userService.getUser(null); // BadRequest
        userService.getUser("404"); // NotFound
        userService.getUser("user123"); // Success

        System.out.println("All logged errors: " + repo.findAll().size());
    }
}

