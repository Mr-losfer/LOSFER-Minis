#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""رەسمىي نەشر خاتىرىسىنى (ئۇيغۇرچە) ھاسىل قىلىش.

``dist/`` دىكى بارلىق ئورۇنلاشتۇرما بولاقلىرىنى، ئەسلى ئامبارنىڭ ئىز
قوغلاش ھالىتىنى ۋە ئىمزا ئەھۋالىنى بىرلەشتۈرۈپ، Releases بېتىگە
يوللىنىدىغان ئۇيغۇرچە چۈشەندۈرۈش ھۆججىتىنى يازىدۇ.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", required=True)
    ap.add_argument("--code", type=int, required=True)
    ap.add_argument("--dist", default="dist")
    ap.add_argument("--out", default="RELEASE_NOTES.md")
    ap.add_argument("--signature", choices=("same", "changed"), default="same")
    ap.add_argument("--extra", default="", help="قوشۇمچە ئۇيغۇرچە ئىزاھات (Markdown)")
    args = ap.parse_args()

    dist = ROOT / args.dist
    packages = sorted(p.name for p in dist.iterdir() if p.is_file()) if dist.exists() else []

    state = {}
    state_path = ROOT / "upstream-state.json"
    if state_path.exists():
        state = json.loads(state_path.read_text(encoding="utf-8"))

    latest_tag = state.get("latest_release_tag") or "—"
    features = state.get("upstream_features") or []
    feature_count = len(features)

    # ھەر بىر ئەسلى نەشرنىڭ ئۆز سانىنى ھېسابلايمىز؛ ئىلگىرىكىدەك
    # «8 / 12 / 6» نى قولدا يېزىپ قويمايمىز، يوقىرى نەشرگە كۆتۈرگەندە
    # كونا سان تۇرۇپ قېلىش خاتالىقى تۈزىتىلدى.
    latest_items = [x for x in features if str(x).startswith(f"{latest_tag}:")]
    n_new = sum(1 for x in latest_items if "[يېڭى ئىقتىدار]" in x)
    n_fix = sum(1 for x in latest_items if "[تۈزىتىش]" in x)
    n_polish = sum(1 for x in latest_items if "[كۆرۈنۈش]" in x)

    lines = [
        f"# LOSFER Minis {args.version}",
        "",
        f"**نەشر نومۇرى:** `{args.version}` · **قۇرۇش نومۇرى:** `build {args.code}`",
        "",
        "## بۇ نەشردىكى يېڭىلىقلار",
        "",
        f"- ئەسلى ئامبارنىڭ `{latest_tag}` سىناق نەشرى ئىز قوغلاندى: ئۇنىڭ **{n_new} يېڭى ئىقتىدارى، {n_fix} تۈزىتىشى ۋە {n_polish} كۆرۈنۈش ياخشىلاشى** (جەمئىي `{len(latest_items)}` تۈر) ئۇيغۇرچە ئىز قوغلاش جەدۋىلىگە تولۇق كىرگۈزۈلدى؛ نەشر خاتىرىسىنىڭ ئۈچ بۆلىكىنىڭ ھەممىسى ئاپتوماتىك ئوقۇلىدۇ (ھازىرغىچە جەمئىي `{feature_count}` تۈر خاتىرىلەندى). ئەسلى ئامبار ھازىرچە پەقەت APK ئېلان قىلغانلىقى ئۈچۈن مەنبە كودى بىرلەشتۈرۈلمىدى؛ كود يوللانغان ھامان ئاپتوماتىك بىرلەشتۈرۈلىدۇ.",
        f"- بۇ نەشردە `{latest_tag}` نىڭ {len(latest_items)} تۈرلۈك ئۆزگىرىشى [ئۇيغۇرچە ئىز قوغلاش جەدۋىلى](https://github.com/Mr-losfer/LOSFER-Minis/blob/main/docs/UPSTREAM_TRACKING.md) گە تۈر-تۈر بويىچە تولۇق يېزىلدى؛ نەشر خاتىرىسىنىڭ سانلىق مەلۇماتى ئەمدى ھەر بىر نەشر ئۈچۈن ئاپتوماتىك ھېسابلىنىدۇ، كونا نەشرنىڭ سانى تەكرارلانمايدۇ.",
        f"- ئىز قوغلاش ھالىتىدىكى ئۆز نەشرىمىز (`upstream-state.json` ۋە ھالەت جەدۋىلى) نەشر نومۇرى ئۆستۈرۈلگەندىن كېيىن `build.gradle.kts` بىلەن ئاپتوماتىك ماسلاشتۇرۇلىدىغان بولدى.",
        "- ئىز قوغلاش ئۈچۈن بىۋاسىتە GitHub API ئاۋۋال ئوقۇلىدىغان بولدى؛ ئەينەك مۇلازىمېتىرىدىكى كونا تىزىملىك سەۋەبلىك يېڭى نەشرنى قولدىن بېرىش خەۋپى تۈگىتىلدى.",
        "- **ھەر بىر ئورۇنلاشتۇرما بولاق** قايتا قۇرۇلدى ۋە بۇ رەسمىي Release غا يوللاندى: github يولى APK، store يولى APK، store يولى AAB ۋە يەرلىك بەلگە ئارخىپى.",
        "- ئەسلى ئامبار [OpenMinis/OpenMinis](https://github.com/OpenMinis/OpenMinis) نى ئىز قوغلاش ئاپتوماتلاشتۇرۇلدى: يېڭى نەشر، خەتكۈچ ياكى كود يوللاش بايقالسا، نەشر نومۇرى ئۆزلۈكىدىن ئۆستۈرۈلۈپ، بارلىق ئورۇنلاشتۇرما بولاقلار قايتا قۇرۇلۇپ، رەسمىي (غەيرىي سىناق) Releases تىزىملىكىگە يوللىنىدۇ.",
        "- ئەپ ئىچىدىكى يېڭىلاش تەكشۈرۈشى ئۆز ئامبارىمىزنىڭ بولاق نامىنى توغرا ئوقۇيدۇ: `LOSFER-Minis-<نەشر>-build<نومۇر>-<ABI>.apk` فورماتىدىن نەشر، قۇرۇش نومۇرى ۋە ABI ئايرىپ ئېلىنىدۇ؛ سىناق (beta) يولىمۇ قۇرۇش نومۇرى بويىچە سېلىشتۇرالايدۇ.",
        "- رەسمىي بولاقلار تىزىملىكى: لايىھە (draft) ۋە سىناق (prerelease) نەشرلىرى چىقىرىۋېتىلىدۇ، بىر نەشردىكى **ھەر بىر** APK/AAB ئايرىم بولاق سۈپىتىدە تىزىملىنىدۇ.",
        "- قۇرۇش ئىمزاسى ئامباردىكى تۇراقلىق ئاچقۇچقا باغلاندى، شۇڭا ھەر قېتىملىق يېڭى بولاقنىڭ ئىمزاسى ئوخشاش چىقىدۇ.",
    ]
    if args.extra:
        lines += ["", args.extra]

    lines += [
        "",
        "## ئورۇنلاشتۇرما بولاقلار",
        "",
    ]
    if packages:
        lines.append("| بولاق | چوڭلۇقى |")
        lines.append("| --- | --- |")
        for name in packages:
            size = (dist / name).stat().st_size
            lines.append(f"| `{name}` | {size / 1048576:.2f} MB |")
    else:
        lines.append("بولاق تېپىلمىدى.")

    lines += [
        "",
        "> `arm64-v8a` ئۈسكۈنىلىرى ئۈچۈن github يولى APK، دۇكان يولى ئۈچۈن APK ۋە AAB، "
        "شۇنداقلا يەرلىك بەلگە ئارخىپى تەييارلىنىدۇ؛ ھەممىسى بۇ رەسمىي Release غا يوللاندى. "
        "ئەپ ئىچىدىكى يېڭىلاش تەكشۈرۈشى `LOSFER-Minis-<نەشر>-build<نومۇر>-arm64-v8a.apk` "
        "ناملىق ئاساسلىق بولاقنى تاللايدۇ. iOS نەشرى Xcode/macOS مۇھىتى تەلەپ قىلغانلىقى "
        "ئۈچۈن بۇ ئاپتوماتلاشقان Linux قۇرۇش باسقۇچىغا كىرمەيدۇ.",
        "",
        "## ئەسلى ئامبارنىڭ ھالىتى",
        "",
        "| تۈر | قىممىتى |",
        "| --- | --- |",
        f"| ئەڭ يېڭى نەشر | `{state.get('latest_release_tag', '—')}` |",
        f"| ئەڭ يېڭى مۇقىم نەشر | `{state.get('latest_stable_tag', '—')}` |",
        f"| ئەڭ يېڭى كود | `{str(state.get('latest_commit_sha', ''))[:10]}` |",
        f"| ئىز قوغلانغان ئۆزگىرىش تۈرى | `{feature_count}` |",
        f"| تەكشۈرۈلگەن ۋاقىت | `{state.get('checked_at', '—')}` |",
        "",
        "تەپسىلىي ئىز قوغلاش جەدۋىلى: [docs/UPSTREAM_TRACKING.md]"
        "(https://github.com/Mr-losfer/LOSFER-Minis/blob/main/docs/UPSTREAM_TRACKING.md)",
        "",
        "## قاچىلاش",
        "",
    ]
    if args.signature == "changed":
        lines += [
            "- **دىققەت:** بۇ نەشردىن باشلاپ ئىمزا گۇۋاھنامىسى يېڭىلاندى. "
            "`1.20` ۋە ئۇنىڭدىن بۇرۇنقى نەشرلەرنىڭ ئۈستىگە بىۋاسىتە قاچىلىغىلى "
            "بولمايدۇ؛ ئاۋۋال كونا ئەپنى ئۆچۈرۈپ، ئاندىن يېڭى APK نى قاچىلاڭ "
            "(سانلىق مەلۇماتنى ئالدىن زاپاسلاڭ). كېيىنكى نەشرلەردە ئىمزا ئۆزگەرمەيدۇ.",
        ]
    else:
        lines += ["- ئىلگىرىكى نەشرلەرنىڭ ئۈستىگە بىۋاسىتە قاچىلىغىلى بولىدۇ؛ ئىمزا ئۆزگەرمىدى."]
    lines += [
        "",
        "**ئاپتور:** Mr-losfer · **ئامبار:** "
        "[Mr-losfer/LOSFER-Minis](https://github.com/Mr-losfer/LOSFER-Minis) · "
        "**API ئاچقۇچى:** [api.losfer.cn/pc/api-list/keys](https://api.losfer.cn/pc/api-list/keys/)",
        "",
    ]

    out = ROOT / args.out
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"نەشر خاتىرىسى يېزىلدى: {out.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
