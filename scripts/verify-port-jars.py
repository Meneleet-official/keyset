"""Check collected multi-version JAR metadata and Java targets without starting Minecraft."""
import argparse
import json
import struct
from pathlib import Path
from zipfile import ZipFile

parser = argparse.ArgumentParser()
parser.add_argument('versions', nargs='*', default=['26.2', '1.21.11', '1.21.1', '1.20.1'])
args = parser.parse_args()
repository = Path(__file__).resolve().parents[1]
targets = {'26.2': 25, '1.21.11': 21, '1.21.1': 21, '1.20.1': 17}
for version in args.versions:
    java = targets[version]
    for loader in ('fabric', 'neoforge'):
        name = f'keyset-{loader}-1.2.1+mc{version}.jar'
        path = repository / 'build/mods' / version / name
        with ZipFile(path) as archive:
            names = set(archive.namelist())
            for required in ('dev/keyset/client/KeysetScreen.class',
                             'dev/keyset/client/ResourcePackThemes.class',
                             'dev/keyset/profile/ThemeStore.class',
                             'assets/keyset/icon.png', 'keyset.mixins.json', 'LICENSE'):
                assert required in names, f'{name}: missing {required}'
            mixins = json.loads(archive.read('keyset.mixins.json'))
            assert mixins['compatibilityLevel'] == f'JAVA_{java}', f'{name}: wrong mixin Java level'
            class_versions = [struct.unpack('>H', archive.read(entry)[6:8])[0]
                              for entry in names if entry.endswith('.class')]
            assert class_versions and max(class_versions) <= java + 44, f'{name}: Java bytecode too new'
            if loader == 'fabric':
                metadata = json.loads(archive.read('fabric.mod.json'))
                assert metadata['version'] == f'1.2.1+mc{version}'
                assert version in str(metadata['depends']['minecraft'])
                assert metadata['environment'] == 'client'
            else:
                metadata_path = 'META-INF/mods.toml' if version == '1.20.1' else 'META-INF/neoforge.mods.toml'
                metadata = archive.read(metadata_path).decode('utf-8')
                assert f'1.2.1+mc{version}' in metadata, f'{name}: wrong version metadata'
                if version == '1.20.1':
                    manifest = archive.read('META-INF/MANIFEST.MF').decode('utf-8')
                    assert 'MixinConfigs:' in manifest, f'{name}: missing legacy mixin registration'
            strings = json.loads(archive.read('assets/keyset/lang/en_us.json'))
            assert 'keyset.themes_resource_badge' in strings
        print(f'OK {name}: Java {java}, {path.stat().st_size:,} bytes')
