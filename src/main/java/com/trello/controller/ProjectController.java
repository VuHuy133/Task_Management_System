package com.trello.controller;

import com.trello.dto.ApiResponse;
import com.trello.dto.ProjectRequest;
import com.trello.dto.ProjectResponse;
import com.trello.dto.ProjectMemberResponse;
import com.trello.entity.Project;
import com.trello.entity.ProjectMember;
import com.trello.entity.User;
import com.trello.service.ProjectService;
import com.trello.service.ProjectMemberService;
import com.trello.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMemberService projectMemberService;
    private final UserService userService;

    /**
     * Helper method: Extract userId from authentication
     * Handles both regular login (userId) and OAuth2 login (email)
     */
    private Long extractUserId(Authentication authentication) {
        if (authentication == null) return null;
        
        // Try to parse as Long (regular login)
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            // OAuth2 login - try to get email from OAuth2User
            try {
                Object principal = authentication.getPrincipal();
                if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User) {
                    org.springframework.security.oauth2.core.user.OAuth2User oauth2User = 
                        (org.springframework.security.oauth2.core.user.OAuth2User) principal;
                    String email = oauth2User.getAttribute("email");
                    if (email != null) {
                        User user = userService.findUserByEmail(email);
                        if (user != null) {
                            return user.getId();
                        }
                    }
                }
            } catch (Exception ex) {
                System.out.println("Error extracting userId from OAuth2: " + ex.getMessage());
            }
        }
        return null;
    }

    /**
     * GET /api/projects - Lấy danh sách dự án của User hiện tại với pagination và lọc
     * @param page Trang hiện tại (0-based, mặc định: 0)
     * @param size Số lượng bản ghi trên trang (mặc định: 30)
     * @param filterType Loại lọc: ALL (tất cả + public), JOINED (chỉ đã tham gia), JOINED_PUBLIC (public đã tham gia), JOINED_PRIVATE (private đã tham gia)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<?>> getUserProjects(Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(defaultValue = "ALL") String filterType) {
        try {
            Long userId = extractUserId(authentication);
            if (userId == null) {
                return new ResponseEntity<>(ApiResponse.builder()
                        .success(false)
                        .message("Vui lòng đăng nhập")
                        .statusCode(HttpStatus.UNAUTHORIZED.value())
                        .build(), HttpStatus.UNAUTHORIZED);
            }
            
            boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()) || "ADMIN".equalsIgnoreCase(a.getAuthority()));

            List<Project> projects;
            
            // Lấy danh sách dự án theo loại lọc
            if (isAdmin) {
                projects = projectService.getAllProjects();
            } else {
                List<Project> joinedProjects = projectService.getProjectsByUserId(userId);
                
                if ("ALL".equalsIgnoreCase(filterType)) {
                    // Tất cả dự án đã tham gia + tất cả public projects
                    Set<Long> joinedProjectIds = joinedProjects.stream()
                        .map(Project::getId)
                        .collect(Collectors.toSet());
                    
                    projects = new ArrayList<>(joinedProjects);
                    
                    // Thêm tất cả public projects mà user chưa tham gia
                    List<Project> allPublicProjects = projectService.getAllProjects().stream()
                        .filter(p -> "PUBLIC".equalsIgnoreCase(p.getType()))
                        .filter(p -> !joinedProjectIds.contains(p.getId()))
                        .collect(Collectors.toList());
                    
                    projects.addAll(allPublicProjects);
                } else if ("JOINED".equalsIgnoreCase(filterType)) {
                    // Chỉ dự án đã tham gia (owned or member)
                    projects = projectService.getProjectsOwnedOrJoinedByUser(userId);
                } else if ("JOINED_PUBLIC".equalsIgnoreCase(filterType)) {
                    // Dự án public mà đã tham gia (owned or member)
                    projects = projectService.getProjectsOwnedOrJoinedByUser(userId).stream()
                        .filter(p -> "PUBLIC".equalsIgnoreCase(p.getType()))
                        .collect(Collectors.toList());
                } else if ("JOINED_PRIVATE".equalsIgnoreCase(filterType)) {
                    // Dự án private mà đã tham gia (owned or member)
                    projects = projectService.getProjectsOwnedOrJoinedByUser(userId).stream()
                        .filter(p -> "PRIVATE".equalsIgnoreCase(p.getType()))
                        .collect(Collectors.toList());
                } else {
                    // Mặc định là ALL
                    Set<Long> joinedProjectIds = joinedProjects.stream()
                        .map(Project::getId)
                        .collect(Collectors.toSet());
                    
                    projects = new ArrayList<>(joinedProjects);
                    
                    List<Project> allPublicProjects = projectService.getAllProjects().stream()
                        .filter(p -> "PUBLIC".equalsIgnoreCase(p.getType()))
                        .filter(p -> !joinedProjectIds.contains(p.getId()))
                        .collect(Collectors.toList());
                    
                    projects.addAll(allPublicProjects);
                }
            }
            
            // Pagination logic
            int totalElements = projects.size();
            int totalPages = (int) Math.ceil((double) totalElements / size);
            int fromIndex = page * size;
            int toIndex = Math.min(fromIndex + size, totalElements);
            
            List<Project> paginatedProjects;
            if (fromIndex >= totalElements) {
                paginatedProjects = List.of();
            } else {
                paginatedProjects = projects.subList(fromIndex, toIndex);
            }
            
            List<ProjectResponse> projectResponses = paginatedProjects.stream()
                    .map(p -> convertToResponse(p, userId))
                    .collect(Collectors.toList());

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("content", projectResponses);
            responseData.put("page", page);
            responseData.put("size", size);
            responseData.put("totalElements", totalElements);
            responseData.put("totalPages", totalPages);

            ApiResponse<?> response = ApiResponse.builder()
                    .success(true)
                    .message("Lấy danh sách dự án thành công")
                    .statusCode(HttpStatus.OK.value())
                    .data(responseData)
                    .build();

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            ApiResponse<?> response = ApiResponse.builder()
                    .success(false)
                    .message("Lỗi khi lấy danh sách dự án: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * POST /api/projects - Tạo dự án mới (Admin)
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<?>> createProject(
            @Valid @RequestBody ProjectRequest request,
            Authentication authentication) {
        try {
			Long userId = extractUserId(authentication);
			if (userId == null) {
                return new ResponseEntity<>(ApiResponse.builder()
                        .success(false)
                        .message("Vui lòng đăng nhập")
                        .statusCode(HttpStatus.UNAUTHORIZED.value())
                        .build(), HttpStatus.UNAUTHORIZED);
            }
			
			User owner = userService.getUserById(userId);
			Project project = Project.builder()
				.name(request.getName())
				.description(request.getDescription())
				.type(request.getType())
				.owner(owner)
				.build();

                Project createdProject = projectService.createProject(project);
                ProjectResponse projectResponse = convertToResponse(createdProject, userId);

            ApiResponse<?> response = ApiResponse.builder()
                    .success(true)
                    .message("Tạo dự án thành công")
                    .statusCode(HttpStatus.CREATED.value())
                    .data(projectResponse)
                    .build();

            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            ApiResponse<?> response = ApiResponse.builder()
                    .success(false)
                    .message("Lỗi khi tạo dự án: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * POST /api/projects/{id}/join - Tham gia vào dự án
     */
    @PostMapping("/{id}/join")
    public ResponseEntity<ApiResponse<?>> joinProject(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            Long userId = extractUserId(authentication);
            if (userId == null) {
                return new ResponseEntity<>(ApiResponse.builder()
                        .success(false)
                        .message("Vui lòng đăng nhập")
                        .statusCode(HttpStatus.UNAUTHORIZED.value())
                        .build(), HttpStatus.UNAUTHORIZED);
            }
            
            projectService.addUserToProject(id, userId);

            ApiResponse<?> response = ApiResponse.builder()
                    .success(true)
                    .message("Tham gia dự án thành công")
                    .statusCode(HttpStatus.OK.value())
                    .build();

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            ApiResponse<?> response = ApiResponse.builder()
                    .success(false)
                    .message("Lỗi khi tham gia dự án: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * GET /api/projects/{id} - Lấy chi tiết dự án theo ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getProjectById(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            Long userId = extractUserId(authentication);
            Project project = projectService.getProjectById(id);

            ApiResponse<?> response = ApiResponse.builder()
                    .success(true)
                    .message("Lấy chi tiết dự án thành công")
                    .statusCode(HttpStatus.OK.value())
                    .data(convertToResponse(project, userId))
                    .build();
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            ApiResponse<?> response = ApiResponse.builder()
                    .success(false)
                    .message("Không tìm thấy dự án: " + e.getMessage())
                    .statusCode(HttpStatus.NOT_FOUND.value())
                    .build();
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
    }

    /**
     * PUT /api/projects/{id} - Cập nhật dự án (Admin)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<?>> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody ProjectRequest request,
            Authentication authentication) {
        try {
            Long userId = extractUserId(authentication);
            Project updated = projectService.updateProject(id, userId, request.getName(), request.getDescription(), request.getType());

            ApiResponse<?> response = ApiResponse.builder()
                    .success(true)
                    .message("Cập nhật dự án thành công")
                    .statusCode(HttpStatus.OK.value())
                    .data(convertToResponse(updated, userId))
                    .build();
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            ApiResponse<?> response = ApiResponse.builder()
                    .success(false)
                    .message("Lỗi khi cập nhật dự án: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * GET /api/projects/{id}/members - Lấy danh sách thành viên
     */
    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<?>> getMembers(@PathVariable Long id) {
        try {
            List<ProjectMember> members = projectMemberService.getMembersOfProject(id);
            List<ProjectMemberResponse> memberResponses = members.stream()
                    .map(this::mapProjectMemberToResponse)
                    .collect(Collectors.toList());

            ApiResponse<?> response = ApiResponse.builder()
                    .success(true)
                    .message("Lấy danh sách thành viên thành công")
                    .statusCode(HttpStatus.OK.value())
                    .data(memberResponses)
                    .build();
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            ApiResponse<?> response = ApiResponse.builder()
                    .success(false)
                    .message("Lỗi: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * POST /api/projects/{id}/members - Thêm thành viên vào dự án (theo email)
     */
    @PostMapping("/{id}/members")
    public ResponseEntity<ApiResponse<?>> addMember(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> body) {
        try {
            String email = (String) body.get("email");
            String role = body.containsKey("role") ? (String) body.get("role") : "MEMBER";

            User user = null;
            if (email != null) {
                user = userService.findUserByEmail(email);
            }
            if (user == null && body.containsKey("userId")) {
                Long userId = Long.parseLong(body.get("userId").toString());
                user = userService.getUserById(userId);
            }
            if (user == null) {
                ApiResponse<?> response = ApiResponse.builder()
                        .success(false)
                        .message("Không tìm thấy user với email đã cho")
                        .statusCode(HttpStatus.NOT_FOUND.value())
                        .build();
                return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
            }

            projectMemberService.addMemberToProject(id, user.getId(), role);

            ApiResponse<?> response = ApiResponse.builder()
                    .success(true)
                    .message("Thêm thành viên thành công")
                    .statusCode(HttpStatus.OK.value())
                    .build();
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            ApiResponse<?> response = ApiResponse.builder()
                    .success(false)
                    .message("Lỗi khi thêm thành viên: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * DELETE /api/projects/{id}/members/{userId} - Xóa thành viên khỏi dự án
     */
    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<ApiResponse<?>> removeMember(
            @PathVariable Long id,
            @PathVariable Long userId) {
        try {
            projectMemberService.removeMemberFromProject(id, userId);
            ApiResponse<?> response = ApiResponse.builder()
                    .success(true)
                    .message("Xóa thành viên thành công")
                    .statusCode(HttpStatus.OK.value())
                    .build();
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            ApiResponse<?> response = ApiResponse.builder()
                    .success(false)
                    .message("Lỗi: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * DELETE /api/projects/{id} - Xóa dự án (Admin)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<?>> deleteProject(@PathVariable Long id) {
        try {
            projectService.deleteProject(id);

            ApiResponse<?> response = ApiResponse.builder()
                    .success(true)
                    .message("Xóa dự án thành công")
                    .statusCode(HttpStatus.OK.value())
                    .build();

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            ApiResponse<?> response = ApiResponse.builder()
                    .success(false)
                    .message("Lỗi khi xóa dự án: " + e.getMessage())
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ProjectMemberResponse mapProjectMemberToResponse(ProjectMember projectMember) {
        return ProjectMemberResponse.builder()
                .id(projectMember.getId())
                .projectId(projectMember.getProject().getId())
                .userId(projectMember.getUser().getId())
                .username(projectMember.getUser().getUsername())
                .email(projectMember.getUser().getEmail())
                .role(projectMember.getRole())
                .joinedAt(projectMember.getJoinedAt())
                .build();
    }

    // Overload: convertToResponse without userId (for admin context)
    private ProjectResponse convertToResponse(Project project) {
        return convertToResponse(project, null);
    }

    // Overload: convertToResponse with userId (to check if current user joined)
    private ProjectResponse convertToResponse(Project project, Long userId) {
        List<ProjectMember> members = projectMemberService.getMembersOfProject(project.getId());
        List<ProjectMemberResponse> memberResponses = members.stream()
                .map(this::mapProjectMemberToResponse)
                .collect(Collectors.toList());

        boolean joinedByCurrentUser = userId != null && projectService.isProjectMember(project.getId(), userId);

        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .ownerId(project.getOwnerId())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .type(project.getType())
                .memberCount(members.size())
                .taskCount(project.getTasks() != null ? (int) project.getTasks().size() : 0)
                .members(memberResponses)
                .joinedByCurrentUser(joinedByCurrentUser)
                .build();
    }
}
