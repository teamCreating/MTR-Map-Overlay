"""Offline regression tests for artifact rejection and publishing trust boundaries."""
import argparse
import io
import json
import os
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
import zipfile

import core
import github_api
import pipeline
import publish


LICENSE = (Path(__file__).resolve().parents[2] / 'LICENSE').read_bytes()
SHA = 'a' * 40
OTHER = 'b' * 40


def fixture_jar(path, target='1.21.1-fabric', version='1.5.2', overrides=None):
    branch, mc, loader = core.TARGETS[target]
    legacy = mc == '1.20.4'
    def bytecode(extra=b''):
        return b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, 65 if branch == core.MAIN else 61) + extra
    entries = {core.PREFIX + c + '.class': bytecode() for c in core.REQUIRED}
    entries[core.PREFIX + 'CommandRegistration.class'] = bytecode(b'trainCars')
    hooks = b'method_25394 method_25402' if loader == 'fabric' else b'm_88315_ m_6375_'
    entries[core.PREFIX + 'mixin/client/xaero/XaeroWorldMapMixin.class'] = bytecode(hooks)
    entries[core.PREFIX + 'mixin/MTRAccessorMixin.class'] = bytecode(b'org/mtr/mod/Init')
    entries[core.PREFIX + 'integration/journeymap/MTRJourneyMapPlugin.class'] = bytecode(
        b'journeymap/client/api/ClientPlugin' if legacy else b'journeymap/api/v2/common/JourneyMapPlugin')
    entries[core.PREFIX + 'MTRMapClient.class'] = bytecode()
    entries[core.PREFIX + 'mixin/XaeroMixinPlugin.class'] = bytecode()
    entries[core.PREFIX + 'mixin/client/xaero/TrainMapPauseMixin.class'] = bytecode()
    config = {'package': core.PACKAGE + '.mixin', 'plugin': core.PACKAGE + '.mixin.XaeroMixinPlugin',
              'client': ['client.xaero.TrainMapPauseMixin'], 'mixins': []}
    if branch == core.COMPAT:
        config['mixins'].append('SidingVehiclesAccessor')
        entries[core.PREFIX + 'mixin/SidingVehiclesAccessor.class'] = bytecode()
        if legacy:
            config['client'].append('client.journeymap.JourneyMapFullscreenMixin')
            entries[core.PREFIX + 'mixin/client/journeymap/JourneyMapFullscreenMixin.class'] = bytecode(b'drawMap')
    entries['LICENSE'] = LICENSE
    entries['pack.mcmeta'] = json.dumps({'pack': {'pack_format': 34 if branch == core.MAIN else 22 if legacy else 15}}).encode()
    if tuple(map(int,version.split('.'))) >= (1,5,3):
        for cls in ['extension/OverlayUi','extension/ViewRenderer','extension/ServerOverlayService','network/OverlayRequest','network/OverlayChunk']:
            entries[core.PREFIX+cls+'.class']=bytecode()
        entries[core.PREFIX+'api/MtrMapApi.class']=b'\xca\xfe\xba\xbe'+struct.pack('>HH',0,61)
        entries[core.PREFIX+'CommandRegistration.class']=bytecode(b'trainCars pidsStyle')
        config['client'].append('client.OverlayPointerMixin')
        entries[core.PREFIX+'mixin/client/OverlayPointerMixin.class']=bytecode()
        entries[core.PREFIX+('MTRMapClient' if loader=='fabric' else 'extension/OverlayUiEvents')+'.class']=bytecode(b'OverlayUi')
        entries[core.PREFIX+'mixin/client/xaero/XaeroWorldMapMixin.class']=bytecode(b'method_25394' if loader=='fabric' else b'm_88315_')
    if loader == 'fabric':
        meta = {'id': 'mtrmap', 'version': version, 'depends': {'minecraft': mc,
                'java': '>=21' if branch == core.MAIN else '>=17', 'mtr': '*' if branch == core.MAIN else '>=4.0.5 <4.1'},
                'entrypoints': {'main': [core.PACKAGE + '.MTRMap'], 'client': [core.PACKAGE + '.MTRMapClient'],
                               'journeymap': [core.PACKAGE + '.integration.journeymap.MTRJourneyMapPlugin']}}
        entries['fabric.mod.json'] = json.dumps(meta).encode()
        entries['mtrmap.fabric.mixins.json'] = json.dumps(config).encode()
    else:
        deps = {'minecraft': f'[{mc}]' if branch == core.COMPAT else '[1.21.1,1.21.2)',
                'mtr': '[4.0.5,4.1)' if branch == core.COMPAT else '[4.1.0-beta.2,)'}
        if branch == core.COMPAT:
            deps.update(forge='[49,)' if legacy else '[47,)', journeymap='[5.10.0,)' if legacy else '[1.20.1-6.0.6,)')
            entries['mtrmap.refmap.json'] = b'{}'
        text = f'[[mods]]\nmodId="mtrmap"\nversion="{version}"\n'
        text += ''.join(f'[[dependencies.mtrmap]]\nmodId="{k}"\nversionRange="{v}"\n' for k, v in deps.items())
        entries['META-INF/' + ('mods.toml' if loader == 'forge' else 'neoforge.mods.toml')] = text.encode()
        entries['META-INF/MANIFEST.MF'] = b'MixinConfigs: mtrmap.mixins.json\n'
        entries['mtrmap.mixins.json'] = json.dumps(config).encode()
    entries.update(overrides or {})
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w') as archive:
        for name, data in entries.items():
            if data is not None:
                archive.writestr(name, data)


def fixture_tests(directory, count=86, skip='TestWorldGeneratorTest', failure=False):
    directory.mkdir(parents=True, exist_ok=True)
    suite = ET.Element('testsuite', tests=str(count + 1), failures='1' if failure else '0', errors='0')
    for i in range(count):
        case = ET.SubElement(suite, 'testcase', classname=core.PACKAGE + '.ExampleTest', name=f'case{i}')
        if failure and i == 0:
            ET.SubElement(case, 'failure')
    case = ET.SubElement(suite, 'testcase', classname=core.PACKAGE + '.' + skip, name='generate')
    ET.SubElement(case, 'skipped')
    ET.ElementTree(suite).write(directory / 'TEST-Example.xml')


class ArtifactTests(unittest.TestCase):
    def test_shared_java17_api_and_event_input_on_all_six_targets(self):
        for target in core.TARGETS:
            with self.subTest(target=target):
                fixture_jar(self.jar,target,version='1.5.3')
                core.verify_jar(self.jar,target,'1.5.3',LICENSE)

    def test_api_cannot_leak_implementation_or_silently_change_java_version(self):
        for data in [b'\xca\xfe\xba\xbe\0\0\0A', b'\xca\xfe\xba\xbe\0\0\0=net/minecraft/client/Minecraft']:
            fixture_jar(self.jar,version='1.5.3',overrides={core.PREFIX+'api/MtrMapApi.class':data})
            with self.assertRaises(ValueError): core.verify_jar(self.jar,'1.21.1-fabric','1.5.3',LICENSE)
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.jar = self.root / 'mod.jar'

    def test_all_six_target_contracts(self):
        for target in core.TARGETS:
            with self.subTest(target=target):
                fixture_jar(self.jar, target)
                core.verify_jar(self.jar, target, '1.5.2', LICENSE)

    def test_reject_namespace_and_packaging_regressions(self):
        mutations = [
            {core.PREFIX + 'MTRMap.class': b'\xca\xfe\xba\xbe\0\0\0Acom/lx862/mtrmap/Old'},
            {core.PREFIX + 'MTRMap.class': b'\xca\xfe\xba\xbe\0\0\0Acom.lx862.mtrmap.Old'},
            {core.PREFIX + 'MTRMap.class': b'\xca\xfe\xba\xbe\0\0\0='},
            {core.PREFIX + 'MTRMap.class': None},
            {'LICENSE': b'changed'},
            {'META-INF/jars/dependency.jar': b'jar'},
            {'third/party/Extra.class': b'\xca\xfe\xba\xbe\0\0\0A'},
            {core.PREFIX + 'MTRMap.class': b'\xca\xfe\xba\xbe\0\0\0Anet/neoforged/Bad'},
            {'mtrmap.fabric.mixins.json': b'{"package":"wrong","plugin":"wrong","client":[]}'},
        ]
        for mutation in mutations:
            with self.subTest(mutation=list(mutation)):
                fixture_jar(self.jar, overrides=mutation)
                with self.assertRaises((ValueError, KeyError)):
                    core.verify_jar(self.jar, '1.21.1-fabric', '1.5.2', LICENSE)

    def test_reject_wrong_version(self):
        fixture_jar(self.jar, version='1.5.3')
        with self.assertRaisesRegex(ValueError, 'version'):
            core.verify_jar(self.jar, '1.21.1-fabric', '1.5.2', LICENSE)

    def test_reject_missing_entrypoint(self):
        fixture_jar(self.jar, overrides={core.PREFIX + 'MTRMapClient.class': None})
        with self.assertRaisesRegex(ValueError, 'entrypoint'):
            core.verify_jar(self.jar, '1.21.1-fabric', '1.5.2', LICENSE)

    def test_junit_gates(self):
        fixture_tests(self.root)
        self.assertEqual(core.verify_tests(self.root, 86)['passed'], 86)
        with self.assertRaisesRegex(ValueError, 'Incomplete'):
            core.verify_tests(self.root, 89)
        fixture_tests(self.root, skip='OrdinaryTest')
        with self.assertRaisesRegex(ValueError, 'Unexpected skipped'):
            core.verify_tests(self.root, 86)
        fixture_tests(self.root, failure=True)
        with self.assertRaisesRegex(ValueError, 'Failed'):
            core.verify_tests(self.root, 86)

    def test_missing_junit(self):
        with self.assertRaisesRegex(ValueError, 'Missing'):
            core.verify_tests(self.root, 86)

    def test_safe_artifact_extraction(self):
        for name in ('../outside', '/absolute', '..\\escape', 'C:escape'):
            data = io.BytesIO()
            with zipfile.ZipFile(data, 'w') as archive:
                archive.writestr(name, b'bad')
            with self.assertRaises(ValueError):
                github_api.extract_zip(data.getvalue(), self.root / 'download')

    def test_sha_and_version_inputs(self):
        for value in ('main', 'a'*39, 'a'*41, 'A'*40, '; rm -rf /'):
            with self.assertRaises(ValueError):
                core.sha_ok(value)
        for value in ('../1.5.2', 'v1.5.2', '1.5.2\nother'):
            with self.assertRaises(ValueError):
                core.version_ok(value)

    def test_collect_rejects_missing_target_and_mixed_sources(self):
        with self.assertRaisesRegex(ValueError, 'Incomplete'):
            pipeline.collect(self.root, ['1.21.1-fabric'], {'main': SHA})
        core.write_json(self.root/'target.json', {'target':'1.21.1-fabric', 'source_sha': OTHER, 'attempt':1})
        with self.assertRaisesRegex(ValueError, 'Incomplete'):
            pipeline.collect(self.root, ['1.21.1-fabric'], {'main': SHA})

    def test_collect_uses_latest_attempt(self):
        for attempt in (1,2):
            core.write_json(self.root/str(attempt)/'target.json',
                            {'target':'1.21.1-fabric', 'source_sha':SHA, 'attempt':attempt})
        found = pipeline.collect(self.root, ['1.21.1-fabric'], {'main': SHA})
        self.assertEqual(found['1.21.1-fabric'][1]['attempt'], 2)


class PublishingTests(unittest.TestCase):
    def run_record(self, **updates):
        value = {'event':'workflow_dispatch', 'head_branch':'main', 'conclusion':'success', 'status':'completed',
                 'path':'.github/workflows/candidate.yml', 'head_sha':SHA}
        value.update(updates)
        return value

    def test_candidate_origin_and_expiry(self):
        artifact = {'id':123, 'name':'candidate-42-1', 'expired':False}
        cases = [{'event':'pull_request'}, {'head_branch':core.COMPAT}, {'conclusion':'failure'},
                 {'path':'.github/workflows/build.yml'}, {'status':'in_progress'}]
        for changes in cases:
            with patch.object(publish,'api',return_value=self.run_record(**changes)):
                with self.assertRaises(ValueError):
                    publish.candidate_artifact('teamCreating/MTR-Map-Overlay','42')
        with patch.object(publish,'api',return_value=self.run_record()), patch.object(publish,'pages',return_value=[artifact]):
            self.assertEqual(publish.candidate_artifact('repo','42')[1]['id'],123)
            with self.assertRaises(ValueError):
                publish.candidate_artifact('repo','42','999')
        artifact['expired']=True
        with patch.object(publish,'api',return_value=self.run_record()), patch.object(publish,'pages',return_value=[artifact]):
            with self.assertRaisesRegex(ValueError,'expired'):
                publish.candidate_artifact('repo','42')

    def test_preexisting_tags_are_never_moved(self):
        manifest={'versions':{'main':'1.5.2'},'source':{'main':SHA,'compat':OTHER}}
        with patch.object(publish,'existing_tag',return_value='c'*40), patch.object(publish,'api') as mutation:
            with self.assertRaisesRegex(ValueError,'never overwrite'):
                publish.check_tags('repo',manifest)
            mutation.assert_not_called()

    def test_select_pins_artifact_and_digest_without_writes(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'release-manifest.json'; path.write_text('{}')
            manifest={'workflow_sha':SHA,'versions':{'main':'1.5.2'},'source':{'main':SHA,'compat':OTHER}}
            args=argparse.Namespace(run_id='42',out=temp,dry_run=False)
            with patch.object(publish,'repository',return_value='repo'), \
                 patch.object(publish,'candidate_artifact',return_value=(self.run_record(html_url='url'),{'id':123})), \
                 patch.object(publish,'download_artifact'), patch.object(publish,'check_bundle',return_value=manifest), \
                 patch.object(publish,'check_tags'), patch.object(publish,'summary'), patch.object(publish,'output') as outputs, \
                 patch.object(publish,'api') as writes:
                publish.select(args)
                self.assertEqual(outputs.call_args.kwargs['artifact_id'],123)
                self.assertEqual(outputs.call_args.kwargs['manifest_sha256'],core.sha256(path))
                writes.assert_not_called()

    def test_postapproval_digest_mismatch_stops_before_tag_creation(self):
        with tempfile.TemporaryDirectory() as temp:
            (Path(temp)/'release-manifest.json').write_text('{}')
            args=argparse.Namespace(run_id='42',artifact_id='123',out=temp,manifest_sha256='0'*64,validation='tested')
            with patch.object(publish,'repository',return_value='repo'), \
                 patch.object(publish,'candidate_artifact',return_value=(self.run_record(),{'id':123})), \
                 patch.object(publish,'download_artifact'), patch.object(publish,'api') as writes:
                with self.assertRaisesRegex(ValueError,'changed after selection'):
                    publish.release(args)
                writes.assert_not_called()

    def test_snapshot_rejects_pr_and_foreign_origin(self):
        trigger={'head_repository':{'full_name':'teamCreating/MTR-Map-Overlay'},'event':'pull_request',
                 'head_branch':'main','conclusion':'success','path':'.github/workflows/build.yml'}
        with patch.dict(os.environ,GITHUB_REPOSITORY='teamCreating/MTR-Map-Overlay'), patch.object(publish,'api') as writes:
            with self.assertRaisesRegex(ValueError,'Untrusted'):
                publish.snapshot_one(argparse.Namespace(out='unused'),trigger)
            trigger['head_repository']['full_name']='foreign/repo'
            with self.assertRaisesRegex(ValueError,'Foreign'):
                publish.snapshot_one(argparse.Namespace(out='unused'),trigger)
            writes.assert_not_called()

    def test_snapshot_skips_stale_build_before_download(self):
        trigger={'head_repository':{'full_name':'teamCreating/MTR-Map-Overlay'},'event':'push',
                 'head_branch':'main','head_sha':SHA,'conclusion':'success','path':'.github/workflows/build.yml'}
        with patch.dict(os.environ,GITHUB_REPOSITORY='teamCreating/MTR-Map-Overlay'), \
             patch.object(publish,'api',return_value={'object':{'sha':OTHER}}), \
             patch.object(publish,'summary'), patch.object(publish,'download_artifact') as download:
            publish.snapshot_one(argparse.Namespace(out='unused'),trigger)
            download.assert_not_called()

    def test_snapshot_upload_failure_keeps_old_public_links(self):
        trigger={'id':42,'head_repository':{'full_name':'teamCreating/MTR-Map-Overlay'},'event':'push',
                 'head_branch':'main','head_sha':SHA,'conclusion':'success','path':'.github/workflows/build.yml',
                 'html_url':'https://github.com/example/run/42'}
        original={'id':1,'tag_name':'snapshot','prerelease':True,'draft':False,
                  'body':'old downloads\n<!-- snapshot-state:snapshot-state-old.json -->'}
        manifest={'source':{'main':SHA},'versions':{'main':'1.5.2'},'created_at':'now',
                  'targets':[{'target':t,'jar':t+'.jar','sha256':'a'*64} for t in core.targets_for(core.MAIN)]}
        def page_results(path, key=None):
            if '/actions/runs/' in path:
                return [{'id':99,'name':'ci-bundle-42-1','expired':False}]
            return [{'id':11,'name':'snapshot-state-old.json'}]
        with tempfile.TemporaryDirectory() as temp:
            for row in manifest['targets']:
                (Path(temp)/row['jar']).write_bytes(b'new jar')
            with patch.dict(os.environ,GITHUB_REPOSITORY='teamCreating/MTR-Map-Overlay'), \
                 patch.object(publish,'api',return_value={'object':{'sha':SHA}}) as calls, \
                 patch.object(publish,'pages',side_effect=page_results), patch.object(publish,'download_artifact'), \
                 patch.object(publish,'check_bundle',return_value=manifest), \
                 patch.object(publish,'release_by_tag',return_value=original), \
                 patch.object(publish,'asset_content',return_value=b'{"schema":1,"branches":{}}'), \
                 patch.object(publish,'ensure_asset',side_effect=RuntimeError('upload failed')):
                with self.assertRaisesRegex(RuntimeError,'upload failed'):
                    publish.snapshot_one(argparse.Namespace(out=temp),trigger)
                self.assertTrue(all(len(c.args)==1 for c in calls.call_args_list), 'Public metadata was changed')
                self.assertTrue(original['body'].startswith('old downloads'))

    def test_existing_asset_with_different_content_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'mod.jar';path.write_bytes(b'candidate')
            asset={'name':'mod.jar','state':'uploaded','size':9,'digest':'sha256:'+'0'*64}
            with patch.object(publish,'pages',return_value=[asset]), patch.object(publish,'upload') as upload:
                with self.assertRaisesRegex(ValueError,'differs'):
                    publish.ensure_asset('repo',{'id':1},path)
                upload.assert_not_called()


if __name__ == '__main__':
    unittest.main()
