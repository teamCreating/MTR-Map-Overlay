"""Build, validate and seal immutable CI/candidate bundles."""
from __future__ import annotations

import argparse
import base64
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

from core import (COMPAT, MAIN, PACKAGE, TARGETS, layout, output, properties, require,
                  sha256, sha_ok, summary, targets_for, verify_jar, verify_tests, version_ok, write_json)
from github_api import api, quote, repository


def read_source(repo, sha, path):
    value = api(f'repos/{repo}/contents/{quote(path)}?ref={sha}')
    return base64.b64decode(value['content']).decode()


def resolve(args):
    repo = repository()
    sources = {}
    for branch, given in ((MAIN, args.main_sha), (COMPAT, args.compat_sha)):
        head = api(f'repos/{repo}/git/ref/heads/{quote(branch)}')['object']['sha']
        sha = sha_ok(given) if given else sha_ok(head)
        comparison = api(f'repos/{repo}/compare/{sha}...{head}')
        require(comparison['status'] in ('ahead', 'identical'), f'{sha} is not an ancestor of {branch}')
        sources['main' if branch == MAIN else 'compat'] = sha
    versions = {}
    for key, sha in sources.items():
        text = read_source(repo, sha, 'gradle.properties')
        props = dict(x.strip().split('=', 1) for x in text.splitlines() if '=' in x and not x.lstrip().startswith('#'))
        require(props['maven_group'] == 'io.github.teamcreating', f'{key}: namespace migration not committed')
        versions[key] = version_ok(props['mod_version'])
    if args.version:
        version_ok(args.version)
        require(all(x == args.version for x in versions.values()), 'Source versions do not match requested release')
        notes = read_source(repo, sources['main'], f'docs/releases/v{args.version}.md')
        require(bool(notes.strip()), 'Missing release notes')
        Path('candidate-notes.md').write_text(notes, encoding='utf-8')
    write_json('source-plan.json', {'source': sources, 'versions': versions})
    matrix = [{'target': target, 'sha': sources['main' if value[0] == MAIN else 'compat']}
              for target, value in TARGETS.items()]
    output(main_sha=sources['main'], compat_sha=sources['compat'], matrix=json.dumps({'include': matrix}, separators=(',', ':')))
    summary('Sources locked: ' + ', '.join(f'`{key}: {sha}`' for key, sha in sources.items()))


def branch_plan(args):
    targets = targets_for(args.branch)
    output(matrix=json.dumps({'target': targets}, separators=(',', ':')))


def build(args):
    root = Path(args.root).resolve()
    target = args.target
    branch, mc, loader = TARGETS[target]
    sha = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
    sha_ok(sha)
    build_dir, jar, filename, command = layout(root, target)
    if os.name == 'nt':
        command[0] = command[0].replace('gradlew', 'gradlew.bat')
    else:
        (root / command[0]).chmod(0o755)
    diagnostics = Path(args.out).resolve().parent / 'build.log'
    diagnostics.parent.mkdir(parents=True, exist_ok=True)
    with diagnostics.open('w', encoding='utf-8') as log:
        process = subprocess.Popen(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   encoding='utf-8', errors='replace')
        for line in process.stdout:
            print(line, end='', flush=True)
            log.write(line)
        require(process.wait() == 0, f'Gradle failed: {target}')
    package_target(root, target, args.out, sha)


def package_target(root, target, out, sha=None):
    root = Path(root).resolve()
    sha = sha or subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
    branch, mc, loader = TARGETS[target]
    props = properties(root)
    build_dir, jar, filename, _ = layout(root, target)
    verify_jar(jar, target, props['mod_version'], (root / 'LICENSE').read_bytes())
    tests = {'shared_with': '1.21.1-neoforge'} if target == '1.21.1-fabric' else verify_tests(
        build_dir / 'test-results/test', 86 if branch == MAIN else 89)
    destination = Path(out) / target
    destination.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(jar, destination / filename)
    evidence = destination / 'evidence'
    if 'shared_with' not in tests:
        evidence.mkdir(exist_ok=True)
        for report in (build_dir / 'test-results/test').glob('TEST-*.xml'):
            shutil.copyfile(report, evidence / report.name)
        html = build_dir / 'reports/tests/test'
        if html.exists():
            shutil.copytree(html, evidence / 'html', dirs_exist_ok=True)
    record = {'target': target, 'source_sha': sha_ok(sha), 'version': props['mod_version'],
              'mtr_version': props['mtr_version'], 'jar': filename, 'sha256': sha256(jar),
              'size': jar.stat().st_size, 'tests': tests, 'run_id': os.environ.get('GITHUB_RUN_ID', 'local'),
              'attempt': int(os.environ.get('GITHUB_RUN_ATTEMPT', '1')),
              'tooling_sha': os.environ.get('GITHUB_WORKFLOW_SHA', 'local')}
    write_json(destination / 'target.json', record)
    print(f'{target}: JAR and test evidence verified')


def collect(incoming, expected, sources):
    found = {}
    for path in Path(incoming).rglob('target.json'):
        record = json.loads(path.read_text())
        target = record['target']
        require(target in expected, f'Unexpected target artifact: {target}')
        key = 'main' if TARGETS[target][0] == MAIN else 'compat'
        if record['source_sha'] != sources[key]:
            continue  # A full workflow rerun can have artifacts from an older resolved source pair.
        previous = found.get(target)
        if previous:
            require(record['attempt'] != previous[1]['attempt'], f'Duplicate target artifact: {target}')
        if not previous or record['attempt'] > previous[1]['attempt']:
            found[target] = (path.parent, record)
    require(set(found) == set(expected), f'Incomplete target matrix: {set(expected) - set(found)}')
    return found


def verify_record(directory, record, license_bytes):
    target = record['target']
    require(target in TARGETS, 'Unknown target')
    sha_ok(record['source_sha'])
    version_ok(record['version'])
    branch, mc, loader = TARGETS[target]
    expected_name = f'CRTools-MTR-Map-Overlay-{record["version"]}-MC{mc}-MTR{record["mtr_version"]}-{loader}.jar'
    require(record['mtr_version'] == ('4.1.0-beta.2' if branch == MAIN else '4.0.5'), 'Unexpected tested MTR')
    require(record['jar'] == expected_name, 'Unexpected artifact filename')
    jar = Path(directory) / record['jar']
    require(jar.is_file() and not jar.is_symlink(), 'Missing JAR')
    require(jar.stat().st_size == record['size'] and sha256(jar) == record['sha256'], 'JAR checksum mismatch')
    verify_jar(jar, target, record['version'], license_bytes)
    if target != '1.21.1-fabric':
        tests = verify_tests(Path(directory) / 'evidence', 86 if branch == MAIN else 89)
        require(tests == record['tests'], 'Test evidence mismatch')
    else:
        require(record['tests'] == {'shared_with': '1.21.1-neoforge'}, 'Invalid shared-test declaration')


def bundle(args):
    plan = json.loads(Path(args.plan).read_text())
    sources = plan['source']
    expected = targets_for(args.branch) if args.branch else list(TARGETS)
    found = collect(args.incoming, expected, sources)
    destination = Path(args.out)
    require(not destination.exists(), 'Bundle output already exists')
    destination.mkdir(parents=True)
    license_bytes = (Path(__file__).resolve().parents[2] / 'LICENSE').read_bytes()
    records = []
    for target in expected:
        directory, record = found[target]
        verify_record(directory, record, license_bytes)
        require(record['version'] == plan['versions']['main' if TARGETS[target][0] == MAIN else 'compat'],
                'Target version differs from source plan')
        shutil.copyfile(directory / record['jar'], destination / record['jar'])
        if (directory / 'evidence').exists():
            shutil.copytree(directory / 'evidence', destination / 'evidence' / target)
        records.append(record)
    if args.kind == 'candidate':
        require(len({r['version'] for r in records}) == 1, 'Candidate versions differ')
        shutil.copyfile('candidate-notes.md', destination / 'notes.md')
        (destination / 'MANUAL-VALIDATION.md').write_text(
            '# Candidate manual validation\n\n'
            'Record this run ID, the manifest SHA-256, tester and date before publishing.\n\n'
            + '\n'.join(f'- [ ] {t}: production JAR launch; Xaero and JourneyMap rails/routes/trains/hover; '
                        'server sync and client-only fallback.' for t in expected)
            + '\n\nBuild/unit/JAR checks are not game-runtime validation.\n', encoding='utf-8')
        with zipfile.ZipFile(destination / 'validation-evidence.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
            for report in sorted((destination / 'evidence').rglob('*')):
                if report.is_file():
                    archive.write(report, report.relative_to(destination).as_posix())
            archive.write(destination / 'MANUAL-VALIDATION.md', 'MANUAL-VALIDATION.md')
    (destination / 'SHA256SUMS').write_text(''.join(f'{r["sha256"]}  {r["jar"]}\n' for r in records), encoding='utf-8')
    files = {p.relative_to(destination).as_posix(): {'sha256': sha256(p), 'size': p.stat().st_size}
             for p in destination.rglob('*') if p.is_file()}
    manifest = {'schema': 1, 'kind': args.kind, 'source': sources, 'versions': plan['versions'],
                'run_id': os.environ['GITHUB_RUN_ID'], 'attempt': int(os.environ['GITHUB_RUN_ATTEMPT']),
                'workflow_sha': os.environ['GITHUB_WORKFLOW_SHA'], 'repository': repository(),
                'created_at': datetime.now(timezone.utc).isoformat(), 'targets': records, 'files': files}
    write_json(destination / 'release-manifest.json', manifest)
    summary(f'Validated {len(records)} targets. Manifest SHA-256: `{sha256(destination / "release-manifest.json")}`')


def check_bundle(directory, kind=None, artifact_run_id=None):
    directory = Path(directory)
    manifest = json.loads((directory / 'release-manifest.json').read_text())
    require(manifest['schema'] == 1 and manifest['repository'] == repository(), 'Invalid manifest origin')
    if kind:
        require(manifest['kind'] == kind, 'Wrong bundle kind')
    if artifact_run_id:
        require(str(manifest['run_id']) == str(artifact_run_id), 'Wrong bundle run ID')
    expected = list(TARGETS) if manifest['kind'] in ('candidate', 'nightly') else targets_for(
        MAIN if set(manifest['source']) == {'main'} else COMPAT)
    records = manifest['targets']
    require(len(records) == len(expected) and {r['target'] for r in records} == set(expected), 'Incomplete bundle')
    for key, value in manifest['source'].items():
        require(key in ('main', 'compat'), 'Unknown source')
        sha_ok(value)
    actual = {p.relative_to(directory).as_posix() for p in directory.rglob('*') if p.is_file()}
    require(actual == set(manifest['files']) | {'release-manifest.json'}, 'Bundle file inventory mismatch')
    for name, record in manifest['files'].items():
        path = directory / name
        require(not path.is_symlink() and path.resolve().is_relative_to(directory.resolve()), 'Unsafe bundle path')
        require(path.stat().st_size == record['size'] and sha256(path) == record['sha256'], f'Bundle checksum: {name}')
    license_bytes = (Path(__file__).resolve().parents[2] / 'LICENSE').read_bytes()
    for record in records:
        key = 'main' if TARGETS[record['target']][0] == MAIN else 'compat'
        require(record['source_sha'] == manifest['source'][key], 'Mixed source SHAs')
        require(record['version'] == manifest['versions'][key], 'Mixed version metadata')
        require(record['mtr_version'] == ('4.1.0-beta.2' if key == 'main' else '4.0.5'), 'Wrong MTR version')
        branch, mc, loader = TARGETS[record['target']]
        require(record['jar'] == f'CRTools-MTR-Map-Overlay-{record["version"]}-MC{mc}-MTR{record["mtr_version"]}-{loader}.jar',
                'Wrong JAR filename')
        # verify_record expects an evidence directory beside its JAR.
        verify_jar(directory / record['jar'], record['target'], record['version'], license_bytes)
        require(sha256(directory / record['jar']) == record['sha256'], 'Target hash differs from inventory')
        require((directory / record['jar']).stat().st_size == record['size'], 'Target size differs from inventory')
        if record['target'] != '1.21.1-fabric':
            tests = verify_tests(directory / 'evidence' / record['target'], 86 if key == 'main' else 89)
            require(tests == record['tests'], 'Test evidence differs from manifest')
        else:
            require(record['tests'] == {'shared_with': '1.21.1-neoforge'}, 'Wrong shared-test declaration')
    if manifest['kind'] == 'candidate':
        require(set(manifest['source']) == {'main', 'compat'}, 'Candidate missing sources')
        require(len(set(manifest['versions'].values())) == 1, 'Candidate versions differ')
        require({'notes.md', 'MANUAL-VALIDATION.md', 'SHA256SUMS', 'validation-evidence.zip'} <= actual,
                'Candidate missing release evidence')
    expected_sums = ''.join(f'{r["sha256"]}  {r["jar"]}\n' for r in records)
    require((directory / 'SHA256SUMS').read_text() == expected_sums, 'SHA256SUMS differs from target manifest')
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    p = commands.add_parser('branch-plan'); p.add_argument('--branch', required=True)
    p = commands.add_parser('resolve')
    p.add_argument('--main-sha', default=''); p.add_argument('--compat-sha', default=''); p.add_argument('--version', default='')
    p = commands.add_parser('build'); p.add_argument('--root', required=True)
    p.add_argument('--target', choices=TARGETS, required=True); p.add_argument('--out', required=True)
    p = commands.add_parser('bundle')
    p.add_argument('--incoming', required=True); p.add_argument('--out', required=True); p.add_argument('--plan', required=True)
    p.add_argument('--kind', choices=('ci', 'nightly', 'candidate'), required=True); p.add_argument('--branch')
    p = commands.add_parser('branch-source'); p.add_argument('--branch', required=True); p.add_argument('--root', required=True)
    p = commands.add_parser('check'); p.add_argument('--directory', required=True)
    args = parser.parse_args()
    if args.command == 'branch-source':
        targets_for(args.branch)
        sha = subprocess.check_output(['git', '-C', args.root, 'rev-parse', 'HEAD'], text=True).strip()
        key = 'main' if args.branch == MAIN else 'compat'
        write_json('source-plan.json', {'source': {key: sha}, 'versions': {key: properties(args.root)['mod_version']}})
    elif args.command == 'check':
        print(json.dumps(check_bundle(args.directory), indent=2))
    else:
        globals()[args.command.replace('-', '_')](args)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, RuntimeError, FileNotFoundError) as exc:
        print(f'::error::{exc}', file=sys.stderr)
        sys.exit(1)
