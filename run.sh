#!/bin/bash
set -euo pipefail # [Safety] Exit immediately if a command exits with a non-zero status.

# ------------------------------------------------------------------
# [Description]
# This script manages the lifecycle of the 'minecraft-wrapper-dev' Docker image.
# It provides options to build, clean, and run the Java environment.
# ------------------------------------------------------------------

# Detect Host OS for correct path mounting
case "$(uname -s)" in
    Linux*)                 ROOT=$(pwd);;
    Darwin*)                ROOT=$(pwd);;
    CYGWIN*|MINGW*|MSYS*)   ROOT=$(pwd -W);;
    *)                      echo "Unknown OS. Exiting." && exit 1;;
esac

IMAGE_NAME="minecraft-wrapper-dev"

# ------------------------------------------------------------------
# Function: clean_image
# Description: Removes containers and the specific Docker image.
# ------------------------------------------------------------------
clean_image() {
    local containers
    # List all containers using the image (running or stopped)
    containers=$(docker ps -aq --filter "ancestor=$IMAGE_NAME")
    
    if [[ -n "$containers" ]]; then                                         
        echo "Found containers using '$IMAGE_NAME'. Forcing removal..."
        docker rm -f $containers
    fi
    
    # Check if image exists before removing
    if [[ "$(docker images -q "$IMAGE_NAME" 2> /dev/null)" != "" ]]; then
        docker rmi $(docker images -q "$IMAGE_NAME")
        echo "Removed existing Docker image '$IMAGE_NAME'."
    else
        echo "No existing Docker image '$IMAGE_NAME' found."
    fi
}

# ------------------------------------------------------------------
# Function: build_and_run
# Description: Builds the image (if missing) and runs the container.
# ------------------------------------------------------------------
build_and_run() {
    echo "Preparing to run..."

    # Build only if missing
    if [[ "$(docker images -q "$IMAGE_NAME" 2> /dev/null)" == "" ]]; then
        echo "Image '$IMAGE_NAME' not found. Building..."
        docker build -t "$IMAGE_NAME" .
    else
        echo "Image '$IMAGE_NAME' found. Skipping build."
    fi

    echo "Starting container..."
    echo "--------------------------------------------------------"
    echo "  1. Fix line endings:  dos2unix gradlew"
    echo "  2. Make executable:   chmod +x gradlew"
    echo "  3. Run wrapper:       ./gradlew :app:run"
    echo ""
    echo "  Note: Minecraft server port 25565 is mapped to host."
    echo ""
    echo "dos2unix gradlew"
    echo "chmod +x gradlew"
    echo "./gradlew :app:run"
    echo "./gradlew :app:createDist"
    echo "--------------------------------------------------------"
    
    # Run interactive shell, mounting the current directory to /app
    # -p 25565:25565 maps the Minecraft port so you can connect from the host
    docker run --rm -it -v "$ROOT:/app" -p 25565:25565 "$IMAGE_NAME"
}

# ------------------------------------------------------------------
# Main Logic: Menu
# ------------------------------------------------------------------
echo "Minecraft Wrapper - Docker Environment"
echo "1. Run (Build if missing)"
echo "2. Clean (Remove image) & Run"
echo "3. Clean only & Exit"
read -p "Enter choice [1-3]: " choice

case "$choice" in
    1) build_and_run;;
    2) clean_image && build_and_run;;
    3) clean_image && exit 0;;
    *) echo "Invalid choice. Exiting." && exit 1;;
esac
