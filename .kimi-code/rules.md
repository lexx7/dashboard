# Правила проекта: Spring Boot + PostgreSQL + Kafka + Testcontainers

## Стек и технологии
- Java 25
- Spring Boot 4.x
- PostgreSQL (основная БД)
- Apache Kafka (event streaming)
- Maven / Gradle
- Testcontainers для интеграционных тестов
- JUnit 5 + AssertJ / Mockito

## Архитектура и структура проекта
```
├── src/
│   ├── main/java/com/example/dashboard/
│   │   ├── config/          → Конфигурации Spring, Kafka, БД
│   │   ├── controller/      → REST-контроллеры (только маршрутизация + валидация)
│   │   ├── service/         → Бизнес-логика
│   │   ├── repository/      → Spring Data JPA / JDBC
│   │   ├── entity/          → JPA-сущности
│   │   ├── dto/             → DTO для API (запрос/ответ)
│   │   ├── mapper/          → MapStruct или ручные мапперы
│   │   ├── event/           → Kafka events / DTO для событий
│   │   ├── listener/        → Kafka Listeners
│   │   ├── producer/        → Kafka Producers
│   │   └── exception/       → Кастомные исключения + @ControllerAdvice
│   ├── main/resources/
│   │   ├── application.yml  → Основная конфигурация
│   │   ├── application-dev.yml / -prod.yml / -test.yml
│   │   └── db/migration/    → Flyway / Liquibase миграции
│   └── test/java/
│       └── .../
```

## Правила написания кода

### Контроллеры
- Только REST. Не пиши бизнес-логику в контроллерах — делегируй в `Service`.
- Используй `@Valid` для валидации DTO.
- Возвращай `ResponseEntity<T>`.
- Базовый путь: `/api/v1/...`.

### Сервисы
- Аннотировать `@Service` или `@Component`.
- Внедряй зависимости через `constructor injection` (Lombok `@RequiredArgsConstructor` приветствуется).
- Транзакции: `@Transactional(readOnly = true)` по умолчанию, `@Transactional` только на методах с записью.
- Используй `Optional` вместо `null` где возможно.
- Логируй значимые операции через SLF4J (`log.info(...)`, `log.error(...)`).

### Репозитории
- Spring Data JPA интерфейсы. Пиши `JpaRepository<Entity, ID>`.
- Сложные запросы — `@Query` с JPQL / нативным SQL, или `Specification` / `QueryDSL`.
- Никакого SQL-логики в сервисах — пусть останется в репозитории или `@Query`.

### Сущности (JPA)
- `@Entity` + `@Table(name = "...")` с явным указанием имени таблицы.
- Используй `GenerationType.IDENTITY` для PK в PostgreSQL.
- `LocalDateTime` / `Instant` для дат. Никаких `java.sql.Date` / `java.util.Date`.
- `createdAt` / `updatedAt` — `@CreationTimestamp` / `@UpdateTimestamp` или `@PrePersist`/`@PreUpdate`.
- `equals()` и `hashCode()` — по бизнес-ключу, не по `@Id` (особенно если используешь LAZY-загрузку).

### Kafka
- Сериализация: JSON через `JsonSerializer<T>` / `JsonDeserializer<T>`.
- Топики именуй в `kebab-case`: `user-registration`, `order-completed`.
- Producer: инжекти `KafkaTemplate<String, EventDto>`, логируй отправку.
- Listener: `@KafkaListener(topics = "...", groupId = "...")`. Обрабатывай ошибки: `@RetryableTopic`, `SeekToCurrentErrorHandler` или `DefaultErrorHandler`.
- DTO для событий — immutable, с `record` или Lombok `@Value`.

### Обработка ошибок
- `@ControllerAdvice` + `@ExceptionHandler`.
- Единый `ErrorResponse` DTO: `timestamp`, `status`, `error`, `message`, `path`.
- 400 — валидация, 404 — not found, 409 — конфликт, 500 — внутренняя ошибка.

## База данных и миграции
- Используй **Flyway** или **Liquibase**.
- SQL-миграции в `src/main/resources/db/migration/` (Flyway: `V1__init.sql`, `V2__add_index.sql`).
- Не используй `spring.jpa.hibernate.ddl-auto=update` в production.
- Индексы создавай через миграции, а не `@Index` (если DBA-ревью важно).

## Конфигурация
- `application.yml` — основной. Профили: `dev`, `prod`, `test`.
- Чувствительные данные (пароли, токены) — через переменные окружения `${DB_PASSWORD}`.
- Не храни секреты в `.properties` / `.yml` в репозитории.

## Тестирование

### Unit-тесты
- JUnit 5 (`@Test`, `@DisplayName`, `@ParameterizedTest`).
- Mockito: `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`.
- AssertJ: `assertThat(...).isEqualTo(...)`, `assertThatThrownBy(...)`.
- Тестируй сервисы изолированно, мокируй репозитории.

### Интеграционные тесты (Testcontainers)
- Базовый класс: `@SpringBootTest`, `@Testcontainers`.
- PostgreSQL: `@Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")`.
- Kafka: `@Container static KafkaContainer kafka = new KafkaContainer("confluentinc/cp-kafka:latest")`.
- Динамические property-ы: `@DynamicPropertySource`.
- `@Transactional` на тест-методе — откат после теста.
- Не используй `@DirtiesContext` без необходимости — замедляет.

### Пример базового теста с Testcontainers
```java
@SpringBootTest
@Testcontainers
class OrderServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired OrderService orderService;

    @Test
    @DisplayName("Должен создать заказ и опубликовать событие в Kafka")
    void shouldCreateOrderAndPublishEvent() {
        // given / when / then
    }
}
```

## Команды
- Сборка: `./mvnw clean package` / `./gradlew build`
- Тесты: `./mvnw test` / `./gradlew test`
- Интеграционные тесты: `./mvnw verify -P integration-test` (если выделен profile)
- Запуск: `./mvnw spring-boot:run` / `./gradlew bootRun`

## Стиль кода
- Отступы: 4 пробела.
- Импорты: `java.*` → `javax.*` → `org.*` → `com.*`.
- Максимальная длина строки: 120 символов.
- Переменные и методы: `camelCase`. Классы: `PascalCase`. Константы: `UPPER_SNAKE_CASE`.
- Lombok разрешён: `@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`.
- Предпочитай `var` (Java 10+) только когда тип очевиден.

## Запрещено
- `System.out.println()` — используй `log.debug/info/warn/error`.
- `e.printStackTrace()`.
- Сырые SQL-запросы вне `@Query` / `JdbcTemplate` / миграций.
- `Thread.sleep()` в production-коде — используй `ScheduledExecutorService` или Spring `@Scheduled`.
- Синхронные HTTP-вызовы на критичном пути без таймаута.

## CI / CD
- Сборка и тесты в Docker-контейнере или с поднятым Testcontainers.
- Проверка `mvn verify` перед merge.
- SonarQube / Checkstyle / SpotBugs — желательно.

## SPECS
Файлы спецификаций и планы должны оформляться на русском