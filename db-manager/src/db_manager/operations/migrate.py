"""Database migrate operation using Flyway."""

from __future__ import annotations

from rich.console import Console

from ..flyway_adapter import get_flyway_adapter
from ._common import check_flyway_installed


console = Console()


def migrate(dry_run: bool = False) -> dict[str, int]:
    """Run Flyway migrate.

    Args:
        dry_run: If True, validate without applying

    Returns:
        Dict with operation counts: {"success": N, "errors": N}
    """
    adapter = get_flyway_adapter()

    if not check_flyway_installed(adapter):
        return {"success": 0, "errors": 1}

    console.print("[bold]Running Flyway migrate...[/bold]")

    if dry_run:
        console.print("[yellow](Dry run - no changes will be made)[/yellow]")

    result = adapter.migrate(dry_run=dry_run, out_of_order=True)

    if result["returncode"] == 0:
        console.print("[green]Migration completed successfully[/green]")
        if result["stdout"]:
            console.print(result["stdout"])
        return {"success": 1, "errors": 0}
    else:
        console.print("[red]Migration failed[/red]")
        if result["stderr"]:
            console.print(f"[red]{result['stderr']}[/red]")
        if result["stdout"]:
            console.print(result["stdout"])
        return {"success": 0, "errors": 1}
