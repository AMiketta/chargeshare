# Contributing

Thanks for your interest in ChargeShare! This repository is primarily a **learning / reference project for
Compose Multiplatform**, so issues and pull requests that improve clarity, documentation or correctness are very welcome.

## Workflow

- `main` contains releases, `develop` is the integration branch.
- Branch off `develop` (`feat/<topic>`, `fix/<topic>`, `docs/<topic>`, `chore/<topic>`) and open a pull request **against `develop`**.
- Keep pull requests small and focused; describe *what* and *why*.
- CI must be green (gitleaks secret scan + JVM/server/desktop tests, web and Android builds).

## Local checks

```bash
./gradlew :shared:jvmTest :server:test :composeApp:desktopTest
```

See [README.md](README.md) for the full setup (server, Android, iOS, Web).

## Code style

- Official Kotlin coding conventions (`kotlin.code.style=official`).
- Project conventions and architecture notes are in [AGENTS.md](AGENTS.md) (German).
- Code comments in the codebase are mostly German; new contributions may be in English or German.

## Secrets

Never commit API keys, keystores, `google-services.json`, `GoogleService-Info.plist`, `local.properties` or `.env`.
Configuration is done via environment variables (see `.env.example`). Test code may contain obvious dummy values only
(they are allow-listed in `.gitleaks.toml`).

## License

By contributing you agree that your contributions are licensed under the [Apache License 2.0](LICENSE).
The name and logo "ChargeShare" are not covered by the license (see [NOTICE](NOTICE)).
