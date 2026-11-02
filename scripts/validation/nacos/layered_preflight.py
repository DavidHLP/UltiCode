"""Bounded preflight checks. Only fixed enums and booleans leave this module."""
from pathlib import Path
import base64, os, re, subprocess, tempfile, zipfile
import xml.etree.ElementTree as ET

JVM_STATES=('pass','nonzero','timeout','not_executable','probe_error')
INPUT_KEYS=('jar_readable','jar_structure','jar_crc','main_class_present',
            'config_readable','config_shape','home_writable')

def jvm_probe(java, run=subprocess.run):
    result={'status':'probe_error','major_matches':False,'output_bounded':True}
    if not Path(java).is_file() or not os.access(java,os.X_OK):
        result['status']='not_executable';return result
    try:
        with tempfile.TemporaryFile() as output:
            p=run([str(java),'-version'],stdout=output,stderr=subprocess.STDOUT,timeout=10)
            size=output.tell();output.seek(0);data=output.read(65536)
            result['output_bounded']=size<=65536
            match=re.search(rb'^(?:openjdk|java) version "(\d+)(?:[.\-"]|$)',data,re.M)
            result['major_matches']=bool(match and match.group(1)==b'17')
            result['status']='pass' if p.returncode==0 and result['major_matches'] and result['output_bounded'] else 'nonzero'
    except subprocess.TimeoutExpired:result['status']='timeout'
    except Exception:result['status']='probe_error'
    return result

def input_probe(dist,home):
    result=dict.fromkeys(INPUT_KEYS,False)
    jar=Path(dist)/'target/nacos-server.jar';home=Path(home)
    result['jar_readable']=jar.is_file() and os.access(jar,os.R_OK)
    try:
        with zipfile.ZipFile(jar) as z:
            entries=z.infolist();names=[e.filename for e in entries]
            if len(entries)>10000 or len(set(names))!=len(names) or sum(e.file_size for e in entries)>536870912:
                return result
            result['jar_structure']=True
            result['jar_crc']=z.testzip() is None
            info=z.getinfo('META-INF/MANIFEST.MF')
            if info.file_size<=65536:
                raw=z.read(info).decode('utf-8');raw=raw.replace('\r\n','\n').replace('\n ','')
                attrs=dict(line.split(': ',1) for line in raw.splitlines() if ': ' in line)
                main=attrs.get('Main-Class','')
                result['main_class_present']=bool(re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_$.]*',main) and main.replace('.','/')+'.class' in names)
    except Exception:pass
    try:
        prop=home/'conf/application.properties';xml=home/'conf/nacos-logback.xml'
        if prop.stat().st_size<=1048576 and xml.stat().st_size<=1048576:
            content=prop.read_text();xml_content=xml.read_text();result['config_readable']=True
            values={}
            for line in content.splitlines():
                line=line.strip()
                if line and not line.startswith(('#','!')) and '=' in line:
                    k,v=line.split('=',1);values[k.strip()]=v.strip()
            secret=base64.b64decode(values.get('nacos.core.auth.plugin.nacos.token.secret.key',''),validate=True)
            xml_ok='<!DOCTYPE' not in xml_content and '<!ENTITY' not in xml_content
            if xml_ok:xml_ok=ET.fromstring(xml_content).tag=='configuration'
            result['config_shape']=bool(xml_ok and values.get('server.address')=='127.0.0.1'
                and values.get('nacos.core.auth.enabled')=='true'
                and values.get('nacos.core.auth.system.type')=='nacos'
                and values.get('nacos.core.auth.server.identity.key')
                and values.get('nacos.core.auth.server.identity.value') and len(secret)>=32)
    except Exception:pass
    try:
        with tempfile.TemporaryFile(dir=home) as f:f.write(b'probe');f.flush()
        result['home_writable']=True
    except Exception:pass
    return result

def sanitize(value):
    value=value if isinstance(value,dict) else {}
    jvm=value.get('jvm');jvm=jvm if isinstance(jvm,dict) else {}
    inputs=value.get('inputs');inputs=inputs if isinstance(inputs,dict) else {}
    return {'jvm':{'status':jvm.get('status') if isinstance(jvm.get('status'),str) and jvm.get('status') in JVM_STATES else 'probe_error',
                   'major_matches':jvm.get('major_matches') is True,'output_bounded':jvm.get('output_bounded') is True},
            'inputs':{key:inputs.get(key) is True for key in INPUT_KEYS}}
