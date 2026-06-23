import { useEffect, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { DragDropContext, Droppable, Draggable } from '@hello-pangea/dnd'
import projectService from '../api/projectService'
import taskService from '../api/taskService'
import { useAuth } from '../context/AuthContext'
import {
  groupTasksByStatus,
  buildReorderPayload,
  getDroppableId,
  getDraggableId,
} from '../utils/dragDropUtils'

const PRIORITY_LABEL = { HIGH: 'Cao', MEDIUM: 'TB', LOW: 'Thấp' }
const PRIORITY_STYLE = {
  HIGH: { background: '#fee2e2', color: '#dc2626' },
  MEDIUM: { background: '#fef3c7', color: '#d97706' },
  LOW: { background: '#dcfce7', color: '#16a34a' },
}

export default function ProjectDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [project, setProject] = useState(null)
  const [tasks, setTasks] = useState([])
  const [members, setMembers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [newMemberEmail, setNewMemberEmail] = useState('')
  const [memberError, setMemberError] = useState('')
  const [showMemberForm, setShowMemberForm] = useState(false)
  const [isReordering, setIsReordering] = useState(false)

  useEffect(() => {
    loadProject()
  }, [id])

  const loadProject = async () => {
    try {
      setLoading(true)
      setError('')
      const [projRes, tasksRes, membersRes] = await Promise.all([
        projectService.getProjectById(id),
        projectService.getProjectTasks(id).catch(() => ({ data: [] })),
        projectService.getProjectMembers(id).catch(() => ({ data: [] })),
      ])
      setProject(projRes.data || projRes)
      setTasks(tasksRes.data || tasksRes || [])
      setMembers(membersRes.data || membersRes || [])
    } catch (err) {
      setError('Loi tai du an: ' + (err.response?.data?.message || err.message))
    } finally {
      setLoading(false)
    }
  }

  const handleJoin = async () => {
    try {
      await projectService.joinProject(id)
      loadProject()
    } catch (err) {
      alert(err.response?.data?.message || 'Tham gia that bai')
    }
  }

  const handleLeave = async () => {
    if (!window.confirm('Ban co chac chan muon roi khoi du an nay?')) return
    try {
      await projectService.leaveProject(id, user?.id)
      loadProject()
    } catch (err) {
      alert(err.response?.data?.message || 'Roi du an that bai')
    }
  }

  const handleDelete = async () => {
    if (!window.confirm('Xoa du an nay? Hanh dong khong the hoan tac.')) return
    try {
      await projectService.deleteProject(id)
      navigate('/projects')
    } catch (err) {
      alert(err.response?.data?.message || 'Xoa du an that bai')
    }
  }

  const handleAddMember = async (e) => {
    e.preventDefault()
    if (!newMemberEmail) return
    setMemberError('')
    try {
      await projectService.addUserToProject(id, newMemberEmail, 'MEMBER')
      setNewMemberEmail('')
      setShowMemberForm(false)
      loadProject()
    } catch (err) {
      setMemberError(err.response?.data?.message || 'Them thanh vien that bai')
    }
  }

  const handleRemoveMember = async (memberId) => {
    if (!window.confirm('Xoa thanh vien nay khoi du an?')) return
    try {
      await projectService.leaveProject(id, memberId)
      loadProject()
    } catch (err) {
      alert(err.response?.data?.message || 'Xoa thanh vien that bai')
    }
  }

  const handleDragEnd = async (result) => {
    const { source, destination, draggableId } = result

    // If dropped outside a valid area
    if (!destination) return

    // If dropped in the same position
    if (
      source.droppableId === destination.droppableId &&
      source.index === destination.index
    ) {
      return
    }

    try {
      setIsReordering(true)
      
      // Get source and destination status
      const sourceStatus = source.droppableId.replace('status-', '')
      const destStatus = destination.droppableId.replace('status-', '')

      // Create groups of tasks by status
      const statusGroups = groupTasksByStatus(tasks)

      // Remove task from source
      const sourceTaskIndex = statusGroups[sourceStatus].findIndex(
        t => t.id.toString() === draggableId.replace('task-', '')
      )
      const [movedTask] = statusGroups[sourceStatus].splice(sourceTaskIndex, 1)

      // Add task to destination
      movedTask.status = destStatus
      statusGroups[destStatus].splice(destination.index, 0, movedTask)

      // Build reorder payload
      const payload = { tasks: buildReorderPayload(statusGroups) }

      // Optimistic update
      const newTasks = [
        ...statusGroups.TODO,
        ...statusGroups.DOING,
        ...statusGroups.DONE,
      ]
      setTasks(newTasks)

      // API call
      const res = await taskService.reorderTasks(id, payload)
      if (res.success) {
        setTasks(res.data || newTasks)
      } else {
        throw new Error(res.message || 'Reorder failed')
      }
    } catch (err) {
      // Revert on error
      await loadProject()
      alert('Loi khi cap nhat thu tu: ' + (err.response?.data?.message || err.message))
    } finally {
      setIsReordering(false)
    }
  }

  if (loading) return <div className="text-center py-5"><i className="fas fa-spinner fa-spin fa-2x"></i></div>
  if (error) return <div className="text-center py-5" style={{ color: '#dc2626' }}><i className="fas fa-exclamation-circle"></i> {error}</div>
  if (!project) return <div className="text-center py-5">Khong tim thay du an.</div>

  // Group tasks by status and sort by position
  const tasksByStatus = groupTasksByStatus(tasks)
  const todoTasks = tasksByStatus.TODO
  const doingTasks = tasksByStatus.DOING
  const doneTasks = tasksByStatus.DONE

  const isAdmin = user?.role === 'ADMIN' || user?.roles?.includes('ADMIN')
  const isOwner = project.owner?.id === user?.id
  const isMember = project.joinedByCurrentUser || members.some(m => (m.user?.id || m.userId) === user?.id)
  const doneCount = doneTasks.length
  const totalCount = tasks.length

  const TaskCard = ({ task, index }) => (
    <Draggable
      draggableId={getDraggableId(task.id)}
      index={index}
      isDragDisabled={!isMember && !isAdmin}
    >
      {(provided, snapshot) => (
        <div
          ref={provided.innerRef}
          {...provided.draggableProps}
          {...provided.dragHandleProps}
          onClick={() => navigate('/tasks/' + task.id)}
          style={{
            background: snapshot.isDragging ? '#e0e7ff' : 'white',
            border: '1px solid #e1e4e8',
            borderRadius: '6px',
            padding: '12px',
            marginBottom: '8px',
            cursor: isMember || isAdmin ? 'grab' : 'pointer',
            transition: 'box-shadow 0.2s',
            opacity: snapshot.isDragging ? 0.5 : 1,
            ...provided.draggableProps.style,
          }}
          onMouseEnter={e => !snapshot.isDragging && (e.currentTarget.style.boxShadow = '0 2px 8px rgba(0,0,0,0.15)')}
          onMouseLeave={e => !snapshot.isDragging && (e.currentTarget.style.boxShadow = 'none')}
        >
          <div style={{ fontWeight: 600, fontSize: '14px', marginBottom: '6px' }}>{task.title}</div>
          {task.priority && (
            <span style={{ ...(PRIORITY_STYLE[task.priority] || {}), fontSize: '11px', padding: '2px 6px', borderRadius: '4px', marginRight: '6px' }}>
              {PRIORITY_LABEL[task.priority] || task.priority}
            </span>
          )}
          {task.assignee && (
            <span style={{ fontSize: '11px', color: '#666' }}>
              <i className="fas fa-user" style={{ marginRight: '3px' }}></i>
              {task.assignee.fullName || task.assignee.username}
            </span>
          )}
        </div>
      )}
    </Draggable>
  )

  const KanbanColumn = ({ title, color, tasks: colTasks, status }) => (
    <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
      <div style={{
        background: color,
        borderRadius: '8px 8px 0 0',
        padding: '12px 16px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
      }}>
        <strong style={{ fontSize: '14px' }}>{title}</strong>
        <span style={{ background: 'rgba(0,0,0,0.1)', borderRadius: '12px', padding: '2px 8px', fontSize: '12px', fontWeight: 700 }}>
          {colTasks.length}
        </span>
      </div>
      <Droppable droppableId={getDroppableId(status)}>
        {(provided, snapshot) => (
          <div
            ref={provided.innerRef}
            {...provided.droppableProps}
            style={{
              background: snapshot.isDraggingOver ? '#f0f4ff' : '#f8f9fa',
              padding: '12px',
              minHeight: '200px',
              maxHeight: '400px',
              overflow: 'auto',
              transition: 'background-color 0.2s',
              flex: 1,
            }}
          >
            {colTasks.map((task, index) => (
              <TaskCard key={task.id} task={task} index={index} />
            ))}
            {provided.placeholder}
          </div>
        )}
      </Droppable>
      {(isMember || isAdmin) && (
        <Link
          to={'/tasks/create?projectId=' + id + '&status=' + status}
          style={{
            display: 'block',
            textAlign: 'center',
            padding: '12px',
            border: '2px dashed #d1d5db',
            borderRadius: '0 0 8px 8px',
            color: '#666',
            fontSize: '13px',
            textDecoration: 'none',
            background: '#f8f9fa',
            transition: 'all 0.2s',
          }}
          onMouseEnter={(e) => {
            e.target.style.borderColor = '#667eea'
            e.target.style.color = '#667eea'
            e.target.style.background = '#f0f4ff'
          }}
          onMouseLeave={(e) => {
            e.target.style.borderColor = '#d1d5db'
            e.target.style.color = '#666'
            e.target.style.background = '#f8f9fa'
          }}
        >
          + Them cong viec
        </Link>
      )}
    </div>
  )

  return (
    <>
      <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet" />
      <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet" />
      <style>{`
        body { background: #f5f7fa; font-family: 'Segoe UI', sans-serif; }
        .navbar-custom { background: white; box-shadow: 0 2px 8px rgba(0,0,0,0.1); padding: 0.75rem 2rem; display: flex; align-items: center; justify-content: space-between; }
        .btn { padding: 7px 14px; border-radius: 6px; font-weight: 600; border: none; cursor: pointer; font-size: 13px; text-decoration: none; display: inline-flex; align-items: center; gap: 5px; }
        .btn-primary { background: #667eea; color: white; }
        .btn-primary:hover { background: #5568d3; color: white; }
        .btn-success { background: #22c55e; color: white; }
        .btn-success:hover { background: #16a34a; color: white; }
        .btn-warning { background: #f59e0b; color: white; }
        .btn-warning:hover { background: #d97706; color: white; }
        .btn-danger { background: #ef4444; color: white; }
        .btn-danger:hover { background: #dc2626; color: white; }
        .btn-outline { background: white; color: #555; border: 1px solid #d1d5db; }
        .btn-outline:hover { background: #f9fafb; }
        .page-container { max-width: 1400px; margin: 0 auto; padding: 20px; }
        .project-header { background: white; border: 1px solid #e1e4e8; border-radius: 8px; padding: 24px; margin-bottom: 24px; }
        .stat-pill { display: inline-flex; align-items: center; gap: 6px; background: #f1f5f9; padding: 6px 12px; border-radius: 20px; font-size: 13px; color: #555; margin-right: 8px; }
        .sidebar { width: 280px; flex-shrink: 0; }
        .sidebar-card { background: white; border: 1px solid #e1e4e8; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
        .sidebar-title { font-weight: 700; font-size: 14px; margin-bottom: 12px; color: #333; }
        .kanban-board { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
        @media (max-width: 900px) { .kanban-board { grid-template-columns: 1fr; } .sidebar { width: 100%; } }
      `}</style>

      <nav className="navbar-custom">
        <button onClick={() => navigate('/projects')} style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '15px', fontWeight: 'bold', color: '#333' }}>
          &larr; Quay lai
        </button>
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          {!isMember && !isAdmin && (
            <button onClick={handleJoin} className="btn btn-success">
              <i className="fas fa-plus-circle"></i> Tham gia du an
            </button>
          )}
          {isMember && !isOwner && (
            <button onClick={handleLeave} className="btn btn-warning">
              <i className="fas fa-sign-out-alt"></i> Roi du an
            </button>
          )}
          {(isAdmin || isOwner) && (
            <Link to={'/projects/' + id + '/edit'} className="btn btn-outline">
              <i className="fas fa-edit"></i> Chinh sua
            </Link>
          )}
          {isAdmin && (
            <button onClick={handleDelete} className="btn btn-danger">
              <i className="fas fa-trash"></i> Xoa du an
            </button>
          )}
        </div>
      </nav>

      <div className="page-container">
        <div className="project-header">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '12px' }}>
            <div style={{ flex: 1, minWidth: '300px' }}>
              <h1 style={{ fontSize: '24px', fontWeight: 700, marginBottom: '8px' }}>{project.name}</h1>
              <p style={{ color: '#666', fontSize: '14px', marginBottom: '12px' }}>{project.description || 'Khong co mo ta'}</p>
              <span style={{
                background: project.type === 'PUBLIC' ? '#e0e7ff' : '#fef2f2',
                color: project.type === 'PUBLIC' ? '#1d4ed8' : '#991b1b',
                fontSize: '12px', padding: '3px 10px', borderRadius: '12px', fontWeight: 600, marginBottom: '12px', display: 'inline-block',
              }}>
                {project.type === 'PUBLIC' ? 'Public' : 'Private'}
              </span>

              {/* Progress Bar */}
              <div style={{ marginTop: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                  <span style={{ fontSize: '13px', fontWeight: 600, color: '#333' }}>
                    <i className="fas fa-chart-pie" style={{ marginRight: '6px', color: '#667eea' }}></i>
                    Tien do hoan thanh
                  </span>
                  <span style={{ fontSize: '13px', fontWeight: 700, color: '#667eea' }}>
                    {totalCount === 0 ? '0%' : Math.round((doneCount / totalCount) * 100) + '%'}
                  </span>
                </div>
                <div style={{
                  width: '100%',
                  height: '8px',
                  background: '#e5e7eb',
                  borderRadius: '4px',
                  overflow: 'hidden',
                }}>
                  <div style={{
                    height: '100%',
                    width: totalCount === 0 ? '0%' : (doneCount / totalCount) * 100 + '%',
                    background: doneCount === totalCount && totalCount > 0 ? '#22c55e' : '#667eea',
                    transition: 'width 0.3s ease',
                    borderRadius: '4px',
                  }}></div>
                </div>
                <div style={{ marginTop: '6px', fontSize: '12px', color: '#666' }}>
                  {doneCount} / {totalCount} cong viec hoan thanh
                </div>
              </div>
            </div>
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
              <div className="stat-pill"><i className="fas fa-tasks"></i> {totalCount} cong viec</div>
              <div className="stat-pill"><i className="fas fa-check-circle"></i> {doneCount} hoan thanh</div>
              <div className="stat-pill"><i className="fas fa-users"></i> {members.length} thanh vien</div>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '20px', alignItems: 'flex-start' }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <DragDropContext onDragEnd={handleDragEnd}>
              <div className="kanban-board" style={{ opacity: isReordering ? 0.6 : 1 }}>
                <KanbanColumn title="TODO" color="#dbeafe" tasks={todoTasks} status="TODO" />
                <KanbanColumn title="DOING" color="#fef3c7" tasks={doingTasks} status="DOING" />
                <KanbanColumn title="DONE" color="#dcfce7" tasks={doneTasks} status="DONE" />
              </div>
            </DragDropContext>
          </div>

          <div className="sidebar">
            <div className="sidebar-card">
              <div className="sidebar-title">
                <i className="fas fa-users" style={{ marginRight: '6px', color: '#667eea' }}></i>
                Thanh vien ({members.length})
              </div>
              <div style={{ marginBottom: '10px' }}>
                {members.length === 0 ? (
                  <p style={{ fontSize: '13px', color: '#999' }}>Chua co thanh vien</p>
                ) : (
                  members.map(m => {
                    const memberId = m.user?.id || m.userId
                    const memberName = m.user?.fullName || m.user?.username || m.fullName || m.username
                    const isCurrentUser = memberId === user?.id
                    return (
                      <div key={m.id || memberId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '4px 0' }}>
                        <span style={{ fontSize: '13px' }}>
                          {memberName}
                          {isCurrentUser && (
                            <span style={{ background: '#e0e7ff', color: '#4338ca', fontSize: '10px', padding: '1px 5px', borderRadius: '4px', marginLeft: '5px' }}>
                              Ban
                            </span>
                          )}
                        </span>
                        {(isAdmin || isOwner) && !isCurrentUser && (
                          <button
                            onClick={() => handleRemoveMember(memberId)}
                            style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#ef4444', fontSize: '12px' }}
                            title="Xoa thanh vien"
                          >
                            <i className="fas fa-times"></i>
                          </button>
                        )}
                      </div>
                    )
                  })
                )}
              </div>
              {(isAdmin || isOwner) && (
                <>
                  <button onClick={() => setShowMemberForm(v => !v)} className="btn btn-outline" style={{ width: '100%', justifyContent: 'center' }}>
                    <i className="fas fa-user-plus"></i> Them thanh vien
                  </button>
                  {showMemberForm && (
                    <form onSubmit={handleAddMember} style={{ marginTop: '10px' }}>
                      <input
                        type="email"
                        placeholder="Email thanh vien"
                        value={newMemberEmail}
                        onChange={e => setNewMemberEmail(e.target.value)}
                        style={{ width: '100%', padding: '7px 10px', border: '1px solid #e1e4e8', borderRadius: '6px', marginBottom: '6px', fontSize: '13px', boxSizing: 'border-box' }}
                      />
                      {memberError && <p style={{ color: '#dc2626', fontSize: '12px', marginBottom: '6px' }}>{memberError}</p>}
                      <button type="submit" className="btn btn-primary" style={{ width: '100%', justifyContent: 'center' }}>Them</button>
                    </form>
                  )}
                </>
              )}
            </div>

            {project.owner && (
              <div className="sidebar-card">
                <div className="sidebar-title">
                  <i className="fas fa-crown" style={{ marginRight: '6px', color: '#f59e0b' }}></i>
                  Chu du an
                </div>
                <p style={{ fontSize: '13px' }}>{project.owner.fullName || project.owner.username}</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </>
  )
}
