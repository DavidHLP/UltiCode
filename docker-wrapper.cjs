/**
 * Docker Manager for PM2
 *
 * Manages Docker containers (MySQL, Redis, Nacos) via PM2.
 * Pass action as argument: start, stop, restart, status, logs
 *
 * Usage:
 *   pm2 start docker-wrapper.cjs --name docker-start
 *   pm2 start docker-wrapper.cjs --name docker-stop -- stop
 *   pm2 start docker-wrapper.cjs --name docker-logs -- logs
 */
const { spawn } = require('child_process');
const { existsSync } = require('fs');
const path = require('path');

const COMPOSE_FILE = path.join(__dirname, 'docker-compose.yml');

function dockerCompose(args, inherit = true) {
  return new Promise((resolve, reject) => {
    const options = inherit
      ? { cwd: __dirname, stdio: 'inherit', shell: process.platform === 'win32' }
      : { cwd: __dirname, stdio: 'pipe', shell: process.platform === 'win32' };

    const cmd = spawn('docker', ['compose', '-f', COMPOSE_FILE, ...args], options);

    if (!inherit) {
      cmd.stdout.on('data', (data) => process.stdout.write(data));
      cmd.stderr.on('data', (data) => process.stderr.write(data));
    }

    cmd.on('close', (code) => {
      if (code === 0) resolve();
      else process.exit(code);
    });

    cmd.on('error', (err) => {
      console.error(`[docker] Failed to execute: docker compose ${args.join(' ')}`);
      console.error(err.message);
      process.exit(1);
    });
  });
}

async function main() {
  const args = process.argv.slice(2);
  const action = args[0] || 'up';

  if (!existsSync(COMPOSE_FILE)) {
    console.error(`[docker] ERROR: docker-compose.yml not found at ${COMPOSE_FILE}`);
    process.exit(1);
  }

  // Parse service name if provided (e.g., "up mysql" or "logs redis")
  const [cmd, service] = args;

  console.log(`[docker] Command: docker compose ${args.join(' ')}`);

  try {
    switch (cmd) {
      case 'start':
        await dockerCompose(['start', ...(service ? [service] : [])]);
        break;

      case 'stop':
        await dockerCompose(['stop', ...(service ? [service] : [])]);
        break;

      case 'restart':
        if (service) {
          await dockerCompose(['restart', service]);
        } else {
          await dockerCompose(['restart']);
        }
        break;

      case 'up':
        // Start containers in detached mode
        await dockerCompose(['up', '-d', ...(service ? [service] : [])]);
        console.log('[docker] Containers started');
        break;

      case 'down':
        await dockerCompose(['down']);
        console.log('[docker] Containers stopped and removed');
        break;

      case 'logs':
        await dockerCompose(['logs', '-f', ...(service ? [service] : [])]);
        break;

      case 'ps':
      case 'status':
        await dockerCompose(['ps']);
        break;

      case 'pull':
        await dockerCompose(['pull', ...(service ? [service] : [])]);
        break;

      default:
        console.error(`[docker] Unknown action: ${cmd}`);
        console.error('Available: up, down, start, stop, restart, logs, ps, pull');
        console.error('Examples:');
        console.error('  pm2 start docker-wrapper.cjs --name docker-up');
        console.error('  pm2 start docker-wrapper.cjs --name docker-up-mysql -- up mysql');
        console.error('  pm2 logs docker');
        process.exit(1);
    }
  } catch (error) {
    console.error(`[docker] Error: ${error.message}`);
    process.exit(1);
  }
}

main();
