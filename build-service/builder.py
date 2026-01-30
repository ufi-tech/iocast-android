#!/usr/bin/env python3
"""
Android Builder - Handles Docker-based APK builds
"""
import hashlib
import logging
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Callable, Optional

import docker

import config

logger = logging.getLogger("Builder")


class AndroidBuilder:
    """Handles cloning, building, and packaging Android APKs."""

    def __init__(self):
        self.docker_client = docker.from_env()
        self.work_dir: Optional[Path] = None
        self.container = None
        self.cancelled = False

    def clone_repo(self, branch: str = "main") -> Path:
        """Clone the GitHub repository."""
        self.work_dir = Path(tempfile.mkdtemp(prefix="iocast-build-"))
        repo_url = f"https://github.com/{config.GITHUB_REPO}.git"

        logger.info(f"Cloning {repo_url} branch {branch} to {self.work_dir}")

        result = subprocess.run(
            ["git", "clone", "--depth", "1", "-b", branch, repo_url, str(self.work_dir)],
            capture_output=True,
            text=True,
            timeout=300
        )

        if result.returncode != 0:
            raise RuntimeError(f"Git clone failed: {result.stderr}")

        logger.info(f"Repository cloned successfully")
        return self.work_dir

    def update_version(self, repo_dir: Path, version: str, version_code: int):
        """Update version in build.gradle.kts."""
        gradle_file = repo_dir / "app" / "build.gradle.kts"

        if not gradle_file.exists():
            # Try .gradle instead of .kts
            gradle_file = repo_dir / "app" / "build.gradle"

        if not gradle_file.exists():
            raise FileNotFoundError(f"Could not find build.gradle in {repo_dir}/app/")

        logger.info(f"Updating version to {version} (code: {version_code}) in {gradle_file}")

        content = gradle_file.read_text()

        # Update versionCode
        content = re.sub(
            r'versionCode\s*=?\s*\d+',
            f'versionCode = {version_code}',
            content
        )

        # Update versionName
        content = re.sub(
            r'versionName\s*=?\s*["\'][^"\']+["\']',
            f'versionName = "{version}"',
            content
        )

        gradle_file.write_text(content)
        logger.info("Version updated successfully")

    def build_apk(self, repo_dir: Path,
                  progress_callback: Optional[Callable[[int, str], None]] = None) -> Path:
        """Build the APK using Docker."""
        logger.info(f"Starting Docker build with image {config.DOCKER_IMAGE}")

        if progress_callback:
            progress_callback(0, "Pulling Docker image")

        # Pull the image if needed
        try:
            self.docker_client.images.get(config.DOCKER_IMAGE)
        except docker.errors.ImageNotFound:
            logger.info(f"Pulling Docker image {config.DOCKER_IMAGE}")
            self.docker_client.images.pull(config.DOCKER_IMAGE)

        if progress_callback:
            progress_callback(10, "Starting build container")

        # Run the build - list files first for debugging, then build
        build_command = """
            echo "=== Working directory ===" && \
            pwd && \
            echo "=== Files ===" && \
            ls -la && \
            echo "=== Starting build ===" && \
            chmod +x gradlew && \
            ./gradlew assembleRelease --no-daemon --stacktrace
        """

        logger.info("Running Gradle build in Docker container")

        try:
            # Ensure repo_dir is absolute path
            repo_dir_abs = str(repo_dir.resolve())
            logger.info(f"Mounting {repo_dir_abs} to /project")

            self.container = self.docker_client.containers.run(
                config.DOCKER_IMAGE,
                command=f"bash -c '{build_command}'",
                volumes={
                    repo_dir_abs: {'bind': '/project', 'mode': 'rw'}
                },
                working_dir="/project",
                remove=False,
                detach=True,
                environment={
                    "GRADLE_USER_HOME": "/project/.gradle"
                }
            )

            # Monitor build progress
            progress = 10
            for log in self.container.logs(stream=True, follow=True):
                if self.cancelled:
                    self.container.stop()
                    raise RuntimeError("Build cancelled")

                line = log.decode('utf-8', errors='ignore').strip()
                if line:
                    logger.debug(line)

                    # Parse Gradle progress
                    if "Compiling" in line or "compileReleaseKotlin" in line:
                        progress = 40
                        if progress_callback:
                            progress_callback(progress, "Compiling Kotlin sources")
                    elif "processReleaseResources" in line:
                        progress = 60
                        if progress_callback:
                            progress_callback(progress, "Processing resources")
                    elif "packageRelease" in line:
                        progress = 80
                        if progress_callback:
                            progress_callback(progress, "Packaging APK")
                    elif "BUILD SUCCESSFUL" in line:
                        progress = 100
                        if progress_callback:
                            progress_callback(progress, "Build completed")

            # Check exit code
            result = self.container.wait()
            exit_code = result.get('StatusCode', 1)

            if exit_code != 0:
                logs = self.container.logs().decode('utf-8', errors='ignore')
                raise RuntimeError(f"Build failed with exit code {exit_code}:\n{logs[-2000:]}")

        finally:
            if self.container:
                try:
                    self.container.remove()
                except:
                    pass
                self.container = None

        # Find the built APK
        apk_dir = repo_dir / "app" / "build" / "outputs" / "apk" / "release"
        apk_files = list(apk_dir.glob("*.apk"))

        if not apk_files:
            # Try debug build
            apk_dir = repo_dir / "app" / "build" / "outputs" / "apk" / "debug"
            apk_files = list(apk_dir.glob("*.apk"))

        if not apk_files:
            raise FileNotFoundError(f"No APK found in {repo_dir}/app/build/outputs/apk/")

        apk_path = apk_files[0]
        logger.info(f"APK built successfully: {apk_path}")

        return apk_path

    def calculate_sha256(self, file_path: Path) -> str:
        """Calculate SHA256 checksum of file."""
        sha256_hash = hashlib.sha256()
        with open(file_path, "rb") as f:
            for chunk in iter(lambda: f.read(8192), b""):
                sha256_hash.update(chunk)
        return sha256_hash.hexdigest()

    def get_file_size(self, file_path: Path) -> int:
        """Get file size in bytes."""
        return file_path.stat().st_size

    def cancel(self):
        """Cancel the current build."""
        self.cancelled = True
        if self.container:
            try:
                self.container.stop(timeout=5)
            except:
                pass

    def cleanup(self):
        """Clean up temporary files."""
        self.cancelled = False
        if self.work_dir and self.work_dir.exists():
            logger.info(f"Cleaning up {self.work_dir}")
            shutil.rmtree(self.work_dir, ignore_errors=True)
            self.work_dir = None
