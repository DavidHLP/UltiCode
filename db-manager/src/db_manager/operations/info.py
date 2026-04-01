"""Database info operation using Flyway."""

from __future__ import annotations

from rich.console import Console
from rich.table import Table

from ..flyway_adapter import get_flyway_adapter


console = Console()


def info() -> dict[str, int]:
    """Get Flyway migration info.

    Returns:
        Dict with operation counts: {"success": N, "errors": N}
    """
    adapter = get_flyway_adapter()

    if not adapter.check_flyway_installed():
        console.print(
            "[red]Error: Flyway CLI not found.[/red]\n"
            "Please install Flyway from https://flyway.net/ or use:\n"
            "  docker pull flyway/flyway  # Docker"
        )
        return {"success": 0, "errors": 1}

    if adapter.use_docker:
        console.print("[dim]Using Docker to run Flyway[/dim]\n")

    console.print("[bold]Fetching migration info...[/bold]")

    result = adapter.info()

    if result["returncode"] == 0:
        output = result["stdout"]

        if "|" not in output:
            console.print(output)
            return {"success": 1, "errors": 0}

        table = Table(title="Migration Status")
        table.add_column("Version", style="cyan")
        table.add_column("Description", style="green")
        table.add_column("Type", style="yellow")
        table.add_column("Installed On", style="dim")
        table.add_column("State", style="magenta")

        header_skipped = False
        for line in output.split("\n"):
            stripped = line.strip()
            if not stripped.startswith("|"):
                if "----" in stripped and "+" in stripped:
                    header_skipped = True
                continue
            if not header_skipped:
                continue
            # Skip column header row (Category | Version | Description ...)
            parts = [p.strip() for p in stripped.split("|")]
            if parts[2].lower() == "version":
                continue

            parts = [p.strip() for p in stripped.split("|")]
            if len(parts) >= 8:
                version = parts[2]
                desc = parts[3]
                mtype = parts[4]
                installed = parts[5] or "-"
                state = parts[6]

                if version:
                    state_style = "green" if state == "Success" else "red" if state == "Failed" else "yellow"
                    table.add_row(
                        version, desc, mtype, installed,
                        f"[{state_style}]{state}[/{state_style}]",
                    )

        if table.row_count > 0:
            console.print(table)
        else:
            console.print(output)

        return {"success": 1, "errors": 0}
    else:
        console.print("[red]Failed to get migration info[/red]")
        if result["stderr"]:
            console.print(f"[red]{result['stderr']}[/red]")
        return {"success": 0, "errors": 1}
