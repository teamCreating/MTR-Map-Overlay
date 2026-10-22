"""Shared CI contract. Python 3.12+, standard library only."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import struct
import tomllib
import xml.etree.ElementTree as ET
import zipfile

MAIN = 'main'
COMPAT = 'compat/mtr-4.0-mc-1.20'
PACKAGE = 'io.github.teamcreating.mtrmap'
PREFIX = PACKAGE.replace('.', '/') + '/'
OLD = (b'com/lx862/mtrmap', b'com.lx862.mtrmap')
TARGETS = {
    '1.21.1-neoforge': (MAIN, '1.21.1', 'neoforge'),
    '1.21.1-fabric': (MAIN, '1.21.1', 'fabric'),
    '1.20.1-forge': (COMPAT, '1.20.1', 'forge'),
    '1.20.1-fabric': (COMPAT, '1.20.1', 'fabric'),
    '1.20.4-forge': (COMPAT, '1.20.4', 'forge'),
    '1.20.4-fabric': (COMPAT, '1.20.4', 'fabric'),
}
REQUIRED = (
    'MTRMap', 'CommandRegistration', 'train/CoreTrainAdapter', 'train/ServerTrainStream',
    'train/ClientTrainStream', 'train/TrainViewportIndex', 'train/TrainFrameCodec',
    'mapdata/NativeRailCurve', 'mapdata/PlanarMapScene', 'mapdata/TrainCarGeometry',
    'integration/TrainMapTooltip',
)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha256(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def properties(root):
    return dict(line.strip().split('=', 1) for line in (Path(root) / 'gradle.properties').read_text().splitlines()
                if '=' in line and not line.lstrip().startswith('#'))


def version_ok(version):
    require(bool(re.fullmatch(r'[0-9]+(?:\.[0-9]+)+(?:[-+][A-Za-z0-9.-]+)?', version)), 'Invalid version')
    return version


def sha_ok(sha):
    require(bool(re.fullmatch(r'[0-9a-f]{40}', sha)), 'Expected full lowercase commit SHA')
    return sha


def targets_for(branch):
    require(branch in (MAIN, COMPAT), f'Unsupported branch: {branch}')
    return [key for key, value in TARGETS.items() if value[0] == branch]


def layout(root, target):
    branch, mc, loader = TARGETS[target]
    props = properties(root)
    version = version_ok(props['mod_version'])
    require(props['maven_group'] == 'io.github.teamcreating', 'Unexpected Maven group')
    base = Path(root) / ('fabric' if loader == 'fabric' else '')
    build = base / ('build/mc1.20.4' if mc == '1.20.4' else 'build')
    suffix = ('-fabric' if loader == 'fabric' else '') if branch == MAIN else f'-{loader}-mc{mc}'
    jar = build / 'libs' / f'{props["archives_base_name"]}{suffix}-{version}.jar'
    filename = f'CRTools-MTR-Map-Overlay-{version}-MC{mc}-MTR{props["mtr_version"]}-{loader}.jar'
    command = ['./fabric/gradlew', '-p', 'fabric', 'build'] if loader == 'fabric' else ['./gradlew', 'build']
    if mc == '1.20.4':
        command.append('-Pminecraft_version=1.20.4')
    command.extend(['--no-daemon', '--stacktrace'])
    return build, jar, filename, command


def verify_jar(path, target, version, license_bytes):
    branch, mc, loader = TARGETS[target]
    major = 65 if branch == MAIN else 61
    with zipfile.ZipFile(path) as jar:
        names = set(jar.namelist())
        require(len(names) == len(jar.namelist()), 'Duplicate ZIP entries')
        require(jar.read('LICENSE').replace(b'\r\n', b'\n') == license_bytes.replace(b'\r\n', b'\n'),
                'Missing or altered original LICENSE')
        classes = [n for n in names if n.endswith('.class')]
        require(bool(classes), 'No classes packaged')
        require(not any(n.endswith('.jar') for n in names), 'Bundled dependency JAR')
        require(all(PREFIX + c + '.class' in names for c in REQUIRED), 'Missing core classes')
        forbidden = {'fabric': (b'net/minecraftforge/', b'net/neoforged/'),
                     'forge': (b'net/fabricmc/', b'net/neoforged/'),
                     'neoforge': (b'net/fabricmc/', b'net/minecraftforge/')}[loader]
        for name in classes:
            data = jar.read(name)
            require(name.startswith(PREFIX), f'Unexpected class namespace: {name}')
            require(data[:4] == b'\xca\xfe\xba\xbe' and len(data) >= 8, f'Invalid class: {name}')
            require(struct.unpack('>H', data[6:8])[0] == major, f'Wrong bytecode: {name}')
            require(not any(x in data for x in OLD + forbidden), f'Forbidden namespace: {name}')
            if mc == '1.20.4':
                require(b'journeymap/api/v2/' not in data, f'Wrong JourneyMap API: {name}')
        for name in names:
            if name.endswith(('.json', '.toml', '.mf')):
                require(not any(x in jar.read(name) for x in OLD), f'Old metadata namespace: {name}')
        def class_exists(value):
            require(value.startswith(PACKAGE + '.'), f'Wrong entrypoint namespace: {value}')
            require(value.replace('.', '/') + '.class' in names, f'Missing entrypoint: {value}')
        if loader == 'fabric':
            meta = json.loads(jar.read('fabric.mod.json'))
            require(meta['id'] == 'mtrmap' and meta['version'] == version, 'Wrong Fabric ID/version')
            require(meta['depends']['minecraft'] == mc, 'Wrong Minecraft dependency')
            require(meta['depends']['java'] == ('>=21' if branch == MAIN else '>=17'), 'Wrong Java dependency')
            require(meta['entrypoints']['journeymap'] == [PACKAGE + '.integration.journeymap.MTRJourneyMapPlugin'],
                    'Wrong JourneyMap entrypoint')
            for entries in meta['entrypoints'].values():
                for entry in entries:
                    class_exists(entry)
            require('META-INF/mods.toml' not in names and 'META-INF/neoforge.mods.toml' not in names,
                    'Wrong loader metadata')
            if branch == COMPAT:
                require(meta['depends']['mtr'] == '>=4.0.5 <4.1', 'Wrong MTR range')
            config_name = 'mtrmap.fabric.mixins.json'
        else:
            meta_name = 'META-INF/' + ('mods.toml' if loader == 'forge' else 'neoforge.mods.toml')
            meta = tomllib.loads(jar.read(meta_name).decode())
            require(meta['mods'][0]['modId'] == 'mtrmap' and meta['mods'][0]['version'] == version,
                    'Wrong mod ID/version')
            deps = {d['modId']: d for d in meta['dependencies']['mtrmap']}
            require(deps['minecraft']['versionRange'] == (f'[{mc}]' if branch == COMPAT else '[1.21.1,1.21.2)'),
                    'Wrong Minecraft range')
            require('fabric.mod.json' not in names, 'Wrong loader metadata')
            if branch == COMPAT:
                require(deps['mtr']['versionRange'] == '[4.0.5,4.1)', 'Wrong MTR range')
                require(deps['forge']['versionRange'] == ('[49,)' if mc == '1.20.4' else '[47,)'), 'Wrong Forge range')
                require(deps['journeymap']['versionRange'] == ('[5.10.0,)' if mc == '1.20.4' else '[1.20.1-6.0.6,)'),
                        'Wrong JourneyMap range')
                require('mtrmap.refmap.json' in names, 'Missing production refmap')
            else:
                require(deps['mtr']['versionRange'] == '[4.1.0-beta.2,)', 'Wrong MTR range')
            config_name = 'mtrmap.mixins.json'
            require(b'MixinConfigs: mtrmap.mixins.json' in jar.read('META-INF/MANIFEST.MF'), 'Missing Mixin manifest')
        config = json.loads(jar.read(config_name))
        require(config['package'] == PACKAGE + '.mixin', 'Wrong Mixin namespace')
        class_exists(config['plugin'])
        for mixin in config.get('mixins', []) + config.get('client', []):
            class_exists(config['package'] + '.' + mixin)
        require('client.xaero.TrainMapPauseMixin' in config['client'], 'Missing pause hook')
        if branch == COMPAT:
            require('SidingVehiclesAccessor' in config['mixins'], 'Missing legacy train accessor')
            require(('client.journeymap.JourneyMapFullscreenMixin' in config['client']) == (mc == '1.20.4'),
                    'Wrong legacy JourneyMap mixin')
            require(b'org/mtr/mod/Init' in jar.read(PREFIX + 'mixin/MTRAccessorMixin.class'), 'Wrong MTR accessor')
        hooks = jar.read(PREFIX + 'mixin/client/xaero/XaeroWorldMapMixin.class')
        expected = (b'method_25394', b'method_25402') if loader == 'fabric' else (b'm_88315_', b'm_6375_')
        if loader != 'neoforge':
            require(all(x in hooks for x in expected), 'Missing production Xaero hooks')
        plugin = jar.read(PREFIX + 'integration/journeymap/MTRJourneyMapPlugin.class')
        require((b'journeymap/client/api/ClientPlugin' if mc == '1.20.4' else
                 b'journeymap/api/v2/common/JourneyMapPlugin') in plugin, 'Wrong JourneyMap plugin API')
        require(b'trainCars' in jar.read(PREFIX + 'CommandRegistration.class'), 'Missing train command')
        require(json.loads(jar.read('pack.mcmeta'))['pack']['pack_format'] ==
                (34 if branch == MAIN else 22 if mc == '1.20.4' else 15), 'Wrong pack format')


def verify_tests(directory, minimum):
    reports = list(Path(directory).glob('TEST-*.xml'))
    require(bool(reports), 'Missing JUnit reports')
    passed = skipped = 0
    seen = set()
    for report in reports:
        suite = ET.parse(report).getroot()
        require(int(suite.get('failures', 0)) == 0 and int(suite.get('errors', 0)) == 0,
                f'Failed JUnit suite: {report.name}')
        cases = suite.findall('testcase')
        require(int(suite.get('tests', 0)) == len(cases), 'Incomplete test report')
        for case in cases:
            classname = case.get('classname', '')
            require(classname.startswith(PACKAGE + '.'), 'Wrong JUnit namespace')
            identity = (classname, case.get('name'))
            require(identity not in seen, 'Duplicate test case')
            seen.add(identity)
            require(case.find('failure') is None and case.find('error') is None, 'Failed test case')
            if case.find('skipped') is not None:
                require(classname == PACKAGE + '.TestWorldGeneratorTest', f'Unexpected skipped test: {classname}')
                skipped += 1
            else:
                passed += 1
    require(passed >= minimum, f'Incomplete test suite: {passed} < {minimum}')
    return {'passed': passed, 'skipped': skipped, 'reports': len(reports)}


def output(**values):
    filename = os.environ.get('GITHUB_OUTPUT')
    if filename:
        with open(filename, 'a', encoding='utf-8') as stream:
            for key, value in values.items():
                require('\n' not in str(value), 'Multiline Actions output')
                stream.write(f'{key}={value}\n')
    else:
        print(json.dumps(values))


def summary(text):
    filename = os.environ.get('GITHUB_STEP_SUMMARY')
    if filename:
        with open(filename, 'a', encoding='utf-8') as stream:
            stream.write(text + '\n')
