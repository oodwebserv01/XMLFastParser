#!/usr/bin/env python3
"""XML2TXT — Python-only, stdlib, single-thread, string-FSM, no JVM/lib."""
import os, sys, zipfile, argparse
from datetime import datetime

def load_bp(path):
    with open(path, "r", encoding="utf-8") as f:
        lines = [l.strip() for l in f if l.strip() and not l.startswith("#")]
    entity = "Unknown"
    paths = []
    for line in lines:
        if line.startswith("file:"):
            entity = line.split(":", 1)[1].strip()
        elif line.startswith("-"):
            p = line[1:].strip()
            # drop prefix if any before first / (not needed per spec: keep full path but compare upper)
            paths.append(p)
    return entity, paths

def extract_tag_tree(text, tag_path):
    import xml.etree.ElementTree as ET
    try:
        root = ET.fromstring(text)
    except Exception:
        return ""
    segs = [s for s in tag_path.split("/") if s]
    if len(segs) > 0:
        segs = segs[1:]  # drop root segment (flexible root)
    node = root
    for seg in segs:
        if not seg or seg.startswith("-"):
            continue
        target = seg.split("@")[0].split("#")[0]
        target_base = target.split(":")[-1].split("}")[-1].upper()
        found = None
        for child in node:
            child_tag = child.tag
            if child_tag.startswith("{"):
                child_tag = child_tag.split("}")[-1]
            if child_tag.upper() == target_base:
                found = child
                break
        if found is None:
            return ""
        node = found
    seg = tag_path.split("/")[-1]
    if "@" in seg:
        val = node.get(seg.split("@")[1]) or ""
    elif "#" in seg:
        val = (node.text or "").strip()
    else:
        val = (node.text or "").strip()
    if not val:
        texts = [(c.text or "").strip() for c in node if (c.text or "").strip()]
        val = " ".join(texts)
    return val[:300]

def run(args):
    bp_path = args.p
    src_dir = args.s or "."
    dest_dir = args.d or "."
    threads = args.t or 1  # ignored (single)

    entity, paths = load_bp(bp_path)
    os.makedirs(dest_dir, exist_ok=True)

    # Find sources (no subfolder)
    sources = []
    try:
        for entry in os.listdir(src_dir):
            p = os.path.join(src_dir, entry)
            if entry.endswith(".xml") and os.path.isfile(p):
                sources.append(("file", p))
            elif entry.endswith(".zip") and os.path.isfile(p):
                sources.append(("zip", p))
    except Exception:
        pass

    # Prepare output
    ts = datetime.now().strftime("%Y%m%d%H%M%S")
    pending = os.path.join(dest_dir, f"{entity}_T{threads}_{ts}_pending.txt")

    with open(pending, "w", encoding="utf-8") as out:
        out.write(f"entity={entity}\n")
        out.write(f"source={src_dir}\n")

        for kind, path in sources:
            xml_texts = []
            if kind == "file":
                with open(path, "r", encoding="utf-8", errors="ignore") as f:
                    xml_texts.append((os.path.basename(path), f.read()))
            elif kind == "zip":
                with zipfile.ZipFile(path, "r") as z:
                    for nam in z.namelist():
                        if nam.endswith(".xml"):
                            xml_texts.append((nam, z.read(nam).decode("utf-8", errors="ignore")))

            for name, text in xml_texts:
                for p in paths:
                    val = extract_tag_tree(text, p)
                    if val:
                        out.write(f"{p}\t{val}\n")

    # ClosingJob: rename
    final = pending.replace("_pending.txt", f"_{ts}.txt")
    # But spec says [entityName]_YYYYMMDDHHmmss.txt; use same ts
    # Actually rename to entity_ThreadNo_YYYYMMDDHHmmss.txt (spec earlier)
    # Simplify: rename to entity_T{t}_{ts}.txt
    final_name = f"{entity}_T{threads}_{ts}.txt"
    final_path = os.path.join(dest_dir, final_name)
    os.rename(pending, final_path)
    print("MainLoop complete. Wrote", final_path)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-p", default="Lab/BluPrint.bp")
    parser.add_argument("-s", default=".")
    parser.add_argument("-d", default=".")
    parser.add_argument("-t", type=int, default=1)
    args = parser.parse_args()
    print("BootUp: entity= ", args.p, "threads=", args.t)
    run(args)
    print("ClosingJob: renamed ->", args.d)
