# Agent instructions

Read `CLAUDE.md` in this folder: it is the guide for this repository and applies to every agent and every person,
whatever the tool. The workspace guide one level up (`../CLAUDE.md`, with `../AGENTS.md` as its summary) adds the
cross-repository rules and wins where the two differ.

In short: on `doc-app2.0` this repo is being rebuilt as a Kotlin Multiplatform app (`composeApp/`, package
`shifa.doctor`, MVVM + Repository, Compose Multiplatform, Ktor), from the spec in `docs/kmp-spec/`, the flows in
`docs/ux-flows/` and the design in `docs/design/`. What is built is recorded in `docs/implemented/README.md`. The
Flutter app stays on `main` until the rewrite is merged. Conventional Commits, no AI attribution, no push or pull
request before the owner has seen the work.
