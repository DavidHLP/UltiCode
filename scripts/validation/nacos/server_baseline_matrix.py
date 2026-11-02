"""Run two bounded server-only baselines; never launch the candidate SDK."""
import json
from pathlib import Path
import subprocess
import sys
from safe_failure import sanitize_diagnostic

ROOT=Path(__file__).resolve().parent
MODES=('direct','official-equivalent')

def main(run=subprocess.run):
    results=[]
    for mode in MODES:
        output=ROOT/'real-nacos-result.json'
        output.unlink(missing_ok=True)  # Never reuse a previous mode's result.
        try:
            process=run(['timeout','--kill-after=10s','180s',sys.executable,
                         str(ROOT/'real_nacos_test.py'),'--server-only','--launch-mode',mode],
                        stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=195)
            exit_ok=process.returncode==0
        except subprocess.TimeoutExpired:
            exit_ok=False
        try: data=json.loads(output.read_text())
        except (OSError,ValueError): data={}
        if not isinstance(data,dict): data={}
        passed=exit_ok and data.get('server_only') is True and data.get('launch_mode')==mode \
            and data.get('server_started') is True and data.get('admin_bootstrap_http')==200 \
            and data.get('passed') is True and data.get('stage')=='complete'
        result={'mode':mode,'passed':passed,**sanitize_diagnostic(data)}
        results.append(result)
    passed=all(item['passed'] for item in results)
    print('NACOS_SERVER_BASELINE='+json.dumps({'passed':passed,'results':results},sort_keys=True))
    return 0 if passed else 1

if __name__=='__main__': sys.exit(main())
