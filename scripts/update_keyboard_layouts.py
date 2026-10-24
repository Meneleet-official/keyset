"""Refresh the bundled keycap labels from the Unicode CLDR 43 keyboard archive."""
from pathlib import Path
import json, re, urllib.request, xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor

BASE = 'https://raw.githubusercontent.com/unicode-org/cldr/release-43/'
API = 'https://api.github.com/repos/unicode-org/cldr/contents/keyboards/windows?ref=release-43'
def fetch(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'Keyset-keyboard-data'})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()

files = {entry['name'] for entry in json.loads(fetch(API))}
requested = sorted(name.removesuffix('-t-k0-windows.xml') for name in files if name.endswith('-t-k0-windows.xml'))
physical = dict(zip(
    ['E00'] + ['E%02d' % i for i in range(1, 13)] + ['D%02d' % i for i in range(1, 13)]
        + ['C%02d' % i for i in range(1, 13)] + ['B%02d' % i for i in range(1, 11)],
    ['grave'] + list('1234567890') + ['minus', 'equal'] + list('qwertyuiop')
        + ['left_bracket', 'right_bracket'] + list('asdfghjkl') + ['semicolon', 'apostrophe', 'backslash']
        + list('zxcvbnm') + ['comma', 'period', 'slash']))
def decode(value):
    value = re.sub(r'\\u\{([0-9A-Fa-f]+)\}', lambda m: chr(int(m.group(1), 16)), value)
    upper = value.upper()
    return upper if len(upper) == len(value) else value

def load(locale):
    name = locale + '-t-k0-windows.xml'
    if locale == 'en-dvorak': name = 'en-t-k0-windows-dvorak.xml'
    if locale == 'pl': name = 'pl-t-k0-windows-extended.xml'
    root = ET.fromstring(fetch(BASE + 'keyboards/windows/' + name))
    keymap = root.find('keyMap')
    labels = {physical[node.attrib['iso']]: decode(node.attrib['to']) for node in keymap.findall('map')
              if node.attrib.get('iso') in physical}
    return locale.lower(), {'name': root.find('names/name').attrib['value'], 'labels': labels}

with ThreadPoolExecutor(max_workers=8) as pool:
    layouts = dict(pool.map(load, requested + ['en-dvorak']))

target = Path('common/src/client/resources/assets/keyset/keyboard_layouts.json')
target.write_text(json.dumps({'source': BASE + 'keyboards/windows/', 'layouts': layouts}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
Path('LICENSE-KEYBOARD-DATA.txt').write_bytes(fetch(BASE + 'unicode-license.txt'))
print('Bundled', len(layouts), 'layouts; Polish:', layouts['pl']['name'])
