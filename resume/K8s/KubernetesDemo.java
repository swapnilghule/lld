package LLD2.resume.K8s;

import java.util.*;

/* =====================
   ENUMS
   ===================== */
enum PodStatus {
    PENDING, RUNNING, SUCCEEDED, FAILED, UNKNOWN
}

enum NodeStatus {
    READY, NOT_READY
}

/* =====================
   ENTITIES
   ===================== */
class Node {
    String nodeId;
    NodeStatus status;
    int cpuCapacity;
    int memoryCapacity;

    Node(String nodeId, NodeStatus status, int cpu, int memory) {
        this.nodeId = nodeId;
        this.status = status;
        this.cpuCapacity = cpu;
        this.memoryCapacity = memory;
    }
}

class Pod {
    String podId;
    String nodeId;
    PodStatus status;
    int cpuRequest;
    int memoryRequest;

    Pod(String podId, int cpu, int memory) {
        this.podId = podId;
        this.cpuRequest = cpu;
        this.memoryRequest = memory;
        this.status = PodStatus.PENDING;
    }
}

/* =====================
   REPOSITORIES
   ===================== */
class NodeRepository {
    Map<String, Node> nodes = new HashMap<>();

    void save(Node node) {
        nodes.put(node.nodeId, node);
    }

    Node findById(String nodeId) {
        return nodes.get(nodeId);
    }

    List<Node> findAll() {
        return new ArrayList<>(nodes.values());
    }
}

class PodRepository {
    Map<String, Pod> pods = new HashMap<>();

    void save(Pod pod) {
        pods.put(pod.podId, pod);
    }

    Pod findById(String podId) {
        return pods.get(podId);
    }

    List<Pod> findAll() {
        return new ArrayList<>(pods.values());
    }
}

/* =====================
   SCHEDULER STRATEGY
   ===================== */
interface SchedulingStrategy {
    boolean schedule(Pod pod, List<Node> nodes);
}

class BinPackingStrategy implements SchedulingStrategy {
    public boolean schedule(Pod pod, List<Node> nodes) {
        for (Node node : nodes) {
            if (node.status == NodeStatus.READY &&
                    node.cpuCapacity >= pod.cpuRequest &&
                    node.memoryCapacity >= pod.memoryRequest) {
                pod.nodeId = node.nodeId;
                pod.status = PodStatus.RUNNING;
                node.cpuCapacity -= pod.cpuRequest;
                node.memoryCapacity -= pod.memoryRequest;
                return true;
            }
        }
        return false;
    }
}

/* =====================
   SERVICES
   ===================== */
class NodeService {
    NodeRepository nodeRepo;

    NodeService(NodeRepository nodeRepo) {
        this.nodeRepo = nodeRepo;
    }

    void addNode(Node node) {
        nodeRepo.save(node);
    }

    List<Node> getAllNodes() {
        return nodeRepo.findAll();
    }
}

class PodService {
    PodRepository podRepo;
    SchedulingStrategy scheduler;
    NodeService nodeService;

    PodService(PodRepository podRepo, NodeService nodeService, SchedulingStrategy scheduler) {
        this.podRepo = podRepo;
        this.nodeService = nodeService;
        this.scheduler = scheduler;
    }

    void createPod(Pod pod) {
        List<Node> nodes = nodeService.getAllNodes();
        if (scheduler.schedule(pod, nodes)) {
            podRepo.save(pod);
            System.out.println("Pod " + pod.podId + " scheduled on Node " + pod.nodeId);
        } else {
            System.out.println("Pod " + pod.podId + " could not be scheduled");
        }
    }

    List<Pod> listPods() {
        return podRepo.findAll();
    }
}

/* =====================
   DEMO
   ===================== */
public class KubernetesDemo {
    public static void main(String[] args) {
        NodeRepository nodeRepo = new NodeRepository();
        PodRepository podRepo = new PodRepository();

        NodeService nodeService = new NodeService(nodeRepo);
        nodeService.addNode(new Node("node1", NodeStatus.READY, 8, 16));
        nodeService.addNode(new Node("node2", NodeStatus.READY, 4, 8));

        SchedulingStrategy scheduler = new BinPackingStrategy();
        PodService podService = new PodService(podRepo, nodeService, scheduler);

        Pod pod1 = new Pod("pod1", 2, 4);
        Pod pod2 = new Pod("pod2", 6, 10);
        Pod pod3 = new Pod("pod3", 4, 8);

        podService.createPod(pod1);
        podService.createPod(pod2);
        podService.createPod(pod3);

        System.out.println("All Pods:");
        for (Pod pod : podService.listPods()) {
            System.out.println(pod.podId + " on " + pod.nodeId + " Status: " + pod.status);
        }
    }
}
