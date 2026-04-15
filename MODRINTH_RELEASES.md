# Modrinth version release notes

Short copy for the **Modrinth version description** field. One section per published version; newest first.

---

## 1.1.0-Dev2a (2026-04-14)

- Fixes Folia crash on enable when scheduled restart is on (`UnsupportedOperationException` from `runTaskTimer`).
- Countdown and scheduled-restart timers use the global region scheduler on Folia; Paper behavior unchanged.

---

## 1.1.0-Dev1b (2026-03-21)

- Decorative default `config.yml` header; `config_version` 4.

---

## 1.1.0-Dev1a (2026-03-19)

- Folia support baseline for RestartAnnouncer.
- Branched from the latest version line to avoid breaking existing installs.
