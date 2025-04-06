## Ai Forgot These Cards!
This is an AI-assisted flashcard creation and quiz website.

The _AI-assisted_ part is to enable chatting with an LLM during creation/editing of a flashcard.

This project consists of two parts:

1. **Backend** (this repo): [Java/Spring, JPA/Hibernate, PostgreSQL](https://github.com/darkmusic/ai-forgot-these-cards)
2. **Frontend** [ai-forgot-this-frontend](https://github.com/darkmusic/ai-forgot-this-frontend): [React](https://github.com/darkmusic/ai-forgot-this-frontend), [TypeScript](https://www.typescriptlang.org/), [Vite](https://vite.dev/)

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
- Quiz

Runtime Requirements:
- Java Runtime, currently tested with Java 23
- Ollama must be installed and running
- Docker/Podman/etc. PostgreSQL container must be up and running (see [compose.yaml](compose.yaml)) or another existing PostgreSQL server must be available

### Screenshots
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

### General Remarks
- This project is a work-in-progress and is intended for educational purposes only.
- The AI integration is done using [Ollama](https://ollama.com/), which must be installed and running on your local machine. You can add models to Ollama and use them in the application.
- The frontend is a submodule of this repository, so you will need to clone the frontend separately or initialize and update submodules after cloning this repo.
- The application uses PostgreSQL as the database, and you can run it using Docker, Podman, Rancher Desktop, etc. with the provided `compose.yaml` file. Alternatively, you can configure it to connect to an existing PostgreSQL server.
- AI is provided as assistance, but should not be assumed to be factually correct, especially in regard to the intricacies of grammar and language. Always review the AI-generated content before saving it to ensure accuracy and appropriateness for your use case.
- Different models may provide different results, and the quality of the output will depend on the model used and the input provided.

### Getting Started
To get started with the project, follow these steps:
1. Download and install Ollama if needed, and run it via `ollama serve`."
2. Install Just if needed.
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
3. Install Docker, Podman, Rancher Desktop, etc. if needed.
4. Start the PostgreSQL container using the provided `docker-compose.yaml` file:
   ```bash
   docker compose up -d
   ```
5. Clone the repository and initialize the submodules:
   ```bash
   git clone https://github.com/darkmusic/ai-forgot-these-cards
   cd ai-forgot-these-cards
   git submodule init
   ```
6. Install JDK 23 (GraalVM-CE-23.0.2 has been confirmed to work) and Maven.
7. In src/dep/ai-forgot-this-frontend, run:
   ```bash
   npm install
   ```
8. Make sure your JAVA_HOME is set to the correct JDK version:
   ```bash
   export JAVA_HOME=/path/to/your/jdk
   ```
   or on Windows:
   ```bash
   set JAVA_HOME=C:\path\to\your\jdk
   ```
9. Also ensure your PATH variable points to the correct Java binary. For example, on Unix-like systems:
   ```bash
   export PATH=$JAVA_HOME/bin:$PATH
   ```
   On Windows:
   ```bash
   set PATH=%JAVA_HOME%\bin;%PATH%
   ```
10. In the project root, run:
   ```bash
   just install
   ```
11. Ensure that the DB container is running and you can connect to it.
12. Run the application:
    ```bash
    just run
    ```
13. Open your web browser and navigate to `http://localhost:8086` and log in with username "cards", password "cards".
14. Go to the "Admin" section and add a user with the role "USER".
15. Change the "cards" admin user's password if needed.
16. Add a model to Ollama using the admin interface (e.g. `llama2` or `smollm2:135m`).