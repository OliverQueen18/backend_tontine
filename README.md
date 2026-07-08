# Backend Tontine Marché

API REST Spring Boot 3 (Java 21) pour la gestion multi-agences des tontines de marché.

## Démarrage

```bash
mvn spring-boot:run
```

PostgreSQL requis. Créer la base avant le démarrage :

```sql
CREATE DATABASE "BD_TONTINE";
```

Connexion par défaut :
- Base : `BD_TONTINE`
- User : `oservices`
- Password : `Services@2025*!`
- Host : `localhost:5432`

Les tables et données de démo sont créées automatiquement au démarrage (`ddl-auto: update`).

- API : http://localhost:8081/api
- Swagger : http://localhost:8081/swagger-ui.html

Variables optionnelles : `DATABASE_URL`, `DATABASE_USER`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`.
