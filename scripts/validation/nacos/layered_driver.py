"""One direct server-only attempt, four fixed diagnostic layers; no SDK client."""
import json, subprocess, sys
from pathlib import Path
from layered_preflight import sanitize
from safe_failure import sanitize_diagnostic
ROOT=Path(__file__).resolve().parent

def main(run=subprocess.run):
    output=ROOT/'real-nacos-result.json';output.unlink(missing_ok=True)
    try:
        p=run(['timeout','--kill-after=10s','180s',sys.executable,
               str(ROOT/'real_nacos_test.py'),'--server-only','--launch-mode','direct','--layered'],
              stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=195)
        exit_ok=p.returncode==0
    except Exception:exit_ok=False
    try:
        if output.stat().st_size>131072:raise ValueError('oversized result')
        data=json.loads(output.read_text())
    except Exception:data={}
    if not isinstance(data,dict):data={}
    preflight=sanitize(data.get('preflight'));diagnostic=sanitize_diagnostic(data)
    passed=exit_ok and preflight['jvm']['status']=='pass' and preflight['jvm']['major_matches'] and preflight['jvm']['output_bounded'] and all(preflight['inputs'].values()) \
        and data.get('server_only') is True and data.get('launch_mode')=='direct' \
        and data.get('server_started') is True and data.get('admin_bootstrap_http')==200 \
        and data.get('passed') is True and data.get('stage')=='complete'
    sources=diagnostic['diagnostics']['sources']
    # These are evidence of files only, not proof of application initialization.
    logging_evidence=any(sources[k]['state']=='read' for k in ('application_root','application_config'))
    result={'schema':'nacos-layered-v1','mode':'direct','passed':passed,'preflight':preflight,
            'application_log_file_observed':logging_evidence,
            'readiness':data.get('server_started') is True,
            'bootstrap_ok':data.get('admin_bootstrap_http')==200,**diagnostic}
    print('NACOS_LAYERED='+json.dumps(result,sort_keys=True))
    return 0 if passed else 1
if __name__=='__main__':sys.exit(main())
