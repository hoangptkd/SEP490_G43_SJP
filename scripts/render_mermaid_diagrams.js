const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

async function main() {
  const input = process.argv[2];
  const outputDir = process.argv[3];
  if (!input || !outputDir) {
    throw new Error('Usage: node render_mermaid_diagrams.js diagrams.json outputDir');
  }
  fs.mkdirSync(outputDir, { recursive: true });
  const diagrams = JSON.parse(fs.readFileSync(input, 'utf8'));
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1800, height: 1400 }, deviceScaleFactor: 2 });
  page.setDefaultTimeout(60000);
  await page.setContent(`<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    body { margin: 0; padding: 32px; background: white; font-family: Arial, sans-serif; }
    #diagram { display: inline-block; background: white; }
    svg { max-width: none !important; height: auto !important; }
  </style>
</head>
<body><div id="diagram"></div></body>
</html>`);
  await page.addScriptTag({ url: 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js' });
  await page.evaluate(() => {
    window.mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'loose',
      theme: 'default',
      flowchart: { useMaxWidth: false },
      class: { useMaxWidth: false }
    });
  });
  for (const item of diagrams) {
    const svg = await page.evaluate(async ({ slug, code }) => {
      const host = document.getElementById('diagram');
      host.innerHTML = '';
      const result = await window.mermaid.render(`mermaid_${slug.replace(/[^a-zA-Z0-9_]/g, '_')}`, code);
      host.innerHTML = result.svg;
      return result.svg;
    }, item);
    fs.writeFileSync(path.join(outputDir, `${item.slug}.svg`), svg, 'utf8');
    const box = await page.locator('#diagram svg').boundingBox();
    const width = Math.max(900, Math.ceil(box.width + 80));
    const height = Math.max(700, Math.ceil(box.height + 80));
    await page.setViewportSize({ width: Math.min(width, 2400), height: Math.min(height, 2400) });
    await page.locator('#diagram').screenshot({ path: path.join(outputDir, `${item.slug}.png`) });
  }
  await browser.close();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
