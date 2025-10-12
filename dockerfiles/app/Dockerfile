## Build the WAR and frontend inside Docker (no local JDK/Node required)
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

# Optional: Use an external Maven mirror (e.g., Nexus)
ARG MAVEN_MIRROR_URL

# Copy only files needed to resolve deps first for better caching
COPY pom.xml ./
COPY .mvn/ .mvn/

# Copy sources and frontend
COPY src/ src/
COPY dep/ dep/

# Install minimal tools needed by frontend deploy (GNU make)
RUN apt-get update && apt-get install -y --no-install-recommends make && rm -rf /var/lib/apt/lists/*

# If a mirror is provided, configure Maven settings.xml to use it
RUN if [ -n "$MAVEN_MIRROR_URL" ]; then \
			mkdir -p /root/.m2 && echo "Creating Maven settings.xml with mirror: $MAVEN_MIRROR_URL" && \
			printf '%s\n' \
				'<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' \
				'          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' \
				'          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' \
				'  <mirrors>' \
				'    <mirror>' \
				'      <id>nexus</id>' \
				'      <name>Nexus Proxy</name>' \
				'      <url>'"$MAVEN_MIRROR_URL"'</url>' \
				'      <mirrorOf>*</mirrorOf>' \
				'    </mirror>' \
				'  </mirrors>' \
				'</settings>' > /root/.m2/settings.xml; \
		fi

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
