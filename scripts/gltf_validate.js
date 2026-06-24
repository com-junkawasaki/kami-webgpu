#!/usr/bin/env node
// glTF gate validator. If the Khronos `gltf-validator` npm package resolves, run the real validator
// and fail on any error-severity issue. Otherwise fall back to a structural check: valid JSON with a
// mandatory asset.version. Usage: node gltf_validate.js <file.gltf>
const fs = require('fs');
const file = process.argv[2];
const bytes = fs.readFileSync(file);

function structural() {
  const doc = JSON.parse(bytes.toString('utf8'));   // throws on malformed JSON
  if (!doc.asset || doc.asset.version !== '2.0') throw new Error('missing asset.version "2.0"');
  console.log('structural ok (asset.version 2.0)');
}

let validator;
try { validator = require('gltf-validator'); }            // local / NODE_PATH
catch (_) {
  try {                                                    // fall back to the global npm install
    const root = require('child_process').execSync('npm root -g').toString().trim();
    validator = require(root + '/gltf-validator');
  } catch (_2) { validator = null; }
}

if (validator) {
  validator.validateBytes(new Uint8Array(bytes))
    .then((report) => {
      const errs = report.issues.numErrors;
      if (errs > 0) {
        console.error(JSON.stringify(report.issues.messages.filter((m) => m.severity === 0)));
        process.exit(1);
      }
      console.log(`gltf-validator ok (${report.issues.numWarnings} warnings)`);
    })
    .catch((e) => { console.error(String(e)); process.exit(1); });
} else {
  try { structural(); } catch (e) { console.error(String(e)); process.exit(1); }
}
