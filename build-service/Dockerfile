FROM python:3.11-slim

LABEL maintainer="ufi-tech"
LABEL description="MQTT Android Build Service"

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install Docker CLI (for controlling Docker from within container)
RUN curl -fsSL https://get.docker.com | sh

# Set working directory
WORKDIR /app

# Copy requirements first for better caching
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY *.py .

# Create directories
RUN mkdir -p /app/cache /app/releases

# Environment variables (overridden by docker-compose)
ENV MQTT_HOST=188.228.60.134
ENV MQTT_PORT=1883
ENV BUILD_CACHE_DIR=/app/cache
ENV RELEASES_DIR=/app/releases

# Run as non-root user (optional, but Docker socket needs proper permissions)
# RUN useradd -m builduser && chown -R builduser:builduser /app
# USER builduser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD python -c "import paho.mqtt.client as mqtt; print('OK')" || exit 1

# Start the service
CMD ["python", "-u", "build_service.py"]
