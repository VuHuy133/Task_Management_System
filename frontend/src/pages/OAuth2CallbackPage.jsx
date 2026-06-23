import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

/**
 * Trang callback sau khi đăng nhập OAuth2 (Google).
 * Backend redirect về: /oauth2/callback?token=<jwt>&refreshToken=<refresh>&email=<email>&name=<name>&role=<role>&id=<id>
 * Trang này lưu token + user info vào localStorage rồi redirect về home.
 */
export default function OAuth2CallbackPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  useEffect(() => {
    const token = searchParams.get('token')
    const refreshToken = searchParams.get('refreshToken')
    const email = searchParams.get('email')
    const username = searchParams.get('username')
    const name = searchParams.get('name')
    const role = searchParams.get('role')
    const id = searchParams.get('id')

    if (token) {
      // Lưu access token
      localStorage.setItem('accessToken', token)
      
      // Lưu refresh token nếu có
      if (refreshToken) localStorage.setItem('refreshToken', refreshToken)
      
      // Lưu user info từ query params
      const userInfo = {
        id: id || ((() => {
          try {
            const payload = JSON.parse(atob(token.split('.')[1]))
            return payload.sub
          } catch {
            return null
          }
        })()),
        email: email,
        username: username,
        name: name,
        role: role
      }
      localStorage.setItem('user', JSON.stringify(userInfo))
      
      console.log('✓ OAuth2 callback stored:', userInfo)
      navigate('/', { replace: true })
    } else {
      navigate('/login?error=oauth2_failed', { replace: true })
    }
  }, [searchParams, navigate])

  return (
    <>
      <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet" />
      <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet" />
      <style>{`
        :root {
          --primary: #2563eb;
          --primary-dark: #1e40af;
          --primary-light: #3b82f6;
          --body-bg: #f8fafc;
          --card-bg: #ffffff;
          --muted: #475569;
        }
        * { box-sizing: border-box; }
        body {
          background: linear-gradient(135deg, #f0f9ff 0%, #e0e7ff 100%);
          font-family: 'Segoe UI', -apple-system, sans-serif;
          color: var(--muted);
          min-height: 100vh;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 20px;
          margin: 0;
        }
        .text-center { text-align: center; }
        .text-primary { color: var(--primary) !important; }
        .text-muted { color: var(--muted); }
        .fa-3x { font-size: 2.5em; }
        h5 { font-size: 20px; margin: 1rem 0 0.5rem; }
        p { font-size: 14px; margin: 0.5rem 0; }
        .mt-2 { margin-top: 0.5rem; }
        .mb-3 { margin-bottom: 1rem; }
        .fw-bold { font-weight: 700; }
        .me-2 { margin-right: 0.5rem; }
      `}</style>
      <div className="text-center">
        <i className="fas fa-check-square fa-3x text-primary mb-3"></i>
        <h5 className="fw-bold">TaskFlow</h5>
        <p className="text-muted mt-2">
          <i className="fas fa-spinner fa-spin me-2"></i>Processing login...
        </p>
      </div>
    </>
  )
}
