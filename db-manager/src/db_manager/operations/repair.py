"""Database repair operation using Flyway."""

from __future__ import annotations

from rich.console import Console

from ..flyway_adapter import get_flyway_adapter
from ._common import check_flyway_installed


console = Console()


def repair() -> dict[str, int]:
    """Repair Flyway metadata table.

    Returns:
        Dict with operation counts: {"success": N, "errors": N}
    """
    adapter = get_flyway_adapter()

    if not check_flyway_installed(adapter):
        return {"success": 0, "errors": 1}

    console.print("[bold yellow]Running Flyway repair...[/bold yellow]")
    console.print("[yellow]This will repair the schema_migrations table.[/yellow]")

    result = adapter.repair()

    if result["returncode"] == 0:
        console.print("[green]Repair completed successfully[/green]")
        if result["stdout"]:
            console.print(result["stdout"])
        return {"success": 1, "errors": 0}
    else:
        console.print("[red]Repair failed[/red]")
        if result["stderr"]:
            console.print(f"[red]{result['stderr']}[/red]")
        return {"success": 0, "errors": 1}
