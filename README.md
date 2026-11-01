# Liferay Upgrade Report Script

Read-only Groovy scripts that collect the information Liferay needs to assess an upgrade: version, infrastructure,
data volumes, customizations and features in use. They cover Liferay Portal **Community Edition (CE)**, Liferay
Portal **EE** and Liferay **DXP**, including all **Quarterly Releases**.

> [!IMPORTANT]
> - The scripts are **read-only**: they only read configuration and count records. They do not change data or settings.
> - Run them on a **UAT / test environment only, never on production**. Use a UAT copy that is as close to production
>   as possible (same version, a recent copy of the database and document library, the same custom plugins).
> - On a large database the script can take several minutes, because it counts the rows of every table. Run it
>   outside busy hours.
> - You run these scripts **at your own risk**.

The process has three steps:

| Step | Who | What |
| --- | --- | --- |
| 0 | You | Identify your Liferay edition and version, and download the matching script |
| 1 | You | Run the script on UAT, save the output as a `.txt` file and send it to Liferay |
| 2 | Liferay | Health check of your installation, and validation of whether you qualify for the Liferay upgrade program |

## Step 0: Identify your version

Sign in as an administrator and open **Server Administration**:

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

## Step 1: Run the script and send the output

1. On your **UAT** environment, open **Server Administration** (see Step 0) and select the **Script** tab.
2. Set **Language** to **Groovy**.
3. Delete the sample code in the script box, and paste the **whole** content of the script you downloaded.
4. Click **Execute** and wait until the page reloads. Don't click again or close the page while it runs.
5. The result appears in the **Output** box below the script. It starts with `"Key", "Value", "Description"` and
   ends with lines starting with `"uas_`.
6. Save the output as a text file:
   - Click inside the **Output** box, select all of it (Ctrl+A / Cmd+A) and copy it (Ctrl+C / Cmd+C).
   - Windows: open **Notepad**, paste, then **File > Save As**, set **Save as type** to *Text Documents (\*.txt)*.
   - Mac: open **TextEdit**, choose **Format > Make Plain Text**, paste, then **File > Save** with a `.txt` name.
   - Name the file `<company>_<liferay-version>_upgrade-report_<yyyy-mm-dd>.txt`, for example
     `acme_ce-7.4-ga132_upgrade-report_2026-11-01.txt`.
7. Check the file before sending it: it should contain the `"uas_end_date"` line at the end. If you see lines with
   `UAS ERROR`, still send the file; it tells Liferay what could not be read.
8. Send the `.txt` file to Liferay:
   - **Liferay subscribers (EE / DXP):** open a ticket in the [Liferay Help Center](https://help.liferay.com),
     choose your project, describe the request as *"Upgrade assessment report"*, and attach the `.txt` file.
   - **Community Edition users, or if you prefer email:** email the `.txt` file as an attachment to your
     **designated Liferay customer success contact**, with the subject *"Upgrade assessment report - <company>"*.

The output contains technical details about your installation (versions, the database URL, plugin and site names,
record counts) but no content from your sites. The scripts do not read passwords; however, the database URL is
included as it is configured, so if your JDBC URL contains a user name or password, remove them from the `.txt` file
before sending it. Share the file only with Liferay.

## Step 2: Liferay health check

Liferay reviews the report, carries out a health check of your installation, and validates whether you qualify for
the Liferay upgrade program. Your customer success contact will follow up with the results and the next steps.

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
    `infra_feature_flags_enabled_count`, `infra_database_partition_enabled`, `infra_cluster_link_enabled`,
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
  - Configuration and platform: CAS, NTLM, OpenAM / OpenSSO, Google Login, OpenID, OAuth 1.0a, Akismet, Live
    Users, SOAP web services, OpenOffice, Java version, application server, Elasticsearch 7 / Solr / sidecar,
    Liferay Sync
  - Custom code in `osgi/modules` and `osgi/war`: Java EE (`javax.*`, Jakarta EE from 2025.Q3), Soy,
    liferay-npm-bundler / AMD, removed `aui:nav-*` tags, `Liferay.OAuth2`, EXT plugins

If a script does not match the running version, `ce_target_match` / `infra_liferay_script_target_match` is `false`
and the description names the script to use instead. A feature that does not exist in a given release is reported
with an empty value and an `n/a - ...` description instead of stopping the script.
