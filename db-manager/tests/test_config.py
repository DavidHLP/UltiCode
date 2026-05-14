from __future__ import annotations

import os
from pathlib import Path
from unittest.mock import patch

import pytest

from db_manager.config import (
    find_env_file,
    get_database_url,
    get_jdbc_url,
    load_config,
    _parse_database_url,
)


class TestFindEnvFile:
    def test_finds_env_in_current_directory(self, tmp_path: Path) -> None:
        env_file = tmp_path / ".env"
        env_file.write_text("DB_HOST=testhost\n")
        found = find_env_file(tmp_path)
        assert found == env_file

    def test_finds_env_in_parent_directory(self, tmp_path: Path) -> None:
        child = tmp_path / "child"
        child.mkdir()
        env_file = tmp_path / ".env"
        env_file.write_text("DB_HOST=testhost\n")
        found = find_env_file(child)
        assert found == env_file

    def test_returns_none_when_no_env_found(self, tmp_path: Path) -> None:
        found = find_env_file(tmp_path)
        assert found is None

    def test_uses_cwd_when_no_start_path(self, monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
        monkeypatch.chdir(tmp_path)
        env_file = tmp_path / ".env"
        env_file.write_text("DB_HOST=testhost\n")
        found = find_env_file()
        assert found == env_file


class TestLoadConfig:
    @patch("db_manager.config.find_env_file")
    @patch("db_manager.config.load_dotenv")
    def test_loads_from_separate_env_vars(self, mock_load_dotenv, mock_find_env, monkeypatch: pytest.MonkeyPatch) -> None:
        mock_find_env.return_value = None
        monkeypatch.setenv("DB_HOST", "myhost")
        monkeypatch.setenv("DB_PORT", "3307")
        monkeypatch.setenv("DB_USER", "myuser")
        monkeypatch.setenv("DB_PASSWORD", "mypass")
        monkeypatch.setenv("DB_NAME", "mydb")
        monkeypatch.delenv("DATABASE_URL", raising=False)
        config = load_config()
        assert config == {
            "host": "myhost",
            "port": 3307,
            "user": "myuser",
            "password": "mypass",
            "database": "mydb",
        }

    @patch("db_manager.config.find_env_file")
    @patch("db_manager.config.load_dotenv")
    def test_uses_defaults_when_no_env_vars(self, mock_load_dotenv, mock_find_env, monkeypatch: pytest.MonkeyPatch) -> None:
        mock_find_env.return_value = None
        for key in ["DATABASE_URL", "DB_HOST", "DB_PORT", "DB_USER", "DB_PASSWORD", "DB_NAME"]:
            monkeypatch.delenv(key, raising=False)
        config = load_config()
        assert config["host"] == "localhost"
        assert config["port"] == 23306
        assert config["user"] == ""
        assert config["password"] == ""
        assert config["database"] == "ulticode"

    @patch("db_manager.config.find_env_file")
    @patch("db_manager.config.load_dotenv")
    def test_prefers_database_url_over_separate_vars(self, mock_load_dotenv, mock_find_env, monkeypatch: pytest.MonkeyPatch) -> None:
        mock_find_env.return_value = None
        monkeypatch.setenv("DATABASE_URL", "mysql://urluser:urlpass@urlhost:3308/urldb")
        monkeypatch.setenv("DB_HOST", "myhost")
        config = load_config()
        assert config["host"] == "urlhost"
        assert config["port"] == 3308
        assert config["user"] == "urluser"
        assert config["password"] == "urlpass"
        assert config["database"] == "urldb"

    @patch("db_manager.config.find_env_file")
    @patch("db_manager.config.load_dotenv")
    def test_loads_from_env_file(self, mock_load_dotenv, mock_find_env, tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
        env_file = tmp_path / ".env"
        env_file.write_text("DB_HOST=filehost\nDB_PORT=3309\nDB_USER=fileuser\n")
        mock_find_env.return_value = env_file
        for key in ["DATABASE_URL", "DB_HOST", "DB_PORT", "DB_USER", "DB_PASSWORD", "DB_NAME"]:
            monkeypatch.delenv(key, raising=False)
        from dotenv import load_dotenv as real_load_dotenv
        real_load_dotenv(env_file, override=True)
        config = load_config()
        assert config["host"] == "filehost"
        assert config["port"] == 3309
        assert config["user"] == "fileuser"


class TestParseDatabaseUrl:
    def test_parses_standard_url(self) -> None:
        result = _parse_database_url("mysql://user:pass@host:3306/dbname")
        assert result["host"] == "host"
        assert result["port"] == 3306
        assert result["user"] == "user"
        assert result["password"] == "pass"
        assert result["database"] == "dbname"

    def test_parses_url_with_encoded_characters(self) -> None:
        result = _parse_database_url("mysql://user:p%40ss@host:3306/db")
        assert result["password"] == "p@ss"

    def test_uses_defaults_for_missing_components(self) -> None:
        result = _parse_database_url("mysql://host/db")
        assert result["host"] == "host"
        assert result["port"] == 23306
        assert result["user"] == ""
        assert result["password"] == ""
        assert result["database"] == "db"

    def test_parses_url_with_query_params(self) -> None:
        result = _parse_database_url("mysql://user:pass@host:3306/db?ssl=true")
        assert result["database"] == "db"
        assert result["query"] == {"ssl": ["true"]}


class TestGetDatabaseUrl:
    def test_builds_url_from_config(self) -> None:
        config = {
            "user": "u",
            "password": "p",
            "host": "h",
            "port": 1234,
            "database": "db",
        }
        url = get_database_url(config)
        assert url == "mysql+pymysql://u:p@h:1234/db"

    def test_uses_defaults_when_config_missing_keys(self) -> None:
        url = get_database_url({})
        assert url == "mysql+pymysql://:@localhost:23306/ulticode"

    @patch("db_manager.config.load_config")
    def test_loads_config_when_none_provided(self, mock_load_config) -> None:
        mock_load_config.return_value = {
            "host": "autohost",
            "port": 5555,
            "user": "autouser",
            "password": "autopass",
            "database": "autodb",
        }
        url = get_database_url()
        assert url == "mysql+pymysql://autouser:autopass@autohost:5555/autodb"


class TestGetJdbcUrl:
    def test_builds_jdbc_url_from_config(self) -> None:
        config = {
            "user": "u",
            "password": "p",
            "host": "h",
            "port": 1234,
            "database": "db",
        }
        url = get_jdbc_url(config)
        assert url == (
            "jdbc:mysql://h:1234/db"
            "?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8"
        )

    def test_uses_defaults_when_config_missing_keys(self) -> None:
        url = get_jdbc_url({})
        assert "jdbc:mysql://localhost:23306/ulticode" in url
        assert "useUnicode=true" in url
        assert "characterEncoding=UTF-8" in url

    @patch("db_manager.config.load_config")
    def test_loads_config_when_none_provided(self, mock_load_config) -> None:
        mock_load_config.return_value = {
            "host": "jdbchost",
            "port": 6666,
            "user": "",
            "password": "",
            "database": "jdbcdb",
        }
        url = get_jdbc_url()
        assert "jdbc:mysql://jdbchost:6666/jdbcdb" in url
