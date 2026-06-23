import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Sidebar from '../components/Sidebar'
import Topbar from '../components/Topbar'
import taskService from '../api/taskService'

const statusBadges = {
  'TODO': { bg: '#f1f5f9', color: '#64748b', text: 'Chưa bắt đầu' },
  'DOING': { bg: '#fef3c7', color: '#d97706', text: 'Đang làm' },
  'DONE': { bg: '#dcfce7', color: '#16a34a', text: 'Hoàn thành' },
}

const priorityBadges = {
  'HIGH': { bg: '#fee2e2', color: '#dc2626', text: 'Cao' },
  'MEDIUM': { bg: '#fef3c7', color: '#d97706', text: 'Trung bình' },
  'LOW': { bg: '#dcfce7', color: '#16a34a', text: 'Thấp' },
}

export default function TaskListPage() {
  const [tasks, setTasks] = useState([])
  const [loading, setLoading] = useState(true)
  const [filterStatus, setFilterStatus] = useState('ALL')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)

  useEffect(() => {
    setPage(0)
    loadTasks(0)
  }, [filterStatus])

  useEffect(() => {
    loadTasks(page)
  }, [page])

  const loadTasks = async (pageNum) => {
    try {
      setLoading(true)
      const params = { page: pageNum, size: 30 }
      if (filterStatus !== 'ALL') params.status = filterStatus
      const res = await taskService.getTasks(params)
      const data = res.data
      if (data && data.content) {
        setTasks(data.content)
        setTotalPages(data.totalPages)
        setTotalElements(data.totalElements)
      } else {
        setTasks([])
      }
    } catch (err) {
      console.error('Failed to load tasks', err)
    } finally {
      setLoading(false)
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
        .filters { display: flex; gap: 8px; margin-bottom: 20px; }
        .filter-btn {
          padding: 6px 12px; border-radius: 6px; border: 1px solid var(--border);
          background: white; color: var(--text); font-size: 13px; font-weight: 500;
          cursor: pointer; transition: all .15s;
        }
        .filter-btn.active { background: var(--accent); color: white; border-color: var(--accent); }
        .filter-btn:hover { border-color: var(--accent); }
        .sec-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 20px; }
        .data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
        .data-table th { background: #f8fafc; font-size: 11px; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: .04em; padding: 12px; border-bottom: 2px solid var(--border); }
        .data-table td { padding: 12px; border-bottom: 1px solid #f1f5f9; vertical-align: middle; }
        .data-table tr:last-child td { border-bottom: none; }
        .data-table tr:hover td { background: #f8fafc; }
        .b-status, .b-priority { font-size: 11px; padding: 2px 8px; border-radius: 6px; display: inline-block; }
        .empty-state { text-align: center; padding: 40px 20px; }
        .empty-state i { font-size: 3rem; color: var(--muted); margin-bottom: 16px; }
        .pagination-container { display: flex; align-items: center; justify-content: center; gap: 4px; margin-top: 20px; }
        .pagination-btn { padding: 8px 12px; border: 2px solid #3b82f6; border-radius: 8px; font-size: 13px; cursor: pointer; background: #ffffff; color: #1e293b; }
      `}</style>

      <Sidebar isAdmin={false} />
      <div className="main-wrapper">
        <Topbar
          title="Công việc của tôi"
          action={<Link to="/tasks/create" className="btn-create"><i className="fas fa-plus"></i> Tạo công việc</Link>}
        />

        <div className="page-content">
          <div className="page-header">
            <h3>Công việc của tôi</h3>
            <p>Quản lý các công việc của bạn</p>
          </div>

          <div className="filters">
            <button className={`filter-btn ${filterStatus === 'ALL' ? 'active' : ''}`} onClick={() => setFilterStatus('ALL')}>
              Tất cả ({totalElements})
            </button>
            <button className={`filter-btn ${filterStatus === 'TODO' ? 'active' : ''}`} onClick={() => setFilterStatus('TODO')}>
              <i className="fas fa-circle-notch me-1"></i> Chưa bắt đầu
            </button>
            <button className={`filter-btn ${filterStatus === 'DOING' ? 'active' : ''}`} onClick={() => setFilterStatus('DOING')}>
              <i className="fas fa-circle-notch me-1"></i> Đang làm
            </button>
            <button className={`filter-btn ${filterStatus === 'DONE' ? 'active' : ''}`} onClick={() => setFilterStatus('DONE')}>
              <i className="fas fa-check-circle me-1"></i> Hoàn thành
            </button>
          </div>

          {loading ? (
            <div className="text-center py-4">
              <i className="fas fa-spinner fa-spin me-2"></i>Đang tải...
            </div>
          ) : tasks.length === 0 ? (
            <div className="empty-state">
              <i className="fas fa-tasks"></i>
              <p>Chưa có công việc nào</p>
              <Link to="/tasks/create" className="btn-create">
                <i className="fas fa-plus"></i> Tạo công việc đầu tiên
              </Link>
            </div>
          ) : (
            <>
              <div className="sec-card">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Công việc</th>
                      <th>Dự án</th>
                      <th>Trạng thái</th>
                      <th>Độ ưu tiên</th>
                      <th>Hạn chót</th>
                      <th style={{ textAlign: 'right' }}>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {tasks.map((task) => {
                      const status = statusBadges[task.status] || statusBadges.TODO
                      const priority = priorityBadges[task.priority] || priorityBadges.MEDIUM
                      return (
                        <tr key={task.id}>
                          <td><Link to={`/tasks/${task.id}`} style={{ textDecoration: 'none', color: 'inherit', fontWeight: 600 }}>{task.title}</Link></td>
                          <td>{task.project?.name || '-'}</td>
                          <td>
                            <span className="b-status" style={{ background: status.bg, color: status.color }}>
                              {status.text}
                            </span>
                          </td>
                          <td>
                            <span className="b-priority" style={{ background: priority.bg, color: priority.color }}>
                              {priority.text}
                            </span>
                          </td>
                          <td>{task.dueDate ? new Date(task.dueDate).toLocaleDateString('vi-VN') : '-'}</td>
                          <td style={{ textAlign: 'right' }}>
                            <Link to={`/tasks/${task.id}`} className="btn btn-sm btn-outline-primary" style={{ fontSize: '12px' }}>Xem</Link>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
              <div className="pagination-container">
                {renderPaginationButtons()}
              </div>
              <div style={{ textAlign: 'center', marginTop: 16, fontSize: 12, color: '#64748b' }}>
                Trang {page + 1} / {totalPages} (Tổng: {totalElements} công việc)
              </div>
            </>
          )}
        </div>
      </div>
    </>
  )
}
