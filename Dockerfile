# Use Official Java 21 (LTS) image based on Ubuntu Jammy
FROM eclipse-temurin:21-jdk-jammy

# Install basic utilities
# dos2unix is crucial for running Windows-edited scripts (like gradlew) in Linux
RUN apt-get update \
 && apt-get install -y dos2unix \
 && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Expose the default Minecraft Server port
EXPOSE 25565

# Default command: interactive shell
CMD ["bash"]