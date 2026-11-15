#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ئەسلى ئامبارنى ئىز قوغلاش / Follow the upstream OpenMinis repository.

ئەسلى ئامبار (OpenMinis/OpenMinis) دىكى يېڭى نەشر، يېڭى خەتكۈچ ۋە يېڭى
كود يوللاشلارنى تەكشۈرۈپ، ھالىتى ``upstream-state.json`` غا ۋە
``docs/UPSTREAM_TRACKING.md`` دىكى ھالەت بۆلىكىگە يازىدۇ. ھەر بىر چاقىرىش
ئاۋۋال ``https://gh-proxy.com/`` ئەينىكىنى، مەغلۇپ بولسا بىۋاسىتە
GitHub نى ئىشلىتىدۇ.

ئىشلىتىش:
    python3 scripts/track_upstream.py --write     # ھۆججەتلەرنى يېڭىلايدۇ
    python3 scripts/track_upstream.py --check     # CI ئۈچۈن: has_update نى JSON دا بېرىدۇ
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
STATE_FILE = ROOT / "upstream-state.json"
DOC_FILE = ROOT / "docs" / "UPSTREAM_TRACKING.md"
GRADLE_FILE = ROOT / "src" / "android" / "app" / "build.gradle.kts"

UPSTREAM = "OpenMinis/OpenMinis"
OWN_REPO = os.environ.get("OWN_REPO", "Mr-losfer/LOSFER-Minis")
GH_PROXY = "https://gh-proxy.com/"
START = "<!-- STATUS:START -->"
END = "<!-- STATUS:END -->"


def _get(path: str):
    """GitHub API چاقىرىشى: ئەينەك ئاۋۋال، ئاندىن بىۋاسىتە."""
    direct = "https://api.github.com" + path
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    # [LOSFER] ئەينەك بەزىدە كونا نەشر تىزىملىكىنى ساقلاپ قالىدۇ؛ ئىز
    # قوغلاش ئەڭ يېڭى ئۇچۇرنى تەلەپ قىلىدۇ. شۇڭا ئاچقۇچ بولسا بىۋاسىتە
    # GitHub نى، بولمىسا ئەينەكنى ئاۋۋال سىنايمىز، مەغلۇپ بولسا ئالماشتۇرىمىز.
    urls = [direct, GH_PROXY + direct] if token else [GH_PROXY + direct, direct]
    base_headers = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "LOSFER-Minis-upstream-follow/1.0",
    }
    last = None
    for url in urls:
        headers = dict(base_headers)
        if token and url == direct:
            headers["Authorization"] = "Bearer " + token
        try:
            with urlopen(Request(url, headers=headers), timeout=25) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except (HTTPError, URLError, TimeoutError, ValueError, OSError) as exc:
            last = exc
            continue
    print(f"ئاگاھلاندۇرۇش: {path} ئوقۇلمىدى: {last}", file=sys.stderr)
    return None


def _own_version() -> tuple[str, int]:
    text = GRADLE_FILE.read_text(encoding="utf-8")
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    code = re.search(r"versionCode\s*=\s*(\d+)", text)
    return (name.group(1) if name else "?", int(code.group(1)) if code else 0)


# نەشر خاتىرىسىدىكى ئۈچ بۆلەك: يېڭى ئىقتىدار، تۈزىتىش، كۆرۈنۈش ياخشىلاش.
# ئەسلى ئامبار ھەر بىرىنى ئايرىم «###» ماۋزۇسىدا بېرىدۇ؛ ئىز قوغلاش
# پەقەت «New features» نى ئەمەس، ھەممىسىنى خاتىرىلىشى كېرەك.
FEATURE_SECTIONS = (
    ("### New features", "يېڭى ئىقتىدار"),
    ("### Fixes", "تۈزىتىش"),
    ("### Polish", "كۆرۈنۈش"),
)


def _features(body: str) -> list[str]:
    """ئىنگلىزچە نەشر خاتىرىسىدىكى بارلىق ئۆزگىرىش تۈرلىرىنى ئالىدۇ."""
    en = re.search(r"## English(.*?)(?:---|\n## 中文)", body or "", re.S)
    part = en.group(1) if en else (body or "")
    found: list[str] = []
    for heading, label in FEATURE_SECTIONS:
        block = re.search(re.escape(heading) + r"(.*?)(?:\n### |\Z)", part, re.S)
        if not block:
            continue
        for ln in block.group(1).strip().splitlines():
            if ln.startswith("- "):
                found.append(f"[{label}] {ln[2:].strip()}")
    return found


def collect() -> dict:
    repo = _get(f"/repos/{UPSTREAM}") or {}
    releases = _get(f"/repos/{UPSTREAM}/releases?per_page=10") or []
    commits = _get(f"/repos/{UPSTREAM}/commits?per_page=5") or []
    tags = _get(f"/repos/{UPSTREAM}/tags?per_page=10") or []
    ver, code = _own_version()

    latest = releases[0] if isinstance(releases, list) and releases else {}
    beta = [r for r in releases if isinstance(r, dict) and r.get("prerelease")]
    stable = [r for r in releases if isinstance(r, dict) and not r.get("prerelease") and not r.get("draft")]

    feature_lines: list[str] = []
    for r in (beta[:5] if isinstance(releases, list) else []):
        for line in _features(r.get("body") or ""):
            feature_lines.append(f"{r.get('tag_name', '?')}: {line}")

    return {
        "checked_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "upstream_repo": UPSTREAM,
        "own_repo": OWN_REPO,
        "own_version": ver,
        "own_version_code": code,
        "upstream_pushed_at": repo.get("pushed_at") or "",
        "upstream_default_branch": repo.get("default_branch") or "main",
        "latest_release_tag": latest.get("tag_name") or "",
        "latest_release_name": latest.get("name") or "",
        "latest_release_published_at": latest.get("published_at") or "",
        "latest_release_prerelease": bool(latest.get("prerelease")),
        "latest_release_url": latest.get("html_url") or "",
        "latest_stable_tag": (stable[0].get("tag_name") if stable else ""),
        "latest_commit_sha": (commits[0].get("sha") if commits else ""),
        "latest_commit_date": (
            (commits[0].get("commit") or {}).get("author", {}).get("date") if commits else ""
        ),
        "latest_commit_message": (
            (commits[0].get("commit") or {}).get("message", "").splitlines()[0] if commits else ""
        ),
        "tags": [t.get("name") for t in tags if isinstance(t, dict)][:10],
        "upstream_features": feature_lines[:200],
    }


def has_update(now: dict, previous: dict | None) -> bool:
    if not previous:
        return False  # تۇنجى قېتىم پەقەت ھالەت يېزىلىدۇ، يېڭى نەشر چىقارمايدۇ
    keys = ("latest_release_tag", "latest_commit_sha", "upstream_pushed_at")
    return any(now.get(k) != previous.get(k) for k in keys)


def status_block(now: dict, changed: bool) -> str:
    def row(label: str, value: str) -> str:
        return f"| {label} | {value or '—'} |"

    lines = [
        START,
        "",
        "## ھازىرقى ھالەت",
        "",
        "| تۈر | قىممىتى |",
        "| --- | --- |",
        row("تەكشۈرۈلگەن ۋاقىت (UTC)", f"`{now['checked_at']}`"),
        row("ئەسلى ئامبار", f"`{now['upstream_repo']}`"),
        row("ئەسلى ئامبارنىڭ ئەڭ يېڭى نەشرى", f"`{now['latest_release_tag']}` — {now['latest_release_name']}"),
        row("ئەڭ يېڭى مۇقىم نەشر", f"`{now['latest_stable_tag']}`"),
        row("ئەڭ يېڭى كود يوللاش", f"`{now['latest_commit_sha'][:10]}` — {now['latest_commit_message']}"),
        row("ئەسلى ئامبار يېڭىلانغان ۋاقىت", f"`{now['upstream_pushed_at']}`"),
        row("LOSFER Minis نەشرى", f"`{now['own_version']}` (versionCode `{now['own_version_code']}`)"),
        row("يېڭىلىق بايقالدى", "**ھەئە**" if changed else "ياق"),
        "",
        "> بۇ بۆلەك `scripts/track_upstream.py` تەرىپىدىن ئۆزلۈكىدىن يېڭىلىنىدۇ. قولدا تەھرىرلىمەڭ.",
        "",
        END,
    ]
    return "\n".join(lines)


def write_doc(now: dict, changed: bool) -> None:
    DOC_FILE.parent.mkdir(parents=True, exist_ok=True)
    block = status_block(now, changed)
    if DOC_FILE.exists():
        text = DOC_FILE.read_text(encoding="utf-8")
        if START in text and END in text:
            before, rest = text.split(START, 1)
            _, after = rest.split(END, 1)
            DOC_FILE.write_text(before + block + after, encoding="utf-8")
            return
        DOC_FILE.write_text(text.rstrip() + "\n\n" + block + "\n", encoding="utf-8")
        return
    DOC_FILE.write_text(
        "# ئەسلى ئامبارنى ئىز قوغلاش\n\n"
        "LOSFER Minis نىڭ ئەسلى ئامبارى [OpenMinis/OpenMinis]"
        "(https://github.com/OpenMinis/OpenMinis) بولۇپ، بۇ ھۆججەت ئۇنىڭ "
        "يېڭىلىقلىرىنى ئىز قوغلايدۇ.\n\n" + block + "\n",
        encoding="utf-8",
    )


def main() -> int:
    ap = argparse.ArgumentParser(description="ئەسلى ئامبارنى ئىز قوغلاش")
    ap.add_argument("--write", action="store_true", help="ھالەت ۋە ھۆججەتلەرنى يېزىش")
    ap.add_argument("--check", action="store_true", help="يېڭىلىق بارلىقىنى ئېنىقلاش")
    args = ap.parse_args()

    previous = None
    if STATE_FILE.exists():
        try:
            previous = json.loads(STATE_FILE.read_text(encoding="utf-8"))
        except ValueError:
            previous = None

    now = collect()
    changed = has_update(now, previous)

    if args.write:
        STATE_FILE.write_text(json.dumps(now, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        write_doc(now, changed)
        # CI بۇ چاقىرىشنىڭ stdout نى JSON سۈپىتىدە ئوقۇيدۇ، شۇڭا ئۇچۇرنى
        # stderr غا يازىمىز (JSON بۇلغىنىپ كەتمىسۇن).
        print(f"ھالەت يېزىلدى: {STATE_FILE.name}, {DOC_FILE.relative_to(ROOT)}", file=sys.stderr)

    if args.check:
        # CI دا بۇ باسقۇچنىڭ مەغلۇپ بولۇشىنىڭ ئالدىنى ئېلىش ئۈچۈن ھەر ئىككى
        # ئەھۋالدا 0 قايتۇرىمىز؛ قارار ``has_update`` قىممىتىدىن ئېلىنىدۇ.
        print(json.dumps({"has_update": changed, **now}, ensure_ascii=False))
        return 0

    if not args.write:
        print(json.dumps(now, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
