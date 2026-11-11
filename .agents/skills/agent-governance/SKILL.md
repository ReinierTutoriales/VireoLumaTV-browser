---
name: agent-governance
description: Mandatory rules for all agents. Enforces branch naming, authorship, verification before writing, and optimal documentation. Use before any commit, branch, or documentation change.
---

# Agent Governance

Hard constraints that override convenience:

## Branches
- Never create, push, or reference branches whose name contains an AI name, model name, tool name, or non-owner identifier.
- Forbidden patterns: `claude-*`, `gpt-*`, `grok-*`, `ai-*`, `agent-*`, `copilot-*`, `cursor-*`, or any similar.
- Use only short, descriptive, owner-neutral names that describe the change.

## Authorship and signing
- Never set author, committer, or any signature to a name, email, or identity other than the repository owner (`ReinierTutoriales`).
- Do not insert AI names, tool names, or third-party identities into commit messages, trailers (`Signed-off-by`, `Co-authored-by`), or file metadata.
- Commit messages must describe the change factually; do not attribute the work to an agent.
- Agent environments can sign commits through a global Git config (`commit.gpgsign`, an agent key). Before the first commit run `git config --show-origin --get-regexp 'user\.|gpg|sign'`; set `commit.gpgsign=false` in the repository and check `git cat-file -p HEAD` has no `gpgsig` before pushing (2026-11-10).
- Some agent sessions append an attribution footer to every PR description and comment written through their GitHub proxy, and add it back on each edit. From such a session, never create, edit or comment on PRs or issues: push the branch and give the owner the compare link (`/compare/master...<branch>`) to open the PR. If a footer was already published, report it; only the owner can remove it (2026-11-11).

## Owner decisions

- Updating an existing component to the latest official stable release is standing
  owner authorization: proceed without asking, following `AGENTS.md` and exact-SHA
  verification. Resolve migration requirements; report concrete blockers. This
  exception does not authorize unrelated behavior changes or release publication.
- Ask the owner before implementing, integrating or changing any behavior that was not
  explicitly requested, including follow-up "improvements" found during a task. Present the
  finding and options; wait for the owner's choice.
- Merging, releasing, closing PRs and deleting branches also require the owner's answer.
- A behavior the owner chose stays as it is until the owner decides otherwise; the
  contracts are in `docs/REGRESSION_POLICY.md`.

## Long-running jobs (owner decision, 2026-11-11)

- After a push, PR, merge or workflow dispatch, do not wait, sleep or poll for CI,
  releases or other remote jobs. Report the SHA, PR and run link, then end the turn;
  the owner says when it has finished.
- Read status with one REST call (`commits/{sha}/check-runs`, `actions/runs`) only
  when the owner asks or when the next action depends on it (merge, release). If it
  is still running, report that and stop. Fetch failed-job logs only for a failure.

## Verify before writing
- Read the relevant source, tests, contracts, and existing documentation before proposing or writing any change.
- Do not write code, comments, or documentation for the sake of writing.
- Every line added must be the minimal change that solves a demonstrated need and is traceable to existing code or a clearly labeled, minimal assumption.
- If something is unclear, state the gap explicitly instead of inventing behavior.

## Documentation
- Update documentation only when it reflects verified current behavior.
- Do not invent APIs, policies, guarantees, or future plans.
- Prefer editing existing docs over creating new files.
- Keep documentation short, precise, and consistent with the code and tests.
- Keep public README copy focused on the reader: identity, benefits and a clear download; put maintenance rules in their dedicated guides and secondary setup details behind expandable sections.

## Optimal code
- Prefer the simplest correct solution that matches existing style and patterns.
- Avoid speculative refactors, unused helpers, extra abstractions, or changes without measured benefit.
- A change is incomplete until it has been verified against existing tests and contracts.

These rules apply to every agent and every change.

## Learn from verified errors

- After a meaningful regression, failed check or owner correction, identify the
  violated contract and the cause from code, logs or a reproduction. Mark an
  unconfirmed cause as a hypothesis; do not turn it into a permanent rule.
- Fix the cause and its regression coverage first. Add one concise, reusable
  lesson to the relevant existing skill only if it prevents recurrence; update
  or remove superseded guidance instead of growing a chronological error log.
- Keep product contracts and evidence in `docs/REGRESSION_POLICY.md` and the
  existing audit, with a test or commit reference. Do not duplicate them here.
- Before the next related task, read that lesson and check whether its conditions
  still apply. New owner instructions take precedence over historical lessons.
