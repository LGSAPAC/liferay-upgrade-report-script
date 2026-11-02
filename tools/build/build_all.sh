#!/bin/bash
# Rebuilds all version scripts from the sources in this folder. Run from anywhere:
#   tools/build/build_all.sh
# Edit the *_section.groovy files / quarterly_template.groovy here, not the generated *-all.groovy files.
set -e
G=$(cd "$(dirname "$0")" && pwd); R=$(cd "$G/../.." && pwd)
T="$G/quarterly_template.groovy $R/dxp_7_0/dxp_7_0-all.groovy $R/dxp_7_1/dxp_7_1-all.groovy $R/dxp_7_2/dxp_7_2-all.groovy $R/dxp_7_3/dxp_7_3-all.groovy"
python3 "$G/privacy.py" "$G/quarterly_template.groovy" "$R/ee_6_1/ee_6_1-all.groovy" "$R/ee_6_2/ee_6_2-all.groovy" \
    "$R/dxp_7_0/dxp_7_0-all.groovy" "$R/dxp_7_1/dxp_7_1-all.groovy" "$R/dxp_7_2/dxp_7_2-all.groovy" "$R/dxp_7_3/dxp_7_3-all.groovy" >/dev/null
python3 "$G/hosting.py" "$G/quarterly_template.groovy" "$R/ee_6_1/ee_6_1-all.groovy" "$R/ee_6_2/ee_6_2-all.groovy" \
    "$R/dxp_7_0/dxp_7_0-all.groovy" "$R/dxp_7_1/dxp_7_1-all.groovy" "$R/dxp_7_2/dxp_7_2-all.groovy" "$R/dxp_7_3/dxp_7_3-all.groovy" >/dev/null
python3 "$G/add_section.py" "$G/compatibility_section.groovy" 1_compatibility 1_hosting "$G/quarterly_template.groovy" "$R/ee_6_1/ee_6_1-all.groovy" \
    "$R/ee_6_2/ee_6_2-all.groovy" "$R/dxp_7_0/dxp_7_0-all.groovy" "$R/dxp_7_1/dxp_7_1-all.groovy" "$R/dxp_7_2/dxp_7_2-all.groovy" \
    "$R/dxp_7_3/dxp_7_3-all.groovy" >/dev/null
python3 "$G/inject_section.py" "$G/deprecations_section.groovy" 6_deprecations x $T >/dev/null
python3 "$G/inject_section.py" "$G/additional_section.groovy" 7_additional_info x $T >/dev/null
python3 "$G/harden.py" "$G/quarterly_template.groovy:7.4" "$R/dxp_7_0/dxp_7_0-all.groovy:7.0" "$R/dxp_7_1/dxp_7_1-all.groovy:7.1" \
    "$R/dxp_7_2/dxp_7_2-all.groovy:7.2" "$R/dxp_7_3/dxp_7_3-all.groovy:7.3" >/dev/null
(cd "$R" && python3 "$G/gen.py" . "$G/quarterly_template.groovy" >/dev/null && python3 "$G/gen_ce.py" . >/dev/null)
echo "Rebuilt ee_6_x / dxp_7_0..dxp_7_3 (sections), dxp_7_4, dxp_YYYY_qN and ce_* scripts"
