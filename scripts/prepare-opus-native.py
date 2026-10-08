#!/usr/bin/env python3
"""Populate pinned native dependencies; preserve existing directories and local changes."""
import json,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];vendor=root/'native/vendor'
for item in json.loads((root/'scripts/opus-native-lock.json').read_text()):
 target=vendor/item['path'];rev=item['revision']
 if (target/'.git').exists() or (target/'.git').is_file():
  actual=subprocess.check_output(['git','-C',str(target),'rev-parse','HEAD'],text=True).strip()
  if actual!=rev:raise SystemExit('Preserving unexpected revision: '+str(target))
 elif target.exists() and any(target.iterdir()):raise SystemExit('Preserving existing directory: '+str(target))
 else:
  target.mkdir(parents=True,exist_ok=True)
  subprocess.run(['git','init',str(target)],check=True)
  subprocess.run(['git','-C',str(target),'remote','add','origin',item['url']],check=True)
  subprocess.run(['git','-C',str(target),'fetch','--depth','1','origin',rev],check=True)
  subprocess.run(['git','-C',str(target),'checkout','--detach',rev],check=True)
patch=root/'native/patches/ct2-android.patch';target=vendor/'ctranslate2'
if subprocess.run(['git','-C',str(target),'apply','--reverse','--check',str(patch)],capture_output=True).returncode!=0:
 subprocess.run(['git','-C',str(target),'apply','--check',str(patch)],check=True)
 subprocess.run(['git','-C',str(target),'apply',str(patch)],check=True)
