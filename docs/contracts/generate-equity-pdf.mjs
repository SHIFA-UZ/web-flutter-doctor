import puppeteer from 'puppeteer';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const htmlFile = path.join(__dirname, 'equity_partner_shohruhmirzo_sharobov.html');
const outDir = path.join(__dirname, 'output');
const pdfFile = path.join(outDir, 'SHIFA-EQ-0001_Shohruhmirzo_Sharobov.pdf');

if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });

const htmlUrl = 'file:///' + htmlFile.replace(/\\/g, '/');

const browser = await puppeteer.launch({
  headless: true,
  args: ['--no-sandbox', '--disable-setuid-sandbox'],
});
const page = await browser.newPage();
await page.goto(htmlUrl, { waitUntil: 'networkidle0', timeout: 60000 });
await page.emulateMediaType('print');
await page.pdf({
  path: pdfFile,
  format: 'A4',
  printBackground: true,
  preferCSSPageSize: true,
  margin: { top: '18mm', right: '16mm', bottom: '20mm', left: '16mm' },
});
await browser.close();

console.log('PDF saved:', pdfFile);
