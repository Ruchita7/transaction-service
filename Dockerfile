# ─── Stage 1: Build ───
FROM gradle:8.7-jdk21 AS build
WORKDIR /app

# Step 1 — copy only the files needed to resolve dependencies
# (keeps this layer cached as long as build.gradle/settings.gradle don't change)
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./

# Step 2 — download dependencies (cached layer, skipped on rebuild if no dep changes)
RUN ./gradlew dependencies --no-daemon || true

# Step 3 — now copy source code (only this layer rebuilds when source changes)
COPY src ./src

# Step 4 — build the jar (tests skipped here, run separately in CI)
RUN ./gradlew clean build --no-daemon -x test

# ─── Stage 2: Run ───
FROM amazoncorretto:21
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
