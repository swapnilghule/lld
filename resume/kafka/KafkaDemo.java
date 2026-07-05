package LLD2.resume.kafka;

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

    Topic(String name, TopicType type, int partitionCount) {
        this.topicId = UUID.randomUUID().toString();
        this.name = name;
        this.type = type;
        for (int i = 0; i < partitionCount; i++) {
            partitions.add(new Partition());
        }
    }
}

class Partition {
    String partitionId;
    Queue<Message> messages = new LinkedList<>();
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
class OffsetRepository {
    Map<String, Long> offsets = new HashMap<>();

    void commit(String groupId, String topicId, int partitionIndex, long offset) {
        offsets.put(groupId + ":" + topicId + ":" + partitionIndex, offset);
    }

    long getOffset(String groupId, String topicId, int partitionIndex) {
        return offsets.getOrDefault(groupId + ":" + topicId + ":" + partitionIndex, 0L);
    }
}

/* =====================
   STRATEGY
   ===================== */
interface PartitionStrategy {
    int selectPartition(Message message, Topic topic);
}

class RoundRobinPartitionStrategy implements PartitionStrategy {
    private int counter = 0;

    public int selectPartition(Message message, Topic topic) {
        return counter++ % topic.partitions.size();
    }
}

class KeyBasedPartitionStrategy implements PartitionStrategy {
    public int selectPartition(Message message, Topic topic) {
        return Math.abs(message.payload.hashCode()) % topic.partitions.size();
    }
}

/* =====================
   SERVICES
   ===================== */
class BrokerService {
    Map<String, Topic> topics = new HashMap<>();
    PartitionStrategy strategy;

    BrokerService(PartitionStrategy strategy) {
        this.strategy = strategy;
    }

    void registerTopic(Topic topic) {
        topics.put(topic.topicId, topic);
    }

    void publish(String topicId, Message message) {
        Topic topic = topics.get(topicId);
        if (topic == null) return;
        int partitionIndex = strategy.selectPartition(message, topic);
        Partition partition = topic.partitions.get(partitionIndex);
        message.status = MessageStatus.ENQUEUED;
        partition.messages.add(message);
    }

    Message consume(String topicId, int partitionIndex) {
        Topic topic = topics.get(topicId);
        if (topic == null) return null;
        Partition partition = topic.partitions.get(partitionIndex);
        Message msg = partition.messages.poll();
        if (msg != null) msg.status = MessageStatus.DELIVERED;
        return msg;
    }
}

class ProducerService {
    BrokerService broker;

    ProducerService(BrokerService broker) {
        this.broker = broker;
    }

    void send(String topicId, String payload) {
        Message message = new Message(payload);
        broker.publish(topicId, message);
    }
}

class ConsumerService {
    BrokerService broker;
    OffsetRepository offsetRepo;

    ConsumerService(BrokerService broker, OffsetRepository offsetRepo) {
        this.broker = broker;
        this.offsetRepo = offsetRepo;
    }

    Message poll(String topicId, String groupId, int partitionIndex) {
        long offset = offsetRepo.getOffset(groupId, topicId, partitionIndex);
        Message msg = broker.consume(topicId, partitionIndex);
        return msg;
    }

    void ack(Message message, String topicId, String groupId, int partitionIndex) {
        message.status = MessageStatus.ACKED;
        offsetRepo.commit(groupId, topicId, partitionIndex, System.currentTimeMillis());
    }
}

/* =====================
   DEMO
   ===================== */
public class KafkaDemo {
    public static void main(String[] args) {
        PartitionStrategy strategy = new RoundRobinPartitionStrategy();
        BrokerService broker = new BrokerService(strategy);
        OffsetRepository offsetRepo = new OffsetRepository();

        ProducerService producer = new ProducerService(broker);
        ConsumerService consumer = new ConsumerService(broker, offsetRepo);

        // Create Topic with 3 partitions
        Topic orders = new Topic("ORDERS", TopicType.FIFO, 3);
        broker.registerTopic(orders);

        // Publish messages
        producer.send(orders.topicId, "Order Created: ORD100");
        producer.send(orders.topicId, "Order Created: ORD101");
        producer.send(orders.topicId, "Order Created: ORD102");

        // Consume from partition 0
        Message msg = consumer.poll(orders.topicId, "GROUP1", 0);
        System.out.println("Consumed from partition 0: " + msg.payload);
        consumer.ack(msg, orders.topicId, "GROUP1", 0);

        // Consume from partition 1
        Message msg2 = consumer.poll(orders.topicId, "GROUP1", 1);
        System.out.println("Consumed from partition 1: " + msg2.payload);
        consumer.ack(msg2, orders.topicId, "GROUP1", 1);
    }
}
