const fs = require('fs');
const path = require('path');

const versionJson = JSON.parse(fs.readFileSync('loganalysis_frontend/src/version.json', 'utf-8'));

const template = fs.readFileSync('README.tpl.md', 'utf-8');

const replacements = {
  '{{VERSION}}': versionJson.version,
  '{{RELEASE_DATE}}': versionJson.releaseDate
};

let content = template;
for (const [placeholder, value] of Object.entries(replacements)) {
  content = content.split(placeholder).join(value);
}

fs.writeFileSync('README.md', content);

console.log(`✅ README.md generated successfully!`);
console.log(`   Version: ${versionJson.version}`);
console.log(`   Release Date: ${versionJson.releaseDate}`);
