Start Docker services (MySQL, Redis, Nacos) and show status.
```bash
cd "/home/davidhlp/project/UltiCode-Public-Next" && pm2 start docker-wrapper.cjs --name docker-up && sleep 5 && docker compose -f /home/davidhlp/project/UltiCode-Public-Next/docker-compose.yml ps
```
