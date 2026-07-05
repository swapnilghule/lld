package LLD2.ecommerce.selleronboarding;

import java.util.*;

// =====================
// ENUM
// =====================
enum SellerStatus {
    CREATED,
    KYC_SUBMITTED,
    VERIFIED,
    REJECTED,
    ACTIVE
}

// =====================
// ENTITIES
// =====================
class Seller {
    String sellerId;
    String name;
    SellerStatus status;
    KYCDetails kycDetails;

    Seller(String sellerId, String name) {
        this.sellerId = sellerId;
        this.name = name;
        this.status = SellerStatus.CREATED;
    }
}

class KYCDetails {
    String documentId;

    KYCDetails(String documentId) {
        this.documentId = documentId;
    }
}

// =====================
// REPOSITORY
// =====================
class SellerRepository {
    private final Map<String, Seller> store = new HashMap<>();

    void save(Seller seller) {
        store.put(seller.sellerId, seller);
    }

    Seller findById(String sellerId) {
        return store.get(sellerId);
    }
}

// =====================
// STRATEGY
// =====================
interface KYCVerificationStrategy {
    boolean verify(KYCDetails kycDetails);
}

class PANVerificationStrategy implements KYCVerificationStrategy {
    public boolean verify(KYCDetails kycDetails) {
        return kycDetails.documentId.startsWith("PAN");
    }
}

class GSTVerificationStrategy implements KYCVerificationStrategy {
    public boolean verify(KYCDetails kycDetails) {
        return kycDetails.documentId.startsWith("GST");
    }
}

// =====================
// STATE
// =====================
interface SellerState {
    void submitKYC(Seller seller, String documentId);
    void verify(Seller seller, KYCVerificationStrategy strategy);
    void activate(Seller seller);
}

class CreatedState implements SellerState {

    public void submitKYC(Seller seller, String documentId) {
        seller.kycDetails = new KYCDetails(documentId);
        seller.status = SellerStatus.KYC_SUBMITTED;
    }

    public void verify(Seller seller, KYCVerificationStrategy strategy) {
        throw new IllegalStateException("KYC not submitted");
    }

    public void activate(Seller seller) {
        throw new IllegalStateException("Seller not verified");
    }
}

class KYCSubmittedState implements SellerState {

    public void submitKYC(Seller seller, String documentId) {
        throw new IllegalStateException("KYC already submitted");
    }

    public void verify(Seller seller, KYCVerificationStrategy strategy) {
        boolean verified = strategy.verify(seller.kycDetails);
        seller.status = verified ? SellerStatus.VERIFIED : SellerStatus.REJECTED;
    }

    public void activate(Seller seller) {
        throw new IllegalStateException("Verification pending");
    }
}

class VerifiedState implements SellerState {

    public void submitKYC(Seller seller, String documentId) {
        throw new IllegalStateException("Already verified");
    }

    public void verify(Seller seller, KYCVerificationStrategy strategy) {
        throw new IllegalStateException("Already verified");
    }

    public void activate(Seller seller) {
        seller.status = SellerStatus.ACTIVE;
    }
}

// =====================
// SERVICES
// =====================
class SellerOnboardingService {

    private final SellerRepository repository;
    private final KYCVerificationStrategy strategy;

    SellerOnboardingService(SellerRepository repository,
                            KYCVerificationStrategy strategy) {
        this.repository = repository;
        this.strategy = strategy;
    }

    Seller registerSeller(String name) {
        Seller seller = new Seller(UUID.randomUUID().toString(), name);
        repository.save(seller);
        return seller;
    }

    void submitKYC(String sellerId, String documentId) {
        Seller seller = repository.findById(sellerId);
        getState(seller).submitKYC(seller, documentId);
    }

    void verifySeller(String sellerId) {
        Seller seller = repository.findById(sellerId);
        getState(seller).verify(seller, strategy);
    }

    void activateSeller(String sellerId) {
        Seller seller = repository.findById(sellerId);
        getState(seller).activate(seller);
    }

    SellerStatus getStatus(String sellerId) {
        return repository.findById(sellerId).status;
    }

    private SellerState getState(Seller seller) {
        switch (seller.status) {
            case CREATED: return new CreatedState();
            case KYC_SUBMITTED: return new KYCSubmittedState();
            case VERIFIED: return new VerifiedState();
            default:
                throw new IllegalStateException("Invalid state");
        }
    }
}

// =====================
// MAIN
// =====================
public class SellerOnboardingSystem {

    public static void main(String[] args) {

        SellerRepository repository = new SellerRepository();
        KYCVerificationStrategy strategy = new PANVerificationStrategy();

        SellerOnboardingService service =
                new SellerOnboardingService(repository, strategy);

        Seller seller = service.registerSeller("ABC Traders");

        service.submitKYC(seller.sellerId, "PAN12345");
        service.verifySeller(seller.sellerId);
        service.activateSeller(seller.sellerId);

        System.out.println("Final Seller Status: " +
                service.getStatus(seller.sellerId));
    }
}
