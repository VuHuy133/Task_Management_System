package com.trello.service;

import com.trello.entity.Project;
import com.trello.entity.ProjectMember;
import com.trello.entity.User;
import com.trello.repository.ProjectMemberRepository;
import com.trello.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserService userService;

    /**
     * Create a new project (overloaded - accepts Project object)
     */
    public Project createProject(Project project) {
        if (project.getCreatedAt() == null) {
            project.setCreatedAt(LocalDateTime.now());
        }
        if (project.getType() == null) {
            project.setType("PUBLIC");
        }
        if (project.getTasks() == null) {
            project.setTasks(new java.util.ArrayList<>());
        }
        if (project.getMembers() == null) {
            project.setMembers(new java.util.ArrayList<>());
        }
        return projectRepository.save(project);
    }

    /**
     * Create a new project
     */
    public Project createProject(Long ownerId, String name, String description, String type) {
        User owner = userService.getUserById(ownerId);
        Project project = Project.builder()
            .name(name)
            .description(description)
            .owner(owner)
            .type(type != null ? type : "PUBLIC")
            .createdAt(LocalDateTime.now())
            .build();
        return projectRepository.save(project);
    }

    /**
     * Get project by ID
     */
    @Transactional(readOnly = true)
    public Project getProjectById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found with id: " + id));
    }

    /**
     * Get all projects visible to a user (public + private where user is owner/member/admin)
     */
    @Transactional(readOnly = true)
    public List<Project> getProjectsByUserId(Long userId) {
		// Requirement: USER chỉ thấy PROJECT type PUBLIC + PROJECT PRIVATE nếu đã tham gia (member).
		// Admin được tách ở controller/service khác (getAllProjects).
		List<Project> publicProjects = projectRepository.findByType("PUBLIC");

		User user = userService.getUserById(userId);
		List<Project> privateMember = projectMemberRepository.findByUser(user).stream()
				.map(ProjectMember::getProject)
				.filter(p -> "PRIVATE".equals(p.getType()))
				.toList();

		// Combine + deduplicate
		List<Project> result = new java.util.ArrayList<>();
		result.addAll(publicProjects);
		for (Project p : privateMember) {
			if (result.stream().noneMatch(x -> x.getId().equals(p.getId()))) {
				result.add(p);
			}
		}
		return result;
    }

    // Helper: check if user is admin (by role)
    // You may want to move this to UserService if needed elsewhere
    public boolean isAdmin(com.trello.entity.User user) {
        return user != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }

    /**
     * Get projects owned by user
     */
    @Transactional(readOnly = true)
    public List<Project> getProjectsOwnedByUser(Long userId) {
        User owner = userService.getUserById(userId);
        return projectRepository.findByOwner(owner);
    }

    /**
     * Get projects that user has joined (owned or is member of)
     * Used for JOINED, JOINED_PUBLIC, JOINED_PRIVATE filters
     */
    @Transactional(readOnly = true)
    public List<Project> getProjectsOwnedOrJoinedByUser(Long userId) {
        User user = userService.getUserById(userId);
        
        // Get all projects where user is owner
        List<Project> ownedProjects = projectRepository.findByOwner(user);
        Set<Long> projectIds = new java.util.HashSet<>();
        for (Project p : ownedProjects) {
            projectIds.add(p.getId());
        }
        
        // Get all projects where user is a member
        List<Project> memberProjects = projectMemberRepository.findByUser(user).stream()
                .map(ProjectMember::getProject)
                .toList();
        
        List<Project> result = new java.util.ArrayList<>(ownedProjects);
        for (Project p : memberProjects) {
            if (!projectIds.contains(p.getId())) {
                result.add(p);
                projectIds.add(p.getId());
            }
        }
        
        return result;
    }

	/**
	 * Rule xem project:
	 * - PUBLIC: ai cũng xem được (USER chỉ nhìn thấy danh sách phù hợp, nhưng khi view chi tiết vẫn cần check).
	 * - PRIVATE:
	 *   + ADMIN xem được tất cả
	 *   + USER chỉ xem nếu họ là member của project
	 */
	@Transactional(readOnly = true)
	public boolean canUserViewProject(Long projectId, Long userId) {
		Project project = projectRepository.findById(projectId).orElse(null);
		if (project == null) {
			return false;
		}
		String projectType = project.getType();
		if (projectType == null || "PUBLIC".equalsIgnoreCase(projectType)) {
			return true;
		}

		User user = userService.getUserById(userId);
		if (isAdmin(user)) {
			return true;
		}

		// Non-admin: only view PRIVATE if user is a member
		return projectMemberRepository.existsByProjectAndUser(project, user);
	}

    /**
     * Update project
     */
    public Project updateProject(Long id, Long userId, String name, String description) {
        Project project = getProjectById(id);
        if (name != null && !name.isEmpty()) {
            project.setName(name);
        }
        if (description != null && !description.isEmpty()) {
            project.setDescription(description);
        }
        return projectRepository.save(project);
    }

    public Project updateProject(Long id, Long userId, String name, String description, String type) {
        Project project = getProjectById(id);
        if (name != null && !name.isEmpty()) project.setName(name);
        if (description != null && !description.isEmpty()) project.setDescription(description);
        if (type != null && !type.isEmpty()) project.setType(type);
        return projectRepository.save(project);
    }

    /**
     * Delete project (only owner can delete)
     */
    public void deleteProject(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new RuntimeException("Project not found with id: " + id);
        }
        projectRepository.deleteById(id);
    }

    /**
     * Delete project (only owner can delete)
     */
    public void deleteProject(Long id, Long userId) {
        if (!projectRepository.existsById(id)) {
            throw new RuntimeException("Project not found with id: " + id);
        }
        // Optionally check if user is owner or admin here
        projectRepository.deleteById(id);
    }

    /**
     * Add user to project (join project)
     */
    public void addUserToProject(Long projectId, Long userId) {
        Project project = getProjectById(projectId);
        User user = userService.getUserById(userId);
        if (projectMemberRepository.existsByProjectAndUser(project, user)) {
            throw new RuntimeException("User đã là thành viên của dự án này");
        }
        ProjectMember member = ProjectMember.builder()
            .project(project)
            .user(user)
            .role("MEMBER")
            .joinedAt(LocalDateTime.now())
            .build();
        projectMemberRepository.save(member);
    }

    /**
     * Check if user is project owner
     */
    @Transactional(readOnly = true)
    public boolean isProjectOwner(Long projectId, Long userId) {
        // Owner field was removed; ownership is defined by project members with role 'OWNER' or admin membership.
        Project project = getProjectById(projectId);
        User user = userService.getUserById(userId);
        // Check member repository for a member record with role OWNER
        return projectMemberRepository.findByProjectAndUser(project, user)
                .map(pm -> "OWNER".equalsIgnoreCase(pm.getRole()))
                .orElse(false);
    }

    /**
     * Check if user is project member
     */
    @Transactional(readOnly = true)
    public boolean isProjectMember(Long projectId, Long userId) {
        Project project = getProjectById(projectId);
        User user = userService.getUserById(userId);
        return projectMemberRepository.existsByProjectAndUser(project, user);
    }

    /**
     * Get all projects
     */
    @Transactional(readOnly = true)
    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    /**
     * Get all public projects
     */
    @Transactional(readOnly = true)
    public List<Project> getAllPublicProjects() {
        return projectRepository.findByType("PUBLIC");
    }
}
