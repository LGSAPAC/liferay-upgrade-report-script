# Liferay Upgrade Report Script

Read-only Groovy scripts that collect the information Liferay needs to assess an upgrade: version, infrastructure,
data volumes, customizations and features in use. They cover Liferay Portal **Community Edition (CE)**, Liferay
Portal **EE** and Liferay **DXP**, including all **Quarterly Releases**.

**Easiest way:** use the web page **https://lgsapac.github.io/liferay-upgrade-report-script/**. It finds the right
script for your version, copies it for you (with an optional privacy setting), explains how to save the output and
prepares the e-mail to Liferay. The report never goes through the page: you save it on your own computer and e-mail
it to Liferay yourself.

> [!IMPORTANT]
> - The scripts are **read-only**: they only read configuration and count records. They do not change data or settings.
> - Run them on a **UAT / test environment only, never on production**. Use a UAT copy that is as close to production
>   as possible (same version, a recent copy of the database and document library, the same custom plugins).
> - On a large database the script can take several minutes, because it counts the rows of every table. Run it
>   outside busy hours.
> - You run these scripts **at your own risk**.

The Liferay upgrade program has five phases:

| Phase | Step | Who | What |
| --- | --- | --- | --- |
| 1. Assessment | 0 | You | Identify your Liferay edition and version |
| | 1 | You | Run the read-only script on UAT |
| | 2 | You | Save the output as a `.txt` file and e-mail it to support@liferay.com |
| | 3 | Liferay | Health check of your installation |
| | 4 | Liferay, you | Qualification for the upgrade program; if you qualify, Liferay shares the contractual terms for sign-off |
| 2. Preparation | 5 | You | Prepare the deliverables and hand them off to the Liferay Upgrade Team |
| 3. Core solution upgrade | 6 | Liferay | Kick-off and upgrade execution, performed by Liferay (8 to 16 weeks) |
| 4. Validation | 7 | You, Liferay | 30 days of UAT testing and fixes |
| 5. Go-Live | 8 | You, Liferay | The upgraded solution goes live; support then comes from the Liferay Support team |

## Step 0: Identify your version

The quickest way: run [`identify_version.groovy`](identify_version.groovy) in the Script console (see Step 1 for
how to run a script). It prints your edition and version and the name of the script to use, for example:

```
Release:  Liferay Community Edition Portal 7.4.3.132 CE GA132 (...)
Edition:  Liferay Portal Community Edition (CE)
Version:  7.4.3.132
Script:   ce_7_4/ce_7_4-all.groovy
```

Or look it up yourself. Sign in as an administrator and open **Server Administration**:

| Version | Where to find Server Administration |
| --- | --- |
| 6.1 | Go to > Control Panel > Server > Server Administration |
| 6.2 | Admin > Control Panel > Configuration > Server Administration |
| 7.0 - 7.3 | Product Menu (top left) > Control Panel > Configuration > Server Administration |
| 7.4 and Quarterly Releases | Global Menu (grid icon, top right) > Control Panel > System > Server Administration |

You can also go directly to the page:

- 6.1 / 6.2: `http(s)://<your-uat-server>/group/control_panel/manage/-/server/resources`
- 7.0 and later: `http(s)://<your-uat-server>/group/guest/~/control_panel/manage?p_p_id=com_liferay_server_admin_web_portlet_ServerAdminPortlet`

The version is shown at the top of the page, for example:

| Shown on the page | Edition | Version |
| --- | --- | --- |
| `Liferay Portal Community Edition 6.2 CE GA6 (...)` | CE | 6.2 |
| `Liferay Community Edition Portal 7.4.3.132 CE GA132 (...)` | CE | 7.4 |
| `Liferay Digital Experience Platform 7.4.13 Update 92 (...)` | DXP | 7.4 |
| `Liferay Digital Experience Platform 2025.Q1.29 LTS (...)` | DXP | 2025.Q1 |

The same text is printed in the server log when Liferay starts (look for `Starting Liferay`).

Then download the script for your edition and version. Open the file, click **Raw**, and save it (or copy its whole
content).

### Community Edition (CE)

| Your version | Script |
| --- | --- |
| Liferay Portal CE 6.1 (GA1 - GA3) | `ce_6_1/ce_6_1-all.groovy` |
| Liferay Portal CE 6.2 (GA1 - GA6) | `ce_6_2/ce_6_2-all.groovy` |
| Liferay Portal CE 7.0 (GA1 - GA7) | `ce_7_0/ce_7_0-all.groovy` |
| Liferay Portal CE 7.1 (GA1 - GA4) | `ce_7_1/ce_7_1-all.groovy` |
| Liferay Portal CE 7.2 (GA1 - GA2) | `ce_7_2/ce_7_2-all.groovy` |
| Liferay Portal CE 7.3 (GA1 - GA8) | `ce_7_3/ce_7_3-all.groovy` |
| Liferay Portal CE 7.4 (GA1 - GA132) | `ce_7_4/ce_7_4-all.groovy` |

### Liferay Portal EE / Liferay DXP

| Your version | Script |
| --- | --- |
| Liferay Portal EE 6.1 | `ee_6_1/ee_6_1-all.groovy` |
| Liferay Portal EE 6.2 | `ee_6_2/ee_6_2-all.groovy` |
| Liferay DXP 7.0 | `dxp_7_0/dxp_7_0-all.groovy` |
| Liferay DXP 7.1 | `dxp_7_1/dxp_7_1-all.groovy` |
| Liferay DXP 7.2 | `dxp_7_2/dxp_7_2-all.groovy` |
| Liferay DXP 7.3 | `dxp_7_3/dxp_7_3-all.groovy` |
| Liferay DXP 7.4 (GA1 - Update 92) | `dxp_7_4/dxp_7_4-all.groovy` |
| Liferay DXP 2023.Q3 | `dxp_2023_q3/dxp_2023_q3-all.groovy` |
| Liferay DXP 2023.Q4 LTS | `dxp_2023_q4/dxp_2023_q4-all.groovy` |
| Liferay DXP 2024.Q1 | `dxp_2024_q1/dxp_2024_q1-all.groovy` |
| Liferay DXP 2024.Q2 LTS | `dxp_2024_q2/dxp_2024_q2-all.groovy` |
| Liferay DXP 2024.Q3 | `dxp_2024_q3/dxp_2024_q3-all.groovy` |
| Liferay DXP 2024.Q4 | `dxp_2024_q4/dxp_2024_q4-all.groovy` |
| Liferay DXP 2025.Q1 LTS | `dxp_2025_q1/dxp_2025_q1-all.groovy` |
| Liferay DXP 2025.Q2 | `dxp_2025_q2/dxp_2025_q2-all.groovy` |
| Liferay DXP 2025.Q3 | `dxp_2025_q3/dxp_2025_q3-all.groovy` |
| Liferay DXP 2025.Q4 | `dxp_2025_q4/dxp_2025_q4-all.groovy` |
| Liferay DXP 2026.Q1 LTS | `dxp_2026_q1/dxp_2026_q1-all.groovy` |
| Liferay DXP 2026.Q2 | `dxp_2026_q2/dxp_2026_q2-all.groovy` |
| Liferay DXP 2026.Q3 | `dxp_2026_q3/dxp_2026_q3-all.groovy` |

A Quarterly Release script works for every patch of that quarter (for example `dxp_2025_q1` for 2025.Q1.0 to
2025.Q1.x).

## Step 1: Run the script

1. On your **UAT** environment, open **Server Administration** (see Step 0) and select the **Script** tab.
2. Set **Language** to **Groovy**.
3. Delete the sample code in the script box, and paste the **whole** content of the script you downloaded.
   - **Optional, privacy:** to hide names and addresses, change `def UAS_MASK_PRIVATE_DATA = false` to
     `def UAS_MASK_PRIVATE_DATA = true` near the top of the pasted script. Site names, site URLs, virtual instance
     names / web IDs, virtual hosts, host names, e-mail addresses, IP addresses, the database location and
     installation paths are then replaced with placeholders such as `site-1`, `url-1` or `host-1` (the same
     placeholder every time the same value appears). All counts stay the same, and the output contains
     `"uas_private_data_masked", "true"`. Names of custom code (portlets, plugins, modules, themes, object
     definitions) are not masked, because Liferay needs them to assess the upgrade.
4. Click **Execute** and wait until the page reloads. Don't click again or close the page while it runs.
5. The result appears in the **Output** box below the script. It starts with `"Key", "Value", "Description"` and
   ends with lines starting with `"uas_`.

Before you run it, make sure you identified your version in Step 0: a script for another version can fail or
report wrong values.

## Step 2: Save the output and send it to Liferay

1. Save the output as a text file:
   - Click inside the **Output** box, select all of it (Ctrl+A / Cmd+A) and copy it (Ctrl+C / Cmd+C).
   - Windows: open **Notepad**, paste, then **File > Save As**, set **Save as type** to *Text Documents (\*.txt)*.
   - Mac: open **TextEdit**, choose **Format > Make Plain Text**, paste, then **File > Save** with a `.txt` name.
   - Name the file `<company>_<liferay-version>_upgrade-report_<yyyy-mm-dd>.txt`, for example
     `acme_ce-7.4-ga132_upgrade-report_2026-11-01.txt`.
2. Check the file before sending it: it should contain the `"uas_end_date"` line at the end. If you see lines with
   `UAS ERROR`, still send the file; it tells Liferay what could not be read.
3. E-mail the `.txt` file as an attachment to **support@liferay.com**, with the subject
   *"Upgrade assessment report - <company>"*. If you know your **Liferay customer success contact**, put them in CC.
   Don't paste the report into a website or chat: send it only as an e-mail attachment.

The output contains technical details about your installation (versions, the database URL, plugin and site names,
record counts) but no content from your sites, and the scripts do not read passwords. If you prefer not to share
names and addresses, use the privacy option (see Step 1); otherwise, if your JDBC URL contains a user name or
password, remove them from the `.txt` file before sending it. Share the file only with Liferay.

## Step 3: Liferay health check

Liferay reviews the report and carries out a health check of your installation: version, infrastructure, data
volumes, customizations, and features that are deprecated in newer releases.

## Step 4: Qualification and agreement

Liferay validates whether you qualify for the Liferay upgrade program. If you qualify, Liferay shares the
contractual terms with you for sign-off.

## Step 5: Preparation

You prepare the deliverables for the upgrade and hand them off to the Liferay Upgrade Team. Liferay confirms the
exact list; it typically includes a recent copy of the database and document library, the source code of your
customizations, your configuration, and access to the environments and contacts the team needs.

## Step 6: Core solution upgrade

After the kick-off, Liferay performs the upgrade of your solution. This typically takes 8 to 16 weeks.

## Step 7: Validation

30 days of UAT testing of the upgraded solution; issues found are fixed.

## Step 8: Go-Live

The upgraded solution goes live. From then on, support comes from the Liferay Support team.

## If something fails

The scripts are built to always finish and always print every key:

- Each part of the report runs on its own. If a feature is not available, a module is deactivated (for example
  Commerce), or a module failed to start after an upgrade, only the keys of that part are reported with an empty
  value and an `n/a - <reason>` description; the rest of the report is not affected.
- Optional modules are loaded when the script runs instead of being imported, so a missing module cannot stop the
  script from starting. With Commerce deactivated, `newitems_total_commerce_products` is counted in its database
  table.
- Every table count has its own time limit (120 s); tables that cannot be counted are listed in
  `data_tables_unreadable_count`.
- `uas_section_durations` shows how long each part took, which helps if the Script console times out on very large
  installations.

If the page shows *Unable to execute script*, send the error text together with your version.

## Output format

All scripts print CSV lines: `"Key", "Value", "Description"`.

- **CE scripts** print the same keys as the EE / DXP script for that version, plus:
  `ce_edition_detected`, `ce_target_version`, `ce_target_match`, `ce_ga_level`, `ce_commercial_equivalent`,
  `ce_health_flags_count` (for example HSQL database, Java version, no clustering, 6.x plugins to migrate)
  and `uas_target_liferay_edition` (`CE`).
- **7.4 and Quarterly Release scripts** contain all the keys of the previous 7.4 script, plus:
  - Release: `infra_liferay_release_type`, `infra_liferay_edition`, `infra_liferay_update_level`,
    `infra_liferay_quarterly_release` and `infra_liferay_quarterly_release_lts` (Quarterly Release scripts only),
    `infra_liferay_build_age_days`, `infra_liferay_script_target_release`, `infra_liferay_script_target_match`
  - Upgrade readiness: `infra_portal_schema_version`, `infra_upgrade_failed_modules_count`,
    `infra_feature_flags_enabled_count` (feature.flag.* portal properties), `infra_feature_flags_active_count`
    (feature flags enabled per virtual instance, incl. those switched on in Instance / System Settings, by type: beta,
    release, deprecation, dev), `infra_database_partition_enabled`, `infra_cluster_link_enabled`,
    `infra_upgrade_database_auto_run`, `infra_company_default_web_id`
  - Data: `data_forms_count`, `data_form_records_count`, `data_layout_page_template_structures_count`,
    `data_asset_entries_count`, `data_dl_file_versions_count`, `data_organizations_count`,
    `data_user_groups_count`, `data_roles_count`, `data_virtual_hosts_count`
  - Objects: `newitems_total_custom_object_entries_count`, `newitems_custom_object_definitions_info` (per custom
    object: scope, storage, active, fields, relationships, entries), `newitems_picklists_count`,
    `newitems_object_relationships_count`, `newitems_object_actions_count`
  - Client extensions and remote apps, depending on the release: `newitems_remote_apps_count` (early 7.4, with name,
    type and URL), `newitems_client_extensions_info` (created in the UI, with name, type and status),
    `newitems_all_client_extensions_count` (created in the UI and deployed from a Liferay Workspace, by type),
    `newitems_deployed_client_extensions_count` (files in `osgi/client-extensions`)
  - Other features: `newitems_client_extensions_count`,
    `newitems_search_blueprints_count`, `newitems_search_elements_count`,
    `newitems_workflow_definitions_count`, `newitems_style_books_count`, `newitems_collections_count`,
    `newitems_batch_planner_plans_count`, `newitems_oauth2_applications_count`

- **Sizing and hidden customizations** (all 7.x, CE and Quarterly Release scripts):
  - Documents and Media: `data_dl_total_size_bytes`, `data_dl_all_versions_size_bytes` (approximate file store
    size to migrate), `data_dl_files_by_mime_type`
  - Marketplace apps: `custom_code_marketplace_apps_count` (`.lpkg` files not provided by Liferay)
  - Scripts outside of modules: `custom_code_workflow_scripts_count` (Groovy / JavaScript / Java scripts in workflow
    definitions), `custom_code_scheduled_jobs_count` (scheduled jobs not provided by Liferay),
    `custom_code_dispatch_triggers_count` (Dispatch triggers, e.g. Groovy scripts)
  - Languages: `data_locales_enabled_count` (languages per virtual instance, sites with their own languages)
- **Deprecation checks** (all 7.x, CE and Quarterly Release scripts): `deprecation_*` lines show how much the
  installation uses features that Liferay has deprecated, archived, removed or put in maintenance mode, with the
  release and the recommended replacement taken from the "Maintenance Mode and Deprecations" and "Breaking Changes"
  pages on [Liferay Learn](https://learn.liferay.com/w/dxp/self-hosted-installation-and-upgrades/upgrading-liferay/deprecations-and-breaking-changes-reference)
  (7.3, 7.4 and the Quarterly Releases up to 2026.Q3). `deprecations_in_use_count` summarizes everything found:
  - Widgets on pages: Message Boards, Wiki, Microblogs, Questions, Activities, Contacts, Media Gallery,
    Subscriptions, Sites Directory, XSL Content, Web Content Search, Dynamic Data Lists, Kaleo Forms, Polls,
    Bookmarks, classic Search, Asset Publisher and more (`deprecation_widget_*_pages_count`)
  - Pages: widget pages, panel and full page application pages, collection pages, private pages, widget page
    templates, site template propagation, Dialect and archived themes (also `data_layout_types`, `data_themes_in_use`)
  - Data: Message Boards, Wiki, Microblogs, Dynamic Data Lists, Kaleo Forms, Polls, App Builder, Bookmarks, Forms,
    Custom Fields, Knowledge Base, Blogs, local Segments, Object 1:1 relationships, widgets embedded in fragments,
    Velocity templates and Velocity in workflow
  - Deprecation feature flags that are switched on (`deprecation_feature_flags_enabled_count`)
  - Configuration and platform: CAS, NTLM, OpenAM / OpenSSO, Google Login, OpenID, OAuth 1.0a, Akismet, Live
    Users, SOAP web services, OpenOffice, Java version, application server, Elasticsearch 7 / Solr / sidecar,
    Liferay Sync
  - Custom code in `osgi/modules` and `osgi/war`: Java EE (`javax.*`, Jakarta EE from 2025.Q3), Soy,
    liferay-npm-bundler / AMD, removed `aui:nav-*` tags, `Liferay.OAuth2`, EXT plugins

If a script does not match the running version, `ce_target_match` / `infra_liferay_script_target_match` is `false`
and the description names the script to use instead. A feature that does not exist in a given release is reported
with an empty value and an `n/a - ...` description instead of stopping the script.

## For maintainers

The 7.4, Quarterly Release and CE scripts are generated, and the 7.0 - 7.3 scripts get their shared sections, from
the sources in [`tools/build`](tools/build). Change the sources there and run `tools/build/build_all.sh`, instead
of editing the `*-all.groovy` files one by one.

