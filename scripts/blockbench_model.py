#!/usr/bin/env python3
"""Convert Lumistones model.yml to/from a Blockbench generic .bbmodel.
Requires PyYAML: python3 -m pip install -r scripts/requirements.txt
"""
import argparse
import base64
import json
import hashlib
import os
import urllib.request
import math
from pathlib import Path
import re
import struct
import uuid
import zlib
import zipfile
import yaml


MANIFEST_URL = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'


def download_client(version='1.21.10', cache=None):
    """Download only Mojang's client artifact, verify it, and reuse verified cached files."""
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*', version):
        raise ValueError('Invalid Minecraft version')
    cache = Path(cache) if cache else Path(os.environ.get('XDG_CACHE_HOME', Path.home() / '.cache')) / 'lumistones'
    cache.mkdir(parents=True, exist_ok=True)
    jar, receipt = cache / f'minecraft-{version}-client.jar', cache / f'minecraft-{version}.json'
    def verified(path, metadata):
        if not path.is_file() or path.stat().st_size != metadata['size']:
            return False
        with path.open('rb') as stream:
            return hashlib.file_digest(stream, 'sha1').hexdigest() == metadata['sha1']
    if receipt.is_file():
        try:
            if verified(jar, json.loads(receipt.read_text())):
                return jar
        except (ValueError, KeyError, TypeError):
            pass
    def read_json(url):
        with urllib.request.urlopen(url, timeout=30) as response:
            return json.load(response)
    manifest = read_json(MANIFEST_URL)
    entry = next((entry for entry in manifest['versions'] if entry['id'] == version), None)
    if entry is None:
        raise ValueError(f'Minecraft version not found: {version}')
    metadata = read_json(entry['url'])['downloads']['client']
    temporary = jar.with_suffix('.jar.part')
    try:
        print(f'Downloading Minecraft {version} client for textures...')
        with urllib.request.urlopen(metadata['url'], timeout=30) as response, temporary.open('wb') as output:
            while chunk := response.read(1024 * 1024):
                output.write(chunk)
        if not verified(temporary, metadata):
            raise ValueError('Client download failed size/SHA-1 verification; please retry')
        temporary.replace(jar)
        receipt.write_text(json.dumps(metadata))
    finally:
        temporary.unlink(missing_ok=True)
    return jar


def multiply(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def matrix(angles, blockbench=False):
    x, y, z = map(math.radians, angles)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return multiply(multiply(rz, ry), rx) if blockbench else multiply(multiply(rx, ry), rz)


def angles(r, blockbench=False):
    clamp = lambda n: max(-1, min(1, n))
    if blockbench:
        y = math.asin(clamp(-r[2][0]))
        x, z = (math.atan2(r[2][1], r[2][2]), math.atan2(r[1][0], r[0][0])) if abs(math.cos(y)) > 1e-8 else (math.atan2(-r[1][2], r[1][1]), 0)
    else:
        y = math.asin(clamp(r[0][2]))
        x, z = (math.atan2(-r[1][2], r[2][2]), math.atan2(-r[0][1], r[0][0])) if abs(math.cos(y)) > 1e-8 else (math.atan2(r[2][1], r[1][1]), 0)
    return [round(math.degrees(v), 8) for v in (x, y, z)]


def transform(r, point):
    return [sum(r[i][j] * point[j] for j in range(3)) for i in range(3)]


def texture(material, textures_dir=None, client_jar=None, kind='block'):
    filename = material.lower() + '.png'
    if textures_dir:
        root = Path(textures_dir)
        for candidate in [root / filename, root / kind / filename, root / 'assets/minecraft/textures' / kind / filename]:
            if candidate.is_file():
                return 'data:image/png;base64,' + base64.b64encode(candidate.read_bytes()).decode()
    if client_jar:
        with zipfile.ZipFile(client_jar) as archive:
            for category in [kind, 'block' if kind == 'item' else 'item']:
                try:
                    data = archive.read(f'assets/minecraft/textures/{category}/{filename}')
                    return 'data:image/png;base64,' + base64.b64encode(data).decode()
                except KeyError:
                    pass
    print(f'No texture found for {material}; using a color preview.')

    colors = {'POLISHED_DEEPSLATE': (72, 72, 78), 'CHISELED_STONE_BRICKS': (125, 125, 125), 'AMETHYST_BLOCK': (150, 100, 200), 'SEA_LANTERN': (180, 230, 220)}
    color = colors.get(material, (130, 150, 165))
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    pixels = b''.join(b'\0' + bytes(color + (255,)) * 16 for _ in range(16))
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(pixels)) + chunk(b'IEND', b'')
    return 'data:image/png;base64,' + base64.b64encode(png).decode()


def export_model(source, target, textures_dir=None, client_jar=None):
    data = yaml.safe_load(Path(source).read_text())
    elements, textures, palette = [], [], {}
    for name, part in data['parts'].items():
        material, kind = part['material'], part.get('type', 'block')
        if material not in palette:
            palette[material] = len(textures)
            textures.append({'name': material, 'uuid': str(uuid.uuid4()), 'id': str(len(textures)), 'source': texture(material, textures_dir, client_jar, kind), 'width': 16, 'height': 16})
        position = [v * 16 for v in part.get('translation', [0, 0, 0])]
        size = [v * 16 for v in part.get('scale', [1, 1, 1])]
        start = [position[i] - size[i] / 2 for i in range(3)] if kind == 'item' else position
        tag = f"{kind}:{material}" + (f":{part.get('custom-model-data', 0)}" if kind == 'item' else '')
        elements.append({'name': f'{name}' + (' {body_block}' if part.get('material-placeholder') == 'body_block' else '') + f' [{tag}]', 'type': 'cube', 'uuid': str(uuid.uuid4()), 'from': start,
                         'to': [start[i] + size[i] for i in range(3)], 'origin': position,
                         'rotation': angles(matrix(part.get('rotation', [0, 0, 0])), True), 'box_uv': False,
                         'faces': {face: {'uv': [0, 0, 16, 16], 'texture': palette[material]} for face in ['north', 'east', 'south', 'west', 'up', 'down']}})
    model = {'meta': {'format_version': '4.10', 'model_format': 'generic', 'box_uv': False}, 'name': 'Lumistone',
             'resolution': {'width': 16, 'height': 16}, 'elements': elements, 'outliner': [e['uuid'] for e in elements],
             'textures': textures, 'lumistones_label': data.get('label', {}), 'lumistones_runes': data.get('runes', {})}
    Path(target).write_text(json.dumps(model, indent=2) + '\n')


def import_model(source, target):
    model = json.loads(Path(source).read_text())
    def groups(nodes):
        for node in nodes:
            if isinstance(node, dict):
                if any(abs(v) > 1e-8 for v in node.get('rotation', [0, 0, 0])):
                    raise ValueError('Rotated groups are unsupported. Bake group rotations into cubes before conversion.')
                groups(node.get('children', []))
    groups(model.get('outliner', []))
    parts = {}
    for cube in model['elements']:
        if cube.get('export', True) is False:
            continue
        if cube.get('type', 'cube') != 'cube':
            raise ValueError('Only cubes are supported; convert meshes to cubes first.')
        name = cube.get('name', '')
        match = re.fullmatch(r'(.*?)\s*\[(block|item):([A-Z0-9_]+)(?::(\d+))?\]', name)
        if match:
            key, kind, material, custom = match.groups()
            key = key.strip() or 'part'
        elif re.fullmatch(r'[A-Z0-9_]+', name):
            key, kind, material, custom = name.lower(), 'block', name, None
        else:
            raise ValueError(f'Name cube "part [block:STONE]" or "part [item:AMETHYST_SHARD:123]": {name}')
        body_block = key.endswith(' {body_block}')
        if body_block: key = key.removesuffix(' {body_block}').strip()
        if '.' in key or key in parts:
            raise ValueError(f'Part names must be unique and contain no dots: {key}')
        start, end = cube['from'], cube['to']
        if any(not math.isfinite(v) for v in start + end + cube.get('rotation', []) + cube.get('origin', [])):
            raise ValueError('Coordinates must be finite')
        size = [(end[i] - start[i]) / 16 for i in range(3)]
        if any(v <= 0 for v in size):
            raise ValueError('Cubes must have positive dimensions; inflate and mirrored cubes are unsupported.')
        if cube.get('inflate', 0):
            raise ValueError('Bake cube inflate into dimensions before conversion.')
        pivot = cube.get('origin', [0, 0, 0])
        rotation = matrix(cube.get('rotation', [0, 0, 0]), True)
        reference = [(start[i] + end[i]) / 2 for i in range(3)] if kind == 'item' else start
        moved = transform(rotation, [reference[i] - pivot[i] for i in range(3)])
        part = {'type': kind, 'material': material, 'translation': [round((pivot[i] + moved[i]) / 16, 8) for i in range(3)],
                'rotation': angles(rotation), 'scale': [round(v, 8) for v in size]}
        if body_block: part['material-placeholder'] = 'body_block'
        if kind == 'item':
            part['custom-model-data'] = int(custom or 0)
        parts[key] = part
    if not parts or len(parts) > 128:
        raise ValueError('Models require between 1 and 128 exported parts')
    destination = Path(target)
    label = yaml.safe_load(destination.read_text()).get('label', {}) if destination.exists() else model.get('lumistones_label', {'enabled': True, 'scale': .4, 'view-range': .5})
    runes = yaml.safe_load(destination.read_text()).get('runes', {}) if destination.exists() else model.get('lumistones_runes', {})
    destination.write_text(yaml.safe_dump({'label': label, 'runes': runes, 'parts': parts}, sort_keys=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['export', 'import'])
    parser.add_argument('source', type=Path)
    parser.add_argument('target', type=Path)
    parser.add_argument('--textures', type=Path, help='Directory containing material PNG textures')
    parser.add_argument('--client-jar', nargs='?', const='download', help='Download the client automatically, or supply an existing JAR path')
    parser.add_argument('--minecraft-version', default='1.21.10', help='Minecraft client version to download (default: 1.21.10)')
    args = parser.parse_args()
    try:
        if args.action == 'export':
            client = download_client(args.minecraft_version) if args.client_jar == "download" else args.client_jar
            export_model(args.source, args.target, args.textures, client)
        else:
            import_model(args.source, args.target)
    except (ValueError, KeyError, TypeError, OSError, yaml.YAMLError) as error:
        parser.exit(1, f'Conversion failed: {error}\n')
