# AI Personal Knowledge Vault

A full-stack starter for uploading private study/work documents, searching their contents, and asking questions grounded in the matching passages.

## Stack

- React + Vite
- Java 21 + Spring Boot, Spring Security, Spring Data JPA
- H2 locally; Aiven MySQL in deployment for metadata and chunks
- Local filesystem for uploaded source files
- Optional Ollama for local embeddings and chat (no external API key required)

## Requirements

- Node.js 20+
- Java 21+
- Maven 3.9+
- Optional: [Ollama](https://ollama.com/) with models pulled:

```powershell
ollama pull nomic-embed-text
ollama pull llama3.2:3b
```

## Run locally

1. Start the API from the project root:

```powershell
cd backend
mvn spring-boot:run
```

The backend reads local settings from `backend/.env`. Spring Boot loads it automatically whether you start Maven inside `backend` or from the project root. Edit that file to use your Aiven MySQL connection. If it is missing after cloning the repository, create it from the committed-safe template by running `Copy-Item .env.example .env` from the `backend` directory. Operating-system environment variables override values in `.env`. Never commit real database passwords or production secrets.

2. In a second terminal, start the React app:

```powershell
cd frontend
npm install
npm run dev
```

Open the URL printed by Vite (normally http://localhost:5173). Create an account to get started.

The backend listens on port 8081 by default (or the `PORT` environment variable). H2 metadata is stored in `backend/data/knowledge-vault`; original documents are stored under `backend/uploads/`. The H2 console is available at http://localhost:8081/h2-console during local development. Set `VITE_API_URL` if you use a different API port.

## Ollama configuration

The default Ollama URL is `http://localhost:11434`. Configure a different local endpoint or models with environment variables:

```powershell
$env:OLLAMA_BASE_URL = "http://localhost:11434"
$env:OLLAMA_CHAT_MODEL = "llama3.2:3b"
$env:OLLAMA_EMBED_MODEL = "nomic-embed-text"
```

If Ollama is unavailable, uploads and keyword-based retrieval still work. The assistant clearly labels responses as retrieval-only instead of claiming an LLM generated them. Ollama is optional; no hosted LLM provider or API key is configured by default.

## Features

- Email/password registration and login; API routes require a bearer token.
- Upload PDF, DOCX, PPTX, TXT, and Markdown files (up to 20 MB each).
- Text extraction, overlapping chunking, Ollama embeddings, cosine-similarity search, and keyword fallback.
- Answers include source document names and excerpts; the assistant is instructed to say when the uploaded material does not support an answer.
- Delete documents and their stored chunks/files.

## Important production notes

This is a starter MVP. Before production, set a unique long JWT secret, use private persistent file storage, enable HTTPS, configure backups and retention, add rate limits and email verification, and review your privacy/security requirements. For Aiven MySQL, set `SPRING_DATASOURCE_URL` to the JDBC form of Aiven's connection URL, for example `jdbc:mysql://HOST:PORT/DBNAME?sslMode=REQUIRED`, and set `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` in the hosting provider's secret environment settings. Do not commit database credentials.

## Deployment files

- `Dockerfile` builds the backend with Java 21 in a multi-stage image. Configure Render to use Docker and the repository-root `./Dockerfile`; Render supplies the `PORT` environment variable.
- `netlify.toml` configures a Git-based Netlify build from `frontend/`. Set the Netlify build environment variable `VITE_API_URL` to the Render API URL ending in `/api`. For manual deploy, set it in PowerShell before `npm run build`, then drag `frontend/dist` to Netlify.
- Set the Render environment variable `CORS_ALLOWED_ORIGINS` to the exact Netlify site origin (for example `https://your-site.netlify.app`, without a trailing slash). Also set `VAULT_JWT_SECRET` to a unique random secret and `H2_CONSOLE_ENABLED=false`.
- `VAULT_STORAGE_DIR` defaults to `./uploads`. Render's container filesystem is ephemeral; configure persistent storage or move original files to object storage before relying on uploaded originals in production.
