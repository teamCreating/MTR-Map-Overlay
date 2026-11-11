"""Check that an opt-in QA mod cannot shadow production Mixin resources."""
import argparse
import json
from pathlib import Path
import re
import tomllib
import zipfile


def verify(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        forbidden = [name for name in names if name.startswith('mtrmap')
                     and name.endswith(('.mixins.json', '.refmap.json'))]
        if forbidden:
            raise ValueError(f'{path}: production Mixin resources: {forbidden}')
        manifest = archive.read('META-INF/MANIFEST.MF').decode('utf-8')
        if re.search(r'^MixinConfigs:', manifest, re.MULTILINE | re.IGNORECASE):
            raise ValueError(f'{path}: production Mixin manifest registration')
        classes = [name for name in names if name.endswith('.class')]
        if not classes or any(not name.startswith('io/github/teamcreating/mtrmap/qa/') for name in classes):
            raise ValueError(f'{path}: unexpected implementation classes')
        if 'fabric.mod.json' in names:
            identifiers = {json.loads(archive.read('fabric.mod.json'))['id']}
        else:
            metadata = ('META-INF/neoforge.mods.toml' if 'META-INF/neoforge.mods.toml' in names
                        else 'META-INF/mods.toml')
            identifiers = {mod['modId'] for mod in tomllib.loads(archive.read(metadata).decode())['mods']}
            if 'pack.mcmeta' not in names:
                raise ValueError(f'{path}: missing native resource-pack metadata')
        if identifiers != {'mtrmapqa'}:
            raise ValueError(f'{path}: unexpected mod identities: {identifiers}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jars', nargs='+', type=Path)
    for jar in parser.parse_args().jars:
        verify(jar)
        print(f'{jar}: isolated QA artifact OK')
