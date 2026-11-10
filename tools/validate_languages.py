"""Validate language key parity, duplicate keys, placeholders, and static Java translation references."""
import collections
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f'Duplicate translation key: {key}')
        result[key] = value
    return result


def validate():
    folder = ROOT / 'src/main/resources/assets/mtrmap/lang'
    catalogs = {p.stem: json.loads(p.read_text(encoding='utf-8'), object_pairs_hook=unique_object)
                for p in folder.glob('*.json')}
    base = catalogs['en_us']
    placeholder = re.compile(r'%(?:\d+\$)?s')
    for language, catalog in catalogs.items():
        assert catalog.keys() == base.keys(), (language, base.keys() - catalog.keys(), catalog.keys() - base.keys())
        for key, value in catalog.items():
            assert isinstance(value, str), (language, key)
            assert collections.Counter(placeholder.findall(value)) == collections.Counter(placeholder.findall(base[key])), (language, key)
            assert value or not base[key], (language, key, 'Unexpected empty translation')
    for folder in [ROOT / 'src/main/java', ROOT / 'fabric/src/main/java']:
        for path in folder.rglob('*.java'):
            for key in re.findall(r'"(mtrmap\.[\w.]+)"', path.read_text(encoding='utf-8')):
                if key.endswith(('.', '.toml', '.properties', '.json')):
                    continue
                assert key in base, (path, key, 'Translation not found')
    return {language: len(catalog) for language, catalog in catalogs.items()}


if __name__ == '__main__':
    print(json.dumps(validate(), indent=2))
