#!/usr/bin/env python3
import os, sys, zipfile, argparse, datetime, csv
S_ROOT=0; S_LT=1; S_TAGNAME=2; S_ATTR=3; S_VALUE=4; S_INNER=5; S_CLOSE=6; S_JUMP=7
class BP:
    def __init__(self, path):
        self.file=None; self.entity=None; self.paths=[]
        with open(path,'r',encoding='utf-8') as f:
            for line in f:
                line=line.split('#',1)[0].strip()
                if not line: continue
                if line.startswith('file:'): self.file=line[5:].strip()
                elif line.startswith('entity:'): self.entity=line[7:].strip()
                elif line.startswith('- /'): p=line[2:].strip(); self.paths.append('/'.join([seg.split(':')[-1] if ':' in seg else seg for seg in p.split('/') if seg]))
        if self.entity is None: self.entity='out'
class TreeNode:
    def __init__(self, tag):
        self.tag=tag; self.children=[]; self.value=None; self.parent=None
    def add_child(self, node):
        node.parent=self; self.children.append(node)
class CharParser:
    root=None
    def __init__(self, text):
        self.chars=list(text); self.ptr=0; self.n=len(self.chars)
    def parse_char_array(self, bp):
        while self.ptr<self.n and self.chars[self.ptr]=='<':
            if self.ptr+1<self.n and self.chars[self.ptr+1]=='?':
                self.ptr+=2
                while self.ptr<self.n and not (self.chars[self.ptr]=='?' and self.ptr+1<self.n and self.chars[self.ptr+1]=='>'): self.ptr+=1
                if self.ptr<self.n: self.ptr+=2
            else: break
        root=TreeNode('root'); self.root=root; stack=[root]
        while self.ptr<self.n:
            while self.ptr<self.n and ord(self.chars[self.ptr])>127: self.ptr+=1
            if self.ptr>=self.n: break
            ch=self.chars[self.ptr]
            if ch=='<':
                self.ptr+=1
                if self.ptr<self.n and self.chars[self.ptr]=='/':
                    self.ptr+=1
                    tag_chars=[]
                    while self.ptr<self.n and self.chars[self.ptr] not in ' >\t\n\r/': tag_chars.append(self.chars[self.ptr]); self.ptr+=1
                    close_tag=''.join(tag_chars).upper(); close_base=close_tag.split(':')[-1] if ':' in close_tag else close_tag
                    while self.ptr<self.n and self.chars[self.ptr]!='>': self.ptr+=1
                    if self.ptr<self.n: self.ptr+=1
                    # Pop only if top of stack matches close (nested correctness)
                    if stack and stack[-1].tag.upper()==close_base:
                        stack.pop()
                    else:
                        # Fallback search up stack for match
                        for i in range(len(stack)-1,-1,-1):
                            if stack[i].tag.upper()==close_base: stack=stack[:i+1]; break
                    continue
                tag_chars=[]
                while self.ptr<self.n and self.chars[self.ptr] not in ' >\t\n\r/': tag_chars.append(self.chars[self.ptr]); self.ptr+=1
                tag_raw=''.join(tag_chars).upper(); tag=tag_raw.split(':')[-1] if ':' in tag_raw else tag_raw
                while self.ptr<self.n and self.chars[self.ptr]!='>': self.ptr+=1
                if self.ptr<self.n: self.ptr+=1
                node=TreeNode(tag); stack[-1].add_child(node); stack.append(node)
            else:
                inner=[]
                while self.ptr<self.n and self.chars[self.ptr]!='<': inner.append(self.chars[self.ptr]); self.ptr+=1
                val=''.join(inner).strip()
                if val and stack: 
                    if not stack[-1].children: stack[-1].value=val
        res={}
        def collect(node):
            if not node.children and node.value is not None:
                res.setdefault(node.tag,[]).append(node.value)
            for c in node.children: collect(c)
        collect(root)
        return res
class Parser:
    def __init__(self, text, bp):
        self.s=text; self.bp=bp; self.state=S_ROOT; self.stack=[]; self.results={}
        self.paths_filter=getattr(bp,'filtered_paths',None) or getattr(bp,'paths',[])
    def parse(self):
        self.cp=CharParser(self.s)
        res=self.cp.parse_char_array(self.bp); self.tree_root=self.cp.root; return res
class TokenColumnFixed:
    def __init__(self, tag=''):
        self.tag=tag.upper() if tag else ''
        self.path=tag; self.inner=-1; self.all_attr={}
class TokenEntityFixed:
    def __init__(self, name=''):
        self.name=name.upper() if name else ''
        self.columns={}; self.total_column=0
class BlueprintHolder:
    def __init__(self): self.token_entities={}; self.results={}; self.event=None
    def regist(self, token_entity): self.token_entities[token_entity.name]=token_entity
    def set_result(self, file_key, rows): self.results.setdefault(file_key,[]).extend(rows)
    def closing_job(self, out_dir):
        import csv, os, datetime
        for fk,rows in self.results.items():
            ts=datetime.datetime.now().strftime('%Y%m%d%H%M%S')
            safe=fk.replace('/','_').replace('\\','_')[:60]
            path=os.path.join(out_dir, safe+'_'+ts+'.txt')
            with open(path,'w',newline='',encoding='utf-8') as f:
                writer=csv.writer(f,lineterminator='\n',quoting=csv.QUOTE_NONNUMERIC)
                for row in rows: writer.writerow(row)
class TokenColumn: pass
class TokenEntity: pass
def parse_bp_fixed(bp_path):
    bp=BP(bp_path); entities={}; current=None; current_file_key=None
    for line in open(bp_path,'r',encoding='utf-8'):
        line=line.split('#',1)[0].strip()
        if line.startswith('file:'):
            if current is not None and current_file_key: entities[current_file_key]=current
            current_file_key=line[5:].strip(); current=TokenEntityFixed(current_file_key)
        elif line.startswith('entity:'):
            if current is None: current=TokenEntityFixed()
            current.name=line[7:].strip().upper()
        elif line.startswith('- /') and current is not None:
            seg=line[2:].strip(); col=TokenColumnFixed(seg); col.inner=current.total_column; current.total_column+=1
            if '@' in seg: col.all_attr[col.inner]=seg.split('@')[-1]
            if '#' in seg: col.all_attr[col.inner]='value'
            h=hash(seg); current.columns[h]=col
    if current is not None and current_file_key: entities[current_file_key]=current
    return entities
def find_files(src_dir):
    files=[]
    for entry in os.listdir(src_dir):
        if entry.startswith('.') or entry.startswith('~'): continue
        path=os.path.join(src_dir,entry)
        if os.path.isfile(path):
            if entry.lower().endswith('.xml'): files.append(('file',path))
            elif entry.lower().endswith('.zip'): files.append(('zip',path))
    return files
def process_zip(zpath,bp,out_dir,entities=None):
    import zipfile
    with zipfile.ZipFile(zpath,'r') as z:
        for info in z.infolist():
            if info.filename.lower().endswith('.xml'):
                text=z.read(info.filename).decode('utf-8',errors='ignore')
                xml_name=os.path.basename(info.filename)
                ents=entities or parse_bp_fixed('Lab/BluPrint.bp')
                for fk in ents: run_text(text,bp,out_dir,xml_name=xml_name,file_key=fk)
def run_text(text,bp,out_dir,xml_name='',file_key=''):
    import csv,os,datetime
    parser=Parser(text,bp)
    res=parser.parse()
    # If tree root available, also extract via direct tree walk for complete values
    if file_key and hasattr(parser,'tree_root') and parser.tree_root is not None:
        try:
            root=parser.tree_root
            # Direct recursive collection from tree (complete traversal)
            def collect_all(node):
                tag=node.tag.upper()
                if not node.children:
                    if node.value is not None and node.value!='':
                        res.setdefault(tag,[]).append(node.value)
                else:
                    # For intermediate nodes, also collect if they have direct text (skip here)
                    pass
                for c in node.children:
                    collect_all(c)
            # Reset res to ensure full extraction
            res={}
            collect_all(root)
        except Exception: pass
    # Traverse tree by bp paths (FSM + registered handler mechanism — walk tree by registered paths)
    if file_key and hasattr(parser,'tree_root') and parser.tree_root is not None:
        try:
            ents=parse_bp_fixed('Lab/BluPrint.bp')
            ent=ents.get(file_key)
            if ent:
                def traverse(node, path_segs):
                    if not path_segs:
                        if not node.children and node.value is not None:
                            res.setdefault(node.tag,[]).append(node.value)
                        return
                    seg=path_segs[0]
                    for c in node.children:
                        # Flexible base match (segment may be part of tag base)
                        if c.tag.upper()==seg.upper() or (seg.upper() in c.tag.upper() and len(c.tag.upper())>=len(seg.upper())):
                            traverse(c, path_segs[1:])
                root=parser.tree_root
                for _,col in sorted(ent.columns.items(), key=lambda x: x[1].inner if hasattr(x[1],'inner') else 0):
                    segs=[s.split(':')[-1] if ':' in s else s for s in col.tag.split('/') if s and s.strip()!='']
                    if segs: traverse(root, segs)
        except Exception: pass
    if file_key:
        try:
            ents=parse_bp_fixed('Lab/BluPrint.bp')
            ent=ents.get(file_key)
            if ent:
                allowed=set()
                for _,col in ent.columns.items():
                    seg=col.tag.split('/')[-1]
                    allowed.add((seg.split(':')[-1] if ':' in seg else seg).upper())
                res={k:v for k,v in res.items() if (k.split(':')[-1] if ':' in k else k).upper() in allowed}
        except Exception: pass
    ts=datetime.datetime.now().strftime('%Y%m%d%H%M%S')
    safe_name=(file_key or (bp.file or bp.entity or 'out')).replace('/','_').replace('\\','_')[:60]
    pending_path=os.path.join(out_dir,safe_name+'_pending.txt')
    row=[xml_name or 'unknown.xml']
    # Build from bp.paths in order; use filtered res
    paths_for_row=getattr(bp,'paths',[])
    if file_key:
        try:
            ents=parse_bp_fixed('Lab/BluPrint.bp')
            ent=ents.get(file_key)
            if ent:
                allowed_tags=set()
                for _,col in ent.columns.items():
                    seg=col.tag.split('/')[-1]
                    allowed_tags.add((seg.split(':')[-1] if ':' in seg else seg).upper())
                paths_for_row=[p for p in paths_for_row if (p.split('/')[-1].split(':')[-1] if ':' in p.split('/')[-1] else p.split('/')[-1]).upper() in allowed_tags]
        except Exception: pass
    for p in paths_for_row:
        parts=[x for x in p.split('/') if x]
        if not parts: continue
        seg=parts[-1].split('@')[0].split('#')[0]
        if ':' in seg: seg=seg.split(':')[-1]
        key=seg.upper()
        vals=res.get(key,[])
        row.append(vals[0] if vals else '')
    with open(pending_path,'w',newline='',encoding='utf-8') as f:
        writer=csv.writer(f,lineterminator='\n',quoting=csv.QUOTE_NONNUMERIC)
        writer.writerow(row)
    final_path=os.path.join(out_dir,safe_name+'_'+ts+'.txt')
    try: os.rename(pending_path,final_path)
    except Exception: pass
    return final_path
def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('-p','--blueprint',required=True)
    parser.add_argument('-s','--source',required=True)
    parser.add_argument('-d','--dest',default='.')
    parser.add_argument('-t','--threads',type=int,default=1)
    args=parser.parse_args()
    bp=BP(args.blueprint); os.makedirs(args.dest,exist_ok=True)
    files=find_files(args.source)
    for typ,path in files:
        if typ=='file':
            with open(path,'r',encoding='utf-8',errors='ignore') as f: text=f.read()
            ents=parse_bp_fixed('Lab/BluPrint.bp')
            for fk in ents: run_text(text,bp,args.dest,xml_name=os.path.basename(path),file_key=fk)
        elif typ=='zip': process_zip(path,bp,args.dest)
if __name__=='__main__': main()
