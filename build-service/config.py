"""
Configuration for MQTT Android Build Service
"""
import os

# MQTT Configuration
MQTT_HOST = os.getenv("MQTT_HOST", "188.228.60.134")
MQTT_PORT = int(os.getenv("MQTT_PORT", "1883"))
MQTT_USER = os.getenv("MQTT_USER", "admin")
MQTT_PASSWORD = os.getenv("MQTT_PASSWORD", "")
MQTT_CLIENT_ID = os.getenv("MQTT_CLIENT_ID", "iocast-build-service")

# MQTT Topics
TOPIC_TRIGGER = "build/iocast-android/trigger"
TOPIC_CANCEL = "build/iocast-android/cancel"
TOPIC_STATUS = "build/iocast-android/status"
TOPIC_PROGRESS = "build/iocast-android/progress"
TOPIC_RESULT = "build/iocast-android/result"

# GitHub Configuration
GITHUB_TOKEN = os.getenv("GITHUB_TOKEN", "")
GITHUB_REPO = os.getenv("GITHUB_REPO", "ufi-tech/iocast-android")

# Build Configuration
BUILD_CACHE_DIR = os.getenv("BUILD_CACHE_DIR", "/app/cache")
BUILD_TIMEOUT = int(os.getenv("BUILD_TIMEOUT", "1800"))  # 30 minutes
# Use cimg/android which has Java 17 by default (matches project requirements)
DOCKER_IMAGE = os.getenv("DOCKER_IMAGE", "cimg/android:2024.01.1")

# Paths
RELEASES_DIR = os.getenv("RELEASES_DIR", "/app/releases")
