# AGENTS.md

## Cursor Cloud specific instructions

### Codebase overview

This is a logistics/fleet management app ("Controle de Escalas") for Brazilian trucking companies. It has three backend components and an Android app:

| Component | Path | Tech | Dev command |
|---|---|---|---|
| Python Flask API | `backend-python/` | Python 3.12, Flask | `python3 api.py` (port 5000) |
| Node.js FCM Server | `Raiz-prompt/server/` | Node.js 18+, Firebase Admin | `npm start` or `npm run dev` |
| Firebase Cloud Functions | `Raiz-prompt/functions/` | TypeScript, Node.js 20 | `npm run build` (tsc) |
| Android App | `Raiz-prompt/app/` | Kotlin, Jetpack Compose | Requires Android SDK (not available in Cloud) |

### Running services

- **Flask API**: `cd backend-python && python3 api.py` — starts on port 5000. The `/health` endpoint works without Firebase credentials. All other endpoints require `FIREBASE_SERVICE_ACCOUNT_JSON` env var or a `service-account-key.json` file.
- **Node.js server**: `cd Raiz-prompt/server && npm start` — requires Firebase credentials to connect to Firestore. Will exit if credentials are missing.
- **Functions build**: `cd Raiz-prompt/functions && npm run build` — compiles TypeScript. No ESLint config exists in the repo, so `npm run lint` will fail.

### Important notes

- Use `python3` (not `python`) — the `python` alias is not available in the Cloud VM.
- pip installs to `~/.local/bin` which may not be on PATH. Add `export PATH="$HOME/.local/bin:$PATH"` if you need `gunicorn` or `flask` CLI commands.
- Firebase credentials (service account JSON) are required for any endpoint that touches Firestore/FCM/Auth. Without them, only `/health` works on the Flask API.
- The Cloud Functions `package.json` requires Node.js 20 but Node.js 22 is installed; `npm install` and `tsc` work fine despite the engine warning.
- Optional API keys: `OPENAI_API_KEY` (AI assistant), `ORS_API_KEY` (route/ETA calculation), `GEMINI_API_KEY` / `HUGGINGFACE_TOKEN` (alternative AI).
