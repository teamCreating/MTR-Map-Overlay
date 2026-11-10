"""Native client CLI for the opt-in -PnavigationQa development mod."""
import json
import pathlib
import sys
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'build/navigation-qa'
HTTP = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(op='state', **fields):
    payload = None if op == 'state' else json.dumps({'op': op, **fields}).encode('utf-8')
    request = urllib.request.Request('http://localhost:18762/', data=payload,
                                     headers={'Content-Type': 'application/json'})
    with HTTP.open(request, timeout=40) as response:
        result = json.load(response)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    with (OUTPUT / 'cli.jsonl').open('a', encoding='utf-8') as stream:
        stream.write(json.dumps({'at': time.time(), 'op': op, 'fields': fields, 'result': result}, ensure_ascii=False) + '\n')
    return result


def capture(name):
    state = call()
    (OUTPUT / (name + '.json')).write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding='utf-8')
    call('screenshot', name=name)
    return state


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    fields = {}
    for argument in sys.argv[2:]:
        key, value = argument.split('=', 1)
        try:
            fields[key] = json.loads(value)
        except json.JSONDecodeError:
            fields[key] = value
    print(json.dumps(call(sys.argv[1] if len(sys.argv) > 1 else 'state', **fields), ensure_ascii=False, indent=2))
