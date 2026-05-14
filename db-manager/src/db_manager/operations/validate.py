"""Database validate operation using Flyway."""

from __future__ import annotations

from rich.console import Console

from ..flyway_adapter import get_flyway_adapter
from ._common import check_flyway_installed


console = Console()


def validate() -> dict[str, int]:
    """Validate Flyway migration state.

    Returns:
        Dict with operation counts: {"success": N, "errors": N}
    """
    adapter = get_flyway_adapter()

    if not check_flyway_installed(adapter):
        return {"success": 0, "errors": 1}

    console.print("[bold]Validating migration state...[/bold]")

    result = adapter.validate()

    if result["returncode"] == 0:
        console.print("[green]Validation successful[/green]")
        console.print("[green]Database state matches expected migration state.[/green]")
        if result["stdout"]:
            console.print(result["stdout"])
        return {"success": 1, "errors": 0}
    else:
        console.print("[red]Validation failed[/red]")
        console.print("[yellow]Database state may have drifted from expected state.[/yellow]")
        if result["stderr"]:
            console.print(f"[red]{result['stderr']}[/red]")
        if result["stdout"]:
            console.print(result["stdout"])
        return {"success": 0, "errors": 1}
