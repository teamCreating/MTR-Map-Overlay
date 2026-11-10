"""Real Java client checks for Navigate availability, HowToGo handoff, and language rendering."""
import argparse
import json
import math
import time
from overlay_qa_cli import call, capture, OUTPUT


def wait_ready():
    deadline = time.monotonic() + 150
    while time.monotonic() < deadline:
        try:
            state = call()
            if state['connected'] and state.get('catalog', {}).get('status') == 'READY' and not state['languageReloading']:
                return state
        except (OSError, ValueError):
            pass
        time.sleep(1)
    raise TimeoutError('Client/catalog not ready')


def button(label):
    state = call()
    return next(b for b in state['buttons'] if b['label'] == label)


def click_button(label):
    b = button(label)
    call('click', x=b['x'] + b['w']/2, y=b['y'] + b['h']/2)
    time.sleep(.35)
    return call()


def select_station(wanted):
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        state = call()
        target = next((t for t in state['targets'] if t['marker']['name'].startswith(wanted)), None)
        if target:
            if any(b['x'] <= target['x'] < b['x']+b['w'] and b['y'] <= target['y'] < b['y']+b['h'] for b in state['buttons']):
                call('scroll', amount=-1); time.sleep(.3); continue
            call('click', x=target['x'], y=target['y'])
            time.sleep(.35)
            state = call()
            if state.get('selected') == target['station']:
                return state
        time.sleep(.3)
    raise AssertionError(('Native station click did not select', wanted, state))


def run(mode):
    initial = wait_ready()
    if mode == 'absent':
        assert not initial['providers']
    else:
        assert 'mtrmap:howtogo' in initial['providers']
        assert len(initial['providers']) >= 2, 'Install the real MTR Navigation provider too'
    results = []
    for language in ['en_us', 'zh_cn', 'zh_tw', 'ja_jp']:
        call('resume'); call('language', language=language)
        state = wait_ready(); assert state['language'] == language
        assert all(not label.startswith('mtrmap.') for label in state['labels'].values())
        call('command', command='tp Dev 330 -57 30'); time.sleep(.5)
        for key, map_name in [('gui.xaero_open_map', 'xaero'), ('key.journeymap.map_toggle_alt', 'journeymap')]:
            call('key', name=key); time.sleep(.7)
            if language == 'en_us' and map_name == 'xaero' and mode == 'absent':
                for _ in range(3): call('scroll', amount=-1)
                time.sleep(.3)
            state = select_station('QA Middle' if map_name == 'xaero' else 'QA Transfer')
            assert not state['paused']; assert state['unknownStationAvailable'] == 0
            selected = state['selected']; nav = button(state['labels']['navigate'])
            assert nav['enabled'] == (mode != 'absent')
            case = f'{mode}-{language}-{map_name}'
            if mode == 'absent':
                call('move', x=nav['x']+4, y=nav['y']+8); time.sleep(.3)
                capture(case)
                after = click_button(state['labels']['navigate'])
                assert after['screen'] == state['screen'] and after['selected'] == selected
                click_button(state['labels']['expand']); time.sleep(1)
                call('move', x=4, y=50); time.sleep(.2)
                capture(case+'-board')
            else:
                after = click_button(state['labels']['navigate'])
                assert any(b['label'] == 'HowToGo' for b in after['buttons']), 'Expected a provider chooser'
                capture(case+'-chooser')
                before_target = after.get('howtogoTarget')
                after = click_button('HowToGo')
                target = after.get('howtogoTarget'); assert target is not None and after['screen'] == ''
                station = next(s for s in state['catalog']['stations'] if s['id'] == selected)
                assert target['name'] == station['name'].replace('|', ' ')
                assert all(target[c] == math.floor(station['position'][c]) for c in ['x', 'y', 'z'])
                assert target['source'] == 'mtrmap' and target != before_target
                capture(case+'-handoff')
            results.append({'mode': mode, 'language': language, 'map': map_name, 'station': selected, 'passed': True})
            print(json.dumps(results[-1]), flush=True)
            call('resume')
    (OUTPUT / (mode+'-result.json')).write_text(json.dumps({'passed': True, 'cases': results}, indent=2), encoding='utf-8')
    call('language', language='en_us'); wait_ready()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['absent', 'providers'])
    run(parser.parse_args().mode)
