# NeuroInfoGrinder 2.0 Model Worker

Local FastAPI worker for replay-only intelligence stages.

It exposes:

- `GET /health`
- `POST /classify`
- `POST /embed`
- `POST /embed-batch`
- `POST /topics`

The worker uses `BAAI/bge-m3` for embeddings when `sentence-transformers`
is installed and the model can be loaded. The classifier is reported as
`BOOTSTRAP_BERT_CLASSIFIER` until a fine-tuned local classifier is provided.

Run locally:

```powershell
cd backend/2.0/model-worker
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn app:app --host 127.0.0.1 --port 8095
```

Model weights and cache directories are intentionally ignored by git.
