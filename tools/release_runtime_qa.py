"""Acceptance against a staged production candidate, using native client input and raw evidence."""
import argparse
import hashlib
import json
import math
import re
from pathlib import Path
import time
from urllib.parse import unquote, urlparse
import overlay_qa_cli as cli


def wait(predicate, description, timeout=120):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            last = cli.call()
            if last.get('screen', '').endswith('DisconnectedScreen'):
                raise RuntimeError('Client disconnected; release is blocked')
            if predicate(last):
                return last
        except (OSError, ValueError):
            pass
        time.sleep(.5)
    raise TimeoutError((description, last))


def click_button(label):
    state = cli.call()
    button = next(b for b in state['buttons'] if b['label'] == label)
    cli.call('click', x=button['x']+button['w']/2, y=button['y']+button['h']/2)
    time.sleep(.35)
    return cli.call()


def enter_singleplayer():
    state = wait(lambda s: s['connected'] or s['screen'].endswith('SelectWorldScreen') or (s['screen'].endswith('TitleScreen') and not s['languageReloading']), 'normal title screen')
    if state['connected']:
        return
    if state['screen'].endswith('TitleScreen'):
        time.sleep(1)
        button = next(b for b in state['nativeButtons'] if b['label'] == 'Singleplayer')
        cli.call('click', x=button['x']+button['w']/2, y=button['y']+button['h']/2)
    state = wait(lambda s: s['screen'].endswith('SelectWorldScreen'), 'native world selection')
    # Every isolated acceptance profile contains exactly one copied test save.
    listing = next((b for b in state['nativeButtons'] if b['label'] == '' and b['w'] == state['guiWidth']), None)
    # In 1.20.1 this list is not an AbstractWidget. The observed first row is
    # directly below the native search box; keep the click relative to that box.
    search = next(b for b in state['nativeButtons'] if b['label'] == 'search for worlds')
    row_y = listing['y']+16 if listing else search['y']+search['h']+22
    # The world directory is scanned asynchronously after the screen appears.
    for _ in range(10):
        cli.call('click', x=state['guiWidth']/2, y=row_y)
        time.sleep(.5)
        if any(b['label'] == 'Play Selected World' and b['active'] for b in cli.call()['nativeButtons']):
            break
    state = wait(lambda s: any(b['label'] == 'Play Selected World' and b['active'] for b in s['nativeButtons']), 'selected test save')
    button = next(b for b in state['nativeButtons'] if b['label'] == 'Play Selected World')
    cli.call('click', x=button['x']+button['w']/2, y=button['y']+button['h']/2)


def select_station():
    for _ in range(18):
        state = cli.call()
        targets = [t for t in state['targets'] if t['marker']['type'] == 'STATION']
        for target in targets:
            if any(b['x'] <= target['x'] < b['x']+b['w'] and b['y'] <= target['y'] < b['y']+b['h'] for b in state['buttons']):
                continue
            cli.call('click', x=target['x'], y=target['y']); time.sleep(.35)
            result = cli.call()
            if result.get('selected') == target['station']:
                return result
        cli.call('scroll', amount=-1); time.sleep(.25)
    raise AssertionError('No selectable rendered station in the actual map')


def run(args):
    cli.OUTPUT = cli.ROOT / 'build/acceptance160' / args.target
    cli.OUTPUT.mkdir(parents=True, exist_ok=True)
    jar = Path(args.jar).resolve()
    assert hashlib.sha256(jar.read_bytes()).hexdigest() == args.sha256
    result = {'target': args.target, 'candidateSha256': args.sha256, 'candidateJar': str(jar), 'passed': False, 'cases': []}
    path = cli.OUTPUT / 'result.json'
    path.write_text(json.dumps(result, indent=2), encoding='utf-8')
    enter_singleplayer()
    state = wait(lambda s: s['connected'] and s.get('catalog', {}).get('status') == 'READY', 'native world/catalog')
    source = state['overlayCodeSource']
    uri = source.removeprefix('jar:').removesuffix('!/')
    parsed = urlparse(uri)
    location = unquote(parsed.path)
    if parsed.scheme == 'union':
        # Forge 47 SecureJar adds its filesystem instance id to the archive URI.
        match = re.fullmatch(r'(.+\.jar)#\d+', location)
        assert match, ('Unexpected Forge SecureJar URI', source)
        location = match[1]
    else:
        assert parsed.scheme == 'file', ('Unexpected mod code source scheme', source)
    loaded = Path(location.lstrip('/')).resolve()
    assert loaded.samefile(jar), ('Unexpected Overlay code source', source, jar)
    result['overlayCodeSource'] = source
    stations = state['catalog']['stations']; assert stations
    x = (min(s['position']['x'] for s in stations)+max(s['position']['x'] for s in stations))/2
    z = (min(s['position']['z'] for s in stations)+max(s['position']['z'] for s in stations))/2+40
    y = max(s['position']['y'] for s in stations)+6
    for language in ['en_us', 'zh_cn', 'zh_tw', 'ja_jp']:
        cli.call('resume'); cli.call('language', language=language)
        state = wait(lambda s: not s['languageReloading'], 'language reload')
        catalog = json.loads((cli.ROOT/f'src/main/resources/assets/mtrmap/lang/{language}.json').read_text(encoding='utf-8'))
        assert all(value == catalog['mtrmap.ui.'+key] for key, value in state['labels'].items())
        cli.call('command', command=f'tp Dev {x} {y} {z}'); time.sleep(.5)
        for binding, map_name in [('gui.xaero_open_map', 'xaero'), ('key.journeymap.map_toggle_alt', 'journeymap')]:
            cli.call('key', name=binding); time.sleep(.7)
            opening = cli.call()
            if opening['screen'].endswith('AboutDialog'):
                close = next(b for b in opening['nativeButtons'] if b['label'] == 'Close')
                cli.call('click', x=close['x']+close['w']/2, y=close['y']+close['h']/2)
                time.sleep(.7)
            state = select_station(); assert not state['paused']
            assert state['unknownStationAvailable'] == 0
            nav = next(b for b in state['buttons'] if b['label'] == state['labels']['navigate'])
            available = state['available'][state['selected']]
            assert nav['enabled'] == bool(available)
            case = {'language': language, 'map': map_name, 'station': state['selected'], 'providers': available}
            prefix = language+'-'+map_name
            cli.call('move', x=4, y=50); time.sleep(.15); cli.capture(prefix+'-collapsed')
            if not available:
                after = click_button(state['labels']['navigate'])
                assert after['screen'] == state['screen'] and after['selected'] == state['selected']
            click_button(state['labels']['expand'])
            expanded = wait(lambda s: s.get('board') is not None and s['board']['status'] in ['READY', 'LIMITED'], 'native arrival board', 15)
            assert not expanded['paused']; assert expanded['board']['arrivals'], 'Fixture needs a native predicted service'
            before = expanded['board']['serverNow']; time.sleep(2)
            refreshed = cli.call(); assert refreshed['board']['serverNow'] > before, 'Native board stopped refreshing'
            cli.call('move', x=4, y=50); time.sleep(.15); cli.capture(prefix+'-board')
            case['arrivalCount'] = len(refreshed['board']['arrivals']); case['serverTimeAdvanced'] = True
            click_button(refreshed['labels']['collapse'])
            assert cli.call().get('board') is None
            if language == 'en_us' and 'mtrmap:howtogo' in available:
                chooser = click_button(state['labels']['navigate'])
                if len(available) > 1:
                    assert any(b['label'] == 'HowToGo' for b in chooser['buttons'])
                    cli.capture(prefix+'-chooser'); end = click_button('HowToGo')
                else:
                    end = chooser
                station = next(s for s in state['catalog']['stations'] if s['id'] == state['selected'])
                target = end['howtogoTarget']; assert end['screen'] == ''
                assert target['name'] == station['name'].replace('|', ' ') and target['source'] == 'mtrmap'
                assert all(target[c] == math.floor(station['position'][c]) for c in ['x', 'y', 'z'])
                case['howtogoTarget'] = target; cli.capture(prefix+'-handoff')
            cli.call('resume')
            case['passed'] = True; result['cases'].append(case)
            print(json.dumps({'target': args.target, **case}), flush=True)
    cli.call('language', language='en_us'); wait(lambda s: not s['languageReloading'], 'restore English')
    result['passed'] = True
    path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    cli.call('quit')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', required=True)
    parser.add_argument('--jar', required=True)
    parser.add_argument('--sha256', required=True)
    run(parser.parse_args())
