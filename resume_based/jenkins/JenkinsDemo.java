package LLD2.resume_based.jenkins;

import java.util.*;

// =====================
// ENUMS
// =====================
enum JobStatus {
    PENDING, RUNNING, SUCCESS, FAILED
}

// =====================
// ENTITIES
// =====================
class Job {
    String jobId;
    String jobName;
    JobStatus status;
    String gitRepo;

    Job(String jobId, String jobName, String gitRepo) {
        this.jobId = jobId;
        this.jobName = jobName;
        this.gitRepo = gitRepo;
        this.status = JobStatus.PENDING;
    }
}

class Build {
    String buildId;
    Job job;
    JobStatus status;
    Date startTime;
    Date endTime;

    Build(String buildId, Job job) {
        this.buildId = buildId;
        this.job = job;
        this.status = JobStatus.PENDING;
        this.startTime = new Date();
    }
}

// =====================
// REPOSITORIES
// =====================
class JobRepository {
    Map<String, Job> jobs = new HashMap<>();
    void addJob(Job job) { jobs.put(job.jobId, job); }
    Job findById(String jobId) { return jobs.get(jobId); }
}

class BuildRepository {
    Map<String, Build> builds = new HashMap<>();
    void addBuild(Build build) { builds.put(build.buildId, build); }
    List<Build> findByJob(String jobId) {
        List<Build> list = new ArrayList<>();
        for(Build b : builds.values()) if(b.job.jobId.equals(jobId)) list.add(b);
        return list;
    }
}

// =====================
// STRATEGY
// =====================
interface BuildStrategy {
    void execute(Build build);
}

class MavenBuildStrategy implements BuildStrategy {
    public void execute(Build build) {
        System.out.println("Executing Maven build for job: " + build.job.jobName);
        build.status = JobStatus.SUCCESS;
        build.endTime = new Date();
    }
}

class GradleBuildStrategy implements BuildStrategy {
    public void execute(Build build) {
        System.out.println("Executing Gradle build for job: " + build.job.jobName);
        build.status = JobStatus.SUCCESS;
        build.endTime = new Date();
    }
}

// =====================
// OBSERVER
// =====================
interface BuildObserver {
    void notify(String message);
}

class ConsoleNotifier implements BuildObserver {
    public void notify(String message) { System.out.println("Notification: " + message); }
}

// =====================
// SERVICE
// =====================
class JenkinsService {
    JobRepository jobRepo;
    BuildRepository buildRepo;
    BuildStrategy buildStrategy;
    List<BuildObserver> observers = new ArrayList<>();

    JenkinsService(JobRepository jr, BuildRepository br, BuildStrategy bs) {
        this.jobRepo = jr;
        this.buildRepo = br;
        this.buildStrategy = bs;
    }

    void registerObserver(BuildObserver obs) { observers.add(obs); }
    void notifyObservers(String msg) { for(BuildObserver o : observers) o.notify(msg); }

    Build triggerBuild(String jobId) {
        Job job = jobRepo.findById(jobId);
        if(job == null) {
            notifyObservers("Job not found: " + jobId);
            return null;
        }

        Build build = new Build(UUID.randomUUID().toString(), job);
        build.status = JobStatus.RUNNING;
        buildRepo.addBuild(build);
        notifyObservers("Build started for job: " + job.jobName);

        buildStrategy.execute(build);

        notifyObservers("Build finished with status: " + build.status);
        return build;
    }
}

// =====================
// DEMO
// =====================
public class JenkinsDemo {
    public static void main(String[] args) {
        JobRepository jobRepo = new JobRepository();
        BuildRepository buildRepo = new BuildRepository();

        Job job1 = new Job("J1", "Build-App", "https://github.com/example/repo.git");
        jobRepo.addJob(job1);

        JenkinsService jenkinsService = new JenkinsService(jobRepo, buildRepo, new MavenBuildStrategy());
        jenkinsService.registerObserver(new ConsoleNotifier());

        jenkinsService.triggerBuild("J1");
    }
}

