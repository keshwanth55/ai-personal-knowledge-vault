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

## Deployment

The repository includes a Render Blueprint (`render.yaml`) for the Docker backend and `netlify.toml` for the Vite frontend.

1. Push this repository to GitHub and create a Blueprint in Render from it. Set the prompted `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (Aiven MySQL), and `CORS_ALLOWED_ORIGINS` values. Render generates `VAULT_JWT_SECRET`; `H2_CONSOLE_ENABLED` is disabled.
2. Deploy the frontend from the same repository in Netlify. Set `VITE_API_URL` to `https://<your-render-service>.onrender.com/api` in the Netlify environment settings and redeploy.
3. Set `CORS_ALLOWED_ORIGINS` on Render to the exact Netlify site origin, then redeploy the backend.
4. Check the backend at `/api/health` and create an account in the frontend.

Ollama runs locally by default. A cloud deployment cannot reach Ollama on your laptop; configure a reachable, secured Ollama-compatible endpoint with `OLLAMA_BASE_URL` to enable generated chat responses. Without it, document upload and keyword retrieval remain available with retrieval-only chat answers. Uploaded originals are on the container filesystem; configure persistent storage before relying on them in production.

The supplied lab-structure PDF was empty when inspected, so the deployment follows this repository's Render and Netlify configuration.
