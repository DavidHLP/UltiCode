"""Database migrate operation using Flyway."""

from __future__ import annotations

from rich.console import Console

from ..flyway_adapter import get_flyway_adapter


console = Console()


def migrate(dry_run: bool = False) -> dict[str, int]:
    """Run Flyway migrate.

    Args:
        dry_run: If True, validate without applying

    Returns:
        Dict with operation counts: {"success": N, "errors": N}
    """
    adapter = get_flyway_adapter()

    # Check if Flyway is installed
    if not adapter.check_flyway_installed():
        console.print(
            "[red]Error: Flyway CLI not found.[/red]\n"
            "Please install Flyway from https://flyway.net/ or use:\n"
            "  brew install flyway    # macOS\n"
            "  apt install flyway     # Ubuntu/Debian\n"
            "  docker pull flyway/flyway  # Docker"
        )
        return {"success": 0, "errors": 1}

    console.print("[bold]Running Flyway migrate...[/bold]")

    if dry_run:
        console.print("[yellow](Dry run - no changes will be made)[/yellow]")

    result = adapter.migrate(dry_run=dry_run)

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
