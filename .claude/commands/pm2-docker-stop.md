Stop Docker services.
```bash
cd "$CLAUDE_PROJECT_DIR" && pm2 start docker-wrapper.cjs --name docker-down -- down
```
