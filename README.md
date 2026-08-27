# Astron

Astron is the backend system for an AI-assisted software project management platform. It is made up of two services:

- A Spring Boot backend (`backend/`) that handles authentication, users, projects, tasks, developers, assignments, dashboards, and analytics. It stores data in MongoDB.
- A Flask ML service (`astron-ml/`) that handles task effort prediction, developer recommendation (MCDM), and complexity scoring using trained machine learning models.

The two services talk to each other over HTTP. The Spring Boot backend calls the Flask service when it needs a prediction or recommendation.

## Repository Layout

```
astron/
  backend/       Spring Boot application (Java)
  astron-ml/     Flask application (Python) with ML models
  graphify-out/  Generated knowledge graph output, not part of the runtime system
```

## Prerequisites

- Java 17 or newer, and Maven (or use the included Maven wrapper if present)
- Python 3.10 or newer
- A MongoDB database (local instance or a hosted cluster such as MongoDB Atlas)
- Node.js is not required for this repository; the frontend lives in a separate repository, astron-frontend

## Setting Up the Backend (Spring Boot)

1. Move into the backend folder.

   ```
   cd backend
   ```

2. Configure the application. Open `src/main/resources/application.properties` and set the following values:

   - `astron.mongodb.uri` - your MongoDB connection string
   - `spring.mongodb.database` - the database name
   - `astron.jwt.secret` - a secret key used to sign JSON Web Tokens, at least 32 characters long
   - `astron.jwt.expiration-ms` - how long a token stays valid, in milliseconds
   - `astron.cors.origin` - the URL of the frontend application allowed to call this API
   - `astron.flask.uri` - the base URL of the Flask ML service

   Note: the current `application.properties` file has a live MongoDB connection string and password committed directly in it. This should be treated as a leaked credential. Rotate the database password and move all of these values out of source control, for example into environment variables or a `.env` file that is excluded from git.

3. Build and run the application.

   ```
   mvn spring-boot:run
   ```

   Or build a jar and run it directly:

   ```
   mvn clean package
   java -jar target/backend-0.0.1-SNAPSHOT.jar
   ```

4. By default the backend listens on port 8080. The API is available at `http://localhost:8080/api`.

## Setting Up the ML Service (Flask)

1. Move into the astron-ml folder.

   ```
   cd astron-ml
   ```

2. Create and activate a virtual environment.

   ```
   python -m venv venv
   venv\Scripts\activate      (on Windows)
   source venv/bin/activate   (on macOS or Linux)
   ```

3. Install dependencies.

   ```
   pip install -r requirements.txt
   ```

4. Create a `.env` file in the `astron-ml` folder with the following variables:

   ```
   MONGO_URI=your-mongodb-connection-string
   JWT_SECRET=the-same-secret-configured-in-the-backend
   ```

   The JWT secret must match the value used by the Spring Boot backend, since the Flask service verifies tokens issued by the backend.

5. Run the service.

   ```
   python app.py
   ```

   By default it listens on port 5000. The API is available at `http://localhost:5000/api`.

6. Optional setup scripts:

   - `seed_developers.py` populates sample developer records in the database.
   - `cleanup_legacy_tasks.py` removes outdated task records.
   - `make_token.py` generates a JWT for manual API testing.
   - Training code for the prediction model lives in `ml/train.py`, and the trained model is loaded by `ml/predict.py`.

## Running the Full System

Start both services in this order so that the frontend has both APIs available:

1. Start MongoDB (if running locally).
2. Start the Flask ML service on port 5000.
3. Start the Spring Boot backend on port 8080.
4. Start the frontend from the astron-frontend repository, which by default expects the backend at `http://localhost:8080/api` and the ML service at `http://localhost:5000/api`.

## Authentication

The backend issues JSON Web Tokens on login through `AuthController`. Protected endpoints require an `Authorization: Bearer <token>` header. The same token is accepted by the Flask ML service, which validates it using the shared JWT secret.

## Key Backend Endpoints

Controllers are organized by domain under `backend/src/main/java/dev/astron/backend/controller`:

- `AuthController` - login and registration
- `AccountController` and `UserController` - account and user management
- `ProjectController` - project creation and management
- `TaskController` - task creation, updates, and listing
- `AssignmentController` - assigning tasks to developers
- `DeveloperController` - developer resource pool
- `DashboardController` - dashboard summary data
- `AnalyticsController` - workload and analytics data

## Key ML Service Endpoints

Defined in `astron-ml/app.py`:

- `GET /api/health` - health check, no authentication required
- `POST /api/predict` - predicts effort or complexity for a task, requires a valid token

Recommendation logic (MCDM, used to rank candidate developers for a task) is implemented in `mcdm.py`. Task complexity scoring is implemented in `complexity.py`.

## Testing

The ML service includes test scripts that can be run directly with Python, for example:

```
python test_api.py
python test_db.py
python test_mcdm.py
python test_predict.py
python test_handshake.py
```

These are standalone scripts rather than a formal test suite, so review each file before running it, since some may require a running server or a live database connection.

## Notes on This Branch

This is the refactor branch. Expect ongoing structural changes to the backend controllers, the ML service, and the seeding and cleanup scripts as the codebase is reorganized.
