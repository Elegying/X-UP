from pathlib import Path
import argparse, json, shutil, hashlib, zipfile
from huggingface_hub import snapshot_download
from ctranslate2.converters import TransformersConverter
parser=argparse.ArgumentParser(description='Convert pinned Helsinki-NLP models to CTranslate2 INT8')
parser.add_argument('--output', type=Path, required=True)
root=parser.parse_args().output;root.mkdir(parents=True,exist_ok=True)
for pair,rev in [('en-zh','408d9bc410a388e1d9aef112a2daba955b945255'),('ja-en','0770961a39ba6bd66305b149c3f4110bcafca2e6')]:
 dest=root/pair
 source=snapshot_download('Helsinki-NLP/opus-mt-'+pair,revision=rev,allow_patterns=['config.json','pytorch_model.bin','*.spm','vocab.json','tokenizer_config.json','README.md'])
 if not (dest/'model.bin').exists():
  TransformersConverter(source).convert(str(dest),quantization='int8')
 for name in ['source.spm','target.spm']:shutil.copyfile(Path(source)/name,dest/name)
 print(pair,[(p.name,p.stat().st_size) for p in dest.iterdir()],flush=True)
manifest=[]
with zipfile.ZipFile(root/'xup-opus-en-ja-zh-int8-v1.zip','w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for pair in ['en-zh','ja-en']:
  for p in sorted((root/pair).iterdir()):
   if p.is_file():
    name=pair+'/'+p.name;z.write(p,name);manifest.append(dict(path=name,size=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
(root/'manifest.json').write_text(json.dumps(manifest,indent=2))
p=root/'xup-opus-en-ja-zh-int8-v1.zip';print('BUNDLE',p.stat().st_size,hashlib.sha256(p.read_bytes()).hexdigest(),flush=True)
