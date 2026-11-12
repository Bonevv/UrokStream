#!/usr/bin/env python3
"""Optional metadata refresh. Android builds and the app use the checked-in outline offline."""
import json,re,unicodedata,ssl,os,urllib.request,concurrent.futures,collections,hashlib,time
from pathlib import Path
from bs4 import BeautifulSoup
ROOT=Path(__file__).resolve().parents[1]; CACHE=ROOT/'build/site-cache'; CACHE.mkdir(parents=True,exist_ok=True); CA=ssl.create_default_context(cafile=os.environ.get('SSL_CERT_FILE','/etc/ssl/certs/ca-certificates.crt'))
def read_torrent(path):
 data=path.read_bytes(); pos=0
 def decode():
  nonlocal pos
  kind=data[pos:pos+1]
  if kind==b'i':
   pos+=1; end=data.index(b'e',pos); value=int(data[pos:end]); pos=end+1; return value
  if kind in (b'l',b'd'):
   pos+=1; values=[]
   while data[pos:pos+1]!=b'e':values.append(decode())
   pos+=1;return dict(zip(values[::2],values[1::2])) if kind==b'd' else values
  end=data.index(b':',pos); length=int(data[pos:end]); pos=end+1; value=data[pos:pos+length]; pos+=length;return value
 info=decode()[b'info']
 return [{'index':i,'path':'/'.join(p.decode('utf-8') for p in f[b'path'])} for i,f in enumerate(info[b'files'])]
files=read_torrent(ROOT/'app/src/main/assets/lessons.torrent')
def norm(s):return ''.join(c for c in unicodedata.normalize('NFKC',s).casefold() if c.isalnum())
def title(path):return re.sub(r'^\d+[-. ]+','',path.rsplit('/',1)[-1].rsplit('.',1)[0]).removeprefix('Видео урок ').strip()
def source_parts(path):
 p=path.split('/'); group='school'; name=p[0]; start=1
 if name in ['ИТ','Чужди езици','Личностно развитие','За българи в чужбина','Предучилищна подготовка']:
  group={'ИТ':'school','Чужди езици':'languages','Личностно развитие':'more','За българи в чужбина':'more','Предучилищна подготовка':'preschool'}[name]
  name=p[1]; start=2
 label=p[start] if len(p)>start+1 else 'Всички уроци'
 return group,name,label
courses=collections.defaultdict(list)
for f in files:
 if f['path'].rsplit('.',1)[-1].lower() in ['mkv','mp4','avi','m4v','webm','mov','ts','m2ts','mpg','mpeg']:
  courses[source_parts(f['path'])].append(f)
homepage=CACHE/'home.html'
if not homepage.exists():
 req=urllib.request.Request('https://ucha.se/',headers={'User-Agent':'Mozilla/5.0','Accept-Encoding':'identity'})
 with urllib.request.urlopen(req,context=CA,timeout=30) as response:homepage.write_bytes(response.read())
home=BeautifulSoup(homepage.read_bytes(),'html.parser'); targets={}
for ul in home.select('ul.categories-matrix'):
 for li in ul.find_all('li',recursive=False):
  a=li.select_one('a.to-category-page'); n=li.select_one('h4.name')
  if not a or not n:continue
  name=n.get_text(' ',strip=True); preschool=ul.get('id')!='categories-wrap'
  links=li.select('ul.grade-links a[href]') or [a]
  for link in links:
   label=link.get_text(' ',strip=True) if link!=a else 'Всички уроци'
   for key in courses:
    group,subject,course=key
    if (group=='preschool')!=preschool or norm(name)!=norm(subject):continue
    label0=course.removeprefix('Ниво ')
    if norm(label)==norm(label0) or label.isdigit() and course==label+'. клас':targets[key]=link['href']
def fetch(item):
 key,url=item; cache=CACHE/(hashlib.sha256(url.encode()).hexdigest()+'.html')
 try:
  if cache.exists(): data=cache.read_bytes()
  else:
   req=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0','Accept-Encoding':'identity'})
   with urllib.request.urlopen(req,context=CA,timeout=30) as r:data=r.read()
   cache.write_bytes(data)
  soup=BeautifulSoup(data,'html.parser'); lookup=collections.defaultdict(list)
  for f in courses[key]:lookup[norm(title(f['path']))].append(f)
  chapter=''; ordinal=''; matched=[]; total=0
  section=soup.select_one('.lesson-section')
  if not section:return key,[],0,'no lesson section'
  for el in section.select('.content-subcategory-title, a.lesson-item-link'):
   if 'content-subcategory-title' in el.get('class',[]):
    n=el.select_one('.subcategory-name'); o=el.select_one('.subcategory-order')
    chapter=n.get_text(' ',strip=True) if n else '';ordinal=o.get_text(' ',strip=True) if o else ''
   else:
    n=el.select_one('.lesson-item-name'); text=n.get_text(' ',strip=True) if n else el.get('alt') or el.get_text(' ',strip=True)
    total+=1; matches=lookup.get(norm(text),[])
    if len(matches)==1 and chapter:
     f=matches[0]; matched.append((f['index'],ordinal,chapter,total,url))
  return key,matched,total,''
 except Exception as e:return key,[],0,str(e)
print('Torrent courses:',len(courses),'matched public course URLs:',len(targets),flush=True)
allrows=[]; report=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
 for key,rows,total,error in pool.map(fetch,targets.items()):
  allrows.extend(rows);report.append({'group':key[0],'subject':key[1],'course':key[2],'url':targets[key],'torrent_count':len(courses[key]),'site_count':total,'matched':len(rows),'error':error})
  print(key,'mapped',len(rows),'of',len(courses[key]),error[:100],flush=True)
out=ROOT/'app/src/main/assets/site-outline.tsv'
seen=set();lines=['# torrent_index\tsection_label\tsection_title\tsite_order\tsource_url']
for row in sorted(allrows):
 if row[0] in seen:raise RuntimeError('duplicate torrent index')
 seen.add(row[0]); lines.append('\t'.join(str(x).replace('\t',' ').replace('\n',' ') for x in row))
out.write_text('\n'.join(lines)+'\n')
(ROOT/'build/site-outline-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
print('TOTAL',len(seen),'matched lessons', 'asset bytes',out.stat().st_size,flush=True)
