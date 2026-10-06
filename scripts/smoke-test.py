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


def keyboard_visible():
    # InputMethodManager can retain mInputShown=true after its surface is hidden.
    # Back must only dismiss a visible IME, otherwise it dismisses the app menu.
    windows = adb("shell", "dumpsys", "window", "windows")
    return any("InputMethod}" in block.split("\n", 1)[0] and
               "isVisible=true" in block
               for block in re.split(r"(?m)^  Window #", windows)[1:])


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
    wait_for("Главный экран")


home()
# This scenario uses the initial favorites without user-added widgets.
launcher_nodes = [n for n in hierarchy().iter("node") if n.get("package") == "app.better.launcher"]
assert not any(n.get("content-desc") == "Часы" or re.fullmatch(r"\d{1,2}:\d{2}", n.get("text", ""))
               or re.fullmatch(r"[А-Яа-яЁё]+, \d{1,2} [а-яё]+", n.get("text", "")) for n in launcher_nodes)
print("PASS: home has no built-in clock or date")
density = int(re.findall(r"\d+", adb("shell", "wm", "density"))[-1]) / 160
# Navigate from a real app row: the normal home has no footer controls.
assert not any(n.get("content-desc") in ("Все приложения и поиск", "Экран виджетов", "Настройки лаунчера")
               for n in launcher_nodes)
x, y = center(wait_for("Phone"))
adb("shell", "input", "swipe", str(x), str(y), str(int(x + 90 * density)), str(y), "350")
wait_for("Удерживайте пустое место, чтобы добавить виджет")
page_hint = wait_for("Удерживайте пустое место, чтобы добавить виджет")
launcher_nodes = [n for n in hierarchy().iter("node") if n.get("package") == "app.better.launcher"]
assert not any(n.get("text") == "Виджеты" or n.get("content-desc") in
               ("На главный экран", "Добавить виджет", "Изменить порядок виджетов") for n in launcher_nodes)
x1, y1, x2, y2 = map(int, re.findall(r"\d+", page_hint.get("bounds")))
x, y = (x1 + x2) // 2, y2 + int(24 * density)
adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), "850")
wait_for("Поиск виджетов")
adb("shell", "input", "keyevent", "4")
wait_for("Удерживайте пустое место, чтобы добавить виджет")
adb("shell", "input", "swipe", str(x2 - 20), str(y), str(x1 + 20), str(y), "350")
wait_for("Главный экран")
adb("shell", "input", "keyevent", "KEYCODE_MENU")
wait_for("Домашний экран")
tap("Виджеты")
wait_for("Удерживайте пустое место, чтобы добавить виджет")
adb("shell", "input", "keyevent", "4")
wait_for("Главный экран")
adb("shell", "input", "keyevent", "KEYCODE_MENU")
wait_for("Домашний экран")
tap("Виджеты")
home()
print("PASS: widget page swipe, settings entry, Back and Home navigation")

adb("shell", "input", "keyevent", "KEYCODE_MENU")
wait_for("Домашний экран")
adb("shell", "input", "keyevent", "4")
wait_for("Главный экран")
print("PASS: settings entry and system Back")

adb("shell", "input", "keyevent", "KEYCODE_SEARCH")
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
# A fullscreen IME can cover app rows while accessibility still exposes them.
if keyboard_visible():
    adb("shell", "input", "keyevent", "4")
    wait_for("Camera")
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
wait_for("Удерживайте пустое место, чтобы добавить виджет")
adb("shell", "input", "keyevent", "4")
wait_for("Главный экран")
icon = wait_for("Phone: удерживайте для уведомлений и быстрых действий")
x, y = center(icon)
adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), "850")
wait_for("Добавить приложения")
assert not any("Открыть Phone" in (n.get("text"), n.get("content-desc")) for n in hierarchy().iter("node"))
adb("shell", "input", "keyevent", "4")
print("PASS: rightward app swipe opens widgets; icon hold opens quick actions")

# The menu also has a Phone title; wait for the home window before targeting its row.
wait_for("Главный экран")
x, y = center(wait_for("Phone"))
adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), "850")
wait_for("О приложении")
tree = hierarchy()
assert not any(n.get("content-desc") in ("Переименовать", "Дополнительно") for n in tree.iter("node"))
# All actions belong to one list; short windows / larger fonts can require scrolling.
for label in ("Приложения во всплывающем меню", "Скрыть приложение", "Настройки Better Launcher"):
    for attempt in range(6):
        tree = hierarchy()
        if any(n.get("content-desc") == label for n in tree.iter("node")):
            break
        scroll = next(n for n in tree.iter("node") if n.get("class") == "android.widget.ScrollView")
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", scroll.get("bounds")))
        x = (x1 + x2) // 2
        adb("shell", "input", "swipe", str(x), str(y2 - (y2 - y1) // 5), str(x), str(y1 + (y2 - y1) // 5), "400")
    wait_for(label)
adb("shell", "input", "keyevent", "4")
print("PASS: app long press exposes a flat management list without Additional")
wait_for("Главный экран")

x, y = center(wait_for("Phone"))
adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), "850")
tap("Переименовать Phone")
wait_for("Переименовать Phone")
# A short landscape window can scroll Save below the IME, or use an extract UI.
# Back hides the keyboard; the same rename sheet must remain open.
if keyboard_visible():
    adb("shell", "input", "keyevent", "4")
    wait_for("Переименовать Phone")
wait_for("Сохранить")
assert not any(n.get("content-desc") == "Дополнительно" for n in hierarchy().iter("node"))
assert any(n.get("class") == "android.widget.EditText" and n.get("text") == "Phone"
           for n in hierarchy().iter("node"))
# HOME cancels editing and must preserve the current app name.
home()
wait_for("Phone")
print("PASS: app title switches the same menu to rename; HOME cancels without changing the name")

density = int(re.findall(r"\d+", adb("shell", "wm", "density"))[-1]) / 160
rail = next(n for n in hierarchy().iter("node")
            if n.get("content-desc", "").startswith("Алфавит приложений,"))
_, _, rail_right, rail_bottom = map(int, re.findall(r"\d+", rail.get("bounds")))
x = int(rail_right - 32 * density)  # clear Back and landscape system-bar insets
# Use the measured rail rather than portrait display dimensions after rotation.
adb("shell", "input", "swipe", str(x), str(int(rail_bottom - 48 * density)), str(x), str(int(rail_bottom - 2 * density)), "1000")
wait_for("Поиск приложений")
tree = hierarchy()
assert any(re.match(r"Алфавит приложений, [A-Z#]\.", n.get("content-desc", "")) for n in tree.iter("node"))
assert not any(n.get("text") == "Camera" for n in tree.iter("node"))
print("PASS: release at the bottom below the letters keeps the app list at the final section")
adb("shell", "input", "keyevent", "4")
home()
print("PASS: alphabet gesture opens the app list")

crashes = adb("logcat", "-d", "-s", "AndroidRuntime:E")
assert "Process: app.better.launcher" not in crashes, crashes
print("PASS: no Better Launcher runtime crashes")
