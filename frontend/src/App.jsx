import { useEffect, useMemo, useState } from 'react'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''
const TOKEN_KEY = 'daybook.jwt'
const USER_KEY = 'daybook.username'

const moods = [
  { value: 'HAPPY', label: 'Good', face: '☀', className: 'sunny' },
  { value: 'ANXIOUS', label: 'Anxious', face: '〰', className: 'cloudy' },
  { value: 'SAD', label: 'Low', face: '☂', className: 'rainy' },
  { value: 'ANGRY', label: 'Frustrated', face: '✳', className: 'stormy' },
]

function moodFor(value) {
  return moods.find((mood) => mood.value === value) ?? null
}

function friendlyDate(value) {
  if (!value) return 'Just now'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Recently'
  return new Intl.DateTimeFormat(undefined, {
    weekday: 'short', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
  }).format(date)
}

async function request(path, { token, method = 'GET', body, allowEmpty = false } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`
  let response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new Error('Could not reach your journal. Check that the Spring Boot server is running.')
  }

  if (allowEmpty && response.status === 404) return []
  const raw = await response.text()
  let data = raw
  try { data = raw ? JSON.parse(raw) : null } catch { /* The login endpoint returns plain text. */ }
  if (!response.ok) {
    const message = typeof data === 'string' && data ? data : data?.message
    if (response.status === 401 || response.status === 403) throw new Error('Your session expired. Please sign in again.')
    throw new Error(message || `Request failed (${response.status})`)
  }
  return data
}

function Icon({ name, size = 18 }) {
  const common = { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.7, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true }
  const paths = {
    book: <><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v17H6.5A2.5 2.5 0 0 0 4 22z"/><path d="M4 5.5v14A2.5 2.5 0 0 1 6.5 17H20M8 7h8M8 11h7"/></>,
    plus: <><path d="M12 5v14M5 12h14"/></>,
    search: <><circle cx="10.8" cy="10.8" r="6.8"/><path d="m16 16 4.5 4.5"/></>,
    logout: <><path d="M10 17l5-5-5-5M15 12H3"/><path d="M12 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-6"/></>,
    close: <><path d="m18 6-12 12M6 6l12 12"/></>,
    edit: <><path d="m15 5 4 4M4 20l4.2-.8L20 7.4 16.6 4 4.8 15.8z"/></>,
    trash: <><path d="M4 7h16M10 11v6M14 11v6M5.5 7l1 14h11l1-14M9 7V4h6v3"/></>,
    arrow: <><path d="M5 12h14M13 6l6 6-6 6"/></>,
    lock: <><rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 1 1 8 0v3M12 14v3"/></>,
    leaf: <><path d="M20 4c-8 0-14 3-14 10a6 6 0 0 0 6 6c7 0 8-8 8-16Z"/><path d="M4 21c2-5 6-8 11-11"/></>,
  }
  return <svg {...common}>{paths[name]}</svg>
}

function ThemeToggle({ theme, onToggle }) {
  const isDark = theme === 'dark'
  return <button className="theme-toggle" type="button" onClick={onToggle} aria-label={`Switch to ${isDark ? 'light' : 'dark'} mode`} title={`Switch to ${isDark ? 'light' : 'dark'} mode`}><span aria-hidden="true">{isDark ? '☼' : '☾'}</span></button>
}

function Brand({ compact = false }) {
  return <div className={`brand ${compact ? 'brand-compact' : ''}`}><span className="brand-mark"><Icon name="book" size={19} /></span><span>daybook</span></div>
}

function AuthScreen({ onAuth, initialError, theme, onToggleTheme }) {
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ username: '', email: '', password: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (initialError) setError(initialError)
  }, [initialError])

  function update(event) {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }))
    setError('')
  }

  async function submit(event) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      if (mode === 'signup') {
        await request('/public/signup', {
          method: 'POST',
          body: { userName: form.username.trim(), email: form.email.trim(), password: form.password, sentimentAnalysis: false },
        })
      }
      const token = await request('/public/login', {
        method: 'POST', body: { userName: form.username.trim(), password: form.password },
      })
      if (typeof token !== 'string' || !token.trim()) throw new Error('Sign in worked, but the server did not return a valid token.')
      onAuth(token, form.username.trim())
    } catch (err) {
      setError(err.message || 'Something went wrong. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  async function googleLogin() {
    window.location.assign(`${API_BASE}/auth/google/login`)
  }

  return (
    <main className="auth-layout">
      <section className="auth-story">
        <Brand />
        <div className="story-copy">
          <span className="eyebrow"><span className="eyebrow-dot" /> A softer place to land</span>
          <h1>Your thoughts<br />deserve <em>room.</em></h1>
          <p>A private little corner to untangle your day, notice the small things, and hear yourself think.</p>
        </div>
        <div className="story-bottom"><span className="story-line" /><span>Make space for what matters.</span></div>
        <div className="story-orbit orbit-one" /><div className="story-orbit orbit-two" />
        <span className="story-spark spark-one">✳</span><span className="story-spark spark-two">✦</span>
      </section>

      <section className="auth-panel">
        <div className="auth-panel-top"><span className="mobile-brand"><Brand compact /></span><div className="auth-panel-actions"><ThemeToggle theme={theme} onToggle={onToggleTheme} /><span className="auth-prompt">{mode === 'login' ? 'New around here?' : 'Already have an account?'} <button className="text-button" onClick={() => { setMode(mode === 'login' ? 'signup' : 'login'); setError('') }}>{mode === 'login' ? 'Create account' : 'Sign in'}</button></span></div></div>
        <div className="auth-card">
          <div className="auth-icon"><Icon name="leaf" size={22} /></div>
          <p className="eyebrow muted">YOUR PERSONAL JOURNAL</p>
          <h2>{mode === 'login' ? 'Welcome back.' : 'Let’s make a little space.'}</h2>
          <p className="auth-subtitle">{mode === 'login' ? 'Pick up right where you left off.' : 'A few details, then it’s all yours.'}</p>
          <form onSubmit={submit} className="auth-form">
            <label>Username<input name="username" autoComplete="username" placeholder="Your username" value={form.username} onChange={update} required /></label>
            {mode === 'signup' && <label>Email address<input name="email" type="email" autoComplete="email" placeholder="you@example.com" value={form.email} onChange={update} required /></label>}
            <label>Password<input name="password" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} placeholder={mode === 'login' ? 'Your password' : 'At least 8 characters'} minLength={mode === 'signup' ? 8 : undefined} value={form.password} onChange={update} required /></label>
            {error && <div className="form-error" role="alert">{error}</div>}
            <button className="primary-button auth-submit" disabled={busy}>{busy ? 'One moment…' : mode === 'login' ? 'Sign in to your journal' : 'Create your journal'} <Icon name="arrow" size={17} /></button>
          </form>
          <div className="auth-divider"><span />or<span /></div>
          <button className="google-button" type="button" onClick={googleLogin}><GoogleMark /> Continue with Google</button>
          <div className="privacy-note"><Icon name="lock" size={14} /><span>Your entries are tied to your account and only visible to you.</span></div>
        </div>
        <div className="auth-footer">A small note to yourself can change the whole day.</div>
      </section>
    </main>
  )
}

function GoogleMark() {
  return <svg className="google-mark" viewBox="0 0 48 48" aria-hidden="true"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5Z"/><path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.9c-.58 2.96-2.26 5.48-4.77 7.18l7.73 6C44.37 38.01 47 31.95 47 24.55Z"/><path fill="#FBBC05" d="M10.53 28.59A14.4 14.4 0 0 1 9.75 24c0-1.59.27-3.13.76-4.59l-7.98-6.2A23.9 23.9 0 0 0 0 24c0 3.88.93 7.55 2.56 10.78l7.97-6.19Z"/><path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.9-5.8l-7.74-6c-2.14 1.44-4.87 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48Z"/></svg>
}

function EntryEditor({ initial, onSave, onClose, saving }) {
  const [title, setTitle] = useState(initial?.title ?? '')
  const [content, setContent] = useState(initial?.content ?? '')
  const [sentiment, setSentiment] = useState(initial?.sentiment ?? '')
  const [error, setError] = useState('')

  async function submit(event) {
    event.preventDefault()
    if (!title.trim()) { setError('Give this entry a title first.'); return }
    try {
      await onSave({ title: title.trim(), content, sentiment: sentiment || null })
    } catch (err) { setError(err.message) }
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <section className="editor-modal" role="dialog" aria-modal="true" aria-labelledby="editor-title">
        <div className="modal-top"><span className="eyebrow muted">A MOMENT FOR YOU</span><button className="icon-button" onClick={onClose} aria-label="Close editor"><Icon name="close" /></button></div>
        <h2 id="editor-title">{initial ? 'Revisit this moment' : 'What’s on your mind?'}</h2>
        <p className="editor-hint">No need to make it perfect. Just make it yours.</p>
        <form onSubmit={submit}>
          <input className="title-input" placeholder="Give this moment a name…" value={title} onChange={(event) => { setTitle(event.target.value); setError('') }} maxLength={120} autoFocus />
          <textarea className="content-input" placeholder="Start wherever you are…" value={content} onChange={(event) => setContent(event.target.value)} rows={7} />
          <div className="mood-question">How are you feeling about it?</div>
          <div className="mood-picker">
            {moods.map((mood) => <button type="button" key={mood.value} className={`mood-choice ${sentiment === mood.value ? 'selected' : ''}`} onClick={() => setSentiment(sentiment === mood.value ? '' : mood.value)}><span className={`mood-face ${mood.className}`}>{mood.face}</span>{mood.label}</button>)}
          </div>
          {error && <div className="form-error" role="alert">{error}</div>}
          <div className="editor-actions"><button type="button" className="secondary-button" onClick={onClose}>Cancel</button><button className="primary-button" disabled={saving}>{saving ? 'Saving…' : initial ? 'Save changes' : 'Save this moment'} <Icon name="arrow" size={16} /></button></div>
        </form>
      </section>
    </div>
  )
}

function EntryCard({ entry, onEdit, onDelete }) {
  const mood = moodFor(entry.sentiment)
  return (
    <article className="entry-card">
      <div className="entry-meta"><span>{friendlyDate(entry.date)}</span>{mood && <span className={`entry-mood ${mood.className}`}><i>{mood.face}</i>{mood.label}</span>}</div>
      <h3>{entry.title}</h3>
      <p className="entry-content">{entry.content || 'A moment captured, just for you.'}</p>
      <div className="entry-actions"><button onClick={() => onEdit(entry)} aria-label={`Edit ${entry.title}`}><Icon name="edit" size={15} /> Edit</button><button onClick={() => onDelete(entry)} aria-label={`Delete ${entry.title}`}><Icon name="trash" size={15} /> Delete</button></div>
    </article>
  )
}

function Dashboard({ token, username, onLogout, theme, onToggleTheme }) {
  const [entries, setEntries] = useState([])
  const [loading, setLoading] = useState(true)
  const [screenError, setScreenError] = useState('')
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState('ALL')
  const [editing, setEditing] = useState(null)
  const [editorOpen, setEditorOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [toast, setToast] = useState('')
  const [weatherState, setWeatherState] = useState({ status: 'loading', data: null })

  async function loadEntries() {
    setLoading(true)
    setScreenError('')
    try {
      const result = await request('/journal', { token, allowEmpty: true })
      setEntries(Array.isArray(result) ? result : [])
    } catch (err) {
      if (/session expired/i.test(err.message)) { onLogout(); return }
      setScreenError(err.message)
    } finally { setLoading(false) }
  }

  useEffect(() => {
    loadEntries()
    request('/user', { token }).then((result) => {
      if (result?.weather) {
        setWeatherState({ status: 'ready', data: result.weather })
      } else if (typeof result === 'string') {
        const match = result.match(/Weather feels like\s+([\d.]+)/i)
        setWeatherState(match
          ? { status: 'ready', data: { city: 'Mumbai', feelsLike: Number(match[1]), description: 'Current conditions' } }
          : { status: 'unavailable', data: null })
      } else {
        setWeatherState({ status: 'unavailable', data: null })
      }
    }).catch(() => setWeatherState({ status: 'unavailable', data: null }))
  }, [])
  useEffect(() => {
    if (!toast) return undefined
    const timer = window.setTimeout(() => setToast(''), 2800)
    return () => window.clearTimeout(timer)
  }, [toast])

  const visibleEntries = useMemo(() => entries
    .filter((entry) => filter === 'ALL' || entry.sentiment === filter)
    .filter((entry) => `${entry.title ?? ''} ${entry.content ?? ''}`.toLowerCase().includes(query.toLowerCase()))
    .sort((a, b) => new Date(b.date ?? 0) - new Date(a.date ?? 0)), [entries, filter, query])

  const today = new Intl.DateTimeFormat(undefined, { weekday: 'long', month: 'long', day: 'numeric' }).format(new Date())
  const firstName = username?.split(/[ ._-]/)[0] || 'friend'

  function startNew() { setEditing(null); setEditorOpen(true) }
  function startEdit(entry) { setEditing(entry); setEditorOpen(true) }

  async function saveEntry(values) {
    setSaving(true)
    try {
      if (editing) {
        await request(`/journal/id/${encodeURIComponent(editing.id)}`, { token, method: 'PUT', body: values })
        setToast('Your changes are tucked away.')
      } else {
        await request('/journal', { token, method: 'POST', body: values })
        setToast('A moment saved. Well done.')
      }
      setEditorOpen(false)
      await loadEntries()
    } finally { setSaving(false) }
  }

  async function deleteEntry(entry) {
    if (!window.confirm(`Delete “${entry.title}”? This can’t be undone.`)) return
    try {
      await request(`/journal/id/${encodeURIComponent(entry.id)}`, { token, method: 'DELETE' })
      setEntries((current) => current.filter((item) => item.id !== entry.id))
      setToast('Entry deleted.')
    } catch (err) { setScreenError(err.message) }
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Brand />
        <div className="sidebar-label">YOUR SPACE</div>
        <button className="side-link active"><span className="side-icon"><Icon name="book" size={17} /></span>My journal<span className="side-count">{entries.length}</span></button>
        <div className="sidebar-note"><div className="note-doodle">✳</div><p>Little by little,<br />a little becomes<br /><em>a lot.</em></p><span>— Tanzanian proverb</span></div>
        <div className="sidebar-bottom"><div className="avatar">{firstName.slice(0, 1).toUpperCase()}</div><div className="user-label"><strong>{username}</strong><span>Your private space</span></div><button className="logout-button" onClick={onLogout} aria-label="Sign out" title="Sign out"><Icon name="logout" size={17} /></button></div>
      </aside>

      <main className="main-area">
        <header className="topbar"><div className="mobile-brand"><Brand compact /></div><span className="today-label">{today}</span><ThemeToggle theme={theme} onToggle={onToggleTheme} /><button className="mobile-logout" onClick={onLogout} aria-label="Sign out"><Icon name="logout" size={17} /></button></header>
        <div className="content-wrap">
          <section className="welcome-row"><div><div className="eyebrow welcome-eyebrow"><span className="eyebrow-dot" /> YOUR DAY, YOUR PACE</div><h1>Good to see you, {firstName}.</h1><p>Take a breath. What would you like to remember?</p><div className={`weather-card ${weatherState.status}`} role="status" aria-live="polite"><span className="weather-symbol" aria-hidden="true">{weatherState.status === 'ready' ? '☼' : '☁'}</span>{weatherState.status === 'ready' ? <><span className="weather-city">{weatherState.data.city}</span><span className="weather-description">{weatherState.data.description}</span><strong>{Math.round(weatherState.data.feelsLike)}°C</strong><span className="weather-feels">feels like</span><span className="weather-provider">via {weatherState.data.provider || 'Weatherstack'}</span></> : <span className="weather-status">{weatherState.status === 'loading' ? 'Checking local weather…' : 'Weather is currently unavailable'}</span>}</div></div><button className="primary-button new-entry-button" onClick={startNew}><Icon name="plus" size={17} /> New entry</button></section>

          <section className="prompt-card"><div className="prompt-mark">“</div><div><span className="eyebrow muted">A GENTLE PROMPT</span><p>What felt like a small win today?</p></div><span className="prompt-spark">✦</span></section>

          <section className="entries-section">
            <div className="entries-heading"><div><span className="eyebrow muted">YOUR COLLECTION</span><h2>Recent moments <span className="entry-count">{entries.length}</span></h2></div><div className="search-box"><Icon name="search" size={17} /><input aria-label="Search entries" placeholder="Find a moment…" value={query} onChange={(event) => setQuery(event.target.value)} /></div></div>
            <div className="filter-row"><button className={filter === 'ALL' ? 'filter-chip chosen' : 'filter-chip'} onClick={() => setFilter('ALL')}>Everything</button>{moods.map((mood) => <button key={mood.value} className={filter === mood.value ? 'filter-chip chosen' : 'filter-chip'} onClick={() => setFilter(filter === mood.value ? 'ALL' : mood.value)}><i className={`filter-dot ${mood.className}`} />{mood.label}</button>)}</div>
            {screenError && <div className="error-banner" role="alert"><span>{screenError}</span><button onClick={loadEntries}>Try again</button></div>}
            {loading ? <div className="loading-state"><span className="loader" />Gathering your moments…</div> : visibleEntries.length ? <div className="entry-grid">{visibleEntries.map((entry) => <EntryCard key={entry.id} entry={entry} onEdit={startEdit} onDelete={deleteEntry} />)}</div> : <div className="empty-state"><div className="empty-illustration"><span>☁</span><i>✦</i></div><h3>{query || filter !== 'ALL' ? 'No moments found.' : 'Your page is still blank.'}</h3><p>{query || filter !== 'ALL' ? 'Try another search or mood.' : 'That’s a lovely place to begin. Your first entry doesn’t need to be important — it just needs to be yours.'}</p>{!query && filter === 'ALL' && <button className="secondary-button" onClick={startNew}><Icon name="plus" size={16} /> Write your first entry</button>}</div>}
          </section>
          <footer className="page-footer"><span>Made for the moments in between.</span><span><Icon name="lock" size={13} /> Just for you</span></footer>
        </div>
      </main>
      {editorOpen && <EntryEditor initial={editing} onSave={saveEntry} onClose={() => setEditorOpen(false)} saving={saving} />}
      {toast && <div className="toast" role="status">{toast}</div>}
    </div>
  )
}

export default function App() {
  const [token, setToken] = useState(() => sessionStorage.getItem(TOKEN_KEY) || '')
  const [username, setUsername] = useState(() => sessionStorage.getItem(USER_KEY) || '')
  const [googleAuthError, setGoogleAuthError] = useState('')
  const [theme, setTheme] = useState(() => localStorage.getItem('daybook.theme') === 'dark' ? 'dark' : 'light')

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem('daybook.theme', theme)
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#171c18' : '#f7f6f2')
  }, [theme])

  useEffect(() => {
    const params = new URLSearchParams(window.location.hash.replace(/^#/, ''))
    const googleToken = params.get('token')
    const googleError = params.get('authError')
    if (googleToken) {
      signIn(googleToken, params.get('username') || 'friend')
      window.history.replaceState({}, '', window.location.pathname)
    } else if (googleError) {
      const messages = {
        google_cancelled: 'Google sign-in was cancelled.',
        google_session_expired: 'Google sign-in expired. Please try again.',
        google_not_configured: 'Google sign-in is not configured on the server.',
        google_account_conflict: 'That Google account conflicts with an existing account. Contact support to link it.',
        google_failed: 'Google sign-in failed. Please try again.',
      }
      setGoogleAuthError(messages[googleError] || messages.google_failed)
      window.history.replaceState({}, '', window.location.pathname)
    }
  }, [])

  function signIn(nextToken, nextUsername) {
    sessionStorage.setItem(TOKEN_KEY, nextToken)
    sessionStorage.setItem(USER_KEY, nextUsername)
    setToken(nextToken)
    setUsername(nextUsername)
  }

  function signOut() {
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(USER_KEY)
    setToken('')
    setUsername('')
  }

  const toggleTheme = () => setTheme((current) => current === 'dark' ? 'light' : 'dark')
  return token ? <Dashboard token={token} username={username} onLogout={signOut} theme={theme} onToggleTheme={toggleTheme} /> : <AuthScreen onAuth={signIn} initialError={googleAuthError} theme={theme} onToggleTheme={toggleTheme} />
}
