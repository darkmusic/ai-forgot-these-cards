# Ai Forgot These Cards

This is an AI-assisted flashcard creation and quiz website.

The _AI-assisted_ part initially enables chatting with an LLM during creation/editing of a flashcard.

This project consists of two parts:

1. **Backend**: (this repo)
   - Makes use of:
     - [Java](https://www.java.com/)
     - [Spring](https://spring.io/)
     - [JPA](https://www.oracle.com/java/technologies/persistence-jsp.html)
     - [Hibernate](https://hibernate.org/)
     - [Spring Security](https://spring.io/projects/spring-security)
     - [PostgreSQL](https://www.postgresql.org/)
     - [Llama.cpp](https://github.com/ggml-org/llama.cpp) for AI integration
     - [Maven](https://maven.apache.org/)
     - [GNU Make](https://www.gnu.org/software/make/) for build automation
2. **Frontend**: [ai-forgot-this-frontend](https://github.com/darkmusic/ai-forgot-this-frontend)
    - Makes use of:
      - [React](https://react.dev/)
      - [TypeScript](https://www.typescriptlang.org/)
      - [Sass](https://sass-lang.com/) for styling
      - [Vite](https://vite.dev/)
      - [Just](https://just.systems/) for build automation

Features:

- Fully containerized with Docker/Rancher Desktop/Podman/etc.
- User management
- Admin management
- Spring Security
- User profiles
- Llama.cpp integration
  - Chat with a model
- Deck management
- Card management
  - Create a card with AI assistance
  - Edit card with AI assistance
  - View card
  - Markdown support for card content
- Quiz

Runtime Requirements:

- Docker/Rancher Desktop/Podman/etc.
- Llama.cpp must be installed and running (can be on host or another machine)
- GNU Make is needed to run the provided Makefile commands.

Notes:

- You do not need to install JDK, Maven, or Node.js to build/run. All compilation happens inside Docker images via multi-stage builds.

## Architecture

### Application architecture

```mermaid
flowchart TD
    subgraph Backend
        A[Java Spring Boot Application]
        B[PostgreSQL Database]
        C[Llama.cpp AI Service]
    end

    subgraph Frontend
        D[React Application]
    end

    D -->|HTTP Requests| A
    A -->|JPA/Hibernate| B
    A -->|Llama.cpp API Calls| C
```

### Container architecture

```mermaid
flowchart TD
    subgraph Containers
        A[Tomcat/Spring Backend]
        B[Nginx/React Frontend]
        C[PostgreSQL DB]
    end

    B -->|REST API calls| A
    A -->|Database calls| C
```


## Screenshots

Here are some screenshots of the application:

1. **Login Screen**:
   ![Login Screen](res/screenshots/sign_in.png)
2. **User Home Page, showing User Context Menu**:
   ![User Home Page](res/screenshots/user_home.png)
3. **User Settings**:
   ![User Settings](res/screenshots/user_settings.png)
4. **Deck Management (with card template support)**:
   ![Deck Management](res/screenshots/manage_deck.png)
5. **View Card**:
   ![View Card](res/screenshots/view_card.png)
6. **Edit Card (With AI Assistance)**:
   ![Edit Card](res/screenshots/edit_card.png)
7. **Create Card (With AI Assistance)**:
   ![Create Card](res/screenshots/create_card.png)
8. **Quiz (Front of card, showing Markdown formatting)**:
   ![Quiz](res/screenshots/quiz_front.png)
9. **Quiz (Back of card, showing Markdown formatting)**:
   ![Quiz Back](res/screenshots/quiz_back.png)
10. **Admin Home**:
    ![Admin Management](res/screenshots/admin_home.png)
11. **Add User**:
    ![Add User](res/screenshots/add_user.png)

## General Remarks

- This project is a work-in-progress and is intended for educational purposes only.
- The AI integration is done using [Llama.cpp](https://github.com/ggml-org/llama.cpp), which must be installed and running on your local machine. You will need to download a model in GGUF format, and point to this model when starting the Llama.cpp server. For example, if you have a model named `smollm2-135m.gguf`, you would start the server with the command `llama-server -m /path/to/smollm2-135m.gguf`.
- The frontend is a submodule of this repository, so you will need to clone the frontend separately or initialize and update submodules after cloning this repo.
- The application uses PostgreSQL as the database, and you can run it using Docker, Rancher Desktop, etc., with the provided `compose.yml` file. Alternatively, you can configure it to connect to an existing PostgreSQL server by commenting out the `db` service in `compose.yml` and updating the connection settings in `src/main/resources/application.properties`.
- AI is provided as assistance, but should not be assumed to be factually correct, especially regarding the intricacies of grammar and language. Always review the AI-generated content before saving it to ensure accuracy and appropriateness for your use case.
- Different models may provide different results, and the output quality will depend on the model used and the input provided.

## Getting Started

To get started with the project, follow these steps:

1. Download and install Llama.cpp if needed, and run it via `llama-server -m /path/to/model.gguf --port 8087`. Note that if running this on a different machine, you will need to add the --host option to allow connections from other machines.
1. Install Make if needed.
1. If on Windows, add/edit .wslconfig in your user home folder with settings (adjust as needed for memory, etc.):

```bash
[wsl2]
memory=16GB # Limits VM memory in WSL 2
processors=2 # Makes the WSL 2 VM use this many virtual processors
networkingMode=mirrored # Required to resolve an issue with Podman
autoMemoryReclaim=gradual # To optimize memory reclaimation

[automount]
options = "metadata,umask=22,fmask=11" # To make windows disk access faster

[experimental]
sparseVhd=true # To minimize wsl container disk image use
```

1. Install Llama.cpp and download at least one model in GGUF format (e.g., `llama2` or `smollm2:135m`).
1. Make sure Llama.cpp is running via `llama-server -m /path/to/model.gguf --port 8087` (add --host if needed).
1. Update the `spring.ai.openai.chat.base-url` property in `src/main/resources/application.properties` to a URL reachable from inside the `app` container (default may be `http://host.docker.internal:8087` or the LAN IP of your host, depending on your platform).
1. Install Docker, Rancher Desktop, Podman, etc. if needed.
1. Clone the repository and initialize the submodules:

   ```bash
   git clone https://github.com/darkmusic/ai-forgot-these-cards
   cd ai-forgot-these-cards
   git submodule update --init
   ```

1. No local toolchain needed. Build and start the containers (this compiles the backend WAR and the frontend SPA inside Docker images):

   ```bash
   make build-deploy
   ```

1. Open your web browser, navigate to [http://localhost:8086](http://localhost:8086), and log in with username "cards" and password "cards".
1. Go to the "Admin" section and add a user with the role "USER".
1. Change the "cards" admin user's password if needed.

## Exporting the database

This will export the database to `db/backup.sql`.

Notes:

1. This will first delete the existing backup, so back up the backup if you want to keep it.
1. You will be required to enter the password when this runs.

```bash
make export-db
```

## Importing the database

Notes:

1. This will drop the current database, so be sure you have exported it first!
1. You will be required to enter the password when this runs.

```bash
make import-db
```

## Actuator Endpoints

- The application exposes several actuator endpoints for monitoring and management. You can access them at `http://localhost:8080/actuator`.

## Roadmap

- [X] Add theme support, and enable switching between themes.
- [X] Add formatting for flashcards (e.g., Markdown support).
- [X] Add template support for flashcards.
- [X] Enable administrative exporting and importing of the database.
- [X] Create docker-compose for app and website and move entire solution to containers.
- [X] Transition from docker-compose to docker CLI commands.
- [X] Transition to Llama.cpp instead of Ollama.
- [X] Transition to GNU Make instead of Just.
- [X] Transition from using Ollama Spring API to Spring AI OpenAI-compatible API.
- [X] Remove dependency on local building and build entirely inside containers.
- [ ] Add swagger/openapi support for the REST API.
- [ ] Add support for importing/exporting flashcards in different formats (e.g., CSV, YAML, TOML, Anki).
- [ ] Add profile picture upload support.
- [ ] Implement a more sophisticated quiz system with spaced repetition.
- [ ] Add support for statistics and progress tracking.
- [ ] Add support for multiple UI languages.
- [ ] Evaluate possible agentic or other AI-assisted integration, such as using the AI to create flashcards based on user input or other sources.
- [ ] Consider supporting other / custom frontends, such as a mobile app or a different web framework.
- [ ] Add support for more AI models and providers.
