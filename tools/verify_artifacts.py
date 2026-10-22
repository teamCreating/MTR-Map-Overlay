"""Verify release JAR metadata, runtime mappings, bytecode and shared tests."""
import argparse
import json
from pathlib import Path
import struct
import tomllib
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--loader', choices=('forge', 'fabric', 'all'), default='all')
parser.add_argument('--minecraft', choices=('1.20.1', '1.20.4'), default='1.20.1')
args = parser.parse_args()
legacy = args.minecraft == '1.20.4'
properties = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines()
                  if '=' in line and not line.startswith('#'))
version = properties['mod_version']
for loader in (('forge', 'fabric') if args.loader == 'all' else (args.loader,)):
    base = ROOT if loader == 'forge' else ROOT / 'fabric'
    build_dir = base / ('build/mc1.20.4' if legacy else 'build')
    artifact = build_dir / 'libs' / f'CRTools-MTR-Map-Overlay-{loader}-mc{args.minecraft}-{version}.jar'
    with zipfile.ZipFile(artifact) as jar:
        names = set(jar.namelist())
        assert jar.read('LICENSE') == (ROOT / 'LICENSE').read_bytes(), 'Missing original license'
        classes = [name for name in names if name.endswith('.class')]
        assert classes, 'No classes packaged'
        forbidden = b'net/fabricmc/' if loader == 'forge' else b'net/minecraftforge/'
        for name in classes:
            data = jar.read(name)
            assert b'com/lx862/mtrmap' not in data and b'com.lx862.mtrmap' not in data, name
            assert name.startswith('io/github/teamcreating/mtrmap/'), f'Bundled third-party class: {name}'
            assert struct.unpack('>H', data[6:8])[0] == 61, f'Not Java 17 bytecode: {name}'
            assert b'net/neoforged/' not in data and forbidden not in data, f'Wrong loader reference: {name}'
        assert not any(name.endswith('.jar') for name in names), 'Unexpected bundled dependency'
        assert json.loads(jar.read('pack.mcmeta'))['pack']['pack_format'] == (22 if legacy else 15)
        assert b'org/mtr/mod/Init' in jar.read('io/github/teamcreating/mtrmap/mixin/MTRAccessorMixin.class')
        if loader == 'forge':
            metadata = tomllib.loads(jar.read('META-INF/mods.toml').decode())
            assert metadata['mods'][0]['modId'] == 'mtrmap'
            assert metadata['mods'][0]['version'] == version
            deps = {d['modId']: d for d in metadata['dependencies']['mtrmap']}
            assert deps['minecraft']['versionRange'] == f'[{args.minecraft}]'
            assert deps['mtr']['versionRange'] == '[4.0.5,4.1)'
            assert deps['journeymap']['versionRange'] == ('[5.10.0,)' if legacy else '[1.20.1-6.0.6,)')
            assert deps['forge']['versionRange'] == ('[49,)' if legacy else '[47,)')
            assert 'mtrmap.refmap.json' in names
            assert 'MixinConfigs: mtrmap.mixins.json' in jar.read('META-INF/MANIFEST.MF').decode()
            render_name, click_name = b'm_88315_', b'm_6375_'
        else:
            metadata = json.loads(jar.read('fabric.mod.json'))
            assert metadata['id'] == 'mtrmap' and metadata['version'] == version
            assert metadata['depends']['minecraft'] == args.minecraft
            assert metadata['depends']['java'] == '>=17'
            assert metadata['depends']['mtr'] == '>=4.0.5 <4.1'
            assert metadata['entrypoints']['journeymap'] == ['io.github.teamcreating.mtrmap.integration.journeymap.MTRJourneyMapPlugin']
            assert 'META-INF/mods.toml' not in names
            render_name, click_name = b'method_25394', b'method_25402'
        hook = jar.read('io/github/teamcreating/mtrmap/mixin/client/xaero/XaeroWorldMapMixin.class')
        assert render_name in hook and click_name in hook, 'Missing production Xaero hooks'
        config_name = 'mtrmap.mixins.json' if loader == 'forge' else 'mtrmap.fabric.mixins.json'
        config = json.loads(jar.read(config_name))
        assert config['package'] == 'io.github.teamcreating.mtrmap.mixin'
        assert config['plugin'].replace('.', '/') + '.class' in names
        for mixin in config.get('mixins', []) + config.get('client', []):
            assert (config['package'] + '.' + mixin).replace('.', '/') + '.class' in names, mixin
        for name in (name for name in names if name.endswith(('.json', '.toml'))):
            assert b'com.lx862.mtrmap' not in jar.read(name) and b'com/lx862/mtrmap' not in jar.read(name), name
        mixins = config['client']
        assert ('client.journeymap.JourneyMapFullscreenMixin' in mixins) == legacy
        plugin = jar.read('io/github/teamcreating/mtrmap/integration/journeymap/MTRJourneyMapPlugin.class')
        assert (b'journeymap/client/api/ClientPlugin' if legacy else b'journeymap/api/v2/common/JourneyMapPlugin') in plugin
        if legacy:
            for name in classes:
                assert b'journeymap/api/v2/' not in jar.read(name), f'Wrong JourneyMap API: {name}'
            assert b'drawMap' in jar.read('io/github/teamcreating/mtrmap/mixin/client/journeymap/JourneyMapFullscreenMixin.class')
    results = [ET.parse(p).getroot() for p in (build_dir / 'test-results/test').glob('TEST-*.xml')]
    assert results, 'Missing test results'
    assert all(int(r.attrib['failures']) == 0 and int(r.attrib['errors']) == 0 for r in results)
    passed = sum(int(r.attrib['tests']) - int(r.attrib['skipped']) for r in results)
    assert passed >= 89, f'Incomplete shared test run: {passed}'
    print(f'{loader}: metadata, Java 17, loader isolation, Xaero hooks and {passed} tests verified: {artifact}')
