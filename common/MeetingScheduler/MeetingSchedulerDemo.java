package LLD2.common.MeetingScheduler;

import java.util.*;
import java.text.*;

// =====================
// ENUMS
// =====================
enum MeetingStatus {
    SCHEDULED, CANCELLED, COMPLETED
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

class Meeting {
    String meetingId;
    String title;
    User organizer;
    List<User> participants;
    Date startTime;
    Date endTime;
    MeetingStatus status;

    Meeting(String meetingId, String title, User organizer, List<User> participants, Date startTime, Date endTime) {
        this.meetingId = meetingId;
        this.title = title;
        this.organizer = organizer;
        this.participants = participants;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = MeetingStatus.SCHEDULED;
    }
}

// =====================
// REPOSITORIES
// =====================
class UserRepository {
    Map<String, User> users = new HashMap<>();
    void addUser(User u) { users.put(u.userId, u); }
    User findById(String id) { return users.get(id); }
}

class MeetingRepository {
    Map<String, Meeting> meetings = new HashMap<>();
    void addMeeting(Meeting m) { meetings.put(m.meetingId, m); }
    Meeting findById(String id) { return meetings.get(id); }
    List<Meeting> findByUser(String userId) {
        List<Meeting> result = new ArrayList<>();
        for(Meeting m : meetings.values()) {
            if(m.organizer.userId.equals(userId) || m.participants.stream().anyMatch(p -> p.userId.equals(userId))) {
                result.add(m);
            }
        }
        return result;
    }
}

// =====================
// STRATEGY PATTERN FOR CONFLICT CHECK
// =====================
interface ConflictStrategy {
    boolean hasConflict(User user, Date start, Date end, List<Meeting> meetings);
}

class SimpleConflictStrategy implements ConflictStrategy {
    public boolean hasConflict(User user, Date start, Date end, List<Meeting> meetings) {
        for(Meeting m : meetings) {
            if((m.organizer.userId.equals(user.userId) || m.participants.stream().anyMatch(p -> p.userId.equals(user.userId)))
                    && start.before(m.endTime) && end.after(m.startTime)) {
                return true;
            }
        }
        return false;
    }
}

// =====================
// OBSERVER PATTERN
// =====================
interface MeetingObserver {
    void notify(String message);
}

class ConsoleNotifier implements MeetingObserver {
    public void notify(String message) { System.out.println("Notification: " + message); }
}

// =====================
// SERVICE LAYER
// =====================
class MeetingService {
    UserRepository userRepo;
    MeetingRepository meetingRepo;
    ConflictStrategy conflictStrategy;
    List<MeetingObserver> observers = new ArrayList<>();

    MeetingService(UserRepository ur, MeetingRepository mr, ConflictStrategy cs) {
        this.userRepo = ur;
        this.meetingRepo = mr;
        this.conflictStrategy = cs;
    }

    void registerObserver(MeetingObserver obs) { observers.add(obs); }
    void notifyObservers(String msg) { for(MeetingObserver o : observers) o.notify(msg); }

    Meeting scheduleMeeting(String title, User organizer, List<User> participants, Date startTime, Date endTime) {
        // Conflict check
        List<Meeting> organizerMeetings = meetingRepo.findByUser(organizer.userId);
        if(conflictStrategy.hasConflict(organizer, startTime, endTime, organizerMeetings)) {
            notifyObservers("Organizer has a scheduling conflict");
            return null;
        }
        for(User u : participants) {
            if(conflictStrategy.hasConflict(u, startTime, endTime, meetingRepo.findByUser(u.userId))) {
                notifyObservers("Participant " + u.name + " has a scheduling conflict");
                return null;
            }
        }

        Meeting meeting = new Meeting(UUID.randomUUID().toString(), title, organizer, participants, startTime, endTime);
        meetingRepo.addMeeting(meeting);
        notifyObservers("Meeting scheduled: " + title + " by " + organizer.name);
        return meeting;
    }

    void cancelMeeting(Meeting meeting) {
        meeting.status = MeetingStatus.CANCELLED;
        notifyObservers("Meeting cancelled: " + meeting.title);
    }
}

// =====================
// DEMO
// =====================
public class MeetingSchedulerDemo {
    public static void main(String[] args) {
        UserRepository userRepo = new UserRepository();
        MeetingRepository meetingRepo = new MeetingRepository();

        User alice = new User("U1", "Alice");
        User bob = new User("U2", "Bob");
        userRepo.addUser(alice);
        userRepo.addUser(bob);

        MeetingService meetingService = new MeetingService(userRepo, meetingRepo, new SimpleConflictStrategy());
        meetingService.registerObserver(new ConsoleNotifier());

        Calendar cal = Calendar.getInstance();
        Date start = cal.getTime();
        cal.add(Calendar.HOUR, 1);
        Date end = cal.getTime();

        meetingService.scheduleMeeting("Team Sync", alice, List.of(bob), start, end);
    }
}
