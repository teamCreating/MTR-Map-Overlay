"""Inspect the six 1.5.2 artifacts after building both source worktrees."""
import argparse
import json
from pathlib import Path
import struct
import tomllib
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--compat', type=Path, default=ROOT.parent / 'mtrmap-mc120')
args = parser.parse_args()
targets = [
    ('neoforge', '1.21.1', ROOT / 'build', 'CRTools-MTR-Map-Overlay-1.5.2.jar', 65),
    ('fabric', '1.21.1', ROOT / 'fabric/build', 'CRTools-MTR-Map-Overlay-fabric-1.5.2.jar', 65),
]
for mc in ('1.20.1', '1.20.4'):
    for loader in ('forge', 'fabric'):
        base = args.compat if loader == 'forge' else args.compat / 'fabric'
        build = base / ('build' if mc == '1.20.1' else 'build/mc1.20.4')
        targets.append((loader, mc, build, f'CRTools-MTR-Map-Overlay-{loader}-mc{mc}-1.5.2.jar', 61))
required = (
    'train/CoreTrainAdapter', 'train/ServerTrainStream', 'train/ClientTrainStream',
    'train/TrainViewportIndex', 'train/TrainFrameCodec', 'mapdata/NativeRailCurve',
    'mapdata/PlanarMapScene', 'mapdata/TrainCarGeometry', 'integration/TrainMapTooltip',
)
for loader, mc, build, name, major in targets:
    artifact = build / 'libs' / name
    with zipfile.ZipFile(artifact) as jar:
        names = set(jar.namelist())
        assert all(f'com/lx862/mtrmap/{c}.class' in names for c in required), artifact
        assert b'trainCars' in jar.read('com/lx862/mtrmap/CommandRegistration.class'), 'Missing train command'
        assert not any(n.endswith('.jar') for n in names), 'Bundled dependency'
        for n in (n for n in names if n.endswith('.class')):
            data = jar.read(n)
            assert n.startswith('com/lx862/mtrmap/'), n
            assert struct.unpack('>H', data[6:8])[0] == major, n
            forbidden = {'fabric': (b'net/minecraftforge/', b'net/neoforged/'),
                         'forge': (b'net/fabricmc/', b'net/neoforged/'),
                         'neoforge': (b'net/fabricmc/', b'net/minecraftforge/')}[loader]
            assert not any(f in data for f in forbidden), n
        if loader == 'fabric':
            meta = json.loads(jar.read('fabric.mod.json'))
            assert meta['id'] == 'mtrmap' and meta['version'] == '1.5.2'
            assert meta['depends']['minecraft'] == mc
            assert meta['entrypoints']['journeymap'] == ['com.lx862.mtrmap.integration.journeymap.MTRJourneyMapPlugin']
            config = 'mtrmap.fabric.mixins.json'
            hook = jar.read('com/lx862/mtrmap/mixin/client/xaero/XaeroWorldMapMixin.class')
            assert b'method_25394' in hook and b'method_25402' in hook
        else:
            config = 'mtrmap.mixins.json'
            meta = tomllib.loads(jar.read('META-INF/' + ('mods.toml' if loader == 'forge' else 'neoforge.mods.toml')).decode())
            assert meta['mods'][0]['modId'] == 'mtrmap' and meta['mods'][0]['version'] == '1.5.2'
            deps = {d['modId']: d for d in meta['dependencies']['mtrmap']}
            assert deps['minecraft']['versionRange'] == (f'[{mc}]' if loader == 'forge' else '[1.21.1,1.21.2)')
        mixins = json.loads(jar.read(config))
        assert 'client.xaero.TrainMapPauseMixin' in mixins['client']
        if mc != '1.21.1':
            assert 'SidingVehiclesAccessor' in mixins['mixins']
            assert 'com/lx862/mtrmap/mixin/SidingVehiclesAccessor.class' in names
    # Main Fabric shares main NeoForge tests instead of running a duplicate suite.
    tests_build = ROOT / 'build' if mc == '1.21.1' else build
    results = [ET.parse(p).getroot() for p in (tests_build / 'test-results/test').glob('TEST-*.xml')]
    assert results and all(int(r.get('failures', 0)) == 0 and int(r.get('errors', 0)) == 0 for r in results)
    passed = sum(int(r.get('tests', 0)) - int(r.get('skipped', 0)) for r in results)
    assert passed >= (86 if mc == '1.21.1' else 89), passed
    print(f'{mc} {loader}: version, bytecode, loader isolation, train/curve classes, entrypoints and {passed} shared tests OK')
