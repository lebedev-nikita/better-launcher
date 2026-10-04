#!/usr/bin/env python3
"""UI smoke checks against a dedicated emulator with Better Launcher installed.

Requires adb and Python 3. Run after setting Better Launcher as this emulator's
default home app. It launches Camera and preserves favorites and widget placement.
"""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
args = parser.parse_args()


def adb(*command):
    return subprocess.check_output(["adb", "-s", args.serial, *command], text=True)


def hierarchy():
    deadline = time.monotonic() + 30
    while True:
        adb("shell", "rm", "-f", "/data/local/tmp/better-smoke.xml")
        result = adb("shell", "uiautomator", "dump", "/data/local/tmp/better-smoke.xml")
        if "dumped to:" in result:
            return ET.fromstring(adb("shell", "cat", "/data/local/tmp/better-smoke.xml"))
        if time.monotonic() >= deadline:
            raise AssertionError("Unable to obtain a fresh UI hierarchy")
        time.sleep(.25)


def find(tree, label):
    for node in tree.iter("node"):
        if label in (node.get("text"), node.get("content-desc"), node.get("hint")):
            return node
    raise AssertionError(f"Missing UI element: {label}")


def wait_for(label):
    deadline = time.monotonic() + 15
    while True:
        try:
            return find(hierarchy(), label)
        except (AssertionError, subprocess.CalledProcessError, ET.ParseError):
            if time.monotonic() >= deadline:
                raise
            time.sleep(.5)


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap(label):
    x, y = center(wait_for(label))
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(.4)


def home():
    adb("shell", "input", "keyevent", "3")
    time.sleep(.5)
    wait_for("Все приложения")


home()
# This scenario uses the initial favorites without user-added widgets.
launcher_nodes = [n for n in hierarchy().iter("node") if n.get("package") == "app.better.launcher"]
assert not any(n.get("content-desc") == "Часы" or re.fullmatch(r"\d{1,2}:\d{2}", n.get("text", ""))
               or re.fullmatch(r"[А-Яа-яЁё]+, \d{1,2} [а-яё]+", n.get("text", "")) for n in launcher_nodes)
print("PASS: home has no built-in clock or date")
scroll = next(n for n in hierarchy().iter("node") if n.get("class") == "android.widget.ScrollView")
x1, y1, x2, y2 = map(int, re.findall(r"\d+", scroll.get("bounds")))
density = int(re.findall(r"\d+", adb("shell", "wm", "density"))[-1]) / 160
y = int(y1 + 12 * density)  # free space above favorites, inside system insets
adb("shell", "input", "swipe", str(int(x2 * .25)), str(y), str(int(x2 * .65)), str(y), "350")
wait_for("Виджеты")
title = wait_for("Виджеты")
x1, y1, x2, y2 = map(int, re.findall(r"\d+", title.get("bounds")))
adb("shell", "input", "swipe", str(x2 - 20), str((y1 + y2) // 2), str(x1 + 20), str((y1 + y2) // 2), "350")
wait_for("Все приложения")
tap("Экран виджетов")
wait_for("Виджеты")
adb("shell", "input", "keyevent", "4")
wait_for("Все приложения")
tap("Экран виджетов")
home()
print("PASS: widget page swipe, button, Back and Home navigation")

tap("Настройки лаунчера")
wait_for("Домашний экран")
adb("shell", "input", "keyevent", "4")
wait_for("Все приложения")
print("PASS: settings entry and system Back")

tap("Все приложения")
wait_for("Поиск приложений")
# Pace injected keys on cold / busy emulators, then wait for the actual query.
for character in "camera":
    adb("shell", "input", "text", character)
    time.sleep(.15)
wait_for("camera")
wait_for("Camera")
tree = hierarchy()
find(tree, "Camera")
assert not any(n.get("text") == "Phone" for n in tree.iter("node"))
print("PASS: app search filters real installed apps")
tap("Camera")
for _ in range(20):
    focus = adb("shell", "dumpsys", "activity", "activities")
    resumed = re.findall(r"topResumedActivity=.*", focus)
    if not resumed:  # Android 12 uses the older dumpsys field name.
        resumed = re.findall(r"mResumedActivity:.*", focus)
    if resumed and "app.better.launcher/" not in resumed[-1]:
        break
    time.sleep(.25)
assert resumed and "app.better.launcher/" not in resumed[-1], resumed
home()
print("PASS: launching an app and returning Home")

x, y = center(wait_for("Phone"))
adb("shell", "input", "swipe", str(x), str(y), str(x + 220), str(y), "350")
wait_for("Открыть Phone")
adb("shell", "input", "keyevent", "4")
print("PASS: horizontal app swipe opens its pop-up")

x, y = center(wait_for("Phone"))
adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), "850")
wait_for("О приложении")
wait_for("Настройки Better Launcher")
assert not any(n.get("content-desc") == "Переименовать" for n in hierarchy().iter("node"))
tap("Дополнительно")
assert not any(n.get("content-desc") == "Переименовать" for n in hierarchy().iter("node"))
# Additional actions can require scrolling on short windows / larger fonts.
tree = hierarchy()
if not any(n.get("content-desc") == "Скрыть приложение" for n in tree.iter("node")):
    size = adb("shell", "wm", "size")
    width, height = map(int, re.findall(r"(\d+)x(\d+)", size)[-1])
    adb("shell", "input", "swipe", str(width // 2), str(int(height * .8)), str(width // 2), str(int(height * .4)), "400")
wait_for("Скрыть приложение")
adb("shell", "input", "keyevent", "4")
print("PASS: app long press exposes management actions")
wait_for("Все приложения")

x, y = center(wait_for("Phone"))
adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), "850")
tap("Переименовать Phone")
wait_for("Переименовать")
wait_for("Сохранить")
assert any(n.get("class") == "android.widget.EditText" and n.get("text") == "Phone"
           for n in hierarchy().iter("node"))
# HOME cancels editing and must preserve the current app name.
home()
wait_for("Phone")
print("PASS: tapping the app title opens rename; HOME cancels without changing the name")

density = int(re.findall(r"\d+", adb("shell", "wm", "density"))[-1]) / 160
rail = next(n for n in hierarchy().iter("node")
            if n.get("content-desc", "").startswith("Алфавит приложений,"))
_, _, rail_right, rail_bottom = map(int, re.findall(r"\d+", rail.get("bounds")))
x = int(rail_right - 32 * density)  # clear Back and landscape system-bar insets
# Use the measured rail rather than portrait display dimensions after rotation.
adb("shell", "input", "swipe", str(x), str(int(rail_bottom - 48 * density)), str(x), str(int(rail_bottom + 48 * density)), "1000")
wait_for("Поиск приложений")
tree = hierarchy()
assert any(n.get("content-desc", "").startswith("Алфавит приложений, #.") for n in tree.iter("node"))
assert not any(n.get("text") == "Camera" for n in tree.iter("node"))
print("PASS: release below the alphabet keeps the app list at the final section")
adb("shell", "input", "keyevent", "4")
home()
print("PASS: alphabet gesture opens the app list")

crashes = adb("logcat", "-d", "-s", "AndroidRuntime:E")
assert "Process: app.better.launcher" not in crashes, crashes
print("PASS: no Better Launcher runtime crashes")
