import axiosInstance from './axiosInstance'

const projectService = {
  // Get all projects for current user with pagination
  getProjects: (params = {}) =>
    axiosInstance.get('/projects', { params }).then(res => res.data),

  // Get single project by ID
  getProjectById: (id) =>
    axiosInstance.get(`/projects/${id}`).then(res => res.data),

  // Create new project
  createProject: (data) =>
    axiosInstance.post('/projects', data).then(res => res.data),

  // Update project
  updateProject: (id, data) =>
    axiosInstance.put(`/projects/${id}`, data).then(res => res.data),

  // Delete project
  deleteProject: (id) =>
    axiosInstance.delete(`/projects/${id}`).then(res => res.data),

  // Add user to project
  addUserToProject: (projectId, userId, role = 'MEMBER') =>
    axiosInstance.post(`/projects/${projectId}/members`, { userId, role }).then(res => res.data),

  // Remove user from project
  removeUserFromProject: (projectId, userId) =>
    axiosInstance.delete(`/projects/${projectId}/members/${userId}`).then(res => res.data),

  // Get project members
  getProjectMembers: (projectId) =>
    axiosInstance.get(`/projects/${projectId}/members`).then(res => res.data),

  // Get project tasks
  getProjectTasks: (projectId, params = {}) =>
    axiosInstance.get(`/projects/${projectId}/tasks`, { params }).then(res => res.data),

  // Join project
  joinProject: (projectId) =>
    axiosInstance.post(`/projects/${projectId}/join`).then(res => res.data),

  // Leave project (remove self from members)
  leaveProject: (projectId, userId) =>
    axiosInstance.delete(`/projects/${projectId}/members/${userId}`).then(res => res.data),
}

export default projectService
