#!/usr/bin/env python3
"""Render the manual's Markdown subset as a standalone, public HTML page.

No network, authentication, or third-party renderer is needed. The build runs
this script; --check detects stale generated output without modifying files.
"""

import argparse
from html import escape
from pathlib import Path
import re
from urllib.parse import urlsplit


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "USER_MANUAL.md"
TEMPLATE = ROOT / "scripts/user_manual_template.html"
OUTPUT = ROOT / "webApp/src/jsMain/resources/user-manual.html"


def slug(text):
    return re.sub(r"[^\w\- ]", "", text.lower()).replace(" ", "-")


def inline(text):
    """Escape text and render only code, bold, and Markdown links."""
    pattern = re.compile(r"`([^`]+)`|\*\*([^*]+)\*\*|\[([^\]]+)\]\(([^)]+)\)")
    result = []
    start = 0
    for match in pattern.finditer(text):
        result.append(escape(text[start:match.start()]))
        code, strong, label, href = match.groups()
        if code is not None:
            result.append(f"<code>{escape(code)}</code>")
        elif strong is not None:
            result.append(f"<strong>{escape(strong)}</strong>")
        else:
            # This repository-only guide is not a public deployment asset.
            if href == "docs/first-organization-admin.md":
                href = "#prepare-administrator-access"
            if not href.startswith("#") and urlsplit(href).scheme not in ("https", "mailto"):
                raise ValueError(f"Unsupported public manual link: {href}")
            result.append(f'<a href="{escape(href, quote=True)}">{escape(label)}</a>')
        start = match.end()
    result.append(escape(text[start:]))
    return "".join(result)


def render(markdown):
    lines = markdown.splitlines()
    body = []
    contents = []
    identifiers = set()
    index = 0
    while index < len(lines):
        line = lines[index]
        if not line.strip():
            index += 1
            continue
        heading = re.fullmatch(r"(#{1,6}) (.+)", line)
        if heading:
            level, title = len(heading[1]), heading[2]
            if title == "Contents":
                # The same contents are presented as a responsive sidebar.
                index += 1
                while index < len(lines) and not lines[index].startswith("#"):
                    index += 1
                continue
            identifier = slug(title)
            if identifier in identifiers:
                raise ValueError(f"Duplicate heading: {title}")
            identifiers.add(identifier)
            if level == 2:
                contents.append(f'<li><a href="#{identifier}">{escape(title)}</a></li>')
            body.append(f'<h{level} id="{identifier}">{inline(title)}</h{level}>')
            index += 1
        elif line.startswith("|"):
            rows = []
            while index < len(lines) and lines[index].startswith("|"):
                rows.append([cell.strip() for cell in lines[index].strip().strip("|").split("|")])
                index += 1
            if len(rows) < 2 or not all(re.fullmatch(r":?-+:?", cell) for cell in rows[1]):
                raise ValueError("A manual table must have a header separator")
            width = len(rows[0])
            if any(len(row) != width for row in rows):
                raise ValueError("Inconsistent column count in manual table")
            header = "".join(f'<th scope="col">{inline(cell)}</th>' for cell in rows[0])
            records = "".join("<tr>" + "".join(f"<td>{inline(cell)}</td>" for cell in row) + "</tr>" for row in rows[2:])
            body.append('<div class="table-scroll" tabindex="0" role="region" aria-label="Scrollable reference table">'
                        f"<table><thead><tr>{header}</tr></thead><tbody>{records}</tbody></table></div>")
        elif re.match(r"(?:- |\d+\. )", line):
            ordered = bool(re.match(r"\d+\. ", line))
            pattern = r"\d+\. (.+)" if ordered else r"- (.+)"
            tag = "ol" if ordered else "ul"
            items = []
            while index < len(lines) and (item := re.fullmatch(pattern, lines[index])):
                items.append(f"<li>{inline(item[1])}</li>")
                index += 1
            body.append(f"<{tag}>" + "".join(items) + f"</{tag}>")
        else:
            paragraph = []
            while index < len(lines) and lines[index].strip():
                if re.match(r"(?:#|\||- |\d+\. )", lines[index]):
                    break
                if lines[index].startswith(("```", "    ", ">")):
                    raise ValueError("Unsupported Markdown block in user manual")
                paragraph.append(lines[index])
                index += 1
            if not paragraph:
                raise ValueError(f"Unsupported manual block: {line}")
            body.append(f'<p>{inline(" ".join(paragraph))}</p>')
    rendered = "\n".join(body)
    for target in re.findall(r'href="#([^"]+)"', rendered):
        if target not in identifiers:
            raise ValueError(f"Missing manual anchor: {target}")
    return rendered, "\n".join(contents)


def generate():
    body, contents = render(SOURCE.read_text(encoding="utf-8"))
    template = TEMPLATE.read_text(encoding="utf-8")
    return template.replace("{{CONTENTS}}", contents).replace("{{BODY}}", body)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    document = generate()
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != document:
            raise SystemExit("Public manual is stale. Run python3 scripts/generate_user_manual.py")
        print("Public manual is current; all heading links resolve.")
    else:
        OUTPUT.write_text(document, encoding="utf-8")
        print(f"Generated {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
