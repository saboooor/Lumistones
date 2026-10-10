import json
import io
import hashlib
from unittest.mock import patch
import base64
import zipfile
import tempfile
import unittest
from pathlib import Path
import yaml
from blockbench_model import export_model, import_model, matrix, texture, download_client


class ConverterTests(unittest.TestCase):
    def test_download_verifies_and_reuses_cached_client_offline(self):
        data = b'client contents'
        metadata = {'url': 'https://example.test/client.jar', 'size': len(data), 'sha1': hashlib.sha1(data).hexdigest()}
        manifest = {'versions': [{'id': '1.21.10', 'url': 'https://example.test/version.json'}]}
        with tempfile.TemporaryDirectory() as folder:
            responses = [io.BytesIO(json.dumps(manifest).encode()), io.BytesIO(json.dumps({'downloads': {'client': metadata}}).encode()), io.BytesIO(data)]
            with patch('blockbench_model.urllib.request.urlopen', side_effect=responses) as fetch:
                jar = download_client(cache=folder)
                self.assertEqual(data, jar.read_bytes())
                self.assertEqual(3, fetch.call_count)
            with patch('blockbench_model.urllib.request.urlopen', side_effect=AssertionError('Cache should work offline')):
                self.assertEqual(jar, download_client(cache=folder))

    def test_corrupt_download_does_not_replace_client_or_leave_partial_file(self):
        manifest = {'versions': [{'id': '1.21.10', 'url': 'https://example.test/version.json'}]}
        metadata = {'url': 'https://example.test/client.jar', 'size': 4, 'sha1': hashlib.sha1(b'good').hexdigest()}
        with tempfile.TemporaryDirectory() as folder:
            jar = Path(folder) / 'minecraft-1.21.10-client.jar'
            jar.write_bytes(b'previous')
            responses = [io.BytesIO(json.dumps(manifest).encode()), io.BytesIO(json.dumps({'downloads': {'client': metadata}}).encode()), io.BytesIO(b'bad!')]
            with patch('blockbench_model.urllib.request.urlopen', side_effect=responses), self.assertRaises(ValueError):
                download_client(cache=folder)
            self.assertEqual(b'previous', jar.read_bytes())
            self.assertFalse(jar.with_suffix('.jar.part').exists())

    def test_texture_reads_client_assets_and_folder_overrides(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive = root / 'client.jar'
            vanilla = base64.b64decode(texture('STONE').split(',', 1)[1])
            override = base64.b64decode(texture('SEA_LANTERN').split(',', 1)[1])
            with zipfile.ZipFile(archive, 'w') as jar:
                jar.writestr('assets/minecraft/textures/block/stone.png', vanilla)
            self.assertEqual(vanilla, base64.b64decode(texture('STONE', client_jar=archive).split(',', 1)[1]))
            (root / 'stone.png').write_bytes(override)
            self.assertEqual(override, base64.b64decode(texture('STONE', textures_dir=root, client_jar=archive).split(',', 1)[1]))

    def test_bundled_model_round_trip(self):
        source = Path(__file__).resolve().parents[1] / 'src/main/resources/model.yml'
        original = yaml.safe_load(source.read_text())
        with tempfile.TemporaryDirectory() as folder:
            bb, output = Path(folder) / 'model.bbmodel', Path(folder) / 'model.yml'
            export_model(source, bb)
            import_model(bb, output)
            actual = yaml.safe_load(output.read_text())
            self.assertEqual(original['label'], actual['label'])
            self.assertEqual(len(original['parts']), len(actual['parts']))
            for key, part in original['parts'].items():
                self.assertEqual(part['material'], actual['parts'][key]['material'])
                self.assertEqual(part.get('material-placeholder'), actual['parts'][key].get('material-placeholder'))
                for field in ['translation', 'scale']:
                    for a, b in zip(part[field], actual['parts'][key][field]):
                        self.assertAlmostEqual(a, b)

    def test_rotation_and_item_metadata_round_trip(self):
        with tempfile.TemporaryDirectory() as folder:
            source, bb, output = [Path(folder) / file for file in ['source.yml', 'model.bbmodel', 'out.yml']]
            part = {'type': 'item', 'material': 'AMETHYST_SHARD', 'custom-model-data': 42,
                    'translation': [.2, 1.5, -.4], 'rotation': [25, 40, 75], 'scale': [.4, .8, .3]}
            source.write_text(yaml.safe_dump({'parts': {'crystal': part}}))
            export_model(source, bb); import_model(bb, output)
            actual = yaml.safe_load(output.read_text())['parts']['crystal']
            self.assertEqual(actual['custom-model-data'], 42)
            for a, b in zip(part['translation'], actual['translation']):
                self.assertAlmostEqual(a, b)
            for row_a, row_b in zip(matrix(part['rotation']), matrix(actual['rotation'])):
                for a, b in zip(row_a, row_b): self.assertAlmostEqual(a, b)

    def test_moved_pivot_is_baked_into_translation(self):
        with tempfile.TemporaryDirectory() as folder:
            bb, output = Path(folder) / 'model.bbmodel', Path(folder) / 'out.yml'
            bb.write_text(json.dumps({'elements': [{'name': 'test [block:STONE]', 'from': [16, 0, 0], 'to': [32, 16, 16], 'origin': [0, 0, 0], 'rotation': [0, 0, 90]}]}))
            import_model(bb, output)
            position = yaml.safe_load(output.read_text())['parts']['test']['translation']
            self.assertAlmostEqual(position[0], 0); self.assertAlmostEqual(position[1], 1)

    def test_rotated_groups_reject_without_overwriting_output(self):
        with tempfile.TemporaryDirectory() as folder:
            bb, output = Path(folder) / 'model.bbmodel', Path(folder) / 'out.yml'
            bb.write_text(json.dumps({'elements': [], 'outliner': [{'rotation': [0, 45, 0]}]}))
            output.write_text('original')
            with self.assertRaises(ValueError): import_model(bb, output)
            self.assertEqual(output.read_text(), 'original')


if __name__ == '__main__': unittest.main()
