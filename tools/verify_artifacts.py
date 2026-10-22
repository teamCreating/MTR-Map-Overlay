"""Verify compatibility release JARs and shared tests (Python 3.12+)."""
import argparse
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent / 'ci'))
from core import layout, properties, verify_jar, verify_tests

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--loader', choices=('forge', 'fabric', 'all'), default='all')
parser.add_argument('--minecraft', choices=('1.20.1', '1.20.4'), default='1.20.1')
args = parser.parse_args()
for loader in (('forge', 'fabric') if args.loader == 'all' else (args.loader,)):
    target = f'{args.minecraft}-{loader}'
    build, jar, _, _ = layout(ROOT, target)
    verify_jar(jar, target, properties(ROOT)['mod_version'], (ROOT / 'LICENSE').read_bytes())
    tests = verify_tests(build / 'test-results/test', 89)
    print(f'{target}: namespace, metadata, bytecode, mappings, LICENSE and {tests["passed"]} tests OK')
