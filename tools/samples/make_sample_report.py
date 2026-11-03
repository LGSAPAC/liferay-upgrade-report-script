"""Generates samples/sample-health-check-report.pdf: an example of the Liferay DXP architecture health check that
customers receive after the assessment (step 3), in the Liferay visual style (Liferay blue, Source Sans, Clay-like
tables). ALL DATA IS FICTITIOUS.

usage: python3 tools/samples/make_sample_report.py
(needs reportlab and svglib; fonts and logo are in tools/samples/)
"""
import io
import os

from reportlab.graphics import renderPDF
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (Flowable, KeepTogether, NextPageTemplate, PageBreak, PageTemplate, Paragraph,
                                BaseDocTemplate, Frame, Spacer, Table, TableStyle)
from svglib.svglib import svg2rlg

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, 'samples', 'sample-health-check-report.pdf')

# ---- Liferay brand (as used on liferay.com) -------------------------------------------------------------------------
BLUE = colors.HexColor('#0B5FFF')       # Liferay blue
INDIGO = colors.HexColor('#1514A4')
NAVY = colors.HexColor('#00318F')
INK = colors.HexColor('#262C37')
GRAY = colors.HexColor('#54555F')
LIGHT = colors.HexColor('#F0F1F5')
TINT = colors.HexColor('#EDF3FE')
SKY = colors.HexColor('#70A1FF')
CYAN = colors.HexColor('#47FFFC')
LINE = colors.HexColor('#E7E7ED')
RAG = {'Green': ('#287D3C', '#EEF9F0'), 'Amber': ('#B95000', '#FFF4EC'), 'Red': ('#DA1414', '#FEEFEF'),
       'Info': ('#0B5FFF', '#EDF3FE')}

FONTS = os.path.join(HERE, 'fonts')


def fetch_fonts():
    """Downloads Source Sans 3 (SIL Open Font License) from Google Fonts into tools/samples/fonts (not committed)."""
    import re
    import urllib.request
    wanted = {'400': 'Regular', '600': 'SemiBold', '700': 'Bold'}
    if all(os.path.exists(os.path.join(FONTS, f'SourceSans3-{w}.ttf')) for w in wanted.values()):
        return
    os.makedirs(FONTS, exist_ok=True)
    request = urllib.request.Request('https://fonts.googleapis.com/css?family=Source+Sans+3:400,600,700', headers={'User-Agent': 'Mozilla/4.0'})
    css = urllib.request.urlopen(request).read().decode()
    for weight, url in re.findall(r'font-weight: (\d+);.*?url\((https://[^)]+\.ttf)\)', css, re.S):
        urllib.request.urlretrieve(url, os.path.join(FONTS, f'SourceSans3-{wanted[weight]}.ttf'))


fetch_fonts()
for name, file in (('SS', 'Regular'), ('SS-Semi', 'SemiBold'), ('SS-Bold', 'Bold')):
    pdfmetrics.registerFont(TTFont(name, os.path.join(FONTS, f'SourceSans3-{file}.ttf')))
pdfmetrics.registerFontFamily('SS', normal='SS', bold='SS-Bold', italic='SS', boldItalic='SS-Bold')

DISCLAIMER = ('This is a fictitious example. The company, people, data and findings are invented for illustration. '
              'The Liferay DXP architecture health check is provided on a best-effort basis and for reference only. It is '
              'based on the output of the read-only assessment script and is not an audit, a guarantee or a warranty. '
              'Results and recommendations depend on your environment and may change after further review.')

BODY = ParagraphStyle('Body', fontName='SS', fontSize=10, leading=14.5, textColor=INK)
SMALL = ParagraphStyle('Small', parent=BODY, fontSize=8.5, leading=12, textColor=GRAY)
CELL = ParagraphStyle('Cell', parent=BODY, fontSize=9, leading=12)
HEAD = ParagraphStyle('Head', parent=CELL, fontName='SS-Semi', textColor=GRAY, fontSize=8, leading=10)
EYEBROW = ParagraphStyle('Eyebrow', parent=BODY, fontName='SS-Bold', fontSize=8.5, leading=10, textColor=BLUE)
H1 = ParagraphStyle('H1', parent=BODY, fontName='SS-Bold', fontSize=20, leading=24, spaceAfter=6)
H2 = ParagraphStyle('H2', parent=BODY, fontName='SS-Bold', fontSize=13.5, leading=17, spaceBefore=10, spaceAfter=6)


def logo(text_color):
    svg = open(os.path.join(HERE, 'liferay-logo.svg')).read().replace('currentColor', text_color)
    if text_color == '#FFFFFF':
        svg = svg.replace('#0B63CE', '#FFFFFF')
    return svg2rlg(io.StringIO(svg))


LOGO_DARK, LOGO_WHITE = logo('#262C37'), logo('#FFFFFF')


def draw_logo(canvas, drawing, x, y, height):
    scale = height / drawing.height
    canvas.saveState()
    canvas.translate(x, y)
    canvas.scale(scale, scale)
    renderPDF.draw(drawing, canvas, 0, 0)
    canvas.restoreState()


def p(text, style=BODY):
    return Paragraph(text, style)


class Pill(Flowable):
    """Rounded status label, like a Clay label."""

    def __init__(self, text, kind):
        super().__init__()
        self.text, (self.fg, self.bg) = text, RAG[kind]
        self.width, self.height = pdfmetrics.stringWidth(text, 'SS-Bold', 8) + 12, 13

    def draw(self):
        c = self.canv
        c.setFillColor(colors.HexColor(self.bg))
        c.setStrokeColor(colors.HexColor(self.fg))
        c.setLineWidth(0.6)
        c.roundRect(0, 0, self.width, self.height, 6.5, stroke=1, fill=1)
        c.setFillColor(colors.HexColor(self.fg))
        c.setFont('SS-Bold', 8)
        c.drawCentredString(self.width / 2, 3.6, self.text)


def rag(kind, text=None):
    return Pill((text or kind).upper(), kind)


def table(rows, widths, header=True, zebra=False):
    data = []
    for i, row in enumerate(rows):
        data.append([c if not isinstance(c, str) else Paragraph(c.upper() if (header and i == 0) else c,
                                                                  HEAD if (header and i == 0) else CELL) for c in row])
    t = Table(data, colWidths=[w * mm for w in widths], repeatRows=1 if header else 0)
    style = [('LINEBELOW', (0, 0), (-1, -1), 0.6, LINE), ('VALIGN', (0, 0), (-1, -1), 'MIDDLE'),
             ('TOPPADDING', (0, 0), (-1, -1), 5), ('BOTTOMPADDING', (0, 0), (-1, -1), 5),
             ('LEFTPADDING', (0, 0), (-1, -1), 7), ('RIGHTPADDING', (0, 0), (-1, -1), 7)]
    if header:
        style += [('BACKGROUND', (0, 0), (-1, 0), LIGHT), ('LINEBELOW', (0, 0), (-1, 0), 0.8, LINE)]
    t.setStyle(TableStyle(style))
    return t


def callout(text, kind='Info'):
    fg, bg = RAG[kind]
    t = Table([[Paragraph(text, ParagraphStyle('c', parent=SMALL, textColor=INK, fontSize=9, leading=13))]], colWidths=[170 * mm])
    t.setStyle(TableStyle([('BACKGROUND', (0, 0), (-1, -1), colors.HexColor(bg)),
                           ('LINEBEFORE', (0, 0), (0, -1), 3, colors.HexColor(fg)),
                           ('LEFTPADDING', (0, 0), (-1, -1), 10), ('RIGHTPADDING', (0, 0), (-1, -1), 10),
                           ('TOPPADDING', (0, 0), (-1, -1), 8), ('BOTTOMPADDING', (0, 0), (-1, -1), 8)]))
    return t


def kpis(items):
    """Row of KPI tiles: (value, label, kind)."""
    cells = []
    for value, label, kind in items:
        fg = colors.HexColor(RAG[kind][0])
        cells.append([Paragraph(value, ParagraphStyle('v', parent=BODY, fontName='SS-Bold', fontSize=19, leading=22, textColor=fg)),
                      Paragraph(label, ParagraphStyle('l', parent=SMALL, fontSize=8, leading=10.5))])
    t = Table([cells], colWidths=[170 * mm / len(items)] * len(items), hAlign='LEFT')
    style = [('BACKGROUND', (0, 0), (-1, -1), LIGHT), ('VALIGN', (0, 0), (-1, -1), 'TOP'),
             ('TOPPADDING', (0, 0), (-1, -1), 9), ('BOTTOMPADDING', (0, 0), (-1, -1), 9),
             ('LEFTPADDING', (0, 0), (-1, -1), 10)]
    for i, (_, _, kind) in enumerate(items):
        style.append(('LINEABOVE', (i, 0), (i, 0), 3, colors.HexColor(RAG[kind][0])))
    t.setStyle(TableStyle(style))
    return t


def section(number, title, *flowables):
    return KeepTogether([p(f'<font color="#0B5FFF">{number:02d}</font>&nbsp;&nbsp;{title}', H2)] + list(flowables))


# ---- page decoration --------------------------------------------------------------------------------------------------------
def watermark(canvas):
    w, h = A4
    canvas.saveState()
    canvas.setFillColor(colors.Color(0.04, 0.37, 1, alpha=0.06))
    canvas.setFont('SS-Bold', 52)
    canvas.translate(w / 2, h / 2)
    canvas.rotate(35)
    canvas.drawCentredString(0, 0, 'SAMPLE - FICTITIOUS DATA')
    canvas.restoreState()


def cover(canvas, doc):
    w, h = A4
    canvas.saveState()
    canvas.setFillColor(BLUE)
    canvas.rect(0, 0, w, h, stroke=0, fill=1)
    # brand shapes
    canvas.setFillColor(INDIGO)
    canvas.circle(w + 20 * mm, h - 40 * mm, 95 * mm, stroke=0, fill=1)
    canvas.setFillColor(NAVY)
    canvas.circle(w - 5 * mm, -30 * mm, 80 * mm, stroke=0, fill=1)
    canvas.setStrokeColor(CYAN)
    canvas.setLineWidth(3)
    canvas.line(20 * mm, 168 * mm, 60 * mm, 168 * mm)
    draw_logo(canvas, LOGO_WHITE, 20 * mm, h - 32 * mm, 12 * mm)
    canvas.setFillColor(CYAN)
    canvas.setFont('SS-Bold', 10)
    canvas.drawString(20 * mm, 225 * mm, 'SAMPLE REPORT  ·  FICTITIOUS DATA')
    canvas.setFillColor(colors.white)
    canvas.setFont('SS-Bold', 34)
    canvas.drawString(20 * mm, 205 * mm, 'Liferay DXP')
    canvas.drawString(20 * mm, 191 * mm, 'Architecture Health Check')
    canvas.setFont('SS', 14)
    canvas.setFillColor(colors.HexColor('#D6E4FF'))
    canvas.drawString(20 * mm, 177 * mm, 'Liferay Upgrade Program  ·  Assessment phase, step 3')
    canvas.restoreState()


def inner(canvas, doc):
    w, h = A4
    watermark(canvas)
    canvas.saveState()
    draw_logo(canvas, LOGO_DARK, 20 * mm, h - 17 * mm, 7 * mm)
    canvas.setFont('SS-Semi', 8.5)
    canvas.setFillColor(GRAY)
    canvas.drawRightString(w - 20 * mm, h - 13.5 * mm, 'Architecture Health Check  ·  Example Corp (fictitious)')
    canvas.setStrokeColor(BLUE)
    canvas.setLineWidth(1.2)
    canvas.line(20 * mm, h - 20 * mm, w - 20 * mm, h - 20 * mm)
    canvas.setStrokeColor(LINE)
    canvas.setLineWidth(0.6)
    canvas.line(20 * mm, 16 * mm, w - 20 * mm, 16 * mm)
    canvas.setFont('SS', 7.5)
    canvas.drawString(20 * mm, 11 * mm, 'Sample with fictitious data  ·  Best effort, for reference only  ·  Not an audit, guarantee or warranty')
    canvas.setFont('SS-Bold', 8)
    canvas.setFillColor(BLUE)
    canvas.drawRightString(w - 20 * mm, 11 * mm, f'{doc.page}')
    canvas.restoreState()


def build():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    doc = BaseDocTemplate(OUT, pagesize=A4, title='Sample Liferay DXP Architecture Health Check (fictitious data)',
                          author='Liferay Upgrade Program', subject='Example report - best effort, for reference only')
    w, h = A4
    doc.addPageTemplates([
        PageTemplate('cover', [Frame(20 * mm, 18 * mm, w - 40 * mm, 140 * mm, id='c')], onPage=cover),
        PageTemplate('inner', [Frame(20 * mm, 20 * mm, w - 40 * mm, h - 46 * mm, id='i')], onPage=inner)])
    s = []

    # ---- cover: details card + disclaimer -----------------------------------------------------------------------------
    card = Table([[Paragraph(k.upper(), HEAD), Paragraph(v, ParagraphStyle('cv', parent=CELL, fontName='SS-Semi'))] for k, v in [
        ('Customer', 'Example Corp (fictitious)'),
        ('Environment assessed', 'UAT, copy of production from 2026-10-15'),
        ('Current release', 'Liferay DXP 7.3 (Update 35)'),
        ('Recommended target', 'Liferay DXP 2026.Q1 LTS'),
        ('Input', 'Output of the read-only assessment script dxp_7_3-all.groovy'),
        ('Report date', '2026-11-03')]], colWidths=[48 * mm, 117 * mm])
    card.setStyle(TableStyle([('BACKGROUND', (0, 0), (-1, -1), colors.white), ('LINEBELOW', (0, 0), (-1, -2), 0.6, LINE),
                              ('VALIGN', (0, 0), (-1, -1), 'MIDDLE'), ('TOPPADDING', (0, 0), (-1, -1), 7),
                              ('BOTTOMPADDING', (0, 0), (-1, -1), 7), ('LEFTPADDING', (0, 0), (-1, -1), 12)]))
    s += [card, Spacer(1, 8 * mm),
          p('<b>Disclaimer.</b> ' + DISCLAIMER, ParagraphStyle('cd', parent=SMALL, textColor=colors.HexColor('#D6E4FF'), fontSize=8.5)),
          NextPageTemplate('inner'), PageBreak()]

    # ---- 01 summary -----------------------------------------------------------------------------------------------------------
    s += [p('EXECUTIVE SUMMARY', EYEBROW), p('Ready for the upgrade program', H1),
          p('Example Corp runs Liferay DXP 7.3, which left Premium Support on 2024-10-12 and is in Limited Support until '
            '2027-10-12. The platform is stable and well within sizing limits, but the application server, JDK, search engine '
            'and part of the custom code must change for an upgrade to 2026.Q1 LTS. None of the findings blocks the upgrade.'),
          Spacer(1, 5 * mm),
          kpis([('Amber', 'Overall rating', 'Amber'), ('3', 'Components not certified for the target', 'Red'),
                ('14', 'Custom modules to migrate to Jakarta EE', 'Amber'), ('212 GB', 'Documents to migrate (standard size)', 'Green')]),
          Spacer(1, 5 * mm),
          table([['Area', 'Rating', 'Summary'],
                 ['Support lifecycle', rag('Amber'), 'Limited Support: no new fixes. Plan the upgrade before 2027-10-12.'],
                 ['Infrastructure', rag('Red'), 'Tomcat 9, JDK 11 and Elasticsearch 7.17 are not certified for 2026.Q1 LTS.'],
                 ['Database', rag('Amber'), 'MySQL 5.7 is deprecated; move to MySQL 8.0 or 8.4 with the upgrade.'],
                 ['Data volumes', rag('Green'), '14,200 users, 38,500 web content articles, 212 GB documents: standard upgrade.'],
                 ['Customizations', rag('Amber'), '47 custom modules, 14 use Java EE (javax.*) and need Jakarta EE.'],
                 ['Deprecated features', rag('Amber'), '6 deprecated features in use, 2 with no direct replacement.']],
                [38, 24, 108]),
          Spacer(1, 4 * mm),
          callout('<b>Overall:</b> ready for the upgrade program, with infrastructure changes and a code migration (Java EE to '
                  'Jakarta EE) as the main work items.')]

    s.append(section(1, 'Support lifecycle', table(
        [['Release', 'First ship date', 'End of Premium Support', 'End of life', 'Status today'],
         ['Liferay DXP 7.3', '2020-10-13', '2024-10-12', '2027-10-12', rag('Amber', 'Limited Support')],
         ['Liferay DXP 2026.Q1 LTS (target)', '2026-03-18', '2029-03-17', '2031-03-17', rag('Green', 'Premium Support')]],
        [46, 27, 34, 25, 38])))

    s.append(section(2, 'Compatibility with the target release (2026.Q1 LTS)', table(
        [['Component', 'Current', 'Status', 'Recommended action'],
         ['JDK', 'OpenJDK 11.0.22', rag('Red', 'Not certified'), 'Move to JDK 21 (JDK 17 is deprecated).'],
         ['Application server', 'Apache Tomcat 9.0.85', rag('Red', 'Not certified'), 'Move to Tomcat 10.1 (Jakarta EE 10).'],
         ['Database', 'MySQL 5.7.44', rag('Amber', 'Deprecated'), 'Upgrade to MySQL 8.0 or 8.4.'],
         ['Operating system', 'RHEL 7.9', rag('Amber', 'Deprecated'), 'Plan RHEL 8, 9 or 10.'],
         ['Search engine', 'Elasticsearch 7.17.18', rag('Red', 'Not certified'), 'Elasticsearch 7 reached end of life; upgrade to 8.19.'],
         ['Rich text editor', 'CKEditor 4', rag('Amber', 'Deprecated'), 'Review custom CKEditor plugins for CKEditor 5.'],
         ['Hosting', 'Cloud VM (AWS)', rag('Green', 'Certified'), 'EC2 and RDS are certified.']],
        [33, 37, 30, 70])))

    s.append(section(3, 'Data volumes and sizing', table(
        [['Item', 'Count / size', 'Comment'],
         ['Virtual instances / sites', '2 / 64', ''],
         ['Users / organizations', '14,200 / 310', ''],
         ['Web content articles (all versions)', '38,500 (121,000)', 'Consider cleaning up old versions first.'],
         ['Documents and Media', '96,400 files, 212 GB', 'File system store; copy it for the rehearsal.'],
         ['Database size', '41 GB (largest table 9.8 GB)', 'Within normal range.'],
         ['Languages', '3 (en_US, de_DE, ja_JP)', '']],
        [55, 47, 68])))

    s.append(section(4, 'Customizations', table(
        [['Type', 'Count', 'Upgrade impact'],
         ['Custom OSGi modules', '47', '14 import Java EE (javax.*) packages: migrate to Jakarta EE.'],
         ['Themes', '2', '1 theme uses Soy templates (removed): rebuild the templates.'],
         ['Fragments / page templates', '58 / 12', 'Review after the upgrade; usually low effort.'],
         ['Hooks / EXT plugins', '3 / 1', 'Replace the EXT plugin with modules or client extensions.'],
         ['Workflow definitions with scripts', '4', 'Retest; scripts run only if Script Management allows it.'],
         ['Client extensions', '0', 'Consider client extensions for new customizations.']],
        [55, 18, 97])))

    s.append(section(5, 'Deprecated features in use', table(
        [['Feature', 'Usage', 'Status', 'Replacement'],
         ['Message Boards widget', '9 pages', rag('Amber', 'Deprecated'), 'Questions / custom solution'],
         ['Wiki', '2 sites, 1,140 pages', rag('Amber', 'Deprecated'), 'Knowledge Base or web content'],
         ['Dynamic Data Lists', '6 lists', rag('Amber', 'Deprecated'), 'Objects'],
         ['Kaleo Forms', '2 processes', rag('Amber', 'Deprecated'), 'Objects with workflow'],
         ['Velocity templates', '11', rag('Red', 'Removed'), 'FreeMarker'],
         ['Akismet', 'Configured', rag('Amber', 'Deprecated'), 'No direct replacement']],
        [45, 35, 30, 60])))

    s.append(section(6, 'Recommendations and next steps',
                     p('<font color="#0B5FFF"><b>1</b></font>&nbsp;&nbsp;Upgrade the infrastructure with the Liferay upgrade: JDK 21, Tomcat 10.1, Elasticsearch 8.19, MySQL 8.4.'),
                     p('<font color="#0B5FFF"><b>2</b></font>&nbsp;&nbsp;Migrate the 14 custom modules from Java EE to Jakarta EE and replace the EXT plugin.'),
                     p('<font color="#0B5FFF"><b>3</b></font>&nbsp;&nbsp;Decide on replacements for Wiki, Message Boards, Dynamic Data Lists and Kaleo Forms.'),
                     p('<font color="#0B5FFF"><b>4</b></font>&nbsp;&nbsp;Clean up old web content versions to shorten the database upgrade.'),
                     Spacer(1, 4 * mm),
                     callout('<b>Your choice.</b> This health check is free and you can stop here, with no obligation. If you want to '
                             'continue, Liferay validates whether you qualify for the Liferay upgrade program (step 4) and, if you do, '
                             'shares the contractual terms for sign-off.', 'Green'),
                     Spacer(1, 3 * mm),
                     callout('<b>Disclaimer.</b> ' + DISCLAIMER, 'Amber')))

    doc.build(s)
    print('wrote', OUT)


if __name__ == '__main__':
    build()
