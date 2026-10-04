#!/usr/bin/env python3
"""UI smoke checks against a dedicated emulator with Better Launcher installed.

Requires adb and Python 3. Run after setting Better Launcher as this emulator's
default home app. It launches Camera and changes no launcher preferences.
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
    adb("shell", "uiautomator", "dump", "/sdcard/better-smoke.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/better-smoke.xml"))


def find(tree, label):
    for node in tree.iter("node"):
        if label in (node.get("text"), node.get("content-desc"), node.get("hint")):
            return node
    raise AssertionError(f"Missing UI element: {label}")


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap(label):
    x, y = center(find(hierarchy(), label))
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(.4)


def home():
    adb("shell", "input", "keyevent", "3")
    time.sleep(.5)
    find(hierarchy(), "Все приложения")


home()
tap("Настройки лаунчера")
find(hierarchy(), "Домашний экран")
adb("shell", "input", "keyevent", "4")
find(hierarchy(), "Все приложения")
print("PASS: settings entry and system Back")

tap("Все приложения")
adb("shell", "input", "text", "camera")
tree = hierarchy()
find(tree, "Camera")
assert not any(n.get("text") == "Phone" for n in tree.iter("node"))
print("PASS: app search filters real installed apps")
tap("Camera")
focus = adb("shell", "dumpsys", "activity", "activities")
resumed = re.findall(r"topResumedActivity=.*", focus)
if not resumed:  # Android 12 uses the older dumpsys field name.
    resumed = re.findall(r"mResumedActivity:.*", focus)
assert resumed and "app.better.launcher/" not in resumed[-1], resumed
home()
print("PASS: launching an app and returning Home")

x, y = center(find(hierarchy(), "Phone"))
adb("shell", "input", "swipe", str(x), str(y), str(x + 220), str(y), "350")
find(hierarchy(), "Открыть Phone")
adb("shell", "input", "keyevent", "4")
print("PASS: horizontal app swipe opens its pop-up")

x, y = center(find(hierarchy(), "Phone"))
adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), "850")
find(hierarchy(), "Переименовать")
find(hierarchy(), "Скрыть приложение")
adb("shell", "input", "keyevent", "4")
print("PASS: app long press exposes management actions")

size = adb("shell", "wm", "size")
width, height = map(int, re.findall(r"(\d+)x(\d+)", size)[-1])
density = int(re.findall(r"\d+", adb("shell", "wm", "density"))[-1]) / 160
x = int(width - 30 * density)  # clear the OS Back gesture edge
adb("shell", "input", "swipe", str(x), str(int(height * .4)), str(x), str(int(height * .75)), "600")
find(hierarchy(), "Поиск приложений")
adb("shell", "input", "keyevent", "4")
home()
print("PASS: alphabet gesture opens the app list")

crashes = adb("logcat", "-d", "-s", "AndroidRuntime:E")
assert "Process: app.better.launcher" not in crashes, crashes
print("PASS: no Better Launcher runtime crashes")
