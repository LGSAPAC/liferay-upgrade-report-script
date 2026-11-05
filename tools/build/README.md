# Build tools for the version scripts

The `*-all.groovy` scripts share most of their code. Edit the sources here and rebuild:

```
tools/build/build_all.sh
```

| Source | Used for |
| --- | --- |
| `stamp_version.py` | writes `VERSION` (repository root) into every script as `uas_version` |
| `quarterly_template.groovy` | `dxp_7_4` and every `dxp_YYYY_qN` script (via `gen.py`) |
| `helpers_section.groovy` | `uasClass` (runtime class loading), `uasGuard` (per-block error handling), `uasTimed` (durations) |
| `data_section.groovy` | the `2_data` section of all 7.x scripts (one implementation, `@@DATA_VARIANT@@` per version) |
| `dbinfo_section.groovy` | the `3_database_info` section of all 7.x scripts |
| `deprecations_section.groovy` | the `6_deprecations` section of all 7.x scripts |
| `additional_section.groovy` | the `7_additional_info` section of all 7.x scripts |
| `hosting_section.groovy` | the `1_hosting` section (hosting model) of all scripts, 6.1 and later (via `hosting.py`) |
| `compatibility_section.groovy` | the `1_compatibility` section (support lifecycle, compatibility matrix, detectable breaking changes) of all scripts, 6.1 and later (via `add_section.py`). Update the dates and matrix when a new release or matrix is published |
| `ce_section.groovy` | the CE-only section of the `ce_*` scripts (via `gen_ce.py`, from the matching EE / DXP script) |
| `harden.py` | applies the shared sections and robustness fixes to the template and to `dxp_7_0` - `dxp_7_3` (idempotent) |

`build_all.sh` can be run any number of times: a second run does not change any file.

Rules for script code (they keep the scripts working when a module is not active):

- Import only portal-kernel / portal-impl classes. Load classes of OSGi modules with `uasClass('fully.qualified.Name')`.
- Wrap every independent part of the report in `uasGuard([keys]) { ... }` so a failure only affects its own keys.
- Keep the output format: `"key", "value", "description"`, one key per line.
