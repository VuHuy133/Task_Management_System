import axiosInstance from './axiosInstance'

/**
 * Đăng ký tài khoản mới
 */
export const register = (data) => axiosInstance.post('/auth/register', data)

/**
 * Đăng nhập - API + Session
 * Server trả access token trong body + set refresh token vào httpOnly Cookie (Session)
 */
export const login = async (email, password) => {
  const res = await axiosInstance.post('/auth/login', { email, password })
  const { accessToken, user } = res.data.data

  // Chỉ lưu access token và user info (refresh token do server quản lý qua httpOnly Cookie)
  localStorage.setItem('accessToken', accessToken)
  localStorage.setItem('user', JSON.stringify(user))

  return user
}

/**
 * Đăng xuất - Session
 * Gọi API logout: server xóa httpOnly Cookie và blacklist tokens
 * Browser tự động gửi Cookie (withCredentials: true trong axiosInstance)
 */
export const logout = async () => {
  try {
    await axiosInstance.post('/auth/logout')
  } finally {
    // Xóa access token và user info khỏi localStorage
    localStorage.removeItem('accessToken')
    localStorage.removeItem('user')
  }
}

/**
 * Lấy thông tin user hiện tại từ localStorage
 */
export const getCurrentUser = () => {
  const user = localStorage.getItem('user')
  return user ? JSON.parse(user) : null
}

/**
 * Kiểm tra đã đăng nhập chưa
 */
export const isAuthenticated = () => !!localStorage.getItem('accessToken')
