#!/usr/bin/env python3
"""
GitHub Release Manager - Handles creating releases and uploading APKs
"""
import logging
from pathlib import Path
from typing import Optional

from github import Github
from github.GithubException import GithubException

import config

logger = logging.getLogger("GitHubReleaser")


class GitHubReleaser:
    """Handles GitHub release creation and asset uploads."""

    def __init__(self):
        self.github = None
        self.repo = None
        self._initialized = False

    def _ensure_initialized(self):
        """Lazy initialization - only connect when needed."""
        if self._initialized:
            return

        if not config.GITHUB_TOKEN:
            raise ValueError("GITHUB_TOKEN environment variable not set")

        self.github = Github(config.GITHUB_TOKEN)
        self.repo = self.github.get_repo(config.GITHUB_REPO)
        self._initialized = True
        logger.info(f"Connected to GitHub repo: {config.GITHUB_REPO}")

    def create_release(self, version: str, apk_path: Path,
                       notes: Optional[str] = None) -> str:
        """
        Create a GitHub release and upload the APK.

        Args:
            version: Version string (e.g., "1.3.0")
            apk_path: Path to the APK file
            notes: Optional release notes

        Returns:
            URL to the uploaded APK asset
        """
        self._ensure_initialized()
        tag_name = f"v{version}"
        release_name = f"IOCast v{version}"

        if notes is None:
            notes = f"Release v{version}"

        # Check if release already exists
        try:
            existing = self.repo.get_release(tag_name)
            logger.info(f"Release {tag_name} already exists, deleting...")
            existing.delete_release()
        except GithubException as e:
            if e.status != 404:
                raise

        # Create new release
        logger.info(f"Creating release {tag_name}")
        release = self.repo.create_git_release(
            tag=tag_name,
            name=release_name,
            message=notes,
            draft=False,
            prerelease=False
        )

        # Upload APK
        apk_name = f"iocast-v{version}.apk"
        logger.info(f"Uploading {apk_path} as {apk_name}")

        asset = release.upload_asset(
            path=str(apk_path),
            name=apk_name,
            content_type="application/vnd.android.package-archive"
        )

        download_url = asset.browser_download_url
        logger.info(f"APK uploaded successfully: {download_url}")

        return download_url

    def get_latest_release(self) -> Optional[dict]:
        """Get information about the latest release."""
        self._ensure_initialized()
        try:
            release = self.repo.get_latest_release()
            return {
                "tag": release.tag_name,
                "name": release.title,
                "url": release.html_url,
                "published_at": release.published_at.isoformat(),
                "assets": [
                    {
                        "name": asset.name,
                        "url": asset.browser_download_url,
                        "size": asset.size
                    }
                    for asset in release.get_assets()
                ]
            }
        except GithubException as e:
            if e.status == 404:
                return None
            raise

    def list_releases(self, limit: int = 10) -> list:
        """List recent releases."""
        self._ensure_initialized()
        releases = []
        for release in self.repo.get_releases()[:limit]:
            releases.append({
                "tag": release.tag_name,
                "name": release.title,
                "url": release.html_url,
                "published_at": release.published_at.isoformat() if release.published_at else None,
                "assets": [
                    {
                        "name": asset.name,
                        "url": asset.browser_download_url,
                        "size": asset.size
                    }
                    for asset in release.get_assets()
                ]
            })
        return releases
