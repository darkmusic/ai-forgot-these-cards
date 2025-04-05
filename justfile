#set shell := ["pwsh", "-c"]

install:
    @./mvnw clean install -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=prod,spring.output.ansi.enabled=always,--enable-preview"

run:
    @./mvnw clean spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=prod,spring.output.ansi.enabled=always,--enable-preview" 