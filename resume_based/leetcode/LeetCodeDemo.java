package LLD2.resume_based.leetcode;

import java.util.*;

// =====================
// ENUMS
// =====================
enum SubmissionStatus {
    PENDING, RUNNING, SUCCESS, FAILED
}

enum Difficulty {
    EASY, MEDIUM, HARD
}

// =====================
// ENTITIES
// =====================
class User {
    String userId;
    String name;

    User(String userId, String name) {
        this.userId = userId;
        this.name = name;
    }
}

class Problem {
    String problemId;
    String title;
    Difficulty difficulty;
    String description;

    Problem(String problemId, String title, Difficulty difficulty, String description) {
        this.problemId = problemId;
        this.title = title;
        this.difficulty = difficulty;
        this.description = description;
    }
}

class Submission {
    String submissionId;
    User user;
    Problem problem;
    String code;
    SubmissionStatus status;
    Date submittedAt;

    Submission(String submissionId, User user, Problem problem, String code) {
        this.submissionId = submissionId;
        this.user = user;
        this.problem = problem;
        this.code = code;
        this.status = SubmissionStatus.PENDING;
        this.submittedAt = new Date();
    }
}

// =====================
// REPOSITORIES
// =====================
class UserRepository {
    Map<String, User> users = new HashMap<>();
    void addUser(User u) { users.put(u.userId, u); }
    User getUser(String id) { return users.get(id); }
}

class ProblemRepository {
    Map<String, Problem> problems = new HashMap<>();
    void addProblem(Problem p) { problems.put(p.problemId, p); }
    Problem getProblem(String id) { return problems.get(id); }
}

class SubmissionRepository {
    Map<String, Submission> submissions = new HashMap<>();
    void addSubmission(Submission s) { submissions.put(s.submissionId, s); }
    List<Submission> getByUser(String userId) {
        List<Submission> list = new ArrayList<>();
        for(Submission s : submissions.values()) if(s.user.userId.equals(userId)) list.add(s);
        return list;
    }
}

// =====================
// STRATEGY
// =====================
interface JudgingStrategy {
    SubmissionStatus judge(String code, Problem problem);
}

class DummyJudgeStrategy implements JudgingStrategy {
    public SubmissionStatus judge(String code, Problem problem) {
        // dummy logic: code containing "success" passes
        return code.contains("success") ? SubmissionStatus.SUCCESS : SubmissionStatus.FAILED;
    }
}

// =====================
// OBSERVER
// =====================
interface SubmissionObserver {
    void notify(String message);
}

class ConsoleNotifier implements SubmissionObserver {
    public void notify(String message) { System.out.println("Notification: " + message); }
}

// =====================
// SERVICE
// =====================
class CodingPlatformService {
    UserRepository userRepo;
    ProblemRepository problemRepo;
    SubmissionRepository submissionRepo;
    JudgingStrategy judgeStrategy;
    List<SubmissionObserver> observers = new ArrayList<>();

    CodingPlatformService(UserRepository ur, ProblemRepository pr, SubmissionRepository sr, JudgingStrategy js) {
        this.userRepo = ur;
        this.problemRepo = pr;
        this.submissionRepo = sr;
        this.judgeStrategy = js;
    }

    void registerObserver(SubmissionObserver obs) { observers.add(obs); }
    void notifyObservers(String msg) { for(SubmissionObserver o : observers) o.notify(msg); }

    Submission submit(String userId, String problemId, String code) {
        User user = userRepo.getUser(userId);
        Problem problem = problemRepo.getProblem(problemId);
        if(user == null || problem == null) {
            notifyObservers("Invalid user or problem");
            return null;
        }
        Submission sub = new Submission(UUID.randomUUID().toString(), user, problem, code);
        submissionRepo.addSubmission(sub);
        notifyObservers("Submission received for user " + user.name + " on problem " + problem.title);

        sub.status = judgeStrategy.judge(code, problem);
        notifyObservers("Submission status: " + sub.status);
        return sub;
    }
}

// =====================
// DEMO
// =====================
public class LeetCodeDemo {
    public static void main(String[] args) {
        UserRepository userRepo = new UserRepository();
        ProblemRepository problemRepo = new ProblemRepository();
        SubmissionRepository submissionRepo = new SubmissionRepository();

        User user1 = new User("U1", "Alice");
        userRepo.addUser(user1);

        Problem prob1 = new Problem("P1", "Two Sum", Difficulty.EASY, "Find two numbers that add up to target");
        problemRepo.addProblem(prob1);

        CodingPlatformService platformService = new CodingPlatformService(userRepo, problemRepo, submissionRepo, new DummyJudgeStrategy());
        platformService.registerObserver(new ConsoleNotifier());

        platformService.submit("U1", "P1", "some code success");
        platformService.submit("U1", "P1", "some code fail");
    }
}
