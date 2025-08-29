FROM tomcat:10.1.44-jdk21-temurin

RUN rm -rf /usr/local/tomcat/webapps/*
COPY target/*.war /usr/local/tomcat/webapps/ROOT.war
ENV JAVA_OPTS="-Xms512m -Xmx1024m -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080
CMD [ "catalina.sh", "run" ]
