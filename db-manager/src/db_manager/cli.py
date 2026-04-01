"""CLI interface for UltiCode Database Manager using Flyway."""

from __future__ import annotations

import sys

import click
from rich.console import Console

from . import __version__
from .operations import migrate as migrate_op
from .operations import info as info_op
from .operations import repair as repair_op
from .operations import baseline as baseline_op
from .operations import clean as clean_op
from .operations import validate as validate_op


console = Console()


@click.group()
@click.version_option(version=__version__)
def main() -> None:
    """UltiCode Database Management Tool using Flyway.

    A Flyway-based database migration management tool.
    """
    pass


@main.command()
@click.option(
    "--dry-run",
    "-n",
    is_flag=True,
    help="Validate without applying migrations (dry run)",
)
def migrate(dry_run: bool) -> None:
    """Apply pending database migrations.

    Examples:

        db-manager migrate

        db-manager migrate --dry-run
    """
    try:
        result = migrate_op(dry_run=dry_run)
        if result["errors"] > 0:
            sys.exit(1)
    except Exception as e:
        console.print(f"[red]Error:[/red] {e}")
        sys.exit(1)


@main.command()
def info() -> None:
    """Show migration status and history.

    Examples:

        db-manager info
    """
    try:
        result = info_op()
        if result["errors"] > 0:
            sys.exit(1)
    except Exception as e:
        console.print(f"[red]Error:[/red] {e}")
        sys.exit(1)


@main.command()
def repair() -> None:
    """Repair the schema_migrations table.

    Use when migration history is inconsistent.

    Examples:

        db-manager repair
    """
    try:
        result = repair_op()
        if result["errors"] > 0:
            sys.exit(1)
    except Exception as e:
        console.print(f"[red]Error:[/red] {e}")
        sys.exit(1)


@main.command()
def baseline() -> None:
    """Create baseline for existing database.

    Marks current state as V1 for existing databases.

    Examples:

        db-manager baseline
    """
    try:
        result = baseline_op()
        if result["errors"] > 0:
            sys.exit(1)
    except Exception as e:
        console.print(f"[red]Error:[/red] {e}")
        sys.exit(1)


@main.command()
@click.option(
    "--force",
    "-f",
    is_flag=True,
    help="Skip confirmation and drop all objects",
)
def clean(force: bool) -> None:
    """Drop all database objects.

    WARNING: This is a destructive operation!

    Examples:

        db-manager clean --force
    """
    try:
        result = clean_op(force=force)
        if result["errors"] > 0:
            sys.exit(1)
    except Exception as e:
        console.print(f"[red]Error:[/red] {e}")
        sys.exit(1)


@main.command()
def validate() -> None:
    """Validate database state against migrations.

    Examples:

        db-manager validate
    """
    try:
        result = validate_op()
        if result["errors"] > 0:
            sys.exit(1)
    except Exception as e:
        console.print(f"[red]Error:[/red] {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
