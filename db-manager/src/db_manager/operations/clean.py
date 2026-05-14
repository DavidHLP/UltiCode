"""Database clean operation using Flyway."""

from __future__ import annotations

from rich.console import Console
from rich.panel import Panel

from ..flyway_adapter import get_flyway_adapter
from ._common import check_flyway_installed


console = Console()


def clean(force: bool = False) -> dict[str, int]:
    """Drop all database objects.

    Args:
        force: Skip confirmation if True

    Returns:
        Dict with operation counts: {"success": N, "errors": N}
    """
    adapter = get_flyway_adapter()

    if not check_flyway_installed(adapter):
        return {"success": 0, "errors": 1}

    if not force:
        console.print(
            Panel(
                "[bold red]WARNING: This will drop ALL database objects![/bold red]\n\n"
                "This includes all tables, views, indexes, and constraints.\n"
                "This operation is [red]irreversible[/red] and will result in data loss.\n\n"
                "If you want to proceed, run with --force flag:",
                title="Destructive Operation",
                border_style="red",
            )
        )
        console.print("[yellow]  db-manager clean --force[/yellow]")
        return {"success": 0, "errors": 1}

    console.print("[bold red]Running Flyway clean...[/bold red]")
    console.print("[red]Dropping all database objects...[/red]")

    result = adapter.clean(force=True)

    if result["returncode"] == 0:
        console.print("[green]Clean completed successfully[/green]")
        if result["stdout"]:
            console.print(result["stdout"])
        return {"success": 1, "errors": 0}
    else:
        console.print("[red]Clean failed[/red]")
        if result["stderr"]:
            console.print(f"[red]{result['stderr']}[/red]")
        return {"success": 0, "errors": 1}
