#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""نەشر نومۇرىنى بىر قەدەم ئۆستۈرۈش / Bump the release version.

ھەر قېتىملىق كود يېڭىلاشتىن بۇرۇن ``versionCode`` نى بىرگە، ``versionName``
نىڭ كىچىك نومۇرىنى بىرگە ئاشۇرىدۇ (مەسىلەن 1.20/42 → 1.21/43).
يېڭى قىممەتلەرنى stdout غا ۋە ``GITHUB_OUTPUT`` غا يازىدۇ.
"""

from __future__ import annotations

import json
import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "src" / "android" / "app" / "build.gradle.kts"


def main() -> None:
    text = GRADLE.read_text(encoding="utf-8")
    m_code = re.search(r"versionCode\s*=\s*(\d+)", text)
    m_name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not m_code or not m_name:
        raise SystemExit("versionCode ياكى versionName تېپىلمىدى")

    code = int(m_code.group(1)) + 1
    parts = m_name.group(1).split(".")
    parts[-1] = str(int(parts[-1]) + 1)
    name = ".".join(parts)

    text = text[: m_code.start()] + text[m_code.start() :].replace(m_code.group(0), f"versionCode = {code}", 1)
    text = text.replace(m_name.group(0), f'versionName = "{name}"', 1)
    GRADLE.write_text(text, encoding="utf-8")

    result = {"version_name": name, "version_code": code, "tag": f"v{name}"}
    print(json.dumps(result, ensure_ascii=False))
    out = os.environ.get("GITHUB_OUTPUT")
    if out:
        with open(out, "a", encoding="utf-8") as fh:
            for key, value in result.items():
                fh.write(f"{key}={value}\n")


if __name__ == "__main__":
    main()
