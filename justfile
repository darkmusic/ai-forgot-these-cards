set windows-shell := ["pwsh", "-c"]

clean:
    @./mvnw clean

compile:
    @./mvnw clean compile

install:
    @./mvnw clean install

run:
    @./mvnw clean spring-boot:run
