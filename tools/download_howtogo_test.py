"""Fetch the pinned, optional HowToGo NeoForge QA artifact; not a production dependency."""
import hashlib
import pathlib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
URL = 'https://mediafilez.forgecdn.net/files/9082/13/howtogo-0.2.1.jar'
SHA256 = '00017454e639b22a80f9adc167527b55f29cf9bf96face894db135c751ac7d14'
TARGET = ROOT / '.cache/howtogo-0.2.1-neoforge.jar'

if __name__ == '__main__':
    data = TARGET.read_bytes() if TARGET.exists() else urllib.request.urlopen(URL, timeout=60).read()
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise RuntimeError('HowToGo test artifact SHA256 mismatch')
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_bytes(data)
    print(f'{TARGET.name}: SHA256 {SHA256}')
