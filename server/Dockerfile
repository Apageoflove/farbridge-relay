# Build the single-worker FastAPI runtime without writing outside the container layer.
FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1
WORKDIR /app
COPY server/pyproject.toml server/alembic.ini ./
# Install only the locked runtime dependencies in a layer keyed by pyproject;
# normal source edits then reuse this network-heavy layer.
RUN python -c "import subprocess,sys,tomllib; deps=tomllib.load(open('pyproject.toml','rb'))['project']['dependencies']; subprocess.check_call([sys.executable,'-m','pip','install','--index-url','https://pypi.tuna.tsinghua.edu.cn/simple',*deps])"
COPY server/app ./app
COPY server/alembic ./alembic
COPY web/dist ./web-dist
# SSHFS may present source files as owner-only; normalize the immutable image
# layer so the fixed non-root runtime UID can import code and serve assets.
RUN chown -R 65532:65532 /app && chmod -R u=rwX,go=rX /app
USER 65532:65532
EXPOSE 8084
CMD ["uvicorn", "app.main:create_app", "--factory", "--host", "0.0.0.0", "--port", "8084", "--workers", "1", "--proxy-headers", "--forwarded-allow-ips", "172.17.0.1"]
