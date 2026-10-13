#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把 timetable.json 推到手表上（不依赖 Wi-Fi，走 USB 端口转发）。

为什么需要它：
  手表端 App 在 8899 端口跑了一个极简 TCP 服务（协议见 SyncServer.kt）：
      [4 字节 body 长度 BigEndian] + [UTF-8 的 JSON]  →  回 1 字节（1 成功 / 0 失败）
  手机 App 走的是同一条协议，只是通过 Wi-Fi 直连。
  这个脚本让你在「手表 USB 插着电脑」时，改完 JSON 一条命令就能刷进去，
  调试课表时比来回点手机快得多。

用法：
    python3 tools/push_timetable.py                      # 推 tools/timetable.json
    python3 tools/push_timetable.py path/to/other.json   # 推指定文件
    python3 tools/push_timetable.py -s 6e7d057           # 指定设备（多设备时必填）
    python3 tools/push_timetable.py --wifi 192.168.1.87  # 走 Wi-Fi 直连（不插线也行）
"""

import argparse
import json
import os
import socket
import struct
import subprocess
import sys

PORT = 8899
TIMEOUT = 8.0


def find_adb():
    """优先用用户 SDK 里的 adb，找不到就用 PATH 里的。"""
    home = os.path.expanduser("~")
    for p in (
        os.path.join(home, "Library/Android/platform-tools/adb"),
        "/opt/homebrew/bin/adb",
        "/usr/local/bin/adb",
    ):
        if os.path.exists(p):
            return p
    return "adb"


def adb(args, serial=None):
    cmd = [find_adb()]
    if serial:
        cmd += ["-s", serial]
    cmd += args
    return subprocess.run(cmd, capture_output=True, text=True, timeout=60)


def pick_device(serial):
    """没有指定设备时自动挑：优先真机（非 emulator）。"""
    if serial:
        return serial
    out = adb(["devices"]).stdout
    devices = [
        line.split()[0]
        for line in out.splitlines()[1:]
        if line.strip().endswith("device")
    ]
    real = [d for d in devices if not d.startswith("emulator")]
    if not real and not devices:
        sys.exit("没有找到任何 adb 设备，先确认手表已连接并授权 USB 调试")
    return real[0] if real else devices[0]


def validate(obj):
    """推送前做一次基本校验，避免把手表的课表刷成一堆空数据。"""
    if not isinstance(obj, dict):
        return "JSON 顶层必须是对象"
    if "courses" not in obj:
        return "缺少 courses 字段"
    if "semester" not in obj or "startDate" not in obj.get("semester", {}):
        return "缺少 semester.startDate（第 1 周的周一，格式 yyyy-MM-dd）"
    seen_ids = set()
    for i, c in enumerate(obj["courses"]):
        for key in ("id", "name", "day", "sn", "en", "weeks"):
            if key not in c:
                return f"第 {i + 1} 门课缺少字段 {key}"
        if c["id"] in seen_ids:
            return f"课程 id 重复：{c['id']}"
        seen_ids.add(c["id"])
        if not 1 <= c["day"] <= 7:
            return f"{c['name']} 的 day={c['day']} 不合法（1=周一 … 7=周日）"
        if c["sn"] > c["en"]:
            return f"{c['name']} 的起始节次 {c['sn']} 大于结束节次 {c['en']}"
        if not isinstance(c["weeks"], list):
            return f"{c['name']} 的 weeks 必须是数组，例如 [1,2,3] 或 [1,3,5]"
    return None


def push_to(host, port, payload):
    with socket.create_connection((host, port), timeout=TIMEOUT) as s:
        s.sendall(struct.pack(">I", len(payload)) + payload)
        ack = s.recv(1)
    return bool(ack) and ack[0] == 1


def main():
    ap = argparse.ArgumentParser(description="推送课表 JSON 到手表")
    ap.add_argument("json", nargs="?", default=None, help="课表 JSON 路径")
    ap.add_argument("-s", "--serial", default=None, help="adb 设备序列号")
    ap.add_argument("--wifi", default=None, help="手表 IP（走 Wi-Fi 直连，不用插线）")
    args = ap.parse_args()

    here = os.path.dirname(os.path.abspath(__file__))
    path = args.json or os.path.join(here, "timetable.json")
    if not os.path.exists(path):
        sys.exit(f"找不到课表文件：{path}")

    raw = open(path, "rb").read()
    try:
        obj = json.loads(raw.decode("utf-8"))
    except Exception as e:
        sys.exit(f"JSON 解析失败：{e}")

    err = validate(obj)
    if err:
        sys.exit(f"课表校验不通过：{err}")

    # ---- 通道选择 ----
    if args.wifi:
        host, port = args.wifi, PORT
        print(f"通过 Wi-Fi 推送到 {host}:{port} …")
    else:
        serial = pick_device(args.serial)
        r = adb(["forward", f"tcp:{PORT}", f"tcp:{PORT}"], serial)
        if r.returncode != 0:
            sys.exit(f"adb forward 失败：{r.stderr.strip()}")
        host, port = "127.0.0.1", PORT
        print(f"设备 {serial} 已建立端口转发 → 127.0.0.1:{PORT}")

    try:
        ok = push_to(host, port, raw)
    except Exception as e:
        sys.exit(f"连接手表失败：{e}\n"
                 f"  检查：手表端 App 是否打开过一次（服务在 App 启动时才监听）")

    if ok:
        print(f"✅ 推送成功：{len(obj['courses'])} 门课，手表已刷新")
    else:
        print("❌ 手表拒收（回包 0），通常是 JSON 结构不对")
        sys.exit(1)


if __name__ == "__main__":
    main()
