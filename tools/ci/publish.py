"""Publish verified data only; never execute downloaded artifacts or source code."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys

from core import COMPAT, MAIN, output, require, sha256, summary, targets_for, write_json
from github_api import (api, asset_content, download_artifact, pages, quote, release_by_tag,
                        repository, upload)
from pipeline import check_bundle


def candidate_artifact(repo, run_id, artifact_id=None):
    require(str(run_id).isdigit(), 'Invalid candidate run ID')
    run = api(f'repos/{repo}/actions/runs/{run_id}')
    require(run['event'] == 'workflow_dispatch' and run['head_branch'] == MAIN and
            run['conclusion'] == 'success' and run['status'] == 'completed', 'Candidate run is not trusted/successful')
    require(run['path'] == '.github/workflows/candidate.yml', 'Wrong candidate workflow')
    artifacts = pages(f'repos/{repo}/actions/runs/{run_id}/artifacts', 'artifacts')
    if artifact_id:
        artifacts = [a for a in artifacts if str(a['id']) == str(artifact_id)]
    else:
        # Highest completed attempt is selected once, before approval; its ID is pinned afterwards.
        artifacts = [a for a in artifacts if re.fullmatch(r'candidate-' + str(run_id) + r'-[0-9]+', a['name'])]
        if artifacts:
            latest = max(int(a['name'].rsplit('-', 1)[1]) for a in artifacts)
            artifacts = [a for a in artifacts if int(a['name'].rsplit('-', 1)[1]) == latest]
    require(len(artifacts) == 1 and artifacts[0]['name'].startswith(f'candidate-{run_id}-'), 'Missing/ambiguous candidate artifact')
    require(not artifacts[0]['expired'], 'Candidate expired; create and test a new candidate')
    return run, artifacts[0]


def tags_for(manifest):
    version = manifest['versions']['main']
    return {f'v{version}': manifest['source']['main'], f'v{version}-mc1.20': manifest['source']['compat']}


def existing_tag(repo, tag):
    # Listing refs avoids swallowing authorization failures as 404/missing.
    refs = api(f'repos/{repo}/git/matching-refs/tags/{quote(tag)}')
    exact = [r for r in refs if r['ref'] == f'refs/tags/{tag}']
    if not exact:
        return None
    obj = exact[0]['object']
    while obj['type'] == 'tag':
        obj = api(f'repos/{repo}/git/tags/{obj["sha"]}')['object']
    require(obj['type'] == 'commit', 'Tag does not point to a commit')
    return obj['sha']


def check_tags(repo, manifest):
    for tag, sha in tags_for(manifest).items():
        actual = existing_tag(repo, tag)
        require(actual is None or actual == sha, f'Existing tag {tag} points to another commit; never overwrite it')


def select(args):
    repo = repository()
    run, artifact = candidate_artifact(repo, args.run_id)
    download_artifact(repo, artifact, args.out)
    manifest = check_bundle(args.out, 'candidate', args.run_id)
    require(manifest['workflow_sha'] == run['head_sha'], 'Unexpected candidate workflow revision')
    try:
        check_tags(repo, manifest)
    except ValueError as exc:
        if not args.dry_run:
            raise
        summary(f'**Dry-run only: actual publication is blocked:** {exc}')
    digest = sha256(Path(args.out) / 'release-manifest.json')
    output(artifact_id=artifact['id'], manifest_sha256=digest, version=manifest['versions']['main'])
    summary(f'Candidate [{args.run_id}]({run["html_url"]}), artifact `{artifact["id"]}`, '
            f'manifest SHA-256 `{digest}`.\n\n'
            'Review the six candidate JARs and the manual test record before approving publication.')


def ensure_asset(repo, release, path):
    path = Path(path)
    matches = [a for a in pages(f'repos/{repo}/releases/{release["id"]}/assets') if a['name'] == path.name]
    require(len(matches) <= 1, 'Duplicate release asset')
    asset = matches[0] if matches else upload(repo, release, path)
    require(asset['state'] == 'uploaded' and asset['size'] == path.stat().st_size, 'Incomplete uploaded asset')
    remote_digest = asset.get('digest')
    if remote_digest:
        require(remote_digest == 'sha256:' + sha256(path), f'Existing/uploaded content differs: {path.name}')
    else:
        require(hashlib.sha256(asset_content(asset)).hexdigest() == sha256(path), f'Upload checksum: {path.name}')
    return asset


def release(args):
    repo = repository()
    require(bool(args.validation.strip()), 'Manual validation record required')
    require(len(args.validation) <= 8000, 'Manual validation record too long')
    run, artifact = candidate_artifact(repo, args.run_id, args.artifact_id)
    download_artifact(repo, artifact, args.out)
    require(sha256(Path(args.out) / 'release-manifest.json') == args.manifest_sha256, 'Candidate changed after selection')
    manifest = check_bundle(args.out, 'candidate', args.run_id)
    require(manifest['workflow_sha'] == run['head_sha'], 'Unexpected candidate workflow revision')
    check_tags(repo, manifest)
    tag = f'v{manifest["versions"]["main"]}'
    published = release_by_tag(repo, tag)
    files = [Path(args.out) / r['jar'] for r in manifest['targets']]
    files += [Path(args.out) / name for name in ('SHA256SUMS', 'release-manifest.json', 'MANUAL-VALIDATION.md', 'validation-evidence.zip')]
    if published and not published['draft']:
        # A published release is read-only, including on retries.
        assets = pages(f'repos/{repo}/releases/{published["id"]}/assets')
        for path in files:
            matches = [a for a in assets if a['name'] == path.name]
            require(len(matches) == 1, 'Published release has a different asset set; refusing to change it')
            require(matches[0]['size'] == path.stat().st_size and
                    hashlib.sha256(asset_content(matches[0])).hexdigest() == sha256(path), 'Published release differs')
        summary(f'Release already published with identical files: {published["html_url"]}')
        return
    for name, sha in tags_for(manifest).items():
        if existing_tag(repo, name) is None:
            api(f'repos/{repo}/git/refs', 'POST', {'ref': f'refs/tags/{name}', 'sha': sha})
    notes = (Path(args.out) / 'notes.md').read_text(encoding='utf-8')
    notes += (f'\n\n## CI provenance and manual acceptance\n\n'
              f'Candidate: {run["html_url"]}\n\nManifest SHA-256: `{args.manifest_sha256}`\n\n'
              f'Main: `{manifest["source"]["main"]}`; compat: `{manifest["source"]["compat"]}`.\n\n'
              f'Accepted by `{os.environ["GITHUB_ACTOR"]}`:\n\n{args.validation}\n')
    if published is None:
        published = api(f'repos/{repo}/releases', 'POST', {'tag_name': tag, 'name': tag,
                       'target_commitish': manifest['source']['main'], 'draft': True, 'prerelease': False,
                       'make_latest': 'false', 'body': notes})
    else:
        api(f'repos/{repo}/releases/{published["id"]}', 'PATCH', {'body': notes})
    for path in files:
        ensure_asset(repo, published, path)
    result = api(f'repos/{repo}/releases/{published["id"]}', 'PATCH',
                 {'draft': False, 'prerelease': False, 'make_latest': 'true'})
    summary(f'Published {result["html_url"]}. All uploaded JARs match the approved candidate.')


SNAPSHOT_MARKER = r'<!-- snapshot-state:([A-Za-z0-9._-]+) -->'


def snapshot(args):
    """Reconcile both branches: GitHub may replace a pending concurrency-group job."""
    repo = repository()
    event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
    trigger = event.get('workflow_run')
    if trigger:
        snapshot_one(args, trigger)
    for branch in (MAIN, COMPAT):
        runs = api(f'repos/{repo}/actions/workflows/build.yml/runs?branch={quote(branch)}&status=success&per_page=10')['workflow_runs']
        eligible = [r for r in runs if r['event'] in ('push', 'workflow_dispatch') and
                    r['head_repository']['full_name'] == repo]
        if eligible:
            selected = max(eligible, key=lambda r: (r['run_number'], r['run_attempt']))
            if trigger and selected['id'] == trigger['id']:
                continue
            other_args = argparse.Namespace(out=args.out + '-' + ('main' if branch == MAIN else 'compat'))
            snapshot_one(other_args, selected)


def snapshot_one(args, trigger):
    repo = repository()
    require(trigger['head_repository']['full_name'] == repo, 'Foreign build origin')
    require(trigger['event'] in ('push', 'workflow_dispatch') and trigger['head_branch'] in (MAIN, COMPAT)
            and trigger['conclusion'] == 'success' and trigger['path'] == '.github/workflows/build.yml',
            'Untrusted Snapshot build')
    branch = trigger['head_branch']
    key = 'main' if branch == MAIN else 'compat'
    head = api(f'repos/{repo}/git/ref/heads/{quote(branch)}')['object']['sha']
    if trigger['head_sha'] != head:
        summary('A newer source commit exists; this obsolete Snapshot build was skipped.')
        return
    artifacts = pages(f'repos/{repo}/actions/runs/{trigger["id"]}/artifacts', 'artifacts')
    choices = [a for a in artifacts if re.fullmatch(r'ci-bundle-' + str(trigger['id']) + r'-[0-9]+', a['name'])]
    require(bool(choices), 'Missing branch bundle')
    latest = max(int(a['name'].rsplit('-', 1)[1]) for a in choices)
    choices = [a for a in choices if int(a['name'].rsplit('-', 1)[1]) == latest]
    require(len(choices) == 1, 'Ambiguous branch bundle')
    download_artifact(repo, choices[0], args.out)
    manifest = check_bundle(args.out, 'ci', trigger['id'])
    require(manifest['source'] == {key: head}, 'Snapshot source does not match triggering branch')
    require({r['target'] for r in manifest['targets']} == set(targets_for(branch)), 'Wrong Snapshot branch targets')
    published = release_by_tag(repo, 'snapshot')
    state = {'schema': 1, 'branches': {}}
    if published:
        require(published['prerelease'] and not published['draft'] and not published.get('immutable'),
                'Snapshot release must be mutable and marked Prerelease')
        marker = re.search(SNAPSHOT_MARKER, published['body'] or '')
        assets = pages(f'repos/{repo}/releases/{published["id"]}/assets')
        if marker:
            previous = next(a for a in assets if a['name'] == marker[1])
            state = json.loads(asset_content(previous))
        else:
            require(published['body'] == 'Preparing verified Snapshot downloads. Formal releases retain Latest.',
                    'Existing Snapshot is not managed by this publisher')
        require(state['schema'] == 1 and set(state['branches']) <= {'main', 'compat'}, 'Invalid Snapshot state')
        if state['branches'].get(key, {}).get('artifact_id') == choices[0]['id']:
            summary(f'{branch} Snapshot already references this verified artifact.')
            return
    else:
        # This permanent anchor is never force-moved; actual build SHAs are in the manifest.
        default_sha = api(f'repos/{repo}/git/ref/heads/main')['object']['sha']
        require(existing_tag(repo, 'snapshot') in (None, default_sha), 'Unmanaged snapshot tag already exists')
        if existing_tag(repo, 'snapshot') is None:
            api(f'repos/{repo}/git/refs', 'POST', {'ref': 'refs/tags/snapshot', 'sha': default_sha})
        published = api(f'repos/{repo}/releases', 'POST', {'tag_name': 'snapshot', 'name': 'Snapshot — development builds',
                        'prerelease': True, 'draft': False, 'make_latest': 'false',
                        'body': 'Preparing verified Snapshot downloads. Formal releases retain Latest.'})
        assets = []
    staging = Path(args.out).parent / 'snapshot-upload'
    staging.mkdir(exist_ok=True)
    rows = []
    for record in manifest['targets']:
        filename = record['jar'][:-4] + f'-snapshot-{head[:12]}-b{choices[0]["id"]}.jar'
        path = staging / filename
        shutil.copyfile(Path(args.out) / record['jar'], path)
        asset = ensure_asset(repo, published, path)
        rows.append({'target': record['target'], 'name': filename, 'sha256': record['sha256'],
                     'url': asset['browser_download_url'], 'id': asset['id']})
    state['branches'][key] = {'sha': head, 'version': manifest['versions'][key], 'run_url': trigger['html_url'],
                              'updated_at': manifest['created_at'], 'artifact_id': choices[0]['id'], 'files': rows}
    # Immutable generations make the release-body PATCH the single commit point.
    payload = json.dumps(state, sort_keys=True, indent=2).encode() + b'\n'
    state_name = 'snapshot-state-' + hashlib.sha256(payload).hexdigest()[:20] + '.json'
    state_path = staging / state_name
    state_path.write_bytes(payload)
    state_asset = ensure_asset(repo, published, state_path)
    sums = ''.join(f'{r["sha256"]}  {r["name"]}\n' for b in state['branches'].values() for r in b['files'])
    sums_name = state_name.replace('snapshot-state-', 'snapshot-sha256-').replace('.json', '.txt')
    sums_path = staging / sums_name
    sums_path.write_text(sums, encoding='utf-8')
    sums_asset = ensure_asset(repo, published, sums_path)
    body = ('# Snapshot — development builds\n\nAutomatically updated after successful branch CI. '
            'These builds have not passed manual game validation. Stable releases retain **Latest**.\n\n'
            'The fixed snapshot tag is a download anchor. Use the commit links below for actual source; '
            'GitHub-generated source archives are not the sources of these changing builds.\n\n')
    for source_key, entry in sorted(state['branches'].items()):
        body += (f'## {source_key} · {entry["version"]}\n\n'
                 f'[Source `{entry["sha"][:12]}`](https://github.com/{repo}/tree/{entry["sha"]}) · '
                 f'[CI]({entry["run_url"]}) · {entry["updated_at"]}\n\n'
                 '| Target | Download | SHA-256 |\n|---|---|---|\n')
        for row in entry['files']:
            body += f'| {row["target"]} | [{row["name"]}]({row["url"]}) | `{row["sha256"]}` |\n'
        body += '\n'
    body += (f'[Build manifest]({state_asset["browser_download_url"]}) · '
             f'[SHA-256]({sums_asset["browser_download_url"]})\n\n<!-- snapshot-state:{state_name} -->\n')
    # Recheck immediately before changing public links; keep staged files as harmless orphans if obsolete.
    current = api(f'repos/{repo}/git/ref/heads/{quote(branch)}')['object']['sha']
    if current != head:
        summary('Branch advanced during upload; existing Snapshot links were retained.')
        return
    api(f'repos/{repo}/releases/{published["id"]}', 'PATCH', {'body': body, 'prerelease': True, 'make_latest': 'false'})
    keep = {state_name, sums_name} | {r['name'] for b in state['branches'].values() for r in b['files']}
    for asset in pages(f'repos/{repo}/releases/{published["id"]}/assets'):
        if asset['name'] not in keep and (asset['name'].startswith(('snapshot-state-', 'snapshot-sha256-'))
                                         or '-snapshot-' in asset['name']):
            api(f'repos/{repo}/releases/assets/{asset["id"]}', 'DELETE')
    summary(f'Updated {branch} Snapshot: https://github.com/{repo}/releases/tag/snapshot')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    for name in ('select', 'release', 'snapshot'):
        p = commands.add_parser(name)
        p.add_argument('--out', default='candidate-download' if name != 'snapshot' else 'snapshot-download')
        if name != 'snapshot':
            p.add_argument('--run-id', required=True)
        if name == 'select':
            p.add_argument('--dry-run', action='store_true')
        if name == 'release':
            p.add_argument('--artifact-id', required=True); p.add_argument('--manifest-sha256', required=True)
            p.add_argument('--validation', required=True)
    args = parser.parse_args()
    globals()[args.command](args)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, RuntimeError, FileNotFoundError, StopIteration) as exc:
        print(f'::error::{exc}', file=sys.stderr)
        sys.exit(1)
