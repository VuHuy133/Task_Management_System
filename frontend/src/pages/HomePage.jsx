import { useEffect, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import axiosInstance from '../api/axiosInstance'

export default function HomePage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [projects, setProjects] = useState([])
  const [tasks, setTasks] = useState([])
  const [loading, setLoading] = useState(true)
  const [searchTerm, setSearchTerm] = useState('')

  useEffect(() => {
    Promise.all([
      axiosInstance.get('/projects').catch(() => ({ data: { data: [] } })),
    ]).then(([projRes]) => {
      const responseData = projRes.data.data
      // Handle both old format (array) and new pagination format (object with content)
      const projectsList = Array.isArray(responseData) ? responseData : (responseData?.content || [])
      setProjects(projectsList)
    }).finally(() => setLoading(false))
  }, [])

  const handleLogout = async () => {
    await logout()
    navigate('/login?logout=true', { replace: true })
  }

  const initials = (name) => name ? name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2) : 'U'

  const filteredProjects = projects.filter(p =>
    p.name.toLowerCase().includes(searchTerm.toLowerCase())
  )

  return (
    <>
      <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet" />
      <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet" />
      <style>{`
        :root {
          --sidebar-w: 240px;
          --accent: #3b82f6;
          --topbar-h: 64px;
          --body-bg: #f1f5f9;
          --card-bg: #ffffff;
          --border: #e2e8f0;
          --text: #1e293b;
          --muted: #64748b;
        }
        * { box-sizing: border-box; }
        body { background: var(--body-bg); font-family: 'Segoe UI', -apple-system, sans-serif; color: var(--text); margin: 0; }
        .sidebar {
          position: fixed; left: 0; top: 0; bottom: 0;
          width: var(--sidebar-w); background: #0f172a;
          display: flex; flex-direction: column; z-index: 100; overflow-y: auto;
        }
        .sidebar-brand { padding: 20px 20px 16px; border-bottom: 1px solid rgba(255,255,255,0.08); }
        .brand-name { font-size: 17px; font-weight: 700; color: #fff; }
        .brand-sub  { font-size: 11px; color: #94a3b8; }
        .sidebar-nav { padding: 16px 12px; flex: 1; }
        .nav-label { font-size: 10px; font-weight: 600; color: #475569; text-transform: uppercase; letter-spacing: .05em; padding: 10px 8px 4px; }
        .s-link {
          display: flex; align-items: center; gap: 10px;
          padding: 9px 12px; border-radius: 8px;
          color: #94a3b8; text-decoration: none;
          font-size: 13.5px; font-weight: 500; transition: all .15s; margin-bottom: 2px;
        }
        .s-link:hover { background: rgba(255,255,255,0.08); color: #fff; }
        .s-link.active { background: var(--accent); color: #fff; }
        .s-link i { width: 18px; text-align: center; font-size: 13px; }
        .sidebar-footer { padding: 12px; border-top: 1px solid rgba(255,255,255,0.08); }
        .logout-btn {
          display: flex; align-items: center; gap: 10px;
          padding: 9px 12px; border-radius: 8px; color: #f87171;
          background: none; border: none;
          font-size: 13.5px; font-weight: 500; transition: all .15s; width: 100%; cursor: pointer;
        }
        .logout-btn:hover { background: rgba(248,113,113,0.1); color: #ef4444; }
        .main-wrapper { margin-left: var(--sidebar-w); min-height: 100vh; display: flex; flex-direction: column; }
        .topbar {
          height: var(--topbar-h); background: #fff;
          border-bottom: 1px solid var(--border);
          display: flex; align-items: center; padding: 0 24px; gap: 12px;
          position: sticky; top: 0; z-index: 50;
        }
        .topbar-title { font-size: 16px; font-weight: 700; }
        .topbar-right { margin-left: auto; display: flex; align-items: center; gap: 10px; }
        .avatar {
          width: 34px; height: 34px; border-radius: 50%;
          background: var(--accent); color: white;
          display: flex; align-items: center; justify-content: center;
          font-size: 13px; font-weight: 700; flex-shrink: 0;
        }
        .page-content { padding: 24px; flex: 1; }
        .welcome-card {
          background: linear-gradient(135deg, #1d4ed8 0%, #3b82f6 100%);
          border-radius: 14px; padding: 24px; margin-bottom: 24px;
          color: white; display: flex; align-items: center; justify-content: space-between;
        }
        .welcome-card h4 { font-size: 20px; font-weight: 700; margin: 0 0 4px; }
        .welcome-card p  { font-size: 13px; opacity: .85; margin: 0; }
        .stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }
        .stat-card {
          background: var(--card-bg); border: 1px solid var(--border);
          border-radius: 12px; padding: 20px;
          display: flex; align-items: center; gap: 14px; transition: box-shadow .15s;
        }
        .stat-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.08); }
        .stat-icon { width: 44px; height: 44px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 18px; flex-shrink: 0; }
        .stat-val { font-size: 28px; font-weight: 700; line-height: 1; }
        .stat-lbl { font-size: 12px; color: var(--muted); margin-top: 3px; }
        .sec-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 20px; }
        .sec-title { font-size: 15px; font-weight: 700; margin-bottom: 16px; display: flex; align-items: center; justify-content: space-between; }
        .proj-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
        .proj-card {
          background: #f8fafc; border: 1px solid var(--border);
          border-radius: 10px; padding: 14px; text-decoration: none; color: inherit;
          display: block; transition: all .15s;
        }
        .proj-card:hover { border-color: var(--accent); box-shadow: 0 4px 12px rgba(59,130,246,.1); color: inherit; }
        .proj-card h6 { font-size: 13px; font-weight: 700; margin-bottom: 4px; }
        .proj-progress { height: 4px; background: #e2e8f0; border-radius: 2px; overflow: hidden; margin-top: 8px; }
        .proj-progress-bar { height: 100%; background: var(--accent); border-radius: 2px; }
        .btn-create {
          background: var(--accent); color: white; border: none;
          border-radius: 8px; padding: 8px 16px; font-size: 13px; font-weight: 600;
          text-decoration: none; display: inline-flex; align-items: center; gap: 6px;
          transition: opacity .15s; cursor: pointer;
        }
        .btn-create:hover { opacity: .9; color: white; }
        @media (max-width: 1200px) { .stats-grid { grid-template-columns: repeat(2,1fr); } .proj-grid { grid-template-columns: repeat(2,1fr); } }
        @media (max-width: 768px)  { .sidebar { transform: translateX(-100%); } .main-wrapper { margin-left: 0; } .proj-grid { grid-template-columns: 1fr; } }
      `}</style>

      {/* Sidebar */}
      <nav className="sidebar">
        <div className="sidebar-brand">
          <div className="d-flex align-items-center gap-2">
            <i className="fas fa-check-square text-primary"></i>
            <div>
              <div className="brand-name">TaskFlow</div>
              <div className="brand-sub">{user?.role || 'USER'}</div>
            </div>
          </div>
        </div>

        <div className="sidebar-nav">
          <div className="nav-label">Main</div>
          <Link to="/" className="s-link active">
            <i className="fas fa-home"></i> Dashboard
          </Link>
          <Link to="/projects" className="s-link">
            <i className="fas fa-project-diagram"></i> Dự án 
          </Link>
          <Link to="/tasks" className="s-link">
            <i className="fas fa-tasks"></i> Công việc
          </Link>
          <div className="nav-label">Account</div>
          <Link to="/profile" className="s-link">
            <i className="fas fa-user"></i> Hồ sơ
          </Link>
        </div>

        <div className="sidebar-footer">
          <button className="logout-btn" onClick={handleLogout}>
            <i className="fas fa-sign-out-alt"></i> Logout
          </button>
        </div>
      </nav>

      {/* Main */}
      <div className="main-wrapper">
        {/* Topbar */}
        <div className="topbar">
          <span className="topbar-title">Dashboard</span>
          <div className="topbar-right">
            <Link to="/projects/create" className="btn-create">
              <i className="fas fa-plus"></i> New Project
            </Link>
            <div className="avatar">{initials(user?.username || user?.email || 'U')}</div>
          </div>
        </div>

        {/* Content */}
        <div className="page-content">
          {/* Welcome card */}
          <div className="welcome-card">
            <div>
              <h4>Welcome back, {user?.username || 'User'}! 👋</h4>
              <p>Here's what's happening with your projects today.</p>
            </div>
            <i className="fas fa-rocket fa-2x" style={{ opacity: 0.5 }}></i>
          </div>

          {/* Stats */}
          <div className="stats-grid">
            <div className="stat-card">
              <div className="stat-icon" style={{ background: '#dbeafe' }}>
                <i className="fas fa-project-diagram" style={{ color: '#2563eb' }}></i>
              </div>
              <div>
                <div className="stat-val">{projects.length}</div>
                <div className="stat-lbl">Total Projects</div>
              </div>
            </div>
            <div className="stat-card">
              <div className="stat-icon" style={{ background: '#dcfce7' }}>
                <i className="fas fa-check-circle" style={{ color: '#16a34a' }}></i>
              </div>
              <div>
                <div className="stat-val">0</div>
                <div className="stat-lbl">Completed Tasks</div>
              </div>
            </div>
            <div className="stat-card">
              <div className="stat-icon" style={{ background: '#fef3c7' }}>
                <i className="fas fa-clock" style={{ color: '#d97706' }}></i>
              </div>
              <div>
                <div className="stat-val">0</div>
                <div className="stat-lbl">In Progress</div>
              </div>
            </div>
            <div className="stat-card">
              <div className="stat-icon" style={{ background: '#fee2e2' }}>
                <i className="fas fa-exclamation-circle" style={{ color: '#dc2626' }}></i>
              </div>
              <div>
                <div className="stat-val">0</div>
                <div className="stat-lbl">Overdue Tasks</div>
              </div>
            </div>
          </div>

          {/* Projects */}
          <div className="sec-card">
            <div className="sec-title" style={{ gap: 12, alignItems: 'center' }}>
              <span><i className="fas fa-project-diagram me-2 text-primary"></i>All Projects</span>
              <input
                type="text"
                placeholder="Find project..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                style={{
                  flex: '0 1 200px',
                  padding: '6px 10px',
                  border: '1px solid var(--border)',
                  borderRadius: '6px',
                  fontSize: '12px',
                  outline: 'none',
                  transition: 'border-color .15s'
                }}
                onFocus={(e) => e.target.style.borderColor = 'var(--accent)'}
                onBlur={(e) => e.target.style.borderColor = 'var(--border)'}  
              />
              <Link to="/projects" style={{ fontSize: 12, color: 'var(--accent)', textDecoration: 'none', fontWeight: 500 }}>
                See all →
              </Link>
            </div>

            {loading ? (
              <p className="text-muted" style={{ fontSize: 13 }}>
                <i className="fas fa-spinner fa-spin me-2"></i>Loading...
              </p>
            ) : filteredProjects.length === 0 && searchTerm ? (
              <div className="text-center py-4">
                <i className="fas fa-search fa-2x text-muted mb-2"></i>
                <p className="text-muted" style={{ fontSize: 13 }}>No projects match your search.</p>
              </div>
            ) : projects.length === 0 ? (
              <div className="text-center py-4">
                <i className="fas fa-folder-open fa-2x text-muted mb-2"></i>
                <p className="text-muted" style={{ fontSize: 13 }}>No projects yet.</p>
                <Link to="/projects/create" className="btn-create" style={{ marginTop: 8 }}>
                  <i className="fas fa-plus"></i> Create your first project
                </Link>
              </div>
            ) : (
              <div className="proj-grid">
                {filteredProjects.map((p) => (
                  <Link to={`/projects/${p.id}`} key={p.id} className="proj-card">
                    <h6>{p.name}</h6>
                    <span style={{
                      display: 'inline-block',
                      fontSize: 11, fontWeight: 600,
                      padding: '2px 8px', borderRadius: 4,
                      background: p.type === 'PUBLIC' ? '#dbeafe' : '#fee2e2',
                      color: p.type === 'PUBLIC' ? '#1d4ed8' : '#991b1b',
                    }}>
                      {p.type === 'PUBLIC' ? 'Public' : 'Private'}
                    </span>
                  </Link>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </>
  )
}
