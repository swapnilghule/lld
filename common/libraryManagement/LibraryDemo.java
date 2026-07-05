package LLD2.common.libraryManagement;

import java.util.*;
import java.text.*;

// =====================
// ENUMS
// =====================
enum BookStatus {
    AVAILABLE, BORROWED, RESERVED
}

// =====================
// ENTITIES
// =====================
class Book {
    String bookId;
    String title;
    String author;
    BookStatus status;

    Book(String bookId, String title, String author) {
        this.bookId = bookId;
        this.title = title;
        this.author = author;
        this.status = BookStatus.AVAILABLE;
    }
}

class Member {
    String memberId;
    String name;

    Member(String memberId, String name) {
        this.memberId = memberId;
        this.name = name;
    }
}

class Loan {
    String loanId;
    Book book;
    Member member;
    Date issueDate;
    Date dueDate;
    Date returnDate;
    double fine;

    Loan(String loanId, Book book, Member member, Date issueDate, Date dueDate) {
        this.loanId = loanId;
        this.book = book;
        this.member = member;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.fine = 0;
    }
}

// =====================
// REPOSITORIES
// =====================
class BookRepository {
    Map<String, Book> books = new HashMap<>();
    void addBook(Book b) { books.put(b.bookId, b); }
    Book findAvailable() {
        return books.values().stream().filter(b -> b.status == BookStatus.AVAILABLE).findFirst().orElse(null);
    }
    Book findById(String bookId) { return books.get(bookId); }
}

class MemberRepository {
    Map<String, Member> members = new HashMap<>();
    void addMember(Member m) { members.put(m.memberId, m); }
    Member findById(String id) { return members.get(id); }
}

class LoanRepository {
    Map<String, Loan> loans = new HashMap<>();
    void addLoan(Loan l) { loans.put(l.loanId, l); }
    Loan findActiveLoanByBook(String bookId) {
        return loans.values().stream()
                .filter(l -> l.book.bookId.equals(bookId) && l.returnDate == null)
                .findFirst().orElse(null);
    }
}

// =====================
// STRATEGY PATTERN FOR FINE
// =====================
interface FineCalculationStrategy {
    double calculateFine(Loan loan);
}

class DailyFineStrategy implements FineCalculationStrategy {
    double ratePerDay;
    DailyFineStrategy(double rate) { this.ratePerDay = rate; }
    public double calculateFine(Loan loan) {
        if(loan.returnDate == null) return 0;
        long diff = loan.returnDate.getTime() - loan.dueDate.getTime();
        long days = Math.max(0, diff / (1000*60*60*24));
        return days * ratePerDay;
    }
}

// =====================
// OBSERVER PATTERN
// =====================
interface LibraryObserver {
    void notify(String message);
}

class ConsoleNotifier implements LibraryObserver {
    public void notify(String message) { System.out.println("Notification: " + message); }
}

// =====================
// SERVICE LAYER
// =====================
class LibraryService {
    BookRepository bookRepo;
    MemberRepository memberRepo;
    LoanRepository loanRepo;
    FineCalculationStrategy fineStrategy;
    List<LibraryObserver> observers = new ArrayList<>();

    LibraryService(BookRepository br, MemberRepository mr, LoanRepository lr, FineCalculationStrategy fs) {
        this.bookRepo = br;
        this.memberRepo = mr;
        this.loanRepo = lr;
        this.fineStrategy = fs;
    }

    void registerObserver(LibraryObserver obs) { observers.add(obs); }
    void notifyObservers(String msg) { for(LibraryObserver o : observers) o.notify(msg); }

    Loan issueBook(String bookId, String memberId, int days) {
        Book book = bookRepo.findById(bookId);
        Member member = memberRepo.findById(memberId);
        if(book == null || book.status != BookStatus.AVAILABLE) {
            notifyObservers("Book not available: " + bookId);
            return null;
        }
        book.status = BookStatus.BORROWED;
        Date issueDate = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(issueDate);
        cal.add(Calendar.DATE, days);
        Date dueDate = cal.getTime();

        Loan loan = new Loan(UUID.randomUUID().toString(), book, member, issueDate, dueDate);
        loanRepo.addLoan(loan);
        notifyObservers("Book issued: " + book.title + " to member: " + member.name);
        return loan;
    }

    void returnBook(String bookId) {
        Loan loan = loanRepo.findActiveLoanByBook(bookId);
        if(loan == null) return;
        loan.returnDate = new Date();
        loan.fine = fineStrategy.calculateFine(loan);
        loan.book.status = BookStatus.AVAILABLE;
        notifyObservers("Book returned: " + loan.book.title + ", Fine: " + loan.fine);
    }
}

// =====================
// DEMO
// =====================
public class LibraryDemo {
    public static void main(String[] args) {
        BookRepository bookRepo = new BookRepository();
        MemberRepository memberRepo = new MemberRepository();
        LoanRepository loanRepo = new LoanRepository();

        Book b1 = new Book("B1", "Effective Java", "Joshua Bloch");
        bookRepo.addBook(b1);
        Member m1 = new Member("M1", "Alice");
        memberRepo.addMember(m1);

        LibraryService library = new LibraryService(bookRepo, memberRepo, loanRepo, new DailyFineStrategy(5));
        library.registerObserver(new ConsoleNotifier());

        Loan loan = library.issueBook("B1", "M1", 7);

        try { Thread.sleep(2000); } catch(Exception e){}

        library.returnBook("B1");
    }
}
