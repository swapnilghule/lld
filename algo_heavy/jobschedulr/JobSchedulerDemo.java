package LLD2.algo_heavy.jobschedulr;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

// =====================
// ENUMS
// =====================
enum JobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

enum JobType {
    BATCH,
    CRON,
    EVENT_DRIVEN
}

// =====================
// ENTITIES
// =====================
class Job {
    String jobId;
    String jobName;
    JobType jobType;
    JobStatus status;
    Runnable task;

    Job(String jobId, String jobName, JobType jobType, Runnable task) {
        this.jobId = jobId;
        this.jobName = jobName;
        this.jobType = jobType;
        this.task = task;
        this.status = JobStatus.PENDING;
    }
}

// =====================
// REPOSITORY
// =====================
class JobRepository {
    Map<String, Job> jobs = new HashMap<>();

    void save(Job job) {
        jobs.put(job.jobId, job);
    }

    Job findById(String jobId) {
        return jobs.get(jobId);
    }

    List<Job> findAll() {
        return new ArrayList<>(jobs.values());
    }
}

// =====================
// STRATEGY
// =====================
interface SchedulingStrategy {
    void schedule(Job job, ScheduledExecutorService executor);
}

class ImmediateStrategy implements SchedulingStrategy {
    @Override
    public void schedule(Job job, ScheduledExecutorService executor) {
        executor.submit(() -> {
            job.status = JobStatus.RUNNING;
            try {
                job.task.run();
                job.status = JobStatus.COMPLETED;
            } catch (Exception e) {
                job.status = JobStatus.FAILED;
            }
        });
    }
}

class FixedDelayStrategy implements SchedulingStrategy {
    private final long delaySeconds;

    FixedDelayStrategy(long delaySeconds) {
        this.delaySeconds = delaySeconds;
    }

    @Override
    public void schedule(Job job, ScheduledExecutorService executor) {
        executor.scheduleWithFixedDelay(() -> {
            job.status = JobStatus.RUNNING;
            try {
                job.task.run();
                job.status = JobStatus.COMPLETED;
            } catch (Exception e) {
                job.status = JobStatus.FAILED;
            }
        }, 0, delaySeconds, TimeUnit.SECONDS);
    }
}

// =====================
// SERVICE
// =====================
class JobSchedulerService {
    private final JobRepository jobRepository;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);

    JobSchedulerService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    void submitJob(Job job, SchedulingStrategy strategy) {
        jobRepository.save(job);
        strategy.schedule(job, executor);
    }

    JobStatus getJobStatus(String jobId) {
        Job job = jobRepository.findById(jobId);
        return job != null ? job.status : null;
    }

    List<Job> listJobs() {
        return jobRepository.findAll();
    }
}

// =====================
// DEMO
// =====================
public class JobSchedulerDemo {
    public static void main(String[] args) throws InterruptedException {
        JobRepository repo = new JobRepository();
        JobSchedulerService scheduler = new JobSchedulerService(repo);

        Job job1 = new Job("J1", "BatchJob1", JobType.BATCH, () -> System.out.println("Executing batch job"));
        Job job2 = new Job("J2", "EventJob", JobType.EVENT_DRIVEN, () -> System.out.println("Event job executed"));

        scheduler.submitJob(job1, new ImmediateStrategy());
        scheduler.submitJob(job2, new FixedDelayStrategy(5));

        Thread.sleep(2000);
        System.out.println("Job1 status: " + scheduler.getJobStatus("J1"));
        System.out.println("Job2 status: " + scheduler.getJobStatus("J2"));
    }
}
