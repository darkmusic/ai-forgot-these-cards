# Quickstart

This is the shortest path to a working local install.

## 1) Clone and init submodules

```bash
git clone https://github.com/darkmusic/ai-forgot-these-cards
cd ai-forgot-these-cards
git submodule update --init
```

## 2) Create `.env`

```bash
cp .env.example .env
```

If you’re just trying things out, you can often leave most defaults as-is.

## 3) Build and run (core stack)

```bash
make build-deploy-core
```

Open: http://localhost:8080

## 4) Sign in

- Username: `cards`
- Password: `cards`

## 5) Next steps

- Configuration reference: [Configuration.md](Configuration.md)
- Reverse-proxy/full stack: [Deployment.md](Deployment.md)
- AI setup (optional): [AI-Integration.md](AI-Integration.md)
- Export/import and migrations: [Database.md](Database.md)
