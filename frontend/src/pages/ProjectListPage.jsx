import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Sidebar from '../components/Sidebar'
import Topbar from '../components/Topbar'
import projectService from '../api/projectService'
import axiosInstance from '../api/axiosInstance'

export default function ProjectListPage() {
  const navigate = useNavigate()
  const [projects, setProjects] = useState([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [filterType, setFilterType] = useState('ALL')

  useEffect(() => {
    loadProjects(0, filterType)
  }, [filterType])

  useEffect(() => {
    loadProjects(page, filterType)
  }, [page])

  const loadProjects = async (pageNum, filter = filterType) => {
    try {
      setLoading(true)
      const params = { page: pageNum, size: 30, filterType: filter }
      const res = await projectService.getProjects(params)
      const data = res.data
      if (data && data.content) {
        setProjects(data.content)
        setTotalPages(data.totalPages)
        setTotalElements(data.totalElements)
      } else {
        setProjects([])
      }
    } catch (err) {
      console.error('Failed to load projects', err)
    } finally {
      setLoading(false)
    }
  }

  const handleFilterChange = (newFilter) => {
    if (newFilter !== filterType) {
      setFilterType(newFilter)
      setPage(0) // Reset to first page when filter changes
    }
  }

  const goToPage = (pageNum) => {
    setPage(pageNum)
  }

  const renderPaginationButtons = () => {
    if (totalPages <= 1) return null
    
    const buttons = []
    const maxButtons = 5
    let startPage = Math.max(0, page - Math.floor(maxButtons / 2))
    let endPage = Math.min(totalPages - 1, startPage + maxButtons - 1)
    
    if (endPage - startPage < maxButtons - 1) {
      startPage = Math.max(0, endPage - maxButtons + 1)
    }
    
    buttons.push(
      <button key="first" onClick={() => goToPage(0)} style={{ ...paginationBtn, cursor: 'pointer' }}>«</button>
    )
    
    if (startPage > 0) {
      buttons.push(<span key="dots1" style={{ ...paginationBtn, cursor: 'default' }}>...</span>)
    }
    
    for (let i = startPage; i <= endPage; i++) {
      buttons.push(
        <button
          key={i}
          onClick={() => goToPage(i)}
          style={{
            ...paginationBtn,
            background: page === i ? '#3b82f6' : '#ffffff',
            color: page === i ? '#ffffff' : '#1e293b',
            borderColor: page === i ? '#3b82f6' : '#e2e8f0',
            cursor: 'pointer'
          }}
        >
          {i + 1}
        </button>
      )
    }
    
    if (endPage < totalPages - 1) {
      buttons.push(<span key="dots2" style={{ ...paginationBtn, cursor: 'default' }}>...</span>)
    }
    
    buttons.push(
      <button key="last" onClick={() => goToPage(totalPages - 1)} style={{ ...paginationBtn, cursor: 'pointer' }}>»</button>
    )
    
    return buttons
  }

  const paginationBtn = {
    padding: '8px 12px',
    border: '2px solid #3b82f6',
    borderRadius: '8px',
    fontSize: '13px',
    background: '#ffffff',
    color: '#1e293b',
    margin: '0 2px'
  }

  const filterBtn = (label, value) => ({
    padding: '8px 16px',
    border: 'none',
    borderRadius: '8px',
    fontSize: '13px',
    fontWeight: '600',
    cursor: 'pointer',
    background: filterType === value ? '#3b82f6' : '#ffffff',
    color: filterType === value ? '#ffffff' : '#1e293b',
    borderBottom: filterType === value ? '3px solid #3b82f6' : '3px solid transparent',
    transition: 'all .15s',
    marginRight: '8px',
  })

  return (
    <>
      <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet" />
      <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet" />
      <style>{`
        :root {
          --sidebar-w: 240px;
          --sidebar-bg: #0f172a;
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
        .main-wrapper { margin-left: var(--sidebar-w); min-height: 100vh; display: flex; flex-direction: column; }
        .page-content { padding: 24px; flex: 1; }
        .page-header { margin-bottom: 24px; }
        .page-header h3 { font-size: 22px; font-weight: 700; margin: 0 0 4px; }
        .page-header p { font-size: 13px; color: var(--muted); margin: 0; }
        .filter-buttons { display: flex; gap: 8px; margin-bottom: 20px; margin-top: 16px; flex-wrap: wrap; }
        .filter-btn { padding: 8px 16px; border: none; border-radius: 8px; font-size: 13px; font-weight: 600; cursor: pointer; background: #ffffff; color: #1e293b; border-bottom: 3px solid transparent; transition: all .15s; }
        .filter-btn:hover { background: #f1f5f9; }
        .filter-btn.active { background: #3b82f6; color: #ffffff; border-bottom-color: #3b82f6; }
        .proj-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
        .proj-card {
          background: var(--card-bg); border: 1px solid var(--border);
          border-radius: 12px; padding: 16px; text-decoration: none; color: inherit;
          display: block; transition: all .15s;
        }
        .proj-card:hover { border-color: var(--accent); box-shadow: 0 4px 16px rgba(59,130,246,.12); color: inherit; }
        .proj-card h6 { font-size: 14px; font-weight: 700; margin-bottom: 4px; margin: 0 0 4px; }
        .proj-card p { font-size: 12px; color: var(--muted); margin: 0 0 8px; }
        .proj-progress { height: 4px; background: #e2e8f0; border-radius: 2px; overflow: hidden; }
        .proj-progress-bar { height: 100%; background: var(--accent); border-radius: 2px; }
        .empty-state { text-align: center; padding: 40px 20px; }
        .empty-state i { font-size: 3rem; color: var(--muted); margin-bottom: 16px; }
        .empty-state p { color: var(--muted); margin-bottom: 20px; }
        .pagination-container { display: flex; align-items: center; justify-content: center; gap: 4px; margin-top: 20px; }
        .pagination-btn { padding: 8px 12px; border: 2px solid #3b82f6; border-radius: 8px; font-size: 13px; cursor: pointer; background: #ffffff; color: #1e293b; }
        @media (max-width: 1200px) { .proj-grid { grid-template-columns: repeat(2, 1fr); } }
        @media (max-width: 768px) { .proj-grid { grid-template-columns: 1fr; } }
      `}</style>

      <Sidebar isAdmin={false} />
      <div className="main-wrapper">
        <Topbar
          title="Dự án của tôi"
          action={<Link to="/projects/create" className="btn-create"><i className="fas fa-plus"></i> Tạo dự án</Link>}
        />

        <div className="page-content">
          <div className="page-header">
            <h3>Dự án của tôi</h3>
            <p>Quản lý các dự án của bạn</p>
          </div>

          {/* Filter Buttons */}
          <div className="filter-buttons">
            <button
              className={`filter-btn ${filterType === 'ALL' ? 'active' : ''}`}
              onClick={() => handleFilterChange('ALL')}
            >
              Tất cả dự án
            </button>
            <button
              className={`filter-btn ${filterType === 'JOINED' ? 'active' : ''}`}
              onClick={() => handleFilterChange('JOINED')}
            >
              Dự án đã tham gia
            </button>
            <button
              className={`filter-btn ${filterType === 'JOINED_PUBLIC' ? 'active' : ''}`}
              onClick={() => handleFilterChange('JOINED_PUBLIC')}
            >
              Dự án Public (đã tham gia)
            </button>
            <button
              className={`filter-btn ${filterType === 'JOINED_PRIVATE' ? 'active' : ''}`}
              onClick={() => handleFilterChange('JOINED_PRIVATE')}
            >
              Dự án Private (đã tham gia)
            </button>
          </div>

          {loading ? (
            <div className="text-center py-4">
              <i className="fas fa-spinner fa-spin me-2"></i>Đang tải...
            </div>
          ) : projects.length === 0 ? (
            <div className="empty-state">
              <i className="fas fa-folder-open"></i>
              <p>Chưa có dự án nào</p>
              <Link to="/projects/create" className="btn-create">
                <i className="fas fa-plus"></i> Tạo dự án đầu tiên
              </Link>
            </div>
          ) : (
            <>
              <div className="proj-grid">
                {projects.map((proj) => (
                  <Link to={`/projects/${proj.id}`} key={proj.id} className="proj-card">
                    <h6>{proj.name}</h6>
                    <span style={{
                      display: 'inline-block',
                      fontSize: 11, fontWeight: 600,
                      padding: '2px 8px', borderRadius: 4,
                      background: proj.type === 'PUBLIC' ? '#dbeafe' : '#fee2e2',
                      color: proj.type === 'PUBLIC' ? '#1d4ed8' : '#991b1b',
                    }}>
                      {proj.type === 'PUBLIC' ? 'Public' : 'Private'}
                    </span>
                  </Link>
                ))}
              </div>
              <div className="pagination-container">
                {renderPaginationButtons()}
              </div>
              <div style={{ textAlign: 'center', marginTop: 16, fontSize: 12, color: '#64748b' }}>
                Trang {page + 1} / {totalPages} (Tổng: {totalElements} dự án)
              </div>
            </>
          )}
        </div>
      </div>
    </>
  )
}
