# Codex command rules

These Starlark files are narrow command-prefix tripwires, not coding instructions.
Root and nearest `AGENTS.md` govern task scope, authorization and engineering.

- `git-safety.rules`: remote push and common destructive reset/clean/checkout forms.
- `secrets-safety.rules`: direct `cat .env` / `cat ./.env` disclosure.
- `infrastructure-safety.rules`: persistent-data deletion, migration-history and recursive host-permission changes.
- `external-publish.rules`: PR/release/package/image publication and cluster mutations.

Each rule covers direct commands, `rtk` and `rtk proxy`. Ordinary staging,
unstaging, local branch work, environment setup and build-cache cleanup have
no project-specific prompt. This is not an `allow` grant: sandbox, user and
managed policies still apply.

A prefix matcher cannot infer intent, prior conversational authorization,
arbitrary argument ordering, script contents or secret data flow. For example,
`git -C repo push` and `docker compose -f compose.yml down -v` do not match these
literal prefixes. Never rearrange or wrap commands to evade a required approval.
Root authorization and secret-handling rules still apply to unmatched forms.

`prompt` may require an execution approval even after conversational authorization;
do not add another conversational confirmation. The most restrictive matching
policy wins. These files do not guarantee enforcement under every approval or
sandbox mode; they are not a replacement for an isolation boundary.

Project-local rules load only from a trusted `.codex/` layer at startup.
Restart Codex after changes. Validate without executing the target command:

```bash
codex execpolicy check --pretty --rules .codex/rules/git-safety.rules -- git push origin main
codex execpolicy check --pretty --rules .codex/rules/git-safety.rules -- git restore --staged src/file
codex execpolicy check --pretty --rules .codex/rules/secrets-safety.rules -- cat .env
codex execpolicy check --pretty --rules .codex/rules/infrastructure-safety.rules -- docker compose down -v
codex execpolicy check --pretty --rules .codex/rules/external-publish.rules -- docker push registry.example/app:tag
```

See [rule design](../../docs/development/coding-guidelines.md) and
[Codex rules documentation](https://learn.chatgpt.com/docs/agent-configuration/rules).
