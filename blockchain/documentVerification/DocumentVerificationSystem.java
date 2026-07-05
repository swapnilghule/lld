package LLD2.blockchain.documentVerification;

import java.util.*;

/**
 * Document Verification System
 */
public class DocumentVerificationSystem {

    /* =====================
       ENUMS
    ===================== */
    enum DocumentType {
        AADHAR,
        PAN,
        PASSPORT
    }

    enum VerificationStatus {
        SUBMITTED,
        UNDER_REVIEW,
        VERIFIED,
        REJECTED
    }

    enum RejectionReason {
        BLURRY,
        INVALID,
        EXPIRED,
        MISMATCH
    }

    /* =====================
       ENTITY
    ===================== */
    static class Document {
        String documentId;
        String userId;
        DocumentType type;
        String documentNumber;
        VerificationStatus status;
        RejectionReason rejectionReason;

        Document(String documentId, String userId,
                 DocumentType type, String documentNumber) {
            this.documentId = documentId;
            this.userId = userId;
            this.type = type;
            this.documentNumber = documentNumber;
            this.status = VerificationStatus.SUBMITTED;
        }
    }

    /* =====================
       REPOSITORY
    ===================== */
    interface DocumentRepository {
        void save(Document document);
        Document findById(String documentId);
        List<Document> findByUser(String userId);
    }

    static class InMemoryDocumentRepository implements DocumentRepository {
        private final Map<String, Document> store = new HashMap<>();

        public void save(Document document) {
            store.put(document.documentId, document);
        }

        public Document findById(String documentId) {
            return store.get(documentId);
        }

        public List<Document> findByUser(String userId) {
            List<Document> result = new ArrayList<>();
            for (Document doc : store.values()) {
                if (doc.userId.equals(userId)) {
                    result.add(doc);
                }
            }
            return result;
        }
    }

    /* =====================
       VERIFICATION STRATEGY
    ===================== */
    interface VerificationStrategy {
        boolean verify(Document document);
    }

    static class AadharVerificationStrategy implements VerificationStrategy {
        public boolean verify(Document document) {
            return document.documentNumber.length() == 12;
        }
    }

    static class PanVerificationStrategy implements VerificationStrategy {
        public boolean verify(Document document) {
            return document.documentNumber.matches("[A-Z]{5}[0-9]{4}[A-Z]");
        }
    }

    static class PassportVerificationStrategy implements VerificationStrategy {
        public boolean verify(Document document) {
            return document.documentNumber.length() >= 6;
        }
    }

    /* =====================
       STATE (LIFECYCLE)
    ===================== */
    interface DocumentState {
        void process(Document document);
    }

    static class SubmittedState implements DocumentState {
        public void process(Document document) {
            document.status = VerificationStatus.UNDER_REVIEW;
        }
    }

    static class VerifiedState implements DocumentState {
        public void process(Document document) {
            document.status = VerificationStatus.VERIFIED;
        }
    }

    static class RejectedState implements DocumentState {
        private final RejectionReason reason;

        RejectedState(RejectionReason reason) {
            this.reason = reason;
        }

        public void process(Document document) {
            document.status = VerificationStatus.REJECTED;
            document.rejectionReason = reason;
        }
    }

    /* =====================
       SERVICE
    ===================== */
    static class DocumentVerificationService {
        private final DocumentRepository repository;
        private final Map<DocumentType, VerificationStrategy> strategies = new HashMap<>();

        DocumentVerificationService(DocumentRepository repository) {
            this.repository = repository;
            strategies.put(DocumentType.AADHAR, new AadharVerificationStrategy());
            strategies.put(DocumentType.PAN, new PanVerificationStrategy());
            strategies.put(DocumentType.PASSPORT, new PassportVerificationStrategy());
        }

        public String submitDocument(String userId,
                                     DocumentType type,
                                     String documentNumber) {
            Document document = new Document(
                    UUID.randomUUID().toString(),
                    userId,
                    type,
                    documentNumber
            );
            repository.save(document);
            return document.documentId;
        }

        public void verifyDocument(String documentId) {
            Document document = repository.findById(documentId);
            if (document == null) return;

            new SubmittedState().process(document);

            VerificationStrategy strategy = strategies.get(document.type);
            boolean verified = strategy.verify(document);

            if (verified) {
                new VerifiedState().process(document);
            } else {
                new RejectedState(RejectionReason.INVALID).process(document);
            }
        }

        public Document getDocument(String documentId) {
            return repository.findById(documentId);
        }
    }

    /* =====================
       MAIN (DEMO)
    ===================== */
    public static void main(String[] args) {
        DocumentRepository repo = new InMemoryDocumentRepository();
        DocumentVerificationService service =
                new DocumentVerificationService(repo);

        String docId = service.submitDocument(
                "user1",
                DocumentType.PAN,
                "ABCDE1234F"
        );

        service.verifyDocument(docId);

        Document doc = service.getDocument(docId);
        System.out.println("Status: " + doc.status);
    }
}
