# Contributing

## Branch model

- `main`: release branch. Protected. Changes arrive only through a pull request from `develop`, approved by the owner (see `.github/CODEOWNERS`).
- `develop`: integration branch. Protected. Changes arrive only through pull requests from feature branches.
- `feature/<name>`: one focused unit of work, branched from `develop`, merged back into `develop` via pull request.

## Workflow

1. `git switch develop && git pull`, then `git switch -c feature/<name>`.
2. Commit using conventional messages (`feat:`, `fix:`, `refactor:`, `test:`, `build:`, `docs:`, `chore:`).
3. Open a pull request into `develop`.
4. When `develop` is ready to release, open a pull request `develop` -> `main` for owner approval.

No force-pushes to `main` or `develop`.
