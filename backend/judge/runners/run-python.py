#!/usr/bin/env python3
"""
Python code runner for the sandbox.
Executes user code with provided inputs and captures output.
"""

import json
import os
import sys
import traceback
import resource
import signal
from typing import Any
import ast

TIME_LIMIT_MS = int(os.environ.get('TIME_LIMIT_MS', '10000'))
INPUT_FILE = os.environ.get('INPUT_FILE', '/sandbox/input/args.json')
CODE_FILE = os.environ.get('CODE_FILE', '/sandbox/code/solution.py')


def timeout_handler(signum, frame):
    """Handle timeout."""
    print(json.dumps({
        'status': 'Time Limit Exceeded',
        'error': 'Execution timed out',
        'time': TIME_LIMIT_MS,
        'memory': 0
    }))
    sys.exit(1)


def parse_value(value: str) -> Any:
    """Parse a string value into its Python equivalent."""
    if not value or not value.strip():
        return ''
    try:
        return json.loads(value)
    except (json.JSONDecodeError, TypeError):
        return value


def format_value(value: Any) -> str:
    """Format a Python value as a string."""
    if value is None:
        return 'None'
    if isinstance(value, bool):
        return 'true' if value else 'false'
    if isinstance(value, str):
        return value
    if isinstance(value, (int, float)):
        return str(value)
    try:
        return json.dumps(value)
    except (TypeError, ValueError):
        return str(value)


def detect_entry_function(code: str) -> str | None:
    """Detect the main entry function in Python code."""
    tree = ast.parse(code)
    for node in ast.walk(tree):
        if isinstance(node, ast.FunctionDef):
            # Skip private functions and common utility names
            if not node.name.startswith('_') and node.name not in ('main', 'test'):
                return node.name
    # Fallback to 'main' or 'solution'
    if 'def solution' in code:
        return 'solution'
    if 'def solve' in code:
        return 'solve'
    if 'def main' in code:
        return 'main'
    return None


def main():
    # Set timeout handler
    signal.signal(signal.SIGALRM, timeout_handler)
    signal.alarm(TIME_LIMIT_MS // 1000 + 1)

    try:
        # Read code
        with open(CODE_FILE, 'r') as f:
            code = f.read()

        # Detect entry function
        entry_name = detect_entry_function(code)
        if not entry_name:
            print(json.dumps({
                'status': 'Compile Error',
                'error': 'Unable to detect the entry function name.',
                'time': 0,
                'memory': 0
            }))
            sys.exit(1)

        # Read inputs
        inputs = []
        if os.path.exists(INPUT_FILE):
            with open(INPUT_FILE, 'r') as f:
                input_data = json.load(f)
                inputs = input_data.get('args', [])

        # Parse input arguments
        args = [parse_value(inp) for inp in inputs]

        # Create execution namespace
        namespace = {
            '__builtins__': __builtins__,
            '__name__': '__main__',
        }

        # Execute code to define functions
        exec(code, namespace)

        # Get entry function
        entry_func = namespace.get(entry_name)
        if not callable(entry_func):
            print(json.dumps({
                'status': 'Compile Error',
                'error': f'Entry function "{entry_name}" was not found or is not callable.',
                'time': 0,
                'memory': 0
            }))
            sys.exit(1)

        # Execute entry function
        import time
        start_time = time.time()

        result = entry_func(*args)

        elapsed_ms = int((time.time() - start_time) * 1000)
        memory_mb = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024

        # Cancel timeout alarm
        signal.alarm(0)

        print(json.dumps({
            'status': 'Success',
            'output': format_value(result),
            'time': elapsed_ms,
            'memory': round(memory_mb, 2)
        }))

    except SyntaxError as e:
        print(json.dumps({
            'status': 'Compile Error',
            'error': f'Syntax error: {e.msg} at line {e.lineno}',
            'time': 0,
            'memory': 0
        }))
        sys.exit(1)
    except Exception as e:
        signal.alarm(0)
        print(json.dumps({
            'status': 'Runtime Error',
            'error': str(e),
            'time': 0,
            'memory': resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024
        }))
        sys.exit(1)


if __name__ == '__main__':
    main()
