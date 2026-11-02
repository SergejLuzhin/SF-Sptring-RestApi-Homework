# AGENTS.md

## Обзор проекта

Это учебная распределённая система бронирования отелей на Spring Boot. Репозиторий представляет собой многомодульный Maven-проект из четырёх самостоятельных Spring Boot-приложений: Eureka Server, API Gateway, Hotel Management Service и Booking Service.

Важно различать целевой контракт и текущую реализацию. Реализованы каркас приложений, Eureka и Gateway, публичный Hotel Service, пользователи Booking Service, регистрация и вход с выпуском JWT, администрирование пользователей, самостоятельная проверка JWT сервисами и разграничение ролей. Распределённое создание брони выполняет PENDING -> CONFIRMED с внутренними confirm/release либо компенсацию PENDING -> CANCELLED. История, владение и отмена броней, идемпотентность requestId, тайм-ауты, ограниченные повторы и конкурентная защита уже работают. Gateway создаёт или передаёт X-Correlation-ID, бизнес-сервисы помещают correlationId/requestId/bookingId в MDC и логируют ключевые события распределённого сценария. Следующий этап — OpenAPI. Описанные в `README.md` бизнес-эндпойнты являются нормативным контрактом: внутренние пути реализованы и доступны только служебной роли INTERNAL без публикации через Gateway.

Основные документы:

- `README.md` — нормативное описание целевых API-контрактов, DTO, кодов ошибок, бизнес-правил и распределённого сценария бронирования. При изменении контракта его нужно синхронно отражать в README и OpenAPI-документации.
- `homework-task.md` — исходное учебное задание и обязательные требования.
- `homework-plan.md` — поэтапный план реализации и критерии проверки.
- `what-was-done.md` — журнал фактического прогресса; обновлять его при завершении очередных этапов, чтобы он не расходился с кодом.

## Технологии и сборка

- Java: компиляция с `--release 17`; допускается запуск Maven на более новом совместимом JDK.
- Spring Boot: `3.5.7`, родительский POM проекта наследуется от `spring-boot-starter-parent`.
- Spring Cloud: `2025.0.0`, версии управляются через BOM.
- MapStruct: `1.6.3`; Lombok MapStruct Binding: `0.2.0`.
- Бизнес-сервисы: Spring MVC, Spring Data JPA, Bean Validation, H2, Eureka Client, Actuator, Lombok и MapStruct.
- Gateway: Spring Cloud Gateway Server WebFlux и Eureka Client. Это реактивное приложение; не добавлять в него Spring MVC.
- Eureka Server: Eureka Server и Actuator, без бизнес-зависимостей.

Корневой `pom.xml` имеет `packaging=pom`, перечисляет четыре модуля и централизует версии, кодировку UTF-8, уровень Java и annotation processors Lombok/MapStruct. Версии зависимостей, уже управляемые родителем или BOM, не следует дублировать в дочерних POM.

Используется системная команда `mvn`; Maven Wrapper в репозитории отсутствует. Основные команды из корня:

```bash
# Полная очистка, сборка и проверка всех модулей
mvn clean verify

# Все тесты реактора
mvn test

# Сборка одного модуля вместе с необходимыми родительскими модулями
mvn -pl hotel-service -am clean package

# Тесты одного модуля
mvn -pl hotel-service -am test
```

После `package`/`verify` каждый модуль создаёт исполняемый Spring Boot JAR в собственном `target/`, например `hotel-service/target/hotel-service-1.0-SNAPSHOT.jar`.

## Модули и runtime-архитектура

| Модуль | Назначение | Порт | Имя приложения/Eureka | Точка входа |
|---|---|---:|---|---|
| `eureka-server` | Реестр и обнаружение сервисов | 8761 | `eureka-server` | `skillfactory.luzhin.eurekaserver.EurekaServerApplication` |
| `api-gateway` | Публичная маршрутизация через Service Discovery | 8080 | `api-gateway` | `skillfactory.luzhin.gateway.ApiGatewayApplication` |
| `hotel-service` | Целевой сервис отелей, номеров и доступности | 8081 | `hotel-service` | `skillfactory.luzhin.hotel.HotelServiceApplication` |
| `booking-service` | Целевой сервис пользователей и бронирований | 8082 | `booking-service` | `skillfactory.luzhin.booking.BookingServiceApplication` |

Eureka Server не регистрируется клиентом самого себя и не получает собственный реестр. Остальные приложения используют `http://localhost:8761/eureka/` и регистрируются в Eureka.

Gateway содержит только явно заданные маршруты; автоматический Discovery Locator отключён. Маршруты используют `lb://hotel-service` и `lb://booking-service`, сохраняют исходный путь без `StripPrefix`/`RewritePath` и передают стандартные заголовки запроса downstream. Текущие публичные шаблоны:

- Hotel Service: `/api/hotels`, `/api/hotels/{hotelId}`, `/api/rooms`, `/api/rooms/recommend`, `/api/rooms/{roomId}`.
- Booking Service: `/user`, `/user/register`, `/user/auth`, `/booking`, `/booking/{bookingId}`, `/bookings`.

Внутренние пути `/api/rooms/{id}/confirm-availability` и `/api/rooms/{id}/release` не совпадают с публичным односегментным шаблоном `/api/rooms/{roomId}` и не должны добавляться в Gateway. Booking Service вызывает их напрямую через имя `hotel-service` из Eureka с помощью `RestClient`. Подключение и чтение имеют тайм-ауты; confirm и release используют отдельные настройки `hotel.client` с ограниченными повторами и экспоненциальной задержкой только для временных ошибок.

Hotel Service и Booking Service имеют независимые in-memory H2:

- `jdbc:h2:mem:hoteldb` — только Hotel Service;
- `jdbc:h2:mem:bookingdb` — только Booking Service.

Для обеих баз настроены `ddl-auto: create-drop`, `open-in-view: false` и отключённая H2 Console. Данные не сохраняются между перезапусками процессов. Общую базу или общий JPA-модуль между сервисами вводить не предполагается.

Во всех приложениях через Actuator открыты только `health` и `info`.

## Организация кода

Eureka Server и API Gateway пока содержат главный класс и `application.yml`. Hotel Service и Booking Service содержат слои `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `config` и `exception`. Оба бизнес-сервиса имеют тесты в `src/test`. Корневой каталог `src/` не является отдельным модулем и не содержит исходников. Фактическая структура:

```text
SpringFrameWorkHomeWork/
├── pom.xml
├── eureka-server/
│   ├── pom.xml
│   └── src/main/
├── api-gateway/
│   ├── pom.xml
│   └── src/main/
├── hotel-service/
│   ├── pom.xml
│   ├── src/main/
│   └── src/test/
└── booking-service/
    ├── pom.xml
    ├── src/main/
    └── src/test/
```

По `homework-plan.md` бизнес-сервисы должны разделяться на пакеты `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `config` и `exception` внутри соответствующего корневого пакета модуля. Наружу контроллеры должны возвращать DTO, а не JPA-сущности. Каждый сервис остаётся самостоятельным приложением со своей базой.

## Правила разработки

- Основной язык документации, комментариев и JavaDoc — русский.
- Для каждого класса и каждого метода обязателен JavaDoc на русском языке; это прямо зафиксировано в `homework-plan.md` и уже соблюдается в главных классах.
- Сохранять существующее форматирование: четыре пробела в Java и XML, два пробела для вложенности YAML.
- Имена пакетов продолжать в стиле `skillfactory.luzhin.<service>`; не смешивать код разных приложений.
- Конфигурацию конкретного приложения держать в его `application.yml`, общие версии и настройки компиляции — в корневом `pom.xml`.
- Соблюдать контракты из `README.md`: JSON, ISO-даты `yyyy-MM-dd`, `requestId` в формате UUID и ошибки на основе Spring `ProblemDetail` с `application/problem+json`.
- Не считать пример из исходного задания приоритетнее уточнённого контракта README: README уже разрешает неоднозначности URL, ролей и бизнес-правил.

Ключевые целевые инварианты, которые нельзя терять при реализации:

- Корректный интервал бронирования: `startDate < endDate`, семантика `[startDate, endDate)`; пересечение проверяется как `startDate < existingEndDate && endDate > existingStartDate`.
- `Room.available` означает эксплуатационную доступность, а не свободность на даты. Занятость должна определяться отдельными активными записями Hotel Service.
- Рекомендации сортируются строго по `timesBooked ASC, id ASC`; при `autoSelect=true` переданный `roomId` игнорируется.
- Повтор одинакового `requestId` возвращает исходную бронь без дубликата; тот же `requestId` с другими параметрами даёт 409.
- Создание брони — последовательность локальных транзакций `PENDING -> CONFIRMED` либо компенсация `PENDING -> CANCELLED`, без глобальной распределённой транзакции.
- HTTP-вызовы между сервисами не выполняются внутри открытой транзакции БД. Confirm/release должны быть идемпотентными, иметь тайм-ауты и ограниченные повторы; release вызывается также после тайм-аута с неизвестным результатом confirm.
- Проверка пересечений и создание слота в Hotel Service должны быть атомарными и защищёнными от конкурентных бронирований одного номера.
- Ключевые события распределённого сценария должны логироваться с `bookingId` и `requestId`.

## Локальный запуск и проверка

Запускать приложения в отдельных терминалах из корня репозитория:

```bash
mvn -pl eureka-server spring-boot:run
mvn -pl hotel-service spring-boot:run
mvn -pl booking-service spring-boot:run
mvn -pl api-gateway spring-boot:run
```

Сначала запустить Eureka, затем бизнес-сервисы и Gateway; перед проверкой маршрутов дождаться регистрации сервисов. Можно также сначала выполнить `mvn clean package` и запускать соответствующие JAR через `java -jar`.

Проверка готовности:

```bash
curl http://localhost:8761/actuator/health
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

Ожидаемый ответ работающего приложения: `{"status":"UP"}`. Публичные операции Hotel Service, пользовательские эндпойнты и распределённый сценарий Booking Service уже реализованы. Успешное создание возвращает CONFIRMED, конфликт или временный сбой после компенсации оставляет CANCELLED, а неуспешная компенсация возвращает 503 и сохраняет PENDING для сверки. Отмена CONFIRMED вызывает release в Hotel Service.

Перед завершением изменений выполнить `mvn clean verify`, а при затрагивании runtime — проверить соответствующий сценарий на реально запущенных приложениях. Также полезны `git diff --check` и `git status --short` для обнаружения пробельных ошибок и случайных файлов.

## Тестирование

Всего реализованы 117 автоматических проверок: 61 в Hotel Service, 54 в Booking Service и 2 в API Gateway. Используются JUnit 5, MockMvc, AssertJ, Spring Security Test, реальные H2 и профили `test` без Eureka. Тесты покрывают CRUD, доступность, рекомендации, регистрацию, вход, BCrypt, claims JWT, администрирование пользователей, безопасность, распределённое подтверждение, идемпотентность requestId, историю, владение и отмену бронирований. Локальный HTTP-сервер проверяет реальные тайм-ауты, повторы и компенсацию; конкурентные тесты используют реальные транзакции H2 и синхронизацию потоков. Отдельные проверки покрывают приём и создание correlation ID, очистку MDC и логи успешного и неуспешного распределённого сценария. В Eureka Server тесты пока отсутствуют.

Целевая стратегия из `homework-plan.md`:

- JUnit и MockMvc для валидации, CRUD, регистрации, аутентификации, ролей, владения бронями и HTTP-кодов.
- Репозиторные тесты на H2 для запросов доступности и ограничений БД.
- HTTP-заглушка Hotel Service (WireMock или эквивалент) для реальных сценариев тайм-аута, повторов, ошибок confirm и компенсации release.
- Проверки успеха, бизнес-конфликта, недоступности удалённого сервиса, тайм-аута после установки блокировки, повторной доставки и неуспешной компенсации.
- Конкурентные тесты на реальных транзакциях H2 и синхронизации потоков, а не только на Mockito: пересекающиеся и непересекающиеся даты, одинаковые параллельные `requestId`, защита терминальных статусов.
- Финальная сквозная проверка пользовательского сценария только через Gateway, включая недоступность внутреннего release через Gateway.

Тесты размещать в `src/test/java` соответствующего модуля и запускать как модульно, так и полной командой `mvn clean verify`.

## Безопасность

В Booking Service реализованы BCrypt, выпуск пользовательского JWT на один час и служебного INTERNAL JWT на пять минут. В Booking и Hotel настроены stateless OAuth2 Resource Server, проверка HS256-подписи, issuer и срока действия, преобразование claim `role` в authorities и ошибки 401/403 в формате ProblemDetail. Действует модель из README и плана:

- Роли `USER`, `ADMIN` и `INTERNAL`; публичная регистрация создаёт только `USER`.
- Пользовательский JWT действует один час; пароли хранятся только как BCrypt-хеши.
- Booking и Hotel самостоятельно проверяют подпись, issuer и срок действия JWT как OAuth2 Resource Server. Gateway только маршрутизирует и не заменяет backend-проверку.
- Внутренние confirm/release доступны только служебному JWT с ролью `INTERNAL`; пользовательский токен для этого не использовать.
- USER может читать и отменять только собственные брони. ADMIN управляет пользователями, отелями и номерами.
- Секреты JWT и начальные учётные данные ADMIN должны поступать через конфигурацию/переменные окружения. Реальные секреты не коммитить; пароли, хеши и полные JWT не возвращать в API и не писать в логи.
- Не открывать дополнительные Actuator-эндпойнты или H2 Console без необходимости: сейчас доступны только health/info, а консоль отключена.

## Деплоймент и ограничения среды

В репозитории нет Dockerfile, Compose, CI-конфигурации, production-профилей или скриптов развёртывания. Поддерживаемый текущий способ запуска — локальный запуск четырёх Spring Boot-приложений через Maven или исполняемые JAR. Настроенные H2 являются временными базами для учебного проекта, поэтому текущую конфигурацию нельзя считать production-развёртыванием или persistent-хранилищем.
