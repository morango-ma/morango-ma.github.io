// builds legacy.html: the same app, rewritten for old browsers (Android 8 screens, Chrome < 80)
import fs from 'fs'; import { transformSync } from 'esbuild';
const T = { target: 'chrome58', loader: 'js', charset: 'utf8' };
let s = fs.readFileSync('index.html', 'utf8');
s = s.replace(/<script>([\s\S]*?)<\/script>/g, (m, code) => {
  if (code.includes('__LEGACY_GATE__')) return '';
  return '<script>' + transformSync(code, T).code.replace(/<\/script/gi, '<\\/script') + '</script>';
});
// CSS that old browsers don't know: inset → top/right/bottom/left
s = s.replace(/inset:0(?![\d.%])/g, 'top:0;right:0;bottom:0;left:0').replace(/inset:auto -30% -60% auto/g, 'top:auto;right:-30%;bottom:-60%;left:auto').replace(/inset:auto 0 -3px 0/g, 'top:auto;right:0;bottom:-3px;left:0');
s = s.replace(/<script src="https:\/\/cdn\.jsdelivr\.net\/npm\/@supabase\/supabase-js@[^"]+"><\/script>/, '<script src="supabase-legacy.js"></script>');
const poly = `<script>(function(){if(!Object.fromEntries)Object.fromEntries=function(it){var o={};Array.from(it).forEach(function(p){o[p[0]]=p[1]});return o};
if(!Array.prototype.flatMap)Object.defineProperty(Array.prototype,'flatMap',{value:function(f,t){return [].concat.apply([],this.map(f,t))},configurable:true,writable:true});
if(!Array.prototype.flat)Object.defineProperty(Array.prototype,'flat',{value:function(d){d=d===undefined?1:d;return d<1?this.slice():this.reduce(function(a,v){return a.concat(Array.isArray(v)?v.flat(d-1):v)},[])},configurable:true,writable:true});
if(!String.prototype.padStart)String.prototype.padStart=function(n,c){var s=String(this);c=c==null?' ':String(c);while(s.length<n)s=c+s;return s.slice(-n)};
if(typeof globalThis==='undefined')window.globalThis=window;window.__LEGACY=1})();</script>`;
s = s.replace(/<head>/i, '<head>' + poly);
fs.writeFileSync('legacy.html', s);
const sb = fs.readFileSync('node_modules/@supabase/supabase-js/dist/umd/supabase.js', 'utf8');
fs.writeFileSync('supabase-legacy.js', transformSync(sb, T).code);
console.log('legacy ok', s.length);
