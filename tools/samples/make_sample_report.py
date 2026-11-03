"""Generates samples/sample-health-check-report.pdf: an example of the Liferay DXP architecture health check that
customers receive after the assessment (step 3). ALL DATA IS FICTITIOUS.

usage: python3 tools/samples/make_sample_report.py
"""
import os

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import KeepTogether, PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'samples', 'sample-health-check-report.pdf')

BLUE = colors.HexColor('#0b5fff')
INK = colors.HexColor('#1c2129')
MUTED = colors.HexColor('#5b6472')
LINE = colors.HexColor('#d9dde3')
SOFT = colors.HexColor('#f0f2f5')
RAG = {'Green': colors.HexColor('#1d7a3a'), 'Amber': colors.HexColor('#b45309'), 'Red': colors.HexColor('#b3261e')}

DISCLAIMER = ('This is a fictitious example. The company, people, data and findings are invented for illustration. '
              'The Liferay DXP architecture health check is provided on a best-effort basis and for reference only. It is '
              'based on the output of the read-only assessment script and is not an audit, a guarantee or a warranty. '
              'Results and recommendations depend on your environment and may change after further review.')

styles = getSampleStyleSheet()
H1 = ParagraphStyle('H1', parent=styles['Heading1'], fontName='Helvetica-Bold', fontSize=16, textColor=INK, spaceBefore=4, spaceAfter=8)
H2 = ParagraphStyle('H2', parent=styles['Heading2'], fontName='Helvetica-Bold', fontSize=12, textColor=BLUE, spaceBefore=9, spaceAfter=4)
BODY = ParagraphStyle('Body', parent=styles['Normal'], fontName='Helvetica', fontSize=9.5, leading=13.5, textColor=INK)
SMALL = ParagraphStyle('Small', parent=BODY, fontSize=8, leading=11, textColor=MUTED)
CELL = ParagraphStyle('Cell', parent=BODY, fontSize=8.5, leading=11.5)
CELLB = ParagraphStyle('CellB', parent=CELL, fontName='Helvetica-Bold')
COVER_T = ParagraphStyle('CoverT', parent=BODY, fontName='Helvetica-Bold', fontSize=26, leading=31, textColor=INK)
COVER_S = ParagraphStyle('CoverS', parent=BODY, fontSize=13, leading=18, textColor=MUTED)


def p(text, style=BODY):
    return Paragraph(text, style)


def rag(value):
    return Paragraph(f'<font color="{RAG[value].hexval()}"><b>{value}</b></font>', CELL)


def table(rows, widths, header=True):
    data = [[c if not isinstance(c, str) else Paragraph(c, CELLB if (header and i == 0) else CELL) for c in row]
            for i, row in enumerate(rows)]
    t = Table(data, colWidths=[w * mm for w in widths], repeatRows=1 if header else 0)
    style = [('GRID', (0, 0), (-1, -1), 0.5, LINE), ('VALIGN', (0, 0), (-1, -1), 'TOP'),
             ('TOPPADDING', (0, 0), (-1, -1), 3), ('BOTTOMPADDING', (0, 0), (-1, -1), 3)]
    if header:
        style.append(('BACKGROUND', (0, 0), (-1, 0), SOFT))
    t.setStyle(TableStyle(style))
    return t


def box(text, color=SOFT):
    t = Table([[Paragraph(text, SMALL)]], colWidths=[170 * mm])
    t.setStyle(TableStyle([('BACKGROUND', (0, 0), (-1, -1), color), ('BOX', (0, 0), (-1, -1), 0.5, LINE),
                           ('LEFTPADDING', (0, 0), (-1, -1), 8), ('RIGHTPADDING', (0, 0), (-1, -1), 8),
                           ('TOPPADDING', (0, 0), (-1, -1), 6), ('BOTTOMPADDING', (0, 0), (-1, -1), 6)]))
    return t


def decorate(canvas, doc):
    canvas.saveState()
    w, h = A4
    # watermark
    canvas.setFillColor(colors.Color(0.85, 0.1, 0.1, alpha=0.08))
    canvas.setFont('Helvetica-Bold', 54)
    canvas.translate(w / 2, h / 2)
    canvas.rotate(35)
    canvas.drawCentredString(0, 0, 'SAMPLE - FICTITIOUS DATA')
    canvas.restoreState()
    canvas.saveState()
    canvas.setFillColor(BLUE)
    canvas.rect(0, h - 6 * mm, w, 6 * mm, stroke=0, fill=1)
    canvas.setFont('Helvetica', 7.5)
    canvas.setFillColor(MUTED)
    canvas.drawString(20 * mm, 12 * mm, 'Sample Liferay DXP architecture health check - fictitious data - best effort, for reference only')
    canvas.drawRightString(w - 20 * mm, 12 * mm, f'Page {doc.page}')
    canvas.restoreState()


def build():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    doc = SimpleDocTemplate(OUT, pagesize=A4, leftMargin=20 * mm, rightMargin=20 * mm, topMargin=20 * mm, bottomMargin=22 * mm,
                            title='Sample Liferay DXP Architecture Health Check (fictitious data)', author='Liferay Upgrade Program',
                            subject='Example report - best effort, for reference only')
    s = []

    # ---- cover --------------------------------------------------------------------------------------------------
    s += [Spacer(1, 30 * mm), p('SAMPLE REPORT', ParagraphStyle('k', parent=BODY, fontName='Helvetica-Bold', textColor=BLUE, fontSize=10)),
          Spacer(1, 4 * mm), p('Liferay DXP Architecture Health Check', COVER_T), Spacer(1, 3 * mm),
          p('Liferay Upgrade Program - Assessment phase, step 3', COVER_S), Spacer(1, 14 * mm)]
    s.append(table([['Customer', 'Example Corp (fictitious)'],
                    ['Environment assessed', 'UAT, copy of production from 2026-10-15'],
                    ['Current release', 'Liferay DXP 7.3 (Update 35)'],
                    ['Recommended target', 'Liferay DXP 2026.Q1 LTS'],
                    ['Input', 'Output of the read-only assessment script dxp_7_3-all.groovy'],
                    ['Report date', '2026-11-03']], [55, 115], header=False))
    s += [Spacer(1, 14 * mm), box('<b>Disclaimer.</b> ' + DISCLAIMER, colors.HexColor('#fff4de')), PageBreak()]

    # ---- 1. summary ---------------------------------------------------------------------------------------------
    s += [p('1. Executive summary', H1),
          p('Example Corp runs Liferay DXP 7.3, which left Premium Support on 2024-10-12 and is in Limited Support until '
            '2027-10-12. The platform is stable and well within sizing limits, but the application server, JDK, search engine '
            'and part of the custom code must change for an upgrade to 2026.Q1 LTS. None of the findings blocks the upgrade.'),
          Spacer(1, 4 * mm)]
    s.append(table([['Area', 'Rating', 'Summary'],
                    ['Support lifecycle', rag('Amber'), 'Limited Support: no new fixes. Plan the upgrade before 2027-10-12.'],
                    ['Infrastructure', rag('Red'), 'Tomcat 9, JDK 11 and Elasticsearch 7.17 are not certified for 2026.Q1 LTS.'],
                    ['Database', rag('Amber'), 'MySQL 5.7 is deprecated; move to MySQL 8.0 or 8.4 with the upgrade.'],
                    ['Data volumes', rag('Green'), '14,200 users, 38,500 web content articles, 212 GB documents: standard upgrade.'],
                    ['Customizations', rag('Amber'), '47 custom modules, 14 use Java EE (javax.*) and need Jakarta EE.'],
                    ['Deprecated features', rag('Amber'), '6 deprecated features in use, 2 with no direct replacement.']],
                   [38, 20, 112]))
    s += [Spacer(1, 4 * mm), p('<b>Overall:</b> ready for the upgrade program, with infrastructure changes and a code migration '
                               '(Java EE to Jakarta EE) as the main work items.')]

    # ---- 2. support lifecycle -------------------------------------------------------------------------------------
    s += [KeepTogether([p('2. Support lifecycle', H2), table([['Release', 'First ship date', 'End of Premium Support', 'End of life', 'Status today'],
                 ['Liferay DXP 7.3', '2020-10-13', '2024-10-12', '2027-10-12', 'Limited Support'],
                 ['Liferay DXP 2026.Q1 LTS (target)', '2026-03-18', '2029-03-17', '2031-03-17', 'Premium Support']],
                [48, 28, 34, 26, 34])])]

    # ---- 3. compatibility ---------------------------------------------------------------------------------------------
    s += [KeepTogether([p('3. Compatibility with the target release (2026.Q1 LTS)', H2), table([['Component', 'Current', 'Status for 2026.Q1 LTS', 'Recommended action'],
                 ['JDK', 'OpenJDK 11.0.22', rag('Red'), 'Move to JDK 21 (JDK 17 is deprecated).'],
                 ['Application server', 'Apache Tomcat 9.0.85', rag('Red'), 'Move to Tomcat 10.1 (Jakarta EE 10).'],
                 ['Database', 'MySQL 5.7.44', rag('Amber'), 'Deprecated; upgrade to MySQL 8.0 or 8.4.'],
                 ['Operating system', 'Red Hat Enterprise Linux 7.9', rag('Amber'), 'Deprecated; plan RHEL 8, 9 or 10.'],
                 ['Search engine', 'Elasticsearch 7.17.18 (remote)', rag('Red'), 'Elasticsearch 7 reached end of life; upgrade to 8.19.'],
                 ['Rich text editor', 'CKEditor 4 (default)', rag('Amber'), 'Deprecated from 2026.Q3; review custom CKEditor plugins.'],
                 ['Hosting', 'Self-hosted - cloud virtual machine (AWS)', rag('Green'), 'Supported (EC2 and RDS are certified).']],
                [32, 44, 30, 64])])]

    # ---- 4. data volumes ------------------------------------------------------------------------------------------------
    s += [KeepTogether([p('4. Data volumes and sizing', H2), table([['Item', 'Count / size', 'Comment'],
                 ['Virtual instances / sites', '2 / 64', ''],
                 ['Users / organizations', '14,200 / 310', ''],
                 ['Web content articles (all versions)', '38,500 (121,000)', 'Consider cleaning up old versions before the upgrade.'],
                 ['Documents and Media', '96,400 files, 212 GB', 'File system store; copy it for the upgrade rehearsal.'],
                 ['Database size', '41 GB, largest table 9.8 GB', 'Within normal range.'],
                 ['Languages', '3 (en_US, de_DE, ja_JP)', '']],
                [55, 45, 70])])]

    # ---- 5. customizations ------------------------------------------------------------------------------------------------
    s += [KeepTogether([p('5. Customizations', H2), table([['Type', 'Count', 'Upgrade impact'],
                 ['Custom OSGi modules', '47', '14 import Java EE (javax.*) packages: migrate to Jakarta EE.'],
                 ['Themes', '2', '1 theme uses Soy templates (removed): rebuild the templates.'],
                 ['Custom fragments / page templates', '58 / 12', 'Review after the upgrade; usually low effort.'],
                 ['Hooks / EXT plugins', '3 / 1', 'The EXT plugin must be replaced by modules or client extensions.'],
                 ['Workflow definitions with scripts', '4', 'Retest; scripts run only if Script Management allows it.'],
                 ['Client extensions', '0', 'Consider client extensions for new customizations.']],
                [55, 20, 95])])]

    # ---- 6. deprecated features ------------------------------------------------------------------------------------------------
    s += [KeepTogether([p('6. Deprecated features in use', H2), table([['Feature', 'Usage', 'Status', 'Replacement'],
                 ['Message Boards widget', '9 pages', 'Deprecated', 'Questions / custom solution'],
                 ['Wiki', '2 sites, 1,140 pages', 'Deprecated', 'Knowledge Base or web content'],
                 ['Dynamic Data Lists', '6 lists', 'Deprecated', 'Objects'],
                 ['Kaleo Forms', '2 processes', 'Deprecated', 'Objects with workflow'],
                 ['Velocity templates', '11', 'Removed', 'FreeMarker'],
                 ['Akismet', 'Configured', 'Deprecated', 'No direct replacement']],
                [45, 35, 25, 65])])]

    # ---- 7. recommendations ------------------------------------------------------------------------------------------------
    s += [p('7. Recommendations and next steps', H2),
          p('1. Upgrade the infrastructure with the Liferay upgrade: JDK 21, Tomcat 10.1, Elasticsearch 8.19, MySQL 8.4.'),
          p('2. Migrate the 14 custom modules from Java EE to Jakarta EE and replace the EXT plugin.'),
          p('3. Decide on replacements for Wiki, Message Boards, Dynamic Data Lists and Kaleo Forms before the upgrade.'),
          p('4. Clean up old web content versions to shorten the database upgrade.'),
          Spacer(1, 4 * mm),
          box('<b>Your choice.</b> This health check is free and you can stop here, with no obligation. If you want to continue, '
              'Liferay validates whether you qualify for the Liferay upgrade program (step 4) and, if you do, shares the '
              'contractual terms for sign-off.'),
          Spacer(1, 3 * mm),
          box('<b>Disclaimer.</b> ' + DISCLAIMER, colors.HexColor('#fff4de'))]

    doc.build(s, onFirstPage=decorate, onLaterPages=decorate)
    print('wrote', OUT)


if __name__ == '__main__':
    build()
