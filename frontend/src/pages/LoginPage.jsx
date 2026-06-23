import { useState } from 'react'
import { useNavigate, Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import styles from '../styles/auth.module.css'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const isLogout = searchParams.get('logout') === 'true'
  const isExpired = searchParams.get('expired') === 'true'

  const handleChange = (e) =>
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const user = await login(form.email, form.password)
      navigate(user.role === 'ADMIN' ? '/admin' : '/', { replace: true })
    } catch (err) {
      setError(err.response?.data?.message || 'Invalid email or password')
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet" />
      <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet" />
      
      <div className={styles.authBody}>
        <div className={styles.authContainer}>
          <div className={styles.authCard}>
            {/* Header */}
            <div className={styles.authHeader}>
              <div className={styles.brandLogo}>
                <i className="fas fa-check-square"></i>
              </div>
              <h1 className={styles.brandTitle}>TaskFlow</h1>
              <p className={styles.authSubtitle}>Manage your workflow efficiently</p>
            </div>

            {/* Alerts */}
            {isLogout && (
              <div className={`${styles.alert} ${styles.alertSuccess}`}>
                <div className={styles.alertIcon}>
                  <i className="fas fa-check-circle"></i>
                </div>
                <div className={styles.alertMessage}>Logged out successfully</div>
              </div>
            )}
            {isExpired && (
              <div className={`${styles.alert} ${styles.alertWarning}`}>
                <div className={styles.alertIcon}>
                  <i className="fas fa-hourglass-end"></i>
                </div>
                <div className={styles.alertMessage}>Session expired. Please login again.</div>
              </div>
            )}
            {error && (
              <div className={`${styles.alert} ${styles.alertDanger}`}>
                <div className={styles.alertIcon}>
                  <i className="fas fa-exclamation-circle"></i>
                </div>
                <div className={styles.alertMessage}>{error}</div>
              </div>
            )}

            {/* Form */}
            <form onSubmit={handleSubmit}>
              <div className={styles.formGroup}>
                <div className={styles.formFloating}>
                  <input
                    className={styles.formControl}
                    type="email"
                    id="email"
                    name="email"
                    placeholder="Email"
                    value={form.email}
                    onChange={handleChange}
                    required
                  />
                  <label htmlFor="email" className={styles.formLabel}>
                    <i className="fas fa-envelope me-2"></i>Email Address
                  </label>
                </div>
              </div>

              <div className={styles.formGroup}>
                <div className={styles.formFloating}>
                  <input
                    className={styles.formControl}
                    type="password"
                    id="password"
                    name="password"
                    placeholder="Password"
                    value={form.password}
                    onChange={handleChange}
                    required
                  />
                  <label htmlFor="password" className={styles.formLabel}>
                    <i className="fas fa-lock me-2"></i>Password
                  </label>
                </div>
              </div>

              <button type="submit" className={styles.btnAuth} disabled={loading}>
                {loading
                  ? <><i className="fas fa-spinner fa-spin me-2"></i>Logging in...</>
                  : <><i className="fas fa-arrow-right me-2"></i>Login</>
                }
              </button>
            </form>

            {/* Divider */}
            <div className={styles.divider}>
              <span className={styles.dividerText}>or</span>
            </div>

            {/* Google OAuth2 */}
            <a href="http://localhost:8088/oauth2/authorization/google" className={styles.btnGoogle}>
              <i className="fab fa-google"></i>Continue with Google
            </a>

            {/* Footer */}
            <div className={styles.authFooter}>
              Don't have an account? <Link to="/register">Sign up here</Link>
            </div>
          </div>
        </div>
      </div>
    </>
  )
}

