import re
import json
import argparse
from pathlib import Path

# ---------- helpers ----------
def extract_int_list(text: str):
    # matches: intArrayOf(1, 2, 3)
    m = re.search(r'intArrayOf\s*\((.*?)\)', text, flags=re.S)
    if not m:
        return None
    nums = re.findall(r'-?\d+', m.group(1))
    return [int(n) for n in nums]

def extract_long_list(text: str):
    # matches: longArrayOf(10, 10, 10)
    m = re.search(r'longArrayOf\s*\((.*?)\)', text, flags=re.S)
    if not m:
        return None
    nums = re.findall(r'-?\d+', m.group(1))
    return [int(n) for n in nums]

def extract_string_list(field_name: str, text: str):
    # matches: sensationTags = listOf("a","b")
    pattern = rf'{re.escape(field_name)}\s*=\s*listOf\s*\((.*?)\)'
    m = re.search(pattern, text, flags=re.S)
    if not m:
        return []
    # grab all "..."
    return re.findall(r'"(.*?)"', m.group(1))

def extract_id(text: str):
    m = re.search(r'id\s*=\s*"(VIB\d+)"', text)
    return m.group(1) if m else None

def extract_image(text: str):
    # matches: imagePath = R.drawable.vib000
    m = re.search(r'imagePath\s*=\s*R\.drawable\.([A-Za-z0-9_]+)', text)
    return m.group(1) if m else None

def find_vibrationmodel_blocks(kotlin: str):
    """
    Finds every 'VibrationModel(' ... matching closing ')'
    using a simple parenthesis counter from the start position.
    """
    blocks = []
    for match in re.finditer(r'VibrationModel\s*\(', kotlin):
        start = match.start()
        i = match.end() - 1  # at '('
        depth = 0
        while i < len(kotlin):
            ch = kotlin[i]
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
                if depth == 0:
                    # include up to closing ')'
                    blocks.append(kotlin[start:i+1])
                    break
            i += 1
    return blocks

# ---------- main ----------
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, help="Path to Kotlin file containing VibrationModel definitions")
    ap.add_argument("--output", required=True, help="Output JSON path")
    args = ap.parse_args()

    input_path = Path(args.input)
    out_path = Path(args.output)

    kotlin = input_path.read_text(encoding="utf-8", errors="ignore")

    blocks = find_vibrationmodel_blocks(kotlin)
    if not blocks:
        raise SystemExit("No VibrationModel(...) blocks found. Check that --input points to the right file.")

    patterns = []
    for b in blocks:
        vid = extract_id(b)
        if not vid:
            # skip blocks without id
            continue

        timings = extract_long_list(b)
        amplitudes = extract_int_list(b)

        pattern = {
            "id": vid,
            "timings": timings if timings is not None else [],
            "amplitudes": amplitudes if amplitudes is not None else [],
            "sensationTags": extract_string_list("sensationTags", b),
            "emotionTags": extract_string_list("emotionTags", b),
            "metaphors": extract_string_list("metaphors", b),
            "usageExamples": extract_string_list("usageExamples", b),
            "imagePath": extract_image(b),
        }
        patterns.append(pattern)

    # sort by ID so it's stable
    patterns.sort(key=lambda x: x["id"])

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(patterns, indent=2, ensure_ascii=False), encoding="utf-8")

    print(f"Extracted {len(patterns)} patterns -> {out_path}")

if __name__ == "__main__":
    main()
