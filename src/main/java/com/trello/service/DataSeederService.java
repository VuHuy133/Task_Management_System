package com.trello.service;

import com.github.javafaker.Faker;
import com.trello.entity.*;
import com.trello.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DataSeederService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PasswordEncoder passwordEncoder;

    private final Faker faker = new Faker(Locale.US);

    /**
     * Main method to seed all data
     * Only seeds if database is empty. If data exists, returns current stats.
     * @return seeding result summary
     */
    public Map<String, Object> seedAllData() {
        try {
            // Check if data already exists
            long userCount = userRepository.count();
            if (userCount > 0) {
                log.info("⚠️  Database already has {} users. Skipping seeding.", userCount);
                log.info("   Current data stats:");
                // Convert Map<String, Long> to Map<String, Object>
                Map<String, Object> stats = new HashMap<>(getDatabaseStats());
                return stats;
            }

            Map<String, Object> result = new HashMap<>();
            
            log.info("=== Starting Data Seeding ===");
            long startTime = System.currentTimeMillis();

            // Step 1: Clear existing data (should be empty at this point)
            log.info("Clearing existing data...");
            clearAllData();

            // Step 2: Create users
            log.info("Creating 1000 users...");
            List<User> users = createUsers(1000);
            result.put("users_created", users.size());

            // Step 3: Create projects
            log.info("Creating 1000 projects...");
            List<Project> projects = createProjects(1000, users);
            result.put("projects_created", projects.size());

            // Step 4: Create project members
            log.info("Creating project members...");
            int projectMembers = createProjectMembers(users, projects);
            result.put("project_members_created", projectMembers);

            // Step 5: Create tasks
            log.info("Creating 100,000 tasks...");
            int taskCreatedCount = createTasksInBatches(100000, users, projects);
            result.put("tasks_created", taskCreatedCount);

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.info("=== Data Seeding Completed in {} seconds ===", duration);
            result.put("duration_seconds", duration);
            result.put("status", "SUCCESS");

            return result;
        } catch (Exception e) {
            log.error("Error during data seeding", e);
            return Map.of(
                "status", "ERROR",
                "message", e.getMessage()
            );
        }
    }

    /**
     * Clear all data from tables
     */
    private void clearAllData() {
        taskRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * Create users with fake data
     */
    private List<User> createUsers(int count) {
        List<User> users = new ArrayList<>();
        Set<String> usedUsernames = new HashSet<>();
        Set<String> usedEmails = new HashSet<>();

        for (int i = 0; i < count; i++) {
            String username;
            String email;

            // Ensure unique username and email
            do {
                username = faker.name().username();
            } while (usedUsernames.contains(username));
            usedUsernames.add(username);

            do {
                email = faker.internet().emailAddress();
            } while (usedEmails.contains(email));
            usedEmails.add(email);

                User user = User.builder()
                    .username(username)
                    .email(email)
                    .password(passwordEncoder.encode("123456"))
                    .name(faker.name().fullName())
                    .address(faker.address().fullAddress())
                    .role(i == 0 ? "ADMIN" : "USER") // First user is ADMIN
                    .createdAt(LocalDateTime.now().minusDays(faker.random().nextInt(90)))
                    .build();

            users.add(user);

            if (i % 100 == 0) {
                log.debug("Generated {} users", i);
            }
        }

        return userRepository.saveAll(users);
    }

    /**
     * Create projects with fake data
     */
    private List<Project> createProjects(int count, List<User> users) {
        List<Project> projects = new ArrayList<>();
        String[] types = {"PUBLIC", "PRIVATE"};

        for (int i = 0; i < count; i++) {
            User owner = users.get(faker.random().nextInt(users.size()));

            // Limit description to 255 chars (VARCHAR(255) in database)
            String description = faker.lorem().paragraph();
            if (description.length() > 255) {
                description = description.substring(0, 255);
            }

            Project project = Project.builder()
                    .name(faker.company().name() + " - Project " + (i + 1))
                    .description(description)
                    .owner(owner)
                    .type(types[faker.random().nextInt(types.length)])
                    .createdAt(LocalDateTime.now().minusDays(faker.random().nextInt(365)))
                    .updatedAt(LocalDateTime.now())
                    .build();

            projects.add(project);

            if (i % 100 == 0) {
                log.debug("Generated {} projects", i);
            }
        }

        return projectRepository.saveAll(projects);
    }

    /**
     * Create project members - assign random users to random projects
     */
    private int createProjectMembers(List<User> users, List<Project> projects) {
        List<ProjectMember> projectMembers = new ArrayList<>();
        String[] roles = {"OWNER", "MEMBER"};
        Set<String> uniqueMembers = new HashSet<>();

        // Average: 5-10 members per project
        int targetCount = Math.min(projects.size() * 8, users.size() * 3);

        for (int i = 0; i < targetCount; i++) {
            User user = users.get(faker.random().nextInt(users.size()));
            Project project = projects.get(faker.random().nextInt(projects.size()));

            String key = user.getId() + "-" + project.getId();
            if (!uniqueMembers.contains(key)) {
                uniqueMembers.add(key);

                ProjectMember member = ProjectMember.builder()
                        .project(project)
                        .user(user)
                        .role(roles[faker.random().nextInt(roles.length)])
                        .joinedAt(LocalDateTime.now().minusDays(faker.random().nextInt(365)))
                        .build();

                projectMembers.add(member);
            }

            if (i % 1000 == 0) {
                log.debug("Generated {} project members", i);
            }
        }

        projectMemberRepository.saveAll(projectMembers);
        return projectMembers.size();
    }

    /**
     * Create tasks in batches to handle 100,000 records efficiently
     */
    private int createTasksInBatches(int totalTasks, List<User> users, List<Project> projects) {
        String[] statuses = {"TODO", "DOING", "DONE"};
        String[] priorities = {"LOW", "MEDIUM", "HIGH", };
        int batchSize = 500;
        int totalCreated = 0;

        for (int batch = 0; batch < (totalTasks / batchSize); batch++) {
            List<Task> taskBatch = new ArrayList<>();

            for (int i = 0; i < batchSize; i++) {
                // Limit description to 255 chars (VARCHAR(255) in database)
                String description = faker.lorem().paragraph();
                if (description.length() > 255) {
                    description = description.substring(0, 255);
                }

                Task task = Task.builder()
                        .project(projects.get(faker.random().nextInt(projects.size())))
                        .user(users.get(faker.random().nextInt(users.size()))) // Project creator
                        .title(faker.lorem().word() + " - " + (batch * batchSize + i))
                        .description(description)
                        .status(statuses[faker.random().nextInt(statuses.length)])
                        .priority(priorities[faker.random().nextInt(priorities.length)])
                        .assignee(users.get(faker.random().nextInt(users.size()))) // Random assignee
                        .createdAt(LocalDateTime.now().minusDays(faker.random().nextInt(180)))
                        .updatedAt(LocalDateTime.now().minusDays(faker.random().nextInt(30)))
                        .dueDate(LocalDateTime.now().plusDays(faker.random().nextInt(30)))
                        .position(i + 1)
                        .build();

                taskBatch.add(task);
            }

            taskRepository.saveAll(taskBatch);
            totalCreated += taskBatch.size();

            if (batch % 10 == 0) {
                log.debug("Inserted {} tasks", totalCreated);
            }
        }

        return totalCreated;
    }

    /**
     * Get current database statistics
     */
    public Map<String, Long> getDatabaseStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total_users", userRepository.count());
        stats.put("total_projects", projectRepository.count());
        stats.put("total_project_members", projectMemberRepository.count());
        stats.put("total_tasks", taskRepository.count());
        return stats;
    }

    /**
     * Reset password cho tất cả user thành "123456"
     * Dùng khi đã đổ dữ liệu và muốn thay đổi password
     */
    public Map<String, Object> resetAllUserPasswords() {
        return resetAllUserPasswords("123456");
    }

    /**
     * Reset password cho tất cả user thành mật khẩu được cung cấp
     * @param newPassword mật khẩu mới
     */
    public Map<String, Object> resetAllUserPasswords(String newPassword) {
        try {
            List<User> users = userRepository.findAll();
            int updatedCount = 0;

            log.info("=== Starting Password Reset for {} users ===", users.size());
            long startTime = System.currentTimeMillis();

            for (User user : users) {
                String encodedPassword = passwordEncoder.encode(newPassword);
                user.setPassword(encodedPassword);
                updatedCount++;

                if (updatedCount % 100 == 0) {
                    log.debug("Updated password for {} users", updatedCount);
                }
            }

            userRepository.saveAll(users);

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.info("=== Password Reset Completed for {} users in {} seconds ===", updatedCount, duration);

            return Map.of(
                "status", "SUCCESS",
                "message", "Reset password cho tất cả " + updatedCount + " user thành '" + newPassword + "'",
                "updated_count", updatedCount,
                "duration_seconds", duration
            );
        } catch (Exception e) {
            log.error("Error during password reset", e);
            return Map.of(
                "status", "ERROR",
                "message", e.getMessage()
            );
        }
    }
}
