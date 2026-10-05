const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),base=path.join(root,'src/main/java/com/mikasrevs/newphone/client/settings');
fs.mkdirSync(path.join(root,'build'),{recursive:true});
const literal='"(?:\\\\.|[^"\\\\])*"',decode=s=>JSON.parse(s.replace(/\\'/g,"'"));
const ui=fs.readFileSync(path.join(base,'PhoneSpanishTranslations.java'),'utf8').split('static String network')[0];
const needed=new Set([...ui.matchAll(new RegExp('case\\s+('+literal+')','g'))].map(m=>decode(m[1])));
const langDir=path.join(root,'src/main/resources/assets/newphone/lang');
const english=JSON.parse(fs.readFileSync(path.join(langDir,'en_us.json'),'utf8'));
for(const v of Object.values(english))needed.add(v);
const settings=fs.readFileSync(path.join(base,'PhoneSettingsStore.java'),'utf8');
const screen=fs.readFileSync(path.join(root,'src/main/java/com/mikasrevs/newphone/client/zphone/gui/ZPhoneSettingsScreen.java'),'utf8');
for(const row of screen.matchAll(new RegExp('new Row\\(Icon\\.\\w+,\\s*('+literal+'),\\s*('+literal+'),\\s*('+literal+'),\\s*('+literal+')','g'))){needed.add(decode(row[1]));needed.add(decode(row[3]));}
const models=fs.readFileSync(path.join(root,'src/main/java/com/mikasrevs/newphone/phone/model/PhoneModelRegistry.java'),'utf8');
for(const row of models.matchAll(new RegExp('(?:shell\\(|new PhoneModelDefinition\\(\\s*)('+literal+'),\\s*('+literal+'),\\s*('+literal+')','g'))){needed.add(decode(row[2]));needed.add(decode(row[3]));}
const networkPart=settings.split('public static String localizeNetworkText')[1].split('public static boolean use24HourClock')[0];
const network=Object.fromEntries([...networkPart.matchAll(new RegExp('case\\s+('+literal+')\\s*->\\s*('+literal+')','g'))].map(m=>[decode(m[1]),decode(m[2])]));
for(const v of Object.values(network))needed.add(v);
for(const suffix of ['ACCENT_NAMES_EN','PHONE_SCALE_NAMES_EN','WALLPAPER_NAMES_EN']){
 const section=settings.split(suffix+' = {')[1].split('};')[0];for(const m of section.matchAll(new RegExp(literal,'g')))needed.add(decode(m[0]));
}
const norm=s=>s.trim().replace(/â€¦/g,'…').replace(/\.\.\./g,'…').replace(/\s+/g,' ').toLowerCase();
const records=new Map();
const tsv=path.join(root,'tools/phone-chinese-translations.tsv');
if(fs.existsSync(tsv))for(const line of fs.readFileSync(tsv,'utf8').split(/\r?\n/).filter(s=>s&&!s.startsWith('#'))){const row=line.split('\t').map(s=>s.replace(/\\n/g,'\n'));if(row.length!==3)throw new Error('Expected three columns: '+row[0]);records.set(row[0],[row[1],row[2]]);}
const brands=['','…','MPhone','iPhone Duo','Lumi','Minder','X','YouTube','MineStore','BlockPost','Block Snake','Creeper Rush','Gem Pairs','GPS','Bluetooth','Wi-Fi','MCEF','Lightman\'s Currency','OGG / WAV','OGG, WAV','UUID','PIN','%s','%s / %s'];
for(const brand of brands)if(!records.has(brand))records.set(brand,[brand,brand]);
const normalized=new Map([...records].map(([en,v])=>[norm(en),v]));
for(const en of needed){if(records.has(en))continue;let value=normalized.get(norm(en));
 if(!value){const core=en.replace(/^[^\p{L}\p{N}]+/u,'').replace(/[^\p{L}\p{N}]+$/u,'');const found=normalized.get(norm(core));if(found)value=found.map(v=>en.slice(0,en.indexOf(core))+v+en.slice(en.indexOf(core)+core.length));}
 if(value){const leading=en.match(/^\s*/)[0],trailing=en.match(/\s*$/)[0];records.set(en,value.map(v=>leading+v.trim()+trailing));}
}
const missing=[...needed].filter(en=>!records.has(en));
fs.writeFileSync(path.join(root,'build/chinese-translation-missing.json'),JSON.stringify(missing,null,2));
console.log('Total catalog entries:',needed.size,'Translated:',needed.size-missing.length,'Missing:',missing.length);
if(process.argv.includes('--missing')){console.log(JSON.stringify(missing,null,2));process.exit(0);}
if(missing.length)throw new Error('Chinese translations remain incomplete');
const catalogs=path.join(root,'src/main/resources/assets/newphone/phone_translations');fs.mkdirSync(catalogs,{recursive:true});
for(let i=0;i<2;i++){
 const code=i===0?'zh_cn':'zh_tw';
 fs.writeFileSync(path.join(catalogs,code+'.json'),JSON.stringify(Object.fromEntries([...records].sort(([a],[b])=>a.localeCompare(b)).map(([en,v])=>[en,v[i]])),null,2)+'\n');
 fs.writeFileSync(path.join(langDir,code+'.json'),JSON.stringify(Object.fromEntries(Object.entries(english).map(([k,en])=>[k,records.get(en)[i]])),null,2)+'\n');
}
