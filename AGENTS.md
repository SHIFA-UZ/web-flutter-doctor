# Agent instructions

Read `CLAUDE.md` in this folder: it is the guide for this repository and applies to every agent and every person,
whatever the tool. The workspace guide one level up (`../CLAUDE.md`, with `../AGENTS.md` as its summary) adds the
cross-repository rules and wins where the two differ; in particular its approval rule replaces the "commit and push
to a new branch" steps below.

In short: Flutter with Riverpod 2 and manual providers, screens in `lib/features/<feature>/presentation`, providers
in `lib/state/<feature>/`, all HTTP through `ApiClient`; one branch per change from a fresh `main`, Conventional
Commits, no AI attribution, no push or pull request before the owner has seen the work; the web app deploys to
Firebase Hosting on every merge to `main`.
