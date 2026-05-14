"""Shared utilities for database operations."""

from __future__ import annotations

from rich.console import Console


console = Console()


def check_flyway_installed(adapter) -> bool:
    """Check if Flyway is available, print error if not.

    Args:
        adapter: FlywayAdapter instance

    Returns:
        True if Flyway is installed, False otherwise
    """
    if not adapter.check_flyway_installed():
        console.print(
            "[red]Error: Flyway CLI not found.[/red]\n"
            "Please install Flyway from https://flyway.net/ or use:\n"
            "  brew install flyway    # macOS\n"
            "  apt install flyway     # Ubuntu/Debian\n"
            "  docker pull flyway/flyway  # Docker"
        )
        return False
    return True
