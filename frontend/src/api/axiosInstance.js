import axios from 'axios'

const axiosInstance = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
  // Bật withCredentials để browser tự động gửi httpOnly Cookie khi refresh/logout
  withCredentials: true,
})

// -------------------------------------------------------
// Request interceptor: tự động gắn JWT vào mỗi request
// -------------------------------------------------------
axiosInstance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// -------------------------------------------------------
// Response interceptor: tự động refresh token khi 401
// -------------------------------------------------------
axiosInstance.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    // Nếu 401 và chưa thử refresh
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      const refreshToken = localStorage.getItem('refreshToken')
      if (!refreshToken) {
        // Không có refresh token -> logout
        localStorage.clear()
        window.location.href = '/login'
        return Promise.reject(error)
      }

      try {
        // Gọi refresh - browser tự động gửi httpOnly Cookie (không cần truyền refreshToken thủ công)
        const res = await axios.post('/api/auth/refresh', {}, { withCredentials: true })
        const { accessToken } = res.data.data

        localStorage.setItem('accessToken', accessToken)
        // Refresh token được server cập nhật qua Cookie tự động, không cần lưu localStorage

        // Retry request gốc với token mới
        originalRequest.headers['Authorization'] = `Bearer ${accessToken}`
        return axiosInstance(originalRequest)
      } catch {
        localStorage.clear()
        window.location.href = '/login'
        return Promise.reject(error)
      }
    }

    return Promise.reject(error)
  }
)

export default axiosInstance
