#!/usr/bin/env python3
import importlib.util
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('audit', Path(__file__).with_name('audit-apk-provenance.py'))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


def dex_with_class(name):
    # Header, one string_id, one type_id, one class_def, then string data.
    dex = bytearray(154 + len(name))
    dex[:8] = b'dex\n035\0'
    struct.pack_into('<II', dex, 56, 1, 112)
    struct.pack_into('<II', dex, 64, 1, 116)
    struct.pack_into('<II', dex, 96, 1, 120)
    struct.pack_into('<I', dex, 112, 152)
    dex[152] = len(name)
    dex[153:153 + len(name)] = name.encode()
    return bytes(dex)


class PackageAuditTest(unittest.TestCase):
    def test_reads_defined_class_descriptors(self):
        name = 'Lcom/phlox/tvwebbrowser/Original;'
        self.assertEqual([name], audit.class_names(dex_with_class(name)))
        with self.assertRaises(ValueError): audit.class_names(b'not a dex')

    def run_package(self, root, class_name, extras=()):
        package = root / 'example.apk'
        with zipfile.ZipFile(package, 'w') as apk:
            apk.writestr('classes.dex', dex_with_class(class_name))
            for path in audit.PACKAGED_SOURCES:
                source = root / 'app/src/main/assets' / path
                source.parent.mkdir(parents=True, exist_ok=True)
                source.write_bytes(b'reviewed source')
                apk.writestr('assets/' + path, source.read_bytes())
            for name in extras: apk.writestr(name, b'retired')
        def dump(args, **kwargs):
            if args[2] == 'badging': return "package: name='com.reiniertutoriales.vireolumatv'"
            return 'resource string/based_on_tv_bro_sources'
        with patch.object(audit.subprocess, 'check_output', side_effect=dump):
            return audit.audit(package, Path('aapt2'), root)

    def test_accepts_matching_sources_with_notices(self):
        with tempfile.TemporaryDirectory() as folder:
            report = self.run_package(Path(folder), 'Lcom/reiniertutoriales/vireolumatv/Main;')
            self.assertEqual([], report['checks']['issues'])
            self.assertEqual(64, len(report['sha256']))

    def test_rejects_original_namespace_and_deleted_assets(self):
        with tempfile.TemporaryDirectory() as folder:
            report = self.run_package(Path(folder), 'Lcom/phlox/tvwebbrowser/Original;',
                                      ['assets/pages/ic_warning.svg'])
            self.assertEqual(2, len(report['checks']['issues']))
            self.assertEqual(['pages/ic_warning.svg'], report['checks']['removed_assets_still_packaged'])


if __name__ == '__main__': unittest.main()
