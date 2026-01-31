#!/usr/bin/env python3
"""
MQTT Android Build Service

Listens for build commands via MQTT, builds APK using Docker,
and uploads to GitHub Releases.
"""
import json
import logging
import signal
import sys
import time
import threading
from datetime import datetime

import paho.mqtt.client as mqtt

import config
from builder import AndroidBuilder
from github_release import GitHubReleaser

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger("BuildService")


def validate_config():
    """Validate required configuration at startup."""
    errors = []

    if not config.MQTT_PASSWORD:
        errors.append("MQTT_PASSWORD environment variable is not set")

    if not config.GITHUB_TOKEN:
        errors.append("GITHUB_TOKEN environment variable is not set")

    if errors:
        for error in errors:
            logger.error(f"Configuration error: {error}")
        raise ValueError(f"Missing required configuration: {', '.join(errors)}")


class BuildService:
    """Main build service that listens for MQTT commands."""

    def __init__(self):
        self.client = mqtt.Client(client_id=config.MQTT_CLIENT_ID)
        self.builder = AndroidBuilder()
        self.releaser = GitHubReleaser()
        self.current_build = None
        self.build_lock = threading.Lock()
        self.running = True

    def connect(self):
        """Connect to MQTT broker."""
        self.client.username_pw_set(config.MQTT_USER, config.MQTT_PASSWORD)
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message
        self.client.on_disconnect = self._on_disconnect

        logger.info(f"Connecting to MQTT broker at {config.MQTT_HOST}:{config.MQTT_PORT}")
        self.client.connect(config.MQTT_HOST, config.MQTT_PORT, keepalive=60)

    def _on_connect(self, client, userdata, flags, rc):
        """Handle MQTT connection."""
        if rc == 0:
            logger.info("Connected to MQTT broker")
            # Subscribe to build topics
            client.subscribe(config.TOPIC_TRIGGER)
            client.subscribe(config.TOPIC_CANCEL)
            logger.info(f"Subscribed to {config.TOPIC_TRIGGER} and {config.TOPIC_CANCEL}")

            # Publish online status
            self._publish_status("idle", "Build service online and ready")
        else:
            logger.error(f"Failed to connect to MQTT broker: {rc}")

    def _on_disconnect(self, client, userdata, rc):
        """Handle MQTT disconnection."""
        logger.warning(f"Disconnected from MQTT broker: {rc}")
        if self.running:
            logger.info("Attempting to reconnect...")

    def _on_message(self, client, userdata, msg):
        """Handle incoming MQTT messages."""
        topic = msg.topic
        try:
            payload = json.loads(msg.payload.decode('utf-8'))
        except json.JSONDecodeError:
            logger.error(f"Invalid JSON payload on {topic}")
            return

        logger.info(f"Received message on {topic}: {payload}")

        if topic == config.TOPIC_TRIGGER:
            self._handle_trigger(payload)
        elif topic == config.TOPIC_CANCEL:
            self._handle_cancel(payload)

    def _handle_trigger(self, payload):
        """Handle build trigger request."""
        with self.build_lock:
            if self.current_build is not None:
                logger.warning("Build already in progress, rejecting request")
                self._publish_status("busy", "Build already in progress")
                return

            # Extract build parameters
            branch = payload.get("branch", "main")
            version = payload.get("version")
            version_code = payload.get("versionCode")
            requested_by = payload.get("requestedBy", "unknown")

            if not version or not version_code:
                logger.error("Missing version or versionCode in trigger payload")
                self._publish_status("error", "Missing version or versionCode")
                return

            self.current_build = {
                "branch": branch,
                "version": version,
                "versionCode": version_code,
                "requestedBy": requested_by,
                "startedAt": int(time.time())
            }

        # Start build in separate thread
        build_thread = threading.Thread(
            target=self._run_build,
            args=(branch, version, version_code)
        )
        build_thread.start()

    def _handle_cancel(self, payload):
        """Handle build cancel request."""
        with self.build_lock:
            if self.current_build is None:
                logger.info("No build in progress to cancel")
                return

            logger.info("Cancelling current build")
            self.builder.cancel()
            self.current_build = None
            self._publish_status("cancelled", "Build cancelled by user")

    def _run_build(self, branch: str, version: str, version_code: int):
        """Run the build process."""
        start_time = time.time()

        try:
            # Step 1: Clone repository
            self._publish_progress(10, "Cloning repository")
            clone_dir = self.builder.clone_repo(branch)

            # Step 2: Update version in build.gradle
            self._publish_progress(20, "Updating version")
            self.builder.update_version(clone_dir, version, version_code)

            # Step 3: Build APK
            self._publish_progress(30, "Building APK (this may take a while)")
            apk_path = self.builder.build_apk(
                clone_dir,
                progress_callback=self._build_progress_callback
            )

            # Step 4: Calculate checksum
            self._publish_progress(85, "Calculating checksum")
            sha256 = self.builder.calculate_sha256(apk_path)
            apk_size = self.builder.get_file_size(apk_path)

            # Step 5: Upload to GitHub
            self._publish_progress(90, "Uploading to GitHub Releases")
            release_url = self.releaser.create_release(
                version=version,
                apk_path=apk_path,
                notes=f"Automated build v{version} (versionCode: {version_code})"
            )

            # Success!
            build_time = int(time.time() - start_time)
            self._publish_result(
                status="success",
                version=version,
                version_code=version_code,
                apk_url=release_url,
                apk_size=apk_size,
                sha256=sha256,
                build_time=build_time
            )

            logger.info(f"Build completed successfully in {build_time}s")

        except Exception as e:
            logger.exception(f"Build failed: {e}")
            self._publish_result(
                status="failed",
                version=version,
                version_code=version_code,
                error=str(e),
                build_time=int(time.time() - start_time)
            )

        finally:
            with self.build_lock:
                self.current_build = None
            self.builder.cleanup()

    def _build_progress_callback(self, progress: int, message: str):
        """Callback for build progress updates."""
        # Map builder progress (0-100) to our range (30-85)
        mapped_progress = 30 + int(progress * 0.55)
        self._publish_progress(mapped_progress, message)

    def _publish_status(self, status: str, message: str):
        """Publish build status to MQTT."""
        payload = {
            "status": status,
            "message": message,
            "timestamp": int(time.time())
        }
        if self.current_build:
            payload.update({
                "branch": self.current_build["branch"],
                "version": self.current_build["version"],
                "startedAt": self.current_build["startedAt"]
            })

        self.client.publish(
            config.TOPIC_STATUS,
            json.dumps(payload),
            retain=True
        )

    def _publish_progress(self, progress: int, step: str):
        """Publish build progress to MQTT."""
        payload = {
            "progress": progress,
            "step": step,
            "timestamp": int(time.time())
        }
        if self.current_build:
            payload.update({
                "branch": self.current_build["branch"],
                "version": self.current_build["version"],
                "startedAt": self.current_build["startedAt"]
            })

        self.client.publish(config.TOPIC_PROGRESS, json.dumps(payload))
        self._publish_status("building", step)
        logger.info(f"Progress: {progress}% - {step}")

    def _publish_result(self, status: str, version: str, version_code: int,
                       apk_url: str = None, apk_size: int = None,
                       sha256: str = None, build_time: int = None,
                       error: str = None):
        """Publish build result to MQTT."""
        payload = {
            "status": status,
            "version": version,
            "versionCode": version_code,
            "buildTime": build_time,
            "timestamp": int(time.time())
        }

        if status == "success":
            payload.update({
                "apkUrl": apk_url,
                "apkSize": apk_size,
                "sha256": sha256
            })
        else:
            payload["error"] = error

        self.client.publish(
            config.TOPIC_RESULT,
            json.dumps(payload),
            retain=True
        )

        # Also update status
        self._publish_status(
            status,
            f"Build {'completed' if status == 'success' else 'failed'}: v{version}"
        )

    def run(self):
        """Main run loop."""
        self.connect()

        # Setup signal handlers
        signal.signal(signal.SIGINT, self._signal_handler)
        signal.signal(signal.SIGTERM, self._signal_handler)

        logger.info("Build service started, waiting for commands...")

        # Start MQTT loop
        self.client.loop_forever()

    def _signal_handler(self, signum, frame):
        """Handle shutdown signals."""
        logger.info("Shutting down...")
        self.running = False
        self._publish_status("offline", "Build service shutting down")
        self.client.disconnect()
        sys.exit(0)


if __name__ == "__main__":
    # Validate configuration before starting
    validate_config()
    logger.info(f"Using Docker image: {config.DOCKER_IMAGE}")

    service = BuildService()
    service.run()
