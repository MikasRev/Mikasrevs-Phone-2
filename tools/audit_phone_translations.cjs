const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const java = path.join(root, 'src/main/java/com/mikasrevs/newphone');
function files(dir) {
  return fs.readdirSync(dir, {withFileTypes: true}).flatMap(e => e.isDirectory() ? files(path.join(dir,e.name)) : [path.join(dir,e.name)]);
}
const literal = '"(?:\\\\.|[^"\\\\])*"';
const translationTable = p => /^Phone(Spanish|Portuguese|German|French|Russian|Polish|Italian)Translations\.java$/.test(path.basename(p));
function decode(value) { return JSON.parse(value.replace(/\\'/g, "'")); }
const pairs = new Map();
for (const file of files(java).filter(p=>p.endsWith('.java')&&!translationTable(p))) {
  const source = fs.readFileSync(file,'utf8');
  if (file.includes(path.sep+'tablet'+path.sep)) continue;
  const calls = new RegExp('(?:PhoneSettingsStore\\.text|\\btr|(?<![\\w.])text)\\(\\s*('+literal+'|raw)\\s*,\\s*('+literal+')', 'g');
  for (const match of source.matchAll(calls)) {
    const en=decode(match[2]),tr=match[1]==='raw'?en:decode(match[1]);
    if (!pairs.has(en)) pairs.set(en,{tr,files:[]});
    pairs.get(en).files.push(path.relative(java,file));
  }
}
const report={texts:pairs.size,languages:{},resources:{}};
report.rawUi=[];
report.serverNotices=[];
for (const file of files(java).filter(p=>p.endsWith('.java')&&!translationTable(p))) {
 const source=fs.readFileSync(file,'utf8');
 for(const [index,line] of source.split(/\r?\n/).entries()){
  if(file.includes(path.sep+'client'+path.sep)&&!file.includes(path.sep+'settings'+path.sep)){
   const direct=new RegExp('(?:draw\\w*\\([^;]*?|Component\\.literal\\(|notice\\()('+literal+')','g');
   for(const m of line.matchAll(direct)){const value=decode(m[1]);if(/[\p{L}]{3}/u.test(value)&&!line.includes('PhoneSettingsStore.text')&&!line.includes('TabletSettingsStore.text'))report.rawUi.push({file:path.relative(java,file),line:index+1,value});}
  }
  if(file.includes(path.sep+'server'+path.sep)){
   const direct=new RegExp('(?:sendState|sendStateIfOnline|IllegalArgumentException)\\([^;]*?('+literal+')','g');
   for(const m of line.matchAll(direct)){const value=decode(m[1]);if(/[çğıöşüÇĞİÖŞÜ]/u.test(value))report.serverNotices.push({file:path.relative(java,file),line:index+1,value});}
  }
 }
}
for (const file of files(path.join(java,'client/settings')).filter(translationTable)) {
  const source=fs.readFileSync(file,'utf8').split('static String network')[0];
  const cases=new Set([...source.matchAll(new RegExp('case\\s+('+literal+')','g'))].map(m=>decode(m[1])));
  const missing=Object.fromEntries([...pairs].filter(([en])=>!cases.has(en)));
  report.languages[path.basename(file)]={cases:cases.size,missing};
}
const catalogs=path.join(root,'src/main/resources/assets/newphone/phone_translations');
const structured=new Set();
const settingsSource=fs.readFileSync(path.join(java,'client/zphone/gui/ZPhoneSettingsScreen.java'),'utf8');
for(const row of settingsSource.matchAll(new RegExp('new Row\\(Icon\\.\\w+,\\s*('+literal+'),\\s*('+literal+'),\\s*('+literal+'),\\s*('+literal+')','g'))){structured.add(decode(row[1]));structured.add(decode(row[3]));}
const modelSource=fs.readFileSync(path.join(java,'phone/model/PhoneModelRegistry.java'),'utf8');
for(const row of modelSource.matchAll(new RegExp('(?:shell\\(|new PhoneModelDefinition\\(\\s*)('+literal+'),\\s*('+literal+'),\\s*('+literal+')','g'))){structured.add(decode(row[2]));structured.add(decode(row[3]));}
report.structuredTexts=structured.size;
for(const code of ['zh_cn','zh_tw']) {
  const data=JSON.parse(fs.readFileSync(path.join(catalogs,code+'.json'),'utf8'));
  const missing=Object.fromEntries([...pairs].filter(([en])=>!(en in data)));
  for(const en of structured)if(!(en in data))missing[en]={files:['settings rows / model labels']};
  const invalid=Object.entries(data).filter(([en,value])=>
    (en.trim()&&!value.trim())||JSON.stringify(en.match(/%(?:\d+\$)?[sd]/g)||[])!==JSON.stringify(value.match(/%(?:\d+\$)?[sd]/g)||[]));
  if(invalid.length)throw new Error(code+' has blank translations or changed placeholders: '+JSON.stringify(invalid));
  report.languages[code+'.json']={cases:Object.keys(data).length,missing};
}
const lang=path.join(root,'src/main/resources/assets/newphone/lang');
const english=JSON.parse(fs.readFileSync(path.join(lang,'en_us.json'),'utf8'));
for(const file of files(lang).filter(p=>p.endsWith('.json'))) {
  const data=JSON.parse(fs.readFileSync(file,'utf8'));
  report.resources[path.basename(file)]={keys:Object.keys(data).length,missing:Object.keys(english).filter(k=>!(k in data))};
}
fs.mkdirSync(path.join(root,'build'),{recursive:true});
fs.writeFileSync(path.join(root,'build/phone-translation-audit.json'),JSON.stringify(report,null,2));
console.log('Static phone texts:',report.texts);
for(const [name,data] of Object.entries(report.languages)) console.log(name, 'missing:',Object.keys(data.missing).length);
for(const [name,data] of Object.entries(report.resources)) console.log(name,'missing resource keys:',data.missing.length);
if(process.argv.includes('--details')) {
  console.log(JSON.stringify(Object.fromEntries(Object.entries(report.languages['PhoneSpanishTranslations.java'].missing).map(([en,v])=>[en,v.tr])),null,2));
  console.log('Resource keys:',JSON.stringify(report.resources['es_es.json'].missing));
  console.log('Raw UI candidates:',JSON.stringify(report.rawUi));
  console.log('Server notice candidates:',JSON.stringify(report.serverNotices));
}
if(process.argv.includes('--check') && (Object.values(report.languages).some(x=>Object.keys(x.missing).length)||Object.values(report.resources).some(x=>x.missing.length))) process.exitCode=1;
