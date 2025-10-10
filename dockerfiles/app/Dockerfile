## Build the WAR and frontend inside Docker (no local JDK/Node required)
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy only files needed to resolve deps first for better caching
COPY pom.xml ./
COPY .mvn/ .mvn/

# Copy sources and frontend
COPY src/ src/
COPY dep/ dep/

# Install minimal tools needed by frontend deploy (GNU make)
RUN apt-get update && apt-get install -y --no-install-recommends make && rm -rf /var/lib/apt/lists/*

# Build the project (this also runs the frontend build via frontend-maven-plugin)
# -Dmaven.test.skip=true skips both test run and compilation to avoid test-only sources
RUN mvn -B -Dmaven.test.skip=true package

# Runtime image: Tomcat + the built WAR
FROM tomcat:11.0.12-jdk21-temurin

RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /workspace/target/*.war /usr/local/tomcat/webapps/ROOT.war
ENV JAVA_OPTS="-Xms512m -Xmx1024m -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080
CMD [ "catalina.sh", "run" ]
