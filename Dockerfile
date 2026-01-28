# IOCast Android Build Container
# Based on mingc/android-build-box - includes Android SDK, Gradle, Kotlin
FROM mingc/android-build-box:latest

# Set working directory
WORKDIR /project

# Copy project files
COPY . .

# Accept Android SDK licenses
RUN yes | sdkmanager --licenses || true

# Set Gradle options for container environment
ENV GRADLE_OPTS="-Xmx3072m -XX:MaxMetaspaceSize=756m -XX:+UseContainerSupport"

# Build the APK
RUN ./gradlew clean assembleDebug --no-daemon

# Output location
# APK will be at: /project/app/build/outputs/apk/debug/app-debug.apk
