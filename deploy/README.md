# Hetzner deployment

After CI succeeds for a push to `main`, `.github/workflows/deploy.yml` builds the
backend and frontend images, publishes them to GHCR and updates the application
on a Hetzner Cloud server over SSH.

The production stack in `compose.yml` contains the React frontend, Spring Boot
backend, PostgreSQL and Caddy. Caddy terminates HTTPS, requires one of two named
HTTP Basic Auth accounts and forwards requests to the frontend container. The
frontend container serves React and forwards backend paths such as `/api/*` to
Spring Boot. PostgreSQL and the backend are not exposed publicly. The backend reads
the proxy-validated Basic Auth username for `GET /api/me`; it does not validate
passwords itself.

## Infrastructure

Required network configuration:

- DNS `A` record for the application domain pointing to the server;
- inbound TCP `80` and `443` for HTTP/HTTPS;
- inbound TCP `22` for deployment over SSH;
- no public PostgreSQL port `5432`.

## SSH keys

The setup uses two client key pairs and one server host key:

| Item | Purpose | Private part | Public part |
| --- | --- | --- | --- |
| Administrator key | Manual root administration | Administrator's device | Added when the Hetzner server is created |
| Deployment key | GitHub Actions logs in as `deploy` | GitHub secret `HETZNER_SSH_PRIVATE_KEY` | Server file `/home/deploy/.ssh/authorized_keys` |
| Server host key | GitHub verifies the server's identity | Remains on the server | GitHub secret `HETZNER_KNOWN_HOSTS` |

Generate the administrator key on the administration device and add only its
`.pub` file when creating the server in Hetzner:

```bash
ssh-keygen -t ed25519 -a 100 -f ~/.ssh/hetzner_admin -C hetzner-admin
```

Generate the deployment key on the administrator's device, not on the server:

```bash
ssh-keygen -t ed25519 -N "" -f ~/.ssh/tezaurs_deploy -C github-actions-tezaurs
```

Only `~/.ssh/tezaurs_deploy.pub` is copied to the server. The private file
`~/.ssh/tezaurs_deploy` is stored in GitHub and must never be copied to the
server or committed.

## One-time server setup

Install Docker Engine and the Compose plugin using
[Docker's Ubuntu instructions](https://docs.docker.com/engine/install/ubuntu/),
then verify the installation:

```bash
docker version
docker compose version
docker run --rm hello-world
```

The `docker` group is created by Docker installation. If `usermod` reports that
the group does not exist, install Docker first and rerun the command.

As `root`, create the deployment account and directories:

```bash
adduser --disabled-password --gecos "" deploy
usermod -aG docker deploy
install -d -o deploy -g deploy /opt/tezaurs
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
install -m 600 -o deploy -g deploy /dev/null /home/deploy/.ssh/authorized_keys
```

Paste the single line from `~/.ssh/tezaurs_deploy.pub` into
`/home/deploy/.ssh/authorized_keys`. Start a new login session after adding the
group because existing sessions do not receive the new Docker group membership.
Test from the administrator's device:

```bash
ssh -i ~/.ssh/tezaurs_deploy deploy@SERVER_IP
docker version
docker compose version
```

The Docker group grants root-equivalent control of the server, so membership
must remain limited.

Create `/opt/tezaurs/.env` on the server:

```dotenv
APP_DOMAIN=app.example.lv
DATABASE_PASSWORD=replace-with-a-long-random-password
BASIC_AUTH_USER_1=first-reviewer
BASIC_AUTH_PASSWORD_HASH_1='$2a$14$replace-with-a-bcrypt-hash'
BASIC_AUTH_USER_2=second-reviewer
BASIC_AUTH_PASSWORD_HASH_2='$2a$14$replace-with-a-bcrypt-hash'
```

Generate each password hash interactively so the plaintext password is not saved
in shell history:

```bash
docker run --rm -it caddy:2-alpine caddy hash-password
```

Keep the single quotes around hashes in `.env`; bcrypt hashes contain `$`
characters that must remain literal.

Protect it:

```bash
chown deploy:deploy /opt/tezaurs/.env
chmod 600 /opt/tezaurs/.env
```

If the GHCR packages are private, authenticate once as `deploy` with a GitHub token
that has `read:packages` permission. This is unnecessary for public packages.

## Verify the server host key

`HETZNER_KNOWN_HOSTS` prevents the workflow from connecting to an impersonating
server. It is unrelated to the deployment login key.

Read the trusted fingerprint from the server through the Hetzner web console:

```bash
ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
```

On the administrator's device, scan the advertised key and display its
fingerprint:

```bash
ssh-keyscan -t ed25519 SERVER_IP > /tmp/tezaurs_known_hosts
ssh-keygen -lf /tmp/tezaurs_known_hosts
```

The SHA-256 fingerprints must match. After verification, copy the complete output
of this command into the `HETZNER_KNOWN_HOSTS` GitHub secret:

```bash
ssh-keyscan -H -t ed25519 SERVER_IP
```

If the server is rebuilt, its host key changes and deployments correctly fail
until the new key is verified and the secret is updated.

## GitHub configuration

Create a GitHub environment named `production`, restrict it to `main`, and add:

| Environment secret | Value |
| --- | --- |
| `HETZNER_HOST` | Server IP address or DNS name |
| `HETZNER_USER` | `deploy` |
| `HETZNER_SSH_PRIVATE_KEY` | Complete private deployment key file |
| `HETZNER_KNOWN_HOSTS` | Verified `ssh-keyscan` output line |

The CI workflow separately requires the repository secret `SONAR_TOKEN`. A failed
CI run prevents deployment.

## Deploy and inspect

Push to `main` and inspect both the `CI` and `Deploy to Hetzner` workflows in
GitHub Actions. Caddy obtains its TLS certificate after DNS is correct and ports
`80` and `443` are reachable.

Useful server commands:

```bash
cd /opt/tezaurs
docker compose ps
docker compose logs --tail=100 backend
docker compose logs --tail=100 frontend
curl --fail https://app.example.lv/actuator/health
curl --fail https://app.example.lv/
```

Application data and Caddy certificates are stored in Docker named volumes and
survive normal deployments. They are not a backup; PostgreSQL backups must be
configured separately.

## Restore the Tēzaurs reference schemas

The `dict` and `incubator` schemas are restored manually from database dumps.
Keep the dumps outside this repository; the `incubator` schema must be created
before restoring its dump.
