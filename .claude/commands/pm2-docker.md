Start Docker services (MySQL, Redis, Nacos) and show status.
```bash
cd "$CLAUDE_PROJECT_DIR" && pm2 start docker-wrapper.cjs --name docker-up && sleep 5 && docker compose -f $CLAUDE_PROJECT_DIR/docker-compose.yml ps
```
