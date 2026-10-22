"""Verify six platform builds from two source directories (Python 3.12+)."""
import argparse
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent / 'ci'))
from core import MAIN, TARGETS, layout, properties, require, verify_jar, verify_tests

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--main', type=Path, default=ROOT)
parser.add_argument('--compat', type=Path, default=ROOT.parent / 'mtrmap-mc120')
args = parser.parse_args()
require(properties(args.main)['mod_version'] == properties(args.compat)['mod_version'], 'Source versions differ')
for target, (branch, mc, loader) in TARGETS.items():
    root = args.main if branch == MAIN else args.compat
    build, jar, _, _ = layout(root, target)
    verify_jar(jar, target, properties(root)['mod_version'], (root / 'LICENSE').read_bytes())
    tests_dir = args.main / 'build/test-results/test' if mc == '1.21.1' else build / 'test-results/test'
    tests = verify_tests(tests_dir, 86 if branch == MAIN else 89)
    print(f'{target}: namespace, metadata, bytecode, mappings, LICENSE and {tests["passed"]} shared tests OK')
