package LLD2.resume.message_queue;

import java.util.*;

/* =====================
   ENUMS
   ===================== */
enum MessageStatus {
    CREATED, ENQUEUED, DELIVERED, ACKED, FAILED
}

enum TopicType {
    FIFO, PUB_SUB
}

/* =====================
   ENTITIES
   ===================== */
class Message {
    String messageId;
    String payload;
    MessageStatus status;
    Date createdAt;

    Message(String payload) {
        this.messageId = UUID.randomUUID().toString();
        this.payload = payload;
        this.status = MessageStatus.CREATED;
        this.createdAt = new Date();
    }
}

class Topic {
    String topicId;
    String name;
    TopicType type;
    List<Partition> partitions = new ArrayList<>();

    Topic(String name, TopicType type) {
        this.topicId = UUID.randomUUID().toString();
        this.name = name;
        this.type = type;
    }
}

class Partition {
    String partitionId;
    long offset;

    Partition() {
        this.partitionId = UUID.randomUUID().toString();
        this.offset = 0;
    }
}

class ConsumerGroup {
    String groupId;

    ConsumerGroup(String groupId) {
        this.groupId = groupId;
    }
}

/* =====================
   REPOSITORIES
   ===================== */
class MessageRepository {
    Map<String, Queue<Message>> messages = new HashMap<>();

    void enqueue(String topicId, Message message) {
        messages.computeIfAbsent(topicId, k -> new LinkedList<>()).add(message);
        message.status = MessageStatus.ENQUEUED;
    }

    Message dequeue(String topicId) {
        Queue<Message> queue = messages.getOrDefault(topicId, new LinkedList<>());
        Message msg = queue.poll();
        if (msg != null) msg.status = MessageStatus.DELIVERED;
        return msg;
    }
}

class TopicRepository {
    Map<String, Topic> topics = new HashMap<>();

    void save(Topic topic) {
        topics.put(topic.topicId, topic);
    }

    Topic findById(String topicId) {
        return topics.get(topicId);
    }
}

class OffsetRepository {
    Map<String, Long> offsets = new HashMap<>();

    void commit(String groupId, String topicId, long offset) {
        offsets.put(groupId + ":" + topicId, offset);
    }

    long getOffset(String groupId, String topicId) {
        return offsets.getOrDefault(groupId + ":" + topicId, 0L);
    }
}

/* =====================
   STRATEGY
   ===================== */
interface PartitionStrategy {
    Partition selectPartition(Topic topic);
}

class RoundRobinPartitionStrategy implements PartitionStrategy {
    private int counter = 0;

    public Partition selectPartition(Topic topic) {
        if (topic.partitions.isEmpty()) {
            Partition p = new Partition();
            topic.partitions.add(p);
        }
        Partition p = topic.partitions.get(counter % topic.partitions.size());
        counter++;
        return p;
    }
}

class KeyBasedPartitionStrategy implements PartitionStrategy {
    public Partition selectPartition(Topic topic) {
        // simplified hash-based partitioning
        if (topic.partitions.isEmpty()) {
            Partition p = new Partition();
            topic.partitions.add(p);
        }
        int hash = topic.name.hashCode();
        return topic.partitions.get(Math.abs(hash) % topic.partitions.size());
    }
}

/* =====================
   SERVICES
   ===================== */
class BrokerService {
    private final MessageRepository messageRepo;
    private final PartitionStrategy strategy;

    BrokerService(MessageRepository messageRepo, PartitionStrategy strategy) {
        this.messageRepo = messageRepo;
        this.strategy = strategy;
    }

    void registerTopic(Topic topic) {
        // topic already has partitions? ensure at least 1
        if (topic.partitions.isEmpty()) {
            topic.partitions.add(new Partition());
        }
    }

    void routeMessage(Topic topic, Message message) {
        Partition partition = strategy.selectPartition(topic);
        messageRepo.enqueue(topic.topicId, message);
    }
}

class ProducerService {
    private final BrokerService broker;
    private final TopicRepository topicRepo;

    ProducerService(BrokerService broker, TopicRepository topicRepo) {
        this.broker = broker;
        this.topicRepo = topicRepo;
    }

    void publish(String topicId, String payload) {
        Topic topic = topicRepo.findById(topicId);
        if (topic == null) return;
        Message msg = new Message(payload);
        broker.routeMessage(topic, msg);
    }
}

class ConsumerService {
    private final MessageRepository messageRepo;
    private final OffsetRepository offsetRepo;

    ConsumerService(MessageRepository messageRepo, OffsetRepository offsetRepo) {
        this.messageRepo = messageRepo;
        this.offsetRepo = offsetRepo;
    }

    Message poll(String topicId, String groupId) {
        Message msg = messageRepo.dequeue(topicId);
        return msg;
    }

    void ack(Message msg, String topicId, String groupId) {
        msg.status = MessageStatus.ACKED;
        offsetRepo.commit(groupId, topicId, System.currentTimeMillis()); // simplified offset
    }
}

/* =====================
   DEMO
   ===================== */
public class MessageQueueDemo {
    public static void main(String[] args) {
        MessageRepository messageRepo = new MessageRepository();
        TopicRepository topicRepo = new TopicRepository();
        OffsetRepository offsetRepo = new OffsetRepository();

        PartitionStrategy strategy = new RoundRobinPartitionStrategy();
        BrokerService broker = new BrokerService(messageRepo, strategy);
        ProducerService producer = new ProducerService(broker, topicRepo);
        ConsumerService consumer = new ConsumerService(messageRepo, offsetRepo);

        // Create topic
        Topic topic = new Topic("ORDERS", TopicType.FIFO);
        topicRepo.save(topic);
        broker.registerTopic(topic);

        // Publish
        producer.publish(topic.topicId, "Order Created: ORD123");
        producer.publish(topic.topicId, "Order Created: ORD124");

        // Consume
        Message msg1 = consumer.poll(topic.topicId, "GROUP1");
        System.out.println("Consumed: " + msg1.payload);
        consumer.ack(msg1, topic.topicId, "GROUP1");

        Message msg2 = consumer.poll(topic.topicId, "GROUP1");
        System.out.println("Consumed: " + msg2.payload);
        consumer.ack(msg2, topic.topicId, "GROUP1");
    }
}
