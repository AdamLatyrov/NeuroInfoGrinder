const fs = require('fs');
const path = require('path');

const dir = __dirname;
const files = ['materials.valid.json', 'run-summary.valid.json'];

for (const file of files) {
  const filePath = path.join(dir, file);
  let text = fs.readFileSync(filePath, 'utf8').trim();

  try {
    JSON.parse(text);
  } catch (_error) {
    throw new Error(`${file} is not valid JSON: ${_error.message}`);
  }
}

const materials = JSON.parse(fs.readFileSync(path.join(dir, 'materials.valid.json'), 'utf8'));
const summary = JSON.parse(fs.readFileSync(path.join(dir, 'run-summary.valid.json'), 'utf8'));

console.log(JSON.stringify({
  materialsArray: Array.isArray(materials),
  materialCount: materials.length,
  firstMaterialId: materials[0] ? materials[0].id : null,
  lastMaterialId: materials[materials.length - 1] ? materials[materials.length - 1].id : null,
  summaryMaterialCount: summary.materialCount,
  summaryMessages: summary.messageCount,
  summaryRuns: summary.runCount
}, null, 2));
