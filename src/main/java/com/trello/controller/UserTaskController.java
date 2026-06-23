package com.trello.controller;

import com.trello.dto.ApiResponse;
import com.trello.dto.TaskRequest;
import com.trello.dto.TaskResponse;
import com.trello.entity.Task;
import com.trello.entity.TaskComment;
import com.trello.entity.User;
import com.trello.service.TaskService;
import com.trello.service.TaskCommentService;
import com.trello.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * UserTaskController - REST API for tasks from user perspective
 * Provides /api/tasks endpoints without requiring projectId in path
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class UserTaskController {

    private final TaskService taskService;
    private final TaskCommentService taskCommentService;
    private final UserService userService;

    private Long extractUserId(Authentication authentication) {
        if (authentication == null) return null;
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            try {
                Object principal = authentication.getPrincipal();
                if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
                    org.springframework.security.oauth2.core.user.OAuth2User oauth2User =
                        (org.springframework.security.oauth2.core.user.OAuth2User) principal;
                    String email = oauth2User.getAttribute("email");
                    if (email != null) {
                        User user = userService.findUserByEmail(email);
                        if (user != null) return user.getId();
                    }
                }
            } catch (Exception ex) {
                System.out.println("Error extracting userId: " + ex.getMessage());
            }
        }
        return null;
    }

    private TaskResponse convertToResponse(Task task) {
        TaskResponse.ProjectDTO projectDTO = null;
        if (task.getProject() != null) {
            projectDTO = TaskResponse.ProjectDTO.builder()
                    .id(task.getProject().getId())
                    .name(task.getProject().getName())
                    .type(task.getProject().getType())
                    .build();
        }
        
        return TaskResponse.builder()
            .id(task.getId())
            .projectId(task.getProject() != null ? task.getProject().getId() : null)
            .project(projectDTO)
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
     * GET /api/tasks - Lấy danh sách công việc được giao cho user hiện tại với pagination
     * @param status Optional: filter by status (TODO, DOING, DONE)
     * @param page Trang hiện tại (0-based, mặc định: 0)
     * @param size Số lượng bản ghi trên trang (mặc định: 30)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<?>> getUserTasks(Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        try {
            Long userId = extractUserId(authentication);
            if (userId == null) {
                return new ResponseEntity<>(ApiResponse.builder()
                        .success(false).message("Vui lòng đăng nhập")
                        .statusCode(HttpStatus.UNAUTHORIZED.value()).build(), HttpStatus.UNAUTHORIZED);
            }
            List<Task> tasks = taskService.getTasksAssignedToUser(userId);
            
            // Filter by status if provided
            if (status != null && !status.isEmpty()) {
                tasks = tasks.stream()
                        .filter(t -> t.getStatus().equalsIgnoreCase(status))
                        .collect(Collectors.toList());
            }
            
            // Pagination logic
            int totalElements = tasks.size();
            int totalPages = (int) Math.ceil((double) totalElements / size);
            int fromIndex = page * size;
            int toIndex = Math.min(fromIndex + size, totalElements);
            
            List<Task> paginatedTasks;
            if (fromIndex >= totalElements) {
                paginatedTasks = List.of();
            } else {
                paginatedTasks = tasks.subList(fromIndex, toIndex);
            }
            
            List<TaskResponse> responses = paginatedTasks.stream().map(this::convertToResponse).collect(Collectors.toList());
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("content", responses);
            responseData.put("page", page);
            responseData.put("size", size);
            responseData.put("totalElements", totalElements);
            responseData.put("totalPages", totalPages);

            return new ResponseEntity<>(ApiResponse.builder()
                    .success(true).message("Lấy danh sách công việc thành công")
                    .statusCode(HttpStatus.OK.value()).data(responseData).build(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(false).message("Lỗi: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value()).build(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * GET /api/tasks/{id} - Lấy chi tiết công việc theo ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getTaskById(@PathVariable Long id) {
        try {
            Task task = taskService.getTaskById(id);
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(true).message("Lấy công việc thành công")
                    .statusCode(HttpStatus.OK.value()).data(convertToResponse(task)).build(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(false).message("Không tìm thấy công việc: " + e.getMessage())
                    .statusCode(HttpStatus.NOT_FOUND.value()).build(), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * PATCH /api/tasks/{id}/status - Cập nhật trạng thái công việc
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<?>> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        try {
            if (!isValidStatus(status)) {
                return new ResponseEntity<>(ApiResponse.builder()
                        .success(false).message("Trạng thái không hợp lệ. Chỉ cho phép: TODO, DOING, DONE")
                        .statusCode(HttpStatus.BAD_REQUEST.value()).build(), HttpStatus.BAD_REQUEST);
            }
            Task task = taskService.getTaskById(id);
            task.setStatus(status);
            task.setUpdatedAt(LocalDateTime.now());
            Task updated = taskService.updateTask(task);
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(true).message("Cập nhật trạng thái thành công")
                    .statusCode(HttpStatus.OK.value()).data(convertToResponse(updated)).build(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(false).message("Lỗi: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value()).build(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * GET /api/tasks/{id}/comments - Lấy bình luận của công việc
     */
    @GetMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<?>> getComments(@PathVariable Long id) {
        try {
            List<TaskComment> comments = taskCommentService.getCommentsByTaskId(id);
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(true).message("Lấy bình luận thành công")
                    .statusCode(HttpStatus.OK.value()).data(comments).build(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(false).message("Lỗi: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value()).build(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * POST /api/tasks/{id}/comments - Thêm bình luận cho công việc
     */
    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<?>> addComment(
            @PathVariable Long id,
            @RequestParam String content,
            Authentication authentication) {
        try {
            if (content == null || content.trim().isEmpty()) {
                return new ResponseEntity<>(ApiResponse.builder()
                        .success(false).message("Nội dung bình luận không được để trống")
                        .statusCode(HttpStatus.BAD_REQUEST.value()).build(), HttpStatus.BAD_REQUEST);
            }
            Long userId = extractUserId(authentication);
            if (userId == null) {
                return new ResponseEntity<>(ApiResponse.builder()
                        .success(false).message("Vui lòng đăng nhập")
                        .statusCode(HttpStatus.UNAUTHORIZED.value()).build(), HttpStatus.UNAUTHORIZED);
            }
            Task task = taskService.getTaskById(id);
            User user = userService.getUserById(userId);
            TaskComment comment = new TaskComment();
            comment.setTask(task);
            comment.setUser(user);
            comment.setContent(content);
            comment.setCreatedAt(LocalDateTime.now());
            TaskComment created = taskCommentService.createComment(comment);
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(true).message("Thêm bình luận thành công")
                    .statusCode(HttpStatus.CREATED.value()).data(created).build(), HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.builder()
                    .success(false).message("Lỗi: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value()).build(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean isValidStatus(String status) {
        return status != null && (status.equals("TODO") || status.equals("DOING") || status.equals("DONE"));
    }
}
