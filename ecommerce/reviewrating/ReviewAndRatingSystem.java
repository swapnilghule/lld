package LLD2.ecommerce.reviewrating;

import java.util.*;

public class ReviewAndRatingSystem {
    //Entities
    enum ReviewStatus{
        ACTIVE,
        FLAGGED
    }

    enum VoteType{
        HELPFUL,NOT_HELPFUL;
    }

    static class User{
        String userid;
        User(String userId){
            this.userid=userId;
        }
    }

    static class Product{
        String productId;
        Rating rating;

        Product(String productId){
            this.productId=productId;
            this.rating = new Rating(0.0, 0);
        }
    }

    static class Review{
        String reviewId;
        String userId;
        String productId;
        int rating;
        String text;
        ReviewStatus reviewStatus;
        Map<VoteType, Integer> votes= new HashMap<>();

        public Review(String reviewId, String userId, String productId, int rating, String text, ReviewStatus reviewStatus, Map<VoteType, Integer> votes) {
            this.reviewId = reviewId;
            this.userId = userId;
            this.productId = productId;
            this.rating = rating;
            this.text = text;
            this.reviewStatus = reviewStatus;
            this.votes = votes;
            votes.put(VoteType.NOT_HELPFUL, 0);
            votes.put(VoteType.NOT_HELPFUL, 0);
        }

        public Review(String string, String userId, String productId, int rating, String text) {
            this.reviewId = reviewId;
            this.userId = userId;
            this.productId = productId;
            this.rating = rating;
            this.text = text;
        }
    }

    static class Rating{
        double average;
        int count;

        public Rating(double average, int count) {
            this.average = average;
            this.count = count;
        }
    }



    //Repositories

    static class ReviewRepository{
        Map<String, Review> reviewById = new HashMap<>();
        Map<String, List<Review>> reviewsByProduct = new HashMap<>();

        void save(Review review){
            reviewById.put(review.reviewId, review);
            reviewsByProduct.
                    computeIfAbsent(review.productId, k_-> new ArrayList<>()).add(review);
        }

        Review findById(String reviewId){
            return reviewById.get(reviewId);
        }

        List<Review> findByProduct(String productId){
            return reviewsByProduct.getOrDefault(productId, new ArrayList<>());
        }
    }

    static class ProductRepository{
        Map<String, Product> productById= new HashMap<>();

        Product findOrCretae(String productId){
            return productById.computeIfAbsent(productId, Product::new);
        }
    }

    //Strategies

    interface ModerationStrategy{
        boolean apply(Review review);
    }

    static class SpamDetectionStrategy implements  ModerationStrategy{

        @Override
        public boolean apply(Review review) {
            return review.text.toLowerCase().contains("http");
        }
    }

    static class AbuseDetectionStrategy implements  ModerationStrategy{

        @Override
        public boolean apply(Review review) {
            return review.text.toLowerCase().contains("abuse");
        }
    }

    //Services

    static class ModerationService{
        private final List<ModerationStrategy> strategies;

        public ModerationService(List<ModerationStrategy> strategies) {
            this.strategies = strategies;
        }

        boolean moderate(Review review){
            for(ModerationStrategy strategy: strategies){
                if(strategy.apply(review)){
                    review.reviewStatus = ReviewStatus.FLAGGED;
                }
            }

            return true;
        }
    }

    static class RatingService{
        private final ProductRepository productRepository;

        public RatingService(ProductRepository productRepository) {
            this.productRepository = productRepository;
        }

        void updateRating(String productId, int newRating){
            Product product=productRepository.findOrCretae(productId);
            Rating rating = product.rating;

            double total = rating.average * rating.count;
            rating.count++;
            rating.average =(total + newRating) / rating.count;
        }
    }

    private static class ReviewService {
        private final ReviewRepository reviewRepository;
        private final RatingService ratingService;
        private final ModerationService moderationService;

        public ReviewService(ReviewRepository reviewRepository, RatingService ratingService, ModerationService moderationService) {
            this.reviewRepository = reviewRepository;
            this.ratingService = ratingService;
            this.moderationService = moderationService;
        }

        public String addReview( String userId, String productId, int rating, String text) {
            Review review = new Review(
                    UUID.randomUUID().toString(),
                    userId,
                    productId,
                    rating,
                    text

            );

            if (!moderationService.moderate(review)){
                return "Review is flagged by moderation";

            }

            reviewRepository.save(review);
            ratingService.updateRating(productId, rating);
            return review.reviewId;

        }

        public void vote(String reviewId, VoteType type){
            Review review= reviewRepository.findById(reviewId);
            if(review== null || review.reviewStatus != ReviewStatus.ACTIVE) return;

            review.votes.put(type, review.votes.get(type)+1);
        }

        public List<Review> getReviewsFroProduct(String productId){
            return reviewRepository.findByProduct(productId);
        }
    }

    // main code
    public static void main(String[] args){
        ReviewRepository reviewRepository= new ReviewRepository();
        ProductRepository productRepository= new ProductRepository();
        RatingService ratingService= new RatingService(productRepository);

        ModerationService moderationService= new ModerationService(
                List.of(new SpamDetectionStrategy(), new AbuseDetectionStrategy())
        );

        ReviewService reviewService= new ReviewService(reviewRepository, ratingService, moderationService);

        String reviewId= reviewService.addReview("user1", "product1", 5, "Great Product");
        reviewService.vote(reviewId, VoteType.HELPFUL);

        Product product= productRepository.findOrCretae("product1");
        System.out.println("Rating"+ product.rating.average);
        System.out.println("Review Count:"+ reviewService.getReviewsFroProduct("product1").size());

    }





}
