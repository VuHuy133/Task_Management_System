# Trello Mini - Project Management System

A project management platform built with Spring Boot backend and React frontend.

## Frontend

This is the React frontend built with Vite.

### Available Scripts

In the `frontend` directory, you can run:

### `npm install`

Install all project dependencies.

### `npm run dev`

Runs the app in development mode.\
Open [http://localhost:5173](http://localhost:5173) to view it in your browser.

The page will reload when you make changes.

### `npm run build`

Builds the app for production to the `dist` folder.\
It correctly bundles React in production mode and optimizes the build for the best performance.

Your app is ready to be deployed!

## Backend

This is the Spring Boot backend REST API.

### Prerequisites

- JDK 17+
- Maven 3.9.6+
- MySQL 8.0+ (running)
- Redis 7+ (running)

### Installation

In the project root directory, you can run:

### `mvnw clean package`

Build the backend project.

### `mvnw spring-boot:run`

Runs the backend server.\
The server will start at [http://localhost:8088](http://localhost:8088)

## Docker (Recommended)

The easiest way to run the complete application:

```bash
docker compose up -d
```

Services will be available at:
- Frontend: http://localhost:3000
- Backend: http://localhost:8088


<<<<<<< HEAD


Redis will run on `localhost:6379`

### Option 2: Running Locally

1. Ensure MySQL and Redis are running

2. Update `application.properties` with your MySQL and Redis connection details:
```properties

=======
>>>>>>> d7e30d9 (Update code 4)

3. Build the project:
```bash
mvn clean package -DskipTests
```

4. Run the application:
```bash
mvn spring-boot:run
```

The application will start on `http://localhost:3000`

## API Endpoints

### Authentication
- **POST** `/api/auth/register` - Register a new user
```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "password123"
}
```

- **POST** `/api/auth/login` - Login user
```json
{
  "email": "john@example.com",
  "password": "password123"
}
```

- **POST** `/api/auth/logout` - Logout user (requires JWT token)

### Projects
- **POST** `/api/projects` - Create a new project
- **GET** `/api/projects` - Get all projects
- **GET** `/api/projects/{id}` - Get project by ID
- **PUT** `/api/projects/{id}` - Update project
- **DELETE** `/api/projects/{id}` - Delete project

### Tasks
- **POST** `/api/tasks` - Create a new task
- **GET** `/api/projects/{projectId}/tasks` - Get all tasks in a project
- **PUT** `/api/tasks/{id}` - Update task
- **DELETE** `/api/tasks/{id}` - Delete task

### Task Comments
- **POST** `/api/comments` - Add a comment to a task
- **GET** `/api/tasks/{taskId}/comments` - Get all comments for a task

### Project Members
- **POST** `/api/projects/{projectId}/members` - Add member to project
- **GET** `/api/projects/{projectId}/members` - Get project members

## Testing with Postman

A Postman collection is included: `Trello_Mini_API.postman_collection.json`

1. Import the collection in Postman
2. Set the environment variable `jwt_token` after login
3. Use the provided requests to test all endpoints

## Configuration Profiles

The application supports multiple configuration profiles:

- **application.properties** - Default configuration (MySQL localhost)
- **application-dev.properties** - Development configuration
- **application-docker.properties** - Docker environment configuration
- **application-prod.properties** - Production configuration (uses environment variables)

To use a specific profile:
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=docker"
```

## Building Docker Image

Build the Docker image:
```bash
docker build -t trello-mini .
```

Run the container:
```bash
docker run -p 3000:3000 --network trello-network \
  -e SPRING_PROFILES_ACTIVE=docker \
  trello-mini
```

## Project Setup Details

### Spring Security Configuration
- JWT token validation on all protected endpoints
- CORS configuration for frontend integration
- Password encryption using BCryptPasswordEncoder

### Database Initialization
- Automatic schema creation on application startup (via Hibernate)
- Data relationships properly defined with foreign keys

### Caching Strategy
- Redis used for session caching
- Manual cache invalidation on data updates

## Known Issues & Troubleshooting

**Database Connection Error**
- Ensure MySQL is running and accessible
- Check credentials in configuration file
- Verify database name is correct (default: `trello`)

**Redis Connection Error**
- Ensure Redis is running on port 6379
- Check Redis connectivity: `redis-cli ping`

**Port Already in Use**
- Change port in `application.properties`: `server.port=8080`
- Or stop the service using the port

## Future Enhancements

- [ ] Real-time notifications using WebSocket
- [ ] Email notifications for assigned tasks
- [ ] Task filtering and search functionality
- [ ] User profile management
- [ ] Activity logging and audit trail
- [ ] Project templates
- [ ] Bulk operations

## License

MIT License - Feel free to use this project for learning purposes.

## Support

For issues or questions, please create an issue in the repository or contact the development team.
