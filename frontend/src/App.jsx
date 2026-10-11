import React from 'react';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ArrowDown, ArrowUp, BookOpen, Check, ChevronDown, FileText, FileUp,
  FolderOpen, LayoutGrid, LoaderCircle, LogOut, MessageCircle, MoreHorizontal,
  Plus, Search, ShieldCheck, Sparkles, Trash2, UploadCloud, X,
} from 'lucide-react';
import { api } from './api.js';

const prettySize = (bytes = 0) => bytes < 1024 * 1024
  ? `${Math.max(1, Math.round(bytes / 1024))} KB`
  : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;

function AuthScreen({ onLogin }) {
  const [mode, setMode] = useState('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      const result = await api.auth(mode, email, password);
      localStorage.setItem('vault-token', result.token);
      onLogin(email);
    } catch (problem) {
      setError(problem.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-page">
      <div className="auth-card">
        <div className="brand-mark"><BookOpen size={21} /></div>
        <p className="eyebrow">YOUR SECOND BRAIN</p>
        <h1>Your knowledge,<br />all in one place.</h1>
        <p className="auth-copy">Bring your notes and documents together. Ask questions and get answers grounded in your own library.</p>
        <form onSubmit={submit}>
          <label>Email address<input type="email" required value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@example.com" autoComplete="email" /></label>
          <label>Password<input type="password" required minLength="8" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="At least 8 characters" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /></label>
          {error && <p className="error-message">{error}</p>}
          <button className="primary-button auth-submit" disabled={busy}>{busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
        </form>
        <p className="auth-toggle">{mode === 'login' ? 'New to Mindspace?' : 'Already have an account?'} <button onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); }}>{mode === 'login' ? 'Create an account' : 'Sign in'}</button></p>
        <p className="privacy-note"><ShieldCheck size={14} /> Your documents are private to your account</p>
      </div>
    </main>
  );
}

function App() {
  const [email, setEmail] = useState('');
  const [documents, setDocuments] = useState([]);
  const [active, setActive] = useState('assistant');
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState([]);
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [notice, setNotice] = useState('');
  const [search, setSearch] = useState('');
  const [results, setResults] = useState(null);
  const [dragging, setDragging] = useState(false);
  const fileInput = useRef(null);
  const endOfChat = useRef(null);
  const firstName = email.split('@')[0]?.split(/[._-]/)[0] || 'there';

  const loadDocuments = useCallback(async () => {
    try {
      setDocuments(await api.documents());
    } catch (problem) {
      setNotice(problem.message);
    }
  }, []);

  useEffect(() => {
    const token = localStorage.getItem('vault-token');
    const savedEmail = localStorage.getItem('vault-email');
    if (token && savedEmail) setEmail(savedEmail);
  }, []);

  useEffect(() => {
    if (email) {
      localStorage.setItem('vault-email', email);
      loadDocuments();
    }
  }, [email, loadDocuments]);

  useEffect(() => endOfChat.current?.scrollIntoView({ behavior: 'smooth' }), [messages]);

  async function uploadFiles(fileList) {
    const files = Array.from(fileList || []);
    if (!files.length) return;
    setUploading(true);
    setNotice('');
    for (const file of files) {
      try {
        await api.upload(file);
        setNotice(`${file.name} added to your library`);
      } catch (problem) {
        setNotice(`${file.name}: ${problem.message}`);
      }
    }
    await loadDocuments();
    setUploading(false);
  }

  async function sendQuestion(event) {
    event?.preventDefault();
    const text = question.trim();
    if (!text || busy) return;
    setMessages((current) => [...current, { role: 'user', content: text }]);
    setQuestion('');
    setBusy(true);
    try {
      const answer = await api.ask(text);
      setMessages((current) => [...current, { role: 'assistant', ...answer }]);
    } catch (problem) {
      setMessages((current) => [...current, { role: 'assistant', content: problem.message, sources: [] }]);
    } finally {
      setBusy(false);
    }
  }

  async function runSearch(event) {
    event.preventDefault();
    const query = search.trim();
    if (!query) return;
    try {
      setResults(await api.search(query));
    } catch (problem) {
      setNotice(problem.message);
    }
  }

  async function removeDocument(document) {
    if (!window.confirm(`Remove "${document.name}" and its stored content?`)) return;
    try {
      await api.removeDocument(document.id);
      setDocuments((current) => current.filter((item) => item.id !== document.id));
      setNotice('Document removed');
    } catch (problem) {
      setNotice(problem.message);
    }
  }

  function signOut() {
    localStorage.removeItem('vault-token');
    localStorage.removeItem('vault-email');
    setEmail('');
    setDocuments([]);
    setMessages([]);
  }

  if (!email) return <AuthScreen onLogin={setEmail} />;

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#"><span className="brand-mark"><BookOpen size={19} /></span><span>mindspace<span className="brand-dot">.</span></span></a>
        <p className="nav-label">WORKSPACE</p>
        <button className={`nav-item ${active === 'assistant' ? 'selected' : ''}`} onClick={() => setActive('assistant')}><MessageCircle size={17} /> AI assistant <span className="nav-count">⌘ K</span></button>
        <button className={`nav-item ${active === 'library' ? 'selected' : ''}`} onClick={() => setActive('library')}><FolderOpen size={17} /> My library <span className="nav-count">{documents.length}</span></button>
        <button className={`nav-item ${active === 'search' ? 'selected' : ''}`} onClick={() => setActive('search')}><Search size={17} /> Search</button>
        <p className="nav-label collection-label">YOUR LIBRARY <button className="icon-button tiny" title="Upload a document" onClick={() => fileInput.current?.click()}><Plus size={15} /></button></p>
        <div className="sidebar-documents">
          {documents.slice(0, 5).map((document) => <button key={document.id} className="sidebar-doc" onClick={() => setActive('library')}><FileText size={15} /><span>{document.name}</span></button>)}
          {!documents.length && <p className="empty-sidebar">Your documents will show up here.</p>}
        </div>
        <div className="sidebar-bottom"><div className="user-avatar">{email[0]?.toUpperCase()}</div><div className="user-info"><strong>{firstName}</strong><span>Personal workspace</span></div><button className="icon-button sign-out" title="Sign out" onClick={signOut}><LogOut size={16} /></button></div>
      </aside>

      <main className="main-area" onDragOver={(event) => { event.preventDefault(); setDragging(true); }} onDragLeave={(event) => { if (event.target === event.currentTarget) setDragging(false); }} onDrop={(event) => { event.preventDefault(); setDragging(false); uploadFiles(event.dataTransfer.files); }}>
        {dragging && <div className="drop-overlay"><UploadCloud size={34} /><strong>Drop files to add them to your library</strong><span>PDF, DOCX, PPTX, TXT, or Markdown</span></div>}
        <header className="topbar"><div className="breadcrumb"><LayoutGrid size={15} /><span>My workspace</span><span className="breadcrumb-divider">/</span><strong>{active === 'assistant' ? 'AI assistant' : active === 'library' ? 'My library' : 'Search'}</strong></div><div className="topbar-right"><span className="private-pill"><span /> Private</span><button className="avatar-small" title={email}>{email[0]?.toUpperCase()}</button></div></header>
        {notice && <div className="notice" role="status">{notice}<button className="icon-button" onClick={() => setNotice('')}><X size={15} /></button></div>}

        {active === 'assistant' && <section className="assistant-page">
          <div className="chat-heading"><div className="assistant-icon"><Sparkles size={18} /></div><div><p className="eyebrow">YOUR PERSONAL KNOWLEDGE ASSISTANT</p><h1>Good to see you, {firstName}.</h1><p>Ask anything about the ideas and information you've saved.</p></div><button className="secondary-button new-chat" onClick={() => setMessages([])}><Plus size={16} /> New chat</button></div>
          <div className={`chat-content ${messages.length ? 'has-messages' : ''}`}>
            {!messages.length ? <div className="welcome-content"><div className="welcome-spark"><Sparkles size={22} /></div><h2>Your knowledge is ready<br />when you are.</h2><p>Ask a question, explore a topic, or start with one of these.</p><div className="prompt-grid">
              {['Summarize my most recent notes', 'What should I focus on studying?', 'Explain a complex idea simply', 'Create quiz questions from my materials'].map((prompt) => <button key={prompt} className="prompt-card" onClick={() => setQuestion(prompt)}><span>{prompt}</span><ArrowUpRightIcon /></button>)}
            </div></div> : <div className="message-list">{messages.map((message, index) => <article key={index} className={`message ${message.role}`}><div className={`message-icon ${message.role}`}>{message.role === 'user' ? email[0]?.toUpperCase() : <Sparkles size={15} />}</div><div className="message-body"><strong>{message.role === 'user' ? 'You' : 'Mindspace'}</strong><p>{message.content}</p>{message.mode === 'retrieval_only' && <span className="mode-note">Retrieved from your documents · LLM unavailable</span>}{message.sources?.length > 0 && <div className="source-list"><span className="source-heading">SOURCES</span>{message.sources.map((source, sourceIndex) => <div className="source-card" key={`${source.name}-${sourceIndex}`}><FileText size={14} /><div><strong>{source.name}</strong><p>{source.excerpt}</p></div></div>)}</div>}</div></article>)}{busy && <div className="message assistant"><div className="message-icon assistant"><Sparkles size={15} /></div><div className="message-body"><strong>Mindspace</strong><p className="thinking"><LoaderCircle size={15} /> Searching your knowledge…</p></div></div>}<div ref={endOfChat} /></div>}
          </div>
          <div className="composer-wrap"><form className="composer" onSubmit={sendQuestion}><textarea value={question} onChange={(event) => setQuestion(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); sendQuestion(); } }} placeholder="Ask a question about your knowledge..." rows={2} /><div className="composer-footer"><span><Sparkles size={13} /> Answers are grounded in your documents</span><button className="send-button" disabled={!question.trim() || busy} aria-label="Send question"><ArrowUp size={17} /></button></div></form><p className="disclaimer">Mindspace can make mistakes. Check important details against your sources.</p></div>
        </section>}

        {active === 'library' && <section className="library-page"><div className="page-heading"><div><p className="eyebrow">YOUR PERSONAL KNOWLEDGE</p><h1>My library</h1><p>Everything you've added, all in one thoughtful space.</p></div><button className="primary-button" onClick={() => fileInput.current?.click()}><FileUp size={16} /> Add documents</button></div><div className="library-stats"><div><span>DOCUMENTS</span><strong>{documents.length}</strong></div><div><span>STORAGE USED</span><strong>{prettySize(documents.reduce((total, doc) => total + doc.size, 0))}</strong></div><div><span>SEARCHABLE</span><strong>{documents.filter((doc) => doc.chunkCount > 0).length} <Check size={15} /></strong></div></div><div className="section-title"><h2>All documents</h2><span>{documents.length} files</span></div>{documents.length ? <div className="document-grid">{documents.map((document) => <article key={document.id} className="document-card"><div className="document-card-top"><span className="file-icon"><FileText size={19} /></span><button className="icon-button" title={`Delete ${document.name}`} onClick={() => removeDocument(document)}><Trash2 size={16} /></button></div><h3>{document.name}</h3><p>{document.chunkCount || 0} searchable sections · {prettySize(document.size)}</p><span className="document-date">Added {new Date(document.uploadedAt).toLocaleDateString()}</span></article>)}</div> : <div className="empty-state"><div className="empty-icon"><FolderOpen size={24} /></div><h3>Your library is waiting</h3><p>Add your notes, papers, and documents to start building your personal knowledge base.</p><button className="secondary-button" onClick={() => fileInput.current?.click()}><Plus size={16} /> Add your first document</button></div>}</section>}

        {active === 'search' && <section className="search-page"><div className="page-heading"><div><p className="eyebrow">EXPLORE YOUR KNOWLEDGE</p><h1>Search your library</h1><p>Find a thought or idea, even when you don't remember the exact words.</p></div></div><form className="search-bar" onSubmit={runSearch}><Search size={19} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search across your documents..." /><button className="primary-button">Search</button></form>{results && <div className="search-results"><div className="section-title"><h2>Results</h2><span>{results.length} matches</span></div>{results.length ? results.map((item) => <article className="result-card" key={item.chunkId}><div><FileText size={16} /><strong>{item.documentName}</strong><span>{Math.round(item.score * 100)}% match</span></div><p>{item.excerpt}</p></article>) : <div className="empty-state compact"><p>No matching passages yet. Try another phrase or add more documents.</p></div>}</div>}</section>}

        <input ref={fileInput} type="file" multiple accept=".pdf,.txt,.md,.markdown,.docx,.pptx" hidden onChange={(event) => { uploadFiles(event.target.files); event.target.value = ''; }} />
        {uploading && <div className="upload-indicator"><LoaderCircle size={15} /> Processing your documents…</div>}
      </main>
    </div>
  );
}

function ArrowUpRightIcon() {
  return <span className="prompt-arrow"><ArrowDown size={14} /></span>;
}

export default App;
