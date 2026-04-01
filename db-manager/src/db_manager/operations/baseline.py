"""Database baseline operation using Flyway."""

from __future__ import annotations

from rich.console import Console

from ..flyway_adapter import get_flyway_adapter


console = Console()


def baseline() -> dict[str, int]:
    """Create baseline for existing database.

    Returns:
        Dict with operation counts: {"success": N, "errors": N}
    """
    adapter = get_flyway_adapter()

    if not adapter.check_flyway_installed():
        console.print(
            "[red]Error: Flyway CLI not found.[/red]\n"
            "Please install Flyway from https://flyway.net/"
        )
        return {"success": 0, "errors": 1}

    console.print("[bold yellow]Running Flyway baseline...[/bold yellow]")
    console.print(
        "[yellow]This marks the current database state as baseline V1.[/yellow]\n"
        "Future migrations will be applied on top of this baseline."
    )

    result = adapter.baseline()

    if result["returncode"] == 0:
        console.print("[green]Baseline created successfully[/green]")
        if result["stdout"]:
            console.print(result["stdout"])
        return {"success": 1, "errors": 0}
    else:
        console.print("[red]Baseline creation failed[/red]")
        if result["stderr"]:
            console.print(f"[red]{result['stderr']}[/red]")
        return {"success": 0, "errors": 1}
