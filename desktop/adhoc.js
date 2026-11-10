// ad-hoc sign the app so it runs on Apple Silicon without a developer certificate
const { execFileSync } = require('child_process');
const path = require('path');
exports.default = async function (ctx) {
  if (ctx.electronPlatformName !== 'darwin') return;
  if (ctx.arch !== 4) return; // only the merged universal app (Arch.universal); signing the per-arch builds breaks the merge
  const app = path.join(ctx.appOutDir, ctx.packager.appInfo.productFilename + '.app');
  try { execFileSync('codesign', ['--force', '--deep', '--sign', '-', app], { stdio: 'inherit' }); } catch (e) { console.log('codesign skipped:', e.message); }
};
