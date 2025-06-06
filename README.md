# Ai Forgot These Cards

This is an AI-assisted flashcard creation and quiz website.

The _AI-assisted_ part is initially to enable chatting with an LLM during creation/editing of a flashcard.

This project consists of two parts:

1. **Backend**: (this repo)
   - Makes use of:
     - [Java](https://www.java.com/)
     - [Spring](https://spring.io/)
     - [JPA](https://www.oracle.com/java/technologies/persistence-jsp.html)
     - [Hibernate](https://hibernate.org/)
     - [Spring Security](https://spring.io/projects/spring-security)
     - [PostgreSQL](https://www.postgresql.org/)
     - [Ollama](https://ollama.com/) for AI integration
     - [Maven](https://maven.apache.org/)
     - [Just](https://just.systems/) for build automation
2. **Frontend**: [ai-forgot-this-frontend](https://github.com/darkmusic/ai-forgot-this-frontend)
    - Makes use of:
      - [React](https://react.dev/)
      - [TypeScript](https://www.typescriptlang.org/)
      - [Vite](https://vite.dev/)
      - [Just](https://just.systems/) for build automation

Features:

- Admin management
- Spring Security
- User profiles
- Ollama integration
  - List models
  - Add/pull model
  - Chat with model
- Deck management
- Card management
  - Create card with AI assistance
  - Edit card with AI assistance
  - View card
  - Markdown support for card content
- Quiz

Runtime Requirements:

- Java Runtime, currently tested with Java 24 (GraalVM-24.0.1+9.1)
- PostgreSQL - A PostgreSQL container must be up and running (see [compose.yaml](compose.yaml)) or another existing PostgreSQL server must be available
- Ollama must be installed and running
- Docker/Rancher Desktop/etc. (Note that Podman currently is not supported)

## Screenshots

Here are some screenshots of the application:

1. **Login Screen**:

   ![Login Screen](res/screenshots/sign_in.png)
2. **User Home Page, showing User Context Menu**:
   ![User Home Page](res/screenshots/user_home.png)
3. **User Settings**:
   ![User Settings](res/screenshots/user_settings.png)
4. **Deck Management**:
   ![Deck Management](res/screenshots/manage_deck.png)
5. **View Card**:
   ![View Card](res/screenshots/view_card.png)
6. **Edit Card**:
   ![Edit Card](res/screenshots/edit_card.png)
7. **Create Card (With AI Assistance)**:
   ![Create Card](res/screenshots/create_card.png)
8. **Quiz (Front of card)**:
   ![Quiz](res/screenshots/quiz_front.png)
9. **Quiz (Back of card)**:
   ![Quiz Back](res/screenshots/quiz_back.png)
10. **Admin Home**:
    ![Admin Management](res/screenshots/admin_home.png)
11. **Add User**:
    ![Add User](res/screenshots/add_user.png)
12. **Add Model**:
    ![Add Model](res/screenshots/add_model.png)

## General Remarks

- This project is a work-in-progress and is intended for educational purposes only.
- The AI integration is done using [Ollama](https://ollama.com/), which must be installed and running on your local machine. You can add models to Ollama and use them in the application.
- The frontend is a submodule of this repository, so you will need to clone the frontend separately or initialize and update submodules after cloning this repo.
- The application uses PostgreSQL as the database, and you can run it using Docker, Rancher Desktop, etc. with the provided `compose.yml` file. Alternatively, you can configure it to connect to an existing PostgreSQL server.
- AI is provided as assistance, but should not be assumed to be factually correct, especially in regard to the intricacies of grammar and language. Always review the AI-generated content before saving it to ensure accuracy and appropriateness for your use case.
- Different models may provide different results, and the quality of the output will depend on the model used and the input provided.

## Getting Started

To get started with the project, follow these steps:

1. Download and install Ollama if needed, and run it via `ollama serve`."
1. Install Just if needed.

   ```bash
   brew install just
   ```

   or

   ```bash
   scoop install just
   ```

   or

   ```bash
   choco install just
   ```

    or

    ```bash
   paru -S just
   ```

1. Install Docker, Rancher Desktop, etc. if needed.
1. Customize the `docker-compose.yaml` file if needed, and then start the PostgreSQL container:

   ```bash
   docker compose up -d
   ```

1. Clone the repository and initialize the submodules:

   ```bash
   git clone https://github.com/darkmusic/ai-forgot-these-cards
   cd ai-forgot-these-cards
   git submodule update --init
   ```

1. Install JDK 24 and Maven.

- GraalVM-24.0.1+9.1 has been confirmed to work, though other JDKs may work as well.

1. In src/dep/ai-forgot-this-frontend, run:

   ```bash
   npm install
   ```

1. Make sure your JAVA_HOME is set to the correct JDK version:

   ```bash
   export JAVA_HOME=/path/to/your/jdk
   ```

   or on Windows:

   ```bash
   set JAVA_HOME=C:\path\to\your\jdk
   ```

1. Also ensure your PATH variable points to the correct Java binary. For example, on Unix-like systems:

   ```bash
   export PATH=$JAVA_HOME/bin:$PATH
   ```

   On Windows:

   ```bash
   set PATH=%JAVA_HOME%\bin;%PATH%
   ```

1. In the project root, run a maven clean compile (which will also build the frontend) by executing::

   ```bash
   just compile
   ```

1. Ensure that the DB container is running and you can connect to it.
1. Run the application:

    ```bash
    just run
    ```

1. Open your web browser and navigate to `http://localhost:8086` and log in with username "cards", password "cards".
1. Go to the "Admin" section and add a user with the role "USER".
1. Change the "cards" admin user's password if needed.
1. Add a model to Ollama using the admin interface (e.g. `llama2` or `smollm2:135m`).

## Actuator Endpoints

- The application exposes several actuator endpoints for monitoring and management. You can access them at `http://localhost:9090/actuator`.
- Some useful endpoints include:
  - `http://localhost:9090/actuator/swagger-ui`: Access the Swagger UI for API documentation.

## Roadmap

- [X] Add theme support, and enable switching between themes.
- [X] Add formatting for flashcards (e.g. Markdown support).
- [X] Add template support for flashcards.
- [ ] Add profile picture upload support.
- [ ] Implement a more sophisticated quiz system with spaced repetition.
- [ ] Add support for statistics and progress tracking.
- [ ] Add support for multiple languages.
- [ ] Add support for importing/exporting flashcards in different formats (e.g. CSV, Anki).
- [ ] Evaluate possible agentic or other AI-assisted integration, such as using the AI to create flashcards based on user input or other sources.
- [ ] Consider supporting other / custom frontends, such as a mobile app or a different web framework.
- [ ] Add support for more AI models and providers.
- [ ] Enable administrative exporting and importing of database.
