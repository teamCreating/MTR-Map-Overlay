"""Small GitHub REST adapter using the runner's gh authentication."""
from __future__ import annotations

import io
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import urllib.parse
import zipfile

from core import require


def api(path, method='GET', data=None, raw=False):
    command = ['gh', 'api', path, '--method', method]
    payload = None
    if data is not None:
        command += ['--input', '-']
        payload = json.dumps(data).encode()
    result = subprocess.run(command, input=payload, capture_output=True)
    if result.returncode:
        # gh redacts its token; never print the environment or request headers.
        raise RuntimeError(result.stderr.decode(errors='replace').strip())
    return result.stdout if raw else json.loads(result.stdout or b'null')


def pages(path, key=None):
    values = []
    separator = '&' if '?' in path else '?'
    page = 1
    while True:
        response = api(f'{path}{separator}per_page=100&page={page}')
        entries = response[key] if key else response
        values.extend(entries)
        if len(entries) < 100:
            return values
        page += 1


def repository():
    repo = os.environ['GITHUB_REPOSITORY']
    require(repo == 'teamCreating/MTR-Map-Overlay', 'Publishing is restricted to the canonical repository')
    return repo


def quote(value):
    return urllib.parse.quote(value, safe='')


def extract_zip(data, destination):
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        require(sum(x.file_size for x in archive.infolist()) < 400_000_000, 'Artifact too large')
        require(len(archive.namelist()) == len(set(archive.namelist())), 'Duplicate artifact entries')
        for entry in archive.infolist():
            name = PurePosixPath(entry.filename)
            require(not name.is_absolute() and '..' not in name.parts and '\\' not in entry.filename,
                    'Unsafe artifact path')
            require((entry.external_attr >> 16) & 0o170000 != 0o120000, 'Symlink in artifact')
            require(':' not in entry.filename, 'Unsafe artifact path')
        archive.extractall(destination)


def download_artifact(repo, artifact, destination):
    require(not artifact['expired'], 'Candidate artifact has expired')
    data = api(f'repos/{repo}/actions/artifacts/{artifact["id"]}/zip', raw=True)
    extract_zip(data, destination)


def release_by_tag(repo, tag):
    # Avoid interpreting auth/server failures as a missing release.
    matches = [x for x in pages(f'repos/{repo}/releases') if x['tag_name'] == tag]
    require(len(matches) <= 1, 'Ambiguous release tag')
    return matches[0] if matches else None


def upload(repo, release, path):
    path = Path(path)
    result = subprocess.run(['gh', 'release', 'upload', release['tag_name'], str(path), '--repo', repo],
                            capture_output=True)
    require(result.returncode == 0, result.stderr.decode(errors='replace'))
    return next(x for x in pages(f'repos/{repo}/releases/{release["id"]}/assets') if x['name'] == path.name)


def _binary_asset(asset):
    result = subprocess.run(['gh', 'api', asset['url'], '-H', 'Accept: application/octet-stream'], capture_output=True)
    require(result.returncode == 0, result.stderr.decode(errors='replace'))
    return result.stdout


def asset_content(asset):
    return _binary_asset(asset)
