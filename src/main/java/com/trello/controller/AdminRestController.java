package com.trello.controller;

import com.trello.dto.ApiResponse;
import com.trello.dto.TaskResponse;
import com.trello.dto.UserResponse;
import com.trello.entity.Task;
import com.trello.entity.User;
import com.trello.service.ProjectService;
import com.trello.service.TaskService;
import com.trello.service.UserService;
import com.trello.service.DataSeederService;
import com.trello.repository.UserRepository;
import com.trello.repository.ProjectRepository;
import com.trello.repository.TaskRepository;
import com.trello.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AdminRestController - REST API for admin operations at /api/admin
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminRestController {

    private final UserService userService;
    private final TaskService taskService;
    private final ProjectService projectService;
    private final DataSeederService dataSeederService;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    private UserResponse mapUserToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private TaskResponse mapTaskToResponse(Task task) {
        return TaskResponse.builder()
            .id(task.getId())
            .projectId(task.getProject() != null ? task.getProject().getId() : null)
            .title(task.getTitle())
            .description(task.getDescription())
            .status(task.getStatus())
            .priority(task.getPriority())
            .assigneeId(task.getAssignee() != null ? task.getAssignee().getId() : null)
            .userId(task.getUser() != null ? task.getUser().getId() : null)
            .createdAt(task.getCreatedAt())
            .updatedAt(task.getUpdatedAt())
            .dueDate(task.getDueDate())
            .build();
    }

    /**
     * GET /api/admin/stats - Thống kê dashboard (OPTIMIZED - Database COUNT queries only)
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<?>> getDashboardStats() {
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // Use database COUNT queries - much faster than loading all data into memory
            long totalUsers = userRepository.count();
            long totalProjects = projectRepository.count();
            long totalTasks = taskRepository.count();
            long adminCount = userRepository.countAdmins();
            
            // Count tasks by status using database query
            long todoCount = taskRepository.countByStatus("TODO");
            long doingCount = taskRepository.countByStatus("DOING");
            long doneCount = taskRepository.countByStatus("DONE");
            
            // Count overdue tasks using database query 
            long overdueTasks = taskRepository.countOverdueTasks(now);
            
            // Get top 10 overdue tasks using pagination
            Page<Task> overdueTasksPage = taskRepository.findOverdueTasks(now, PageRequest.of(0, 10));
            List<TaskResponse> overdueTasksResponse = overdueTasksPage.getContent().stream()
                .map(this::mapTaskToResponse)
                .collect(Collectors.toList());

            // Build taskStats map
            Map<String, Long> taskStats = new HashMap<>();
            taskStats.put("TODO", todoCount);
            taskStats.put("DOING", doingCount);
            taskStats.put("DONE", doneCount);

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalUsers", totalUsers);
            stats.put("totalProjects", totalProjects);
            stats.put("totalTasks", totalTasks);
            stats.put("adminCount", adminCount);
            stats.put("taskStats", taskStats);
            stats.put("overdueTasks", overdueTasks);
            stats.put("overdueTasksList", overdueTasksResponse);

            return new ResponseEntity<>(ApiResponse.builder()
                    .success(true).message("Lấy thống kê thành công")
                    .statusCode(HttpStatus.OK.value()).data(stats).build(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(false).message("Lỗi: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value()).build(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * GET /api/admin/users - Lấy danh sách users với pagination (admin view)
     */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<?>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<User> usersPage = userRepository.findAll(pageable);
            Page<UserResponse> responsesPage = usersPage.map(this::mapUserToResponse);
            
            Map<String, Object> response = new HashMap<>();
            response.put("content", responsesPage.getContent());
            response.put("page", page);
            response.put("size", size);
            response.put("totalElements", usersPage.getTotalElements());
            response.put("totalPages", usersPage.getTotalPages());
            
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(true).message("Lấy danh sách users thành công")
                    .statusCode(HttpStatus.OK.value()).data(response).build(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(false).message("Lỗi: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value()).build(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * DELETE /api/admin/users/{id} - Xóa user (admin)
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<?>> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(true).message("Xóa user thành công")
                    .statusCode(HttpStatus.OK.value()).build(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(false).message("Lỗi: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value()).build(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * PUT /api/admin/users/{id}/role - Thay đổi role của user
     */
    @PutMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<?>> changeUserRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            String role = body.get("role");
            if (role == null || (!role.equals("ADMIN") && !role.equals("USER"))) {
                return new ResponseEntity<>(ApiResponse.builder()
                        .success(false).message("Role không hợp lệ. Chỉ cho phép: ADMIN, USER")
                        .statusCode(HttpStatus.BAD_REQUEST.value()).build(), HttpStatus.BAD_REQUEST);
            }
            userService.updateRole(id, role);
            User updated = userService.getUserById(id);
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(true).message("Cập nhật role thành công")
                    .statusCode(HttpStatus.OK.value()).data(mapUserToResponse(updated)).build(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(false).message("Lỗi: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value()).build(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * GET /api/admin/tasks - Lấy danh sách tasks với pagination (admin view)
     */
    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<?>> getAllTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Task> tasksPage = taskRepository.findAll(pageable);
            Page<TaskResponse> responsesPage = tasksPage.map(this::mapTaskToResponse);
            
            Map<String, Object> response = new HashMap<>();
            response.put("content", responsesPage.getContent());
            response.put("page", page);
            response.put("size", size);
            response.put("totalElements", tasksPage.getTotalElements());
            response.put("totalPages", tasksPage.getTotalPages());
            
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(true).message("Lấy danh sách tasks thành công")
                    .statusCode(HttpStatus.OK.value()).data(response).build(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(false).message("Lỗi: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value()).build(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * DELETE /api/admin/tasks/{id} - Xóa task (admin)
     */
    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<ApiResponse<?>> deleteTask(@PathVariable Long id) {
        try {
            taskService.deleteTask(id);
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(true).message("Xóa task thành công")
                    .statusCode(HttpStatus.OK.value()).build(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(false).message("Lỗi: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value()).build(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * GET /api/admin/projects - Lấy danh sách projects với pagination (admin view) 
     */
    @GetMapping("/projects")
    public ResponseEntity<ApiResponse<?>> getAllProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<com.trello.entity.Project> projectsPage = projectRepository.findAll(pageable);
            
            Map<String, Object> response = new HashMap<>();
            response.put("content", projectsPage.getContent());
            response.put("page", page);
            response.put("size", size);
            response.put("totalElements", projectsPage.getTotalElements());
            response.put("totalPages", projectsPage.getTotalPages());
            
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(true).message("Lấy danh sách projects thành công")
                    .statusCode(HttpStatus.OK.value()).data(response).build(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(false).message("Lỗi: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value()).build(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * POST /api/admin/seed-data - Seed database with fake data (1000 users, 1000 projects, 100000 tasks)
     * No authentication required - can be called manually via API
     */
    @PreAuthorize("permitAll()")
    @PostMapping("/seed-data")
    public ResponseEntity<?> seedDatabase() {
        Map<String, Object> result = dataSeederService.seedAllData();
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/admin/db-stats - Get database statistics
     */
    @PreAuthorize("permitAll()")
    @GetMapping("/db-stats")
    public ResponseEntity<?> getDatabaseStats() {
        Map<String, Long> stats = dataSeederService.getDatabaseStats();
        return ResponseEntity.ok(new HashMap<String, Object>() {{
            put("data", stats);
            put("timestamp", LocalDateTime.now());
        }});
    }

    /**
     * DELETE /api/admin/clear-data - Clear all data from database
     */
    @DeleteMapping("/clear-data")
    public ResponseEntity<?> clearDatabase() {
        taskRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        
        return ResponseEntity.ok(Map.of(
            "message", "All data cleared",
            "timestamp", LocalDateTime.now()
        ));
    }

    /**
     * POST /api/admin/reset-passwords - Reset password cho tất cả user về "123456"
     * Dùng khi đã đổ dữ liệu và muốn reset password về mặc định
     */
    @PostMapping("/reset-passwords")
    public ResponseEntity<ApiResponse<?>> resetAllPasswords() {
        Map<String, Object> result = dataSeederService.resetAllUserPasswords();
        boolean success = "SUCCESS".equals(result.get("status"));
        
        ApiResponse<?> response = ApiResponse.builder()
                .success(success)
                .message((String) result.get("message"))
                .statusCode(success ? HttpStatus.OK.value() : HttpStatus.INTERNAL_SERVER_ERROR.value())
                .data(result)
                .build();
        
        return success ? ResponseEntity.ok(response) : 
               new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
