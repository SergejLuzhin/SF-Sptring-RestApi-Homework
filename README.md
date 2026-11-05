# SF-Spring-RestApi-Homework

Распределённая система бронирования отелей на Java 17 и Spring Boot. Внешние запросы проходят через API Gateway, адреса сервисов разрешаются через Eureka, а Booking Service и Hotel Service работают с независимыми базами H2.

## Оглавление

- [Быстрый старт через скрипты](#quickstart)
- [Ручной запуск и проверка через curl](#manual-usage)
- [Swagger UI и OpenAPI](#openapi)
- [Запуск автоматических тестов и HTML-отчёт](#testing)
- [Архитектура и состав проекта](#architecture)
- [Booking Service](#booking-service)
- [Hotel Service](#hotel-service)
- [API Gateway](#gateway)
- [Eureka Server и Service Discovery](#eureka)
- [REST API и DTO](#api)
- [Алгоритм планирования занятости номеров](#availability)
- [Согласованность данных между сервисами](#consistency)
- [Безопасность: JWT, роли и Spring Security](#security)
- [Структура баз данных](#databases)
- [Обработка ошибок](#errors)
- [Логирование и корреляция запросов](#logging)
- [Реализация автоматических тестов](#test-design)
- [ADR по ключевым решениям](#adr)

<a id="quickstart"></a>
## Быстрый старт через скрипты

Все команды выполняются из корня репозитория. Скрипты рассчитаны на Bash: macOS, Linux, WSL или Git Bash.

### Требования

- JDK 17 или новее, совместимый с Spring Boot 3.5; код компилируется с `--release 17`.
- Maven 3.9 или новее; Maven Wrapper не используется.
- Свободные порты `8761`, `8080`, `8081`, `8082`.
- Для ручных примеров — `curl` и `jq`.

Проверка окружения:

```bash
java -version
mvn --version
curl --version
jq --version
```

### 1. Запустить все сервисы одной задачей

```bash
./scripts/start-local.sh
```

Скрипт:

1. собирает проект командой `mvn -DskipTests package`;
2. задаёт локальные JWT- и ADMIN-параметры, если они не переданы через окружение;
3. запускает Eureka, Hotel Service, Booking Service и API Gateway;
4. ждёт `UP` от health-эндпойнтов и готовности маршрутов Gateway;
5. оставляет все процессы работающими в текущем терминале.

После сообщения `Локальное окружение готово` доступны:

| Назначение | Адрес |
|---|---|
| Публичный API через Gateway | http://localhost:8080 |
| Swagger Booking Service | http://localhost:8082/swagger-ui.html |
| Swagger Hotel Service | http://localhost:8081/swagger-ui.html |
| Панель Eureka | http://localhost:8761 |

Начальный ADMIN при запуске скриптом: `admin@example.com` / `localAdminPassword123`. Логи процессов сохраняются в `target/local-runtime/logs/`.

Для повторного запуска без пересборки неизменённого кода:

```bash
SKIP_BUILD=1 ./scripts/start-local.sh
```

Терминал запуска должен оставаться открытым. **`Ctrl+C` в нём останавливает все четыре сервиса.**

### 2. Открыть интерактивное меню во втором терминале

```bash
./scripts/api-menu.sh
```

Меню отправляет запросы через Gateway и позволяет выбрать:

- регистрацию, вход USER/ADMIN и администрирование пользователей;
- CRUD отелей и номеров;
- поиск свободных и рекомендованных номеров;
- ручное и автоматическое бронирование;
- повтор последнего запроса для проверки идемпотентности;
- историю, получение и отмену брони;
- health-проверки, просмотр сессии и запуск автотестов.

Для каждой операции выводятся HTTP-метод, URL, заголовки, тело запроса, статус и тело ответа. Пароли и JWT маскируются как `***`. Токены USER и ADMIN, последние `hotelId`, `roomId`, `bookingId` и тело бронирования сохраняются только в памяти меню и подставляются в следующие запросы.

В большинстве полей Enter принимает предложенное значение. Для новой брони `requestId` генерируется автоматически. Выход через пункт `0` или `Ctrl+C` не останавливает сервисы.

### 3. Запустить тесты с отчётом

Из отдельного терминала:

```bash
./scripts/run-tests.sh
```

Тот же прогон доступен в пункте `6` главного меню `api-menu.sh`. Работающие сервисы останавливать не нужно: тесты используют отдельные in-memory H2 и не обращаются к локальной Eureka.

После завершения откройте:

```bash
open target/test-report/index.html
```

На Linux вместо `open` можно открыть этот файл в браузере вручную. Подробности и другие команды приведены в разделе [«Запуск автоматических тестов и HTML-отчёт»](#testing).

### Переменные окружения

| Переменная | Назначение |
|---|---|
| `JWT_SECRET` | Общий HS256-секрет Booking и Hotel, не менее 32 байт |
| `JWT_ISSUER` | Ожидаемый издатель JWT; по умолчанию `booking-service` |
| `ADMIN_USERNAME` | Имя начального ADMIN в Booking Service |
| `ADMIN_PASSWORD` | Пароль начального ADMIN, не менее 8 символов |

`start-local.sh` предоставляет значения для локального запуска. При самостоятельном запуске приложений начальный ADMIN создаётся только если заданы обе ADMIN-переменные. Для отдельного окружения задайте собственный секрет и передайте одинаковые `JWT_SECRET` и `JWT_ISSUER` в Booking и Hotel. Локальные значения по умолчанию не предназначены для production.

<a id="manual-usage"></a>
## Ручной запуск и проверка через curl

### Запуск в четырёх терминалах

Сначала соберите исполняемые JAR:

```bash
mvn clean package
```

Запускайте приложения в указанном порядке и не закрывайте терминалы во время проверки.

Терминал 1 — Eureka:

```bash
mvn -pl eureka-server spring-boot:run
```

Терминал 2 — Hotel Service:

```bash
export JWT_SECRET='local-booking-service-jwt-secret-change-me-32-bytes'
export JWT_ISSUER='booking-service'
mvn -pl hotel-service spring-boot:run
```

Терминал 3 — Booking Service с начальным ADMIN:

```bash
export JWT_SECRET='local-booking-service-jwt-secret-change-me-32-bytes'
export JWT_ISSUER='booking-service'
export ADMIN_USERNAME='admin@example.com'
export ADMIN_PASSWORD='localAdminPassword123'
mvn -pl booking-service spring-boot:run
```

Терминал 4 — API Gateway:

```bash
mvn -pl api-gateway spring-boot:run
```

Вместо Maven можно запустить JAR, сохранив переменные окружения в соответствующих терминалах:

```bash
java -jar eureka-server/target/eureka-server-1.0-SNAPSHOT.jar
java -jar hotel-service/target/hotel-service-1.0-SNAPSHOT.jar
java -jar booking-service/target/booking-service-1.0-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-1.0-SNAPSHOT.jar
```

Эти четыре команды выполняются в отдельных терминалах, а не последовательно в одном.

### Проверка готовности

```bash
curl -fsS http://localhost:8761/actuator/health
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8082/actuator/health
curl -fsS http://localhost:8080/actuator/health
```

Ожидаемый ответ: `{"status":"UP"}`. Дождитесь появления `HOTEL-SERVICE`, `BOOKING-SERVICE` и `API-GATEWAY` в реестре http://localhost:8761. Если запрос сразу после запуска вернул 503, подождите завершения регистрации и повторите его.

### Полный пользовательский сценарий

Все бизнес-запросы ниже идут только через Gateway. Сценарий рассчитан на чистые базы; для повторного прогона перезапустите Hotel и Booking либо измените username, номер комнаты и оба `requestId`.

Зарегистрировать USER и сохранить JWT:

```bash
BASE_URL='http://localhost:8080'

USER_TOKEN=$(curl -sS -X POST "$BASE_URL/user/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"user@example.com","password":"password123"}' | jq -r '.accessToken')
```

Отдельный вход USER выпускает новый часовой токен:

```bash
USER_TOKEN=$(curl -sS -X POST "$BASE_URL/user/auth" \
  -H 'Content-Type: application/json' \
  -d '{"username":"user@example.com","password":"password123"}' | jq -r '.accessToken')
```

Войти как ADMIN:

```bash
ADMIN_TOKEN=$(curl -sS -X POST "$BASE_URL/user/auth" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin@example.com","password":"localAdminPassword123"}' | jq -r '.accessToken')
```

Создать отель и номер:

```bash
HOTEL_ID=$(curl -sS -X POST "$BASE_URL/api/hotels" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Grand Hotel","address":"Moscow, Tverskaya 1"}' | jq -r '.id')

ROOM_ID=$(curl -sS -X POST "$BASE_URL/api/rooms" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"hotelId\":$HOTEL_ID,\"number\":\"101\",\"available\":true}" | jq -r '.id')
```

Получить отели, свободные и рекомендованные номера:

```bash
curl -sS "$BASE_URL/api/hotels" \
  -H "Authorization: Bearer $USER_TOKEN" | jq

curl -sS "$BASE_URL/api/rooms?startDate=2026-11-10&endDate=2026-11-15" \
  -H "Authorization: Bearer $USER_TOKEN" | jq

curl -sS "$BASE_URL/api/rooms/recommend?startDate=2026-11-10&endDate=2026-11-15" \
  -H "Authorization: Bearer $USER_TOKEN" | jq
```

Создать бронь с явным выбором номера:

```bash
BOOKING_RESPONSE=$(curl -sS -X POST "$BASE_URL/booking" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"requestId\":\"6a18c94f-f26b-4f3d-994d-665c49d8b51e\",\"roomId\":$ROOM_ID,\"startDate\":\"2026-11-10\",\"endDate\":\"2026-11-12\",\"autoSelect\":false}")

echo "$BOOKING_RESPONSE" | jq
BOOKING_ID=$(echo "$BOOKING_RESPONSE" | jq -r '.id')
```

Успешный ответ — `201` со статусом `CONFIRMED`. Повтор того же POST с тем же телом и `requestId` возвращает `200` и исходную бронь без нового слота. Тот же `requestId` с другими бизнес-параметрами даёт `409`.

Создать бронь с автоподбором на непересекающиеся даты:

```bash
curl -sS -X POST "$BASE_URL/booking" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"requestId":"dc3e90c2-e767-46ed-b21c-c4a1a725c62a","roomId":null,"startDate":"2026-11-12","endDate":"2026-11-15","autoSelect":true}' | jq
```

Получить историю и конкретную бронь, затем отменить её:

```bash
curl -sS "$BASE_URL/bookings" \
  -H "Authorization: Bearer $USER_TOKEN" | jq

curl -sS "$BASE_URL/booking/$BOOKING_ID" \
  -H "Authorization: Bearer $USER_TOKEN" | jq

curl -i -X DELETE "$BASE_URL/booking/$BOOKING_ID" \
  -H "Authorization: Bearer $USER_TOKEN"
```

Отмена возвращает `204`. Повторный DELETE также возвращает `204`, а последующий GET показывает `CANCELLED`.

Проверить, что внутренний release не опубликован через Gateway:

```bash
curl -i -X POST "$BASE_URL/api/rooms/$ROOM_ID/release" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"bookingId":1,"requestId":"6a18c94f-f26b-4f3d-994d-665c49d8b51e","reason":"COMPENSATION"}'
```

Ожидается `404`: такого публичного маршрута нет независимо от переданного пользовательского JWT.

### Остановка и очистка данных

При запуске через `start-local.sh` нажмите `Ctrl+C` в терминале задачи. При ручном запуске остановите каждое приложение в его терминале.

Обе базы находятся в памяти. Перезапуск Hotel или Booking удаляет данные соответствующего сервиса и создаёт схему заново. Начальный ADMIN снова создаётся при наличии обеих ADMIN-переменных.

<a id="openapi"></a>
## Swagger UI и OpenAPI

Документация бизнес-сервисов доступна без JWT:

| Сервис | Swagger UI | OpenAPI JSON |
|---|---|---|
| Booking Service | http://localhost:8082/swagger-ui.html | http://localhost:8082/v3/api-docs |
| Hotel Service | http://localhost:8081/swagger-ui.html | http://localhost:8081/v3/api-docs |

Используется OpenAPI 3.1 и springdoc-openapi. В Swagger UI кнопка **Authorize** принимает JWT без префикса `Bearer`.

Для публичных операций в списке серверов можно выбрать Gateway `http://localhost:8080` или прямой адрес backend-сервиса. Внутренние confirm/release помечены тегом **INTERNAL: занятость номеров**, требуют служебный INTERNAL JWT и имеют только сервер `http://localhost:8081`.

API Gateway не имеет собственного REST-контракта: он маршрутизирует чужие операции, поэтому не содержит Swagger UI и не агрегирует схемы Booking и Hotel. Документация открывается напрямую у бизнес-сервисов, а выполнять публичные вызовы из неё можно через Gateway.

<a id="testing"></a>
## Запуск автоматических тестов и HTML-отчёт

### Полный прогон с удобным отчётом

```bash
./scripts/run-tests.sh
```

Скрипт выполняет `mvn clean verify`, затем формирует Surefire HTML-отчёты. Maven-лог выводится в терминал и сохраняется целиком. Даже при падении тестов создаётся итоговая HTML-страница, а скрипт возвращает ненулевой код завершения.

Результаты:

| Файл или каталог | Содержимое |
|---|---|
| `target/test-report/index.html` | Общий статус, лог и ссылки на отчёты модулей |
| `target/test-report/test-run.log` | Полный текстовый лог прогона |
| `hotel-service/target/reports/surefire.html` | HTML-отчёт Hotel Service |
| `booking-service/target/reports/surefire.html` | HTML-отчёт Booking Service |
| `api-gateway/target/reports/surefire.html` | HTML-отчёт API Gateway |
| `*/target/surefire-reports/` | Исходные TXT/XML-результаты Surefire |

Открыть общий отчёт в macOS:

```bash
open target/test-report/index.html
```

При ошибке ищите имя тестового класса, assertion и stack trace в консоли, `test-run.log` или TXT-файле соответствующего модуля. Каталоги `target/` не включаются в Git.

### Команды Maven

```bash
# Полная очистка, сборка и проверка всех модулей
mvn clean verify

# Все тесты реактора без повторной упаковки JAR
mvn test

# Тесты одного бизнес-сервиса вместе с родительскими модулями
mvn -pl hotel-service -am test
mvn -pl booking-service -am test

# Только проверки OpenAPI и Swagger UI
mvn -pl booking-service,hotel-service -am \
  -Dtest=OpenApiDocumentationIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

# Обновить HTML после уже выполненного test/verify без clean
mvn surefire-report:report
```

Текущий набор содержит **129 проверок**: 68 в Hotel Service, 59 в Booking Service и 2 в API Gateway. В Eureka Server автотестов нет. Устройство тестов описано в разделе [«Реализация автоматических тестов»](#test-design).

<a id="architecture"></a>
## Архитектура и состав проекта

| Компонент | Имя в Eureka | Порт | Ответственность |
|---|---|---:|---|
| API Gateway | `api-gateway` | 8080 | Публичная маршрутизация и correlation ID |
| Hotel Service | `hotel-service` | 8081 | Отели, номера, рекомендации и занятость |
| Booking Service | `booking-service` | 8082 | Пользователи, JWT и оркестрация бронирований |
| Eureka Server | `eureka-server` | 8761 | Реестр и обнаружение экземпляров |

Поток успешного бронирования:

```text
Клиент
  │ POST /booking + USER JWT + requestId
  ▼
API Gateway ──► Booking Service ──► Hotel Service
  lb://            PENDING            confirm + HELD
  маршрут          локальная H2       локальная H2
                     │                    │
                     └──── CONFIRMED ◄────┘
```

Внешний клиент использует Gateway. Booking Service обращается к Hotel Service напрямую по логическому имени `hotel-service` через Eureka и Spring Cloud LoadBalancer. Gateway не участвует во внутренних confirm/release.

Основные технологии: Spring Boot 3.5.7, Spring Cloud 2025.0.0, Spring MVC, Spring Data JPA, Spring Security OAuth2 Resource Server, Spring Cloud Gateway WebFlux, Eureka, H2, MapStruct, Lombok, springdoc-openapi, JUnit 5 и MockMvc.

Структура репозитория:

```text
SpringFrameWorkHomeWork/
├── pom.xml
├── eureka-server/
├── api-gateway/
├── hotel-service/
│   ├── src/main/
│   └── src/test/
├── booking-service/
│   ├── src/main/
│   └── src/test/
└── scripts/
    ├── start-local.sh
    ├── api-menu.sh
    └── run-tests.sh
```

Корневой Maven POM управляет общими версиями и собирает четыре независимых Spring Boot-приложения. Booking и Hotel разделены на слои `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `config` и `exception`; контроллеры возвращают DTO, а не JPA-сущности.

<a id="booking-service"></a>
## Booking Service

Booking Service — REST-приложение на Spring MVC с собственной H2 `bookingdb`.

Основные функции:

- публичная регистрация USER и вход по учётным данным;
- BCrypt-хеширование паролей;
- выпуск пользовательских JWT на один час;
- выпуск служебных INTERNAL JWT на пять минут для вызовов Hotel;
- административное создание, обновление и удаление пользователей;
- создание, история, получение и отмена личных бронирований;
- автоподбор номера через Hotel Service;
- оркестрация PENDING → CONFIRMED либо компенсация PENDING → CANCELLED;
- идемпотентность по `requestId` и условные переходы статусов.

`BookingService` координирует распределённый сценарий, а `BookingPersistenceService` содержит короткие локальные транзакции. Благодаря этому HTTP-вызовы выполняются вне открытой транзакции БД.

`HotelServiceClient` использует load-balanced Spring `RestClient` с адресом `http://hotel-service`. Для рекомендаций передаётся JWT текущего USER, для confirm/release — новый служебный INTERNAL JWT. Клиент применяет тайм-ауты, ограниченные повторы и передаёт наблюдаемые заголовки.

Проверка владения выполняется по `sub` пользовательского JWT и `user_id` записи. Чужая бронь возвращает `404`, как и несуществующая, чтобы не раскрывать её данные. Пользователя с историей бронирований удалить нельзя: возвращается `409`, история не удаляется каскадно.

<a id="hotel-service"></a>
## Hotel Service

Hotel Service — REST-приложение на Spring MVC с собственной H2 `hoteldb`.

Основные функции:

- создание, просмотр, обновление и удаление отелей;
- создание, обновление и удаление номеров;
- поиск операционно доступных и свободных на даты номеров;
- рекомендации с сортировкой `timesBooked ASC, id ASC`;
- атомарная установка занятости через confirm-availability;
- идемпотентное освобождение занятости через release;
- учёт истории операций в `room_reservations`.

Публичные GET-операции доступны USER, изменения отелей и номеров — ADMIN. Внутренние confirm/release доступны только INTERNAL.

Номер комнаты уникален в пределах отеля. Удаление отеля или номера с активной либо исторической записью занятости запрещено с `409`; отель без такой истории удаляется вместе с номерами.

Алгоритм доступности подробно описан в [отдельном разделе](#availability).

<a id="gateway"></a>
## API Gateway

Gateway реализован на Spring Cloud Gateway Server WebFlux. Это реактивное приложение без Spring MVC и без собственной базы данных.

Он выполняет только инфраструктурные задачи:

- принимает публичные запросы на порту 8080;
- выбирает backend через `lb://hotel-service` или `lb://booking-service`;
- сохраняет исходный путь без `StripPrefix` и `RewritePath`;
- передаёт downstream стандартные заголовки, включая `Authorization`;
- принимает либо создаёт `X-Correlation-ID`, передаёт его сервисам и возвращает клиенту.

Настроены только явные публичные маршруты:

```text
/api/hotels
/api/hotels/{hotelId}
/api/rooms
/api/rooms/recommend
/api/rooms/{roomId}
/user
/user/register
/user/auth
/booking
/booking/{bookingId}
/bookings
```

Автоматический Discovery Locator отключён, поэтому новый backend-эндпойнт не становится публичным автоматически. Многосегментные `/api/rooms/{id}/confirm-availability` и `/api/rooms/{id}/release` не совпадают с `/api/rooms/{roomId}` и через Gateway возвращают `404`.

Gateway не проверяет JWT вместо backend-сервисов и не предоставляет Swagger UI.

<a id="eureka"></a>
## Eureka Server и Service Discovery

Eureka Server работает на порту 8761 и содержит реестр доступных экземпляров. Панель реестра: http://localhost:8761.

- Hotel Service регистрируется как `hotel-service`.
- Booking Service регистрируется как `booking-service`.
- API Gateway регистрируется как `api-gateway`.
- Eureka Server не регистрируется клиентом самого себя и не загружает собственный реестр.

Клиенты используют `http://localhost:8761/eureka/`, регистрируются и получают реестр других сервисов. Запросы с URI `lb://...` и load-balanced `RestClient` разрешают логическое имя в фактический адрес через Service Discovery.

Это устраняет жёстко заданные host/port в маршрутах Gateway и в коде Booking Service. При локальном запуске Eureka стартует первой; бизнес-запросы следует выполнять после регистрации сервисов.

Eureka обеспечивает обнаружение, но не транзакционную согласованность, авторизацию или гарантированную доставку запросов.

<a id="api"></a>
## REST API и DTO

### Общие соглашения

- Формат запросов и ответов: `application/json`.
- Даты: ISO `yyyy-MM-dd`; дата и время создания — ISO 8601 в UTC.
- Идентификаторы сущностей — положительные числа; `requestId` — UUID.
- Защищённые запросы содержат `Authorization: Bearer <JWT>`.
- Ошибки используют `application/problem+json` на основе Spring `ProblemDetail`.
- Все публичные бизнес-пути ниже доступны через Gateway на `http://localhost:8080`.

### Booking Service API

| Метод и путь | Назначение | Доступ | Успех |
|---|---|---|---|
| `POST /user/register` | Регистрация и выпуск JWT | Всем | 201 |
| `POST /user/auth` | Вход и выпуск JWT | Всем | 200 |
| `POST /user` | Создание пользователя | ADMIN | 201 |
| `PATCH /user` | Частичное обновление пользователя | ADMIN | 200 |
| `DELETE /user?id={id}` | Удаление пользователя | ADMIN | 204 |
| `POST /booking` | Создание брони | USER | 201; идемпотентный повтор — 200 |
| `GET /bookings` | История текущего пользователя | USER | 200 |
| `GET /booking/{id}` | Получение своей брони | USER | 200 |
| `DELETE /booking/{id}` | Отмена своей брони | USER | 204 |

Публичная регистрация всегда создаёт USER; назначить себе ADMIN через неё нельзя.

Регистрация и вход:

```json
{
  "username": "user@example.com",
  "password": "password123"
}
```

Ответ аутентификации:

```json
{
  "accessToken": "<JWT>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

Административное создание пользователя:

```json
{
  "username": "admin@example.com",
  "password": "password123",
  "role": "ADMIN"
}
```

При PATCH `/user` обязателен только положительный `id`; изменяются переданные `username`, `password`, `role`. Ответ пользователя содержит только `id`, `username`, `role` — ни пароль, ни BCrypt-хеш не возвращаются.

Создание брони с ручным выбором:

```json
{
  "requestId": "6a18c94f-f26b-4f3d-994d-665c49d8b51e",
  "roomId": 10,
  "startDate": "2026-11-10",
  "endDate": "2026-11-15",
  "autoSelect": false
}
```

При `autoSelect=true` переданный `roomId` игнорируется, при `autoSelect=false` он обязателен. Для автоподбора можно передать `"roomId": null`.

Ответ брони:

```json
{
  "id": 25,
  "userId": 2,
  "roomId": 10,
  "startDate": "2026-11-10",
  "endDate": "2026-11-15",
  "status": "CONFIRMED",
  "createdAt": "2026-10-31T12:00:00Z"
}
```

Статусы: `PENDING`, `CONFIRMED`, `CANCELLED`. История сортируется по `createdAt DESC, id DESC` и содержит только записи текущего пользователя. Повторная отмена CANCELLED возвращает `204` без изменений.

### Hotel Service API

| Метод и путь | Назначение | Доступ | Успех |
|---|---|---|---|
| `POST /api/hotels` | Создание отеля | ADMIN | 201 |
| `GET /api/hotels` | Список отелей | USER | 200 |
| `PATCH /api/hotels/{id}` | Частичное обновление отеля | ADMIN | 200 |
| `DELETE /api/hotels/{id}` | Удаление отеля | ADMIN | 204 |
| `POST /api/rooms` | Создание номера | ADMIN | 201 |
| `GET /api/rooms` | Свободные номера на даты | USER | 200 |
| `GET /api/rooms/recommend` | Рекомендованные свободные номера | USER | 200 |
| `PATCH /api/rooms/{id}` | Частичное обновление номера | ADMIN | 200 |
| `DELETE /api/rooms/{id}` | Удаление номера | ADMIN | 204 |

Для списков номеров обязательны обе даты:

```text
GET /api/rooms?startDate=2026-11-10&endDate=2026-11-15
GET /api/rooms/recommend?startDate=2026-11-10&endDate=2026-11-15
```

Создание отеля:

```json
{
  "name": "Grand Hotel",
  "address": "Moscow, Tverskaya 1"
}
```

Создание номера:

```json
{
  "hotelId": 1,
  "number": "101",
  "available": true
}
```

Если `available` не передано, используется `true`. Ответ номера:

```json
{
  "id": 10,
  "hotelId": 1,
  "number": "101",
  "available": true,
  "timesBooked": 4
}
```

PATCH изменяет только переданные поля. Номер комнаты уникален в пределах отеля.

### Внутренние Hotel Service API

Эти операции не публикуются через Gateway и доступны только с INTERNAL JWT напрямую на порту 8081.

`POST /api/rooms/{id}/confirm-availability`:

```json
{
  "bookingId": 25,
  "requestId": "6a18c94f-f26b-4f3d-994d-665c49d8b51e",
  "startDate": "2026-11-10",
  "endDate": "2026-11-15"
}
```

`POST /api/rooms/{id}/release`:

```json
{
  "bookingId": 25,
  "requestId": "6a18c94f-f26b-4f3d-994d-665c49d8b51e",
  "reason": "COMPENSATION"
}
```

Причины release: `COMPENSATION` и `USER_CANCELLATION`. Ответ внутренней операции:

```json
{
  "bookingId": 25,
  "roomId": 10,
  "status": "HELD"
}
```

После release статус ответа — `RELEASED`. Повторный confirm возвращает исходное состояние без нового слота, повторный release безопасен. Release после confirm, который не создал блокировку, возвращает RELEASED без создания записи.

<a id="availability"></a>
## Алгоритм планирования занятости номеров

### Модель интервалов

Даты корректны только при `startDate < endDate`. Используется полуоткрытый интервал `[startDate, endDate)`: дата выезда одного гостя может быть датой заезда следующего.

Пересечение проверяется формулой:

```text
startDate < existingEndDate && endDate > existingStartDate
```

Примеры для существующего интервала `[10.11, 15.11)`:

| Новый интервал | Результат |
|---|---|
| `[08.11, 10.11)` | Не пересекается |
| `[12.11, 13.11)` | Пересекается |
| `[15.11, 18.11)` | Не пересекается |
| `[09.11, 16.11)` | Пересекается |

### Различие доступности и занятости

`Room.available` — эксплуатационный флаг:

- `available=false` выводит номер из эксплуатации и запрещает новые бронирования;
- `available=true` не означает свободность на конкретные даты.

Фактическая занятость хранится отдельно в `room_reservations`. Только записи со статусом `HELD` блокируют пересекающиеся интервалы. Исторические `RELEASED` записи сохраняются, но не влияют на доступность.

Поиск свободных номеров использует JPQL-запрос с `available=true` и `NOT EXISTS` пересекающегося HELD-слота. Поэтому Hotel Service не читает таблицу `bookings` из чужой базы.

### Атомарное подтверждение

При confirm Hotel Service в одной локальной транзакции:

1. получает строку номера с блокировкой `PESSIMISTIC_WRITE`;
2. повторно проверяет существующую операцию по `bookingId`/`requestId`;
3. проверяет `available` и пересечения с HELD-слотами;
4. создаёт одну запись `HELD`;
5. увеличивает `timesBooked` один раз.

Блокировка строки сериализует confirm/release одного номера. Проверка и создание слота не разделяются конкурентными транзакциями: при пересекающихся датах успешной будет только одна активная блокировка. Уникальные ограничения `booking_id` и `request_id` дополнительно защищают от дубликатов операции.

### Рекомендации и автоподбор

`/api/rooms` возвращает свободные номера без специальной сортировки. `/api/rooms/recommend` возвращает их со строгой сортировкой:

```text
timesBooked ASC, id ASC
```

Сначала выбирается менее загруженный номер; `id` обеспечивает детерминированный порядок при равной статистике. При `autoSelect=true` Booking Service берёт первый элемент рекомендаций и игнорирует переданный `roomId`. Если рекомендаций нет, возвращается `409` без PENDING и удалённой блокировки.

### Статистика timesBooked

- Успешный confirm увеличивает счётчик ровно один раз.
- Идемпотентный повтор confirm не меняет счётчик.
- Release с `COMPENSATION` один раз отменяет увеличение неудачного создания.
- Release с `USER_CANCELLATION` сохраняет историческую статистику успешной брони.
- Повторный release не меняет счётчик повторно.

<a id="consistency"></a>
## Согласованность данных между сервисами

### Локальные транзакции вместо распределённой

Между `bookingdb` и `hoteldb` нет общей транзакции. Согласованность обеспечивается оркестрацией, идемпотентными операциями и компенсацией — подходом Saga без отдельного фреймворка.

Создание брони:

1. Booking проверяет пользователя, даты, `roomId`/`autoSelect` и `requestId`.
2. При автоподборе Booking получает рекомендации и выбирает первый номер.
3. Первая локальная транзакция Booking фиксирует PENDING с фактическим `roomId`.
4. Вне транзакции Booking вызывает confirm с `bookingId`, `requestId`, номером и датами.
5. Hotel атомарно создаёт HELD-слот в своей локальной транзакции.
6. После успешного confirm вторая локальная транзакция Booking переводит PENDING → CONFIRMED.
7. Только теперь клиент получает `201` и CONFIRMED.

При бизнес-конфликте, временной ошибке, исчерпании повторов, тайм-ауте или сбое локального подтверждения Booking вызывает release с `COMPENSATION`. После успешного release отдельная транзакция переводит PENDING → CANCELLED, затем клиенту возвращается исходная ошибка.

Release обязателен и после тайм-аута с неизвестным результатом: Hotel мог успеть создать HELD до потери ответа. Идемпотентность release делает такой вызов безопасным, даже если блокировки не было.

Если компенсация не удалась, Booking возвращает `503`, пишет критический лог и сохраняет PENDING для последующей сверки. Ложный CONFIRMED или CANCELLED не возвращается. Автоматического фонового процесса сверки в учебном проекте нет.

### Отмена существующей брони

| Исходный статус | Поведение DELETE |
|---|---|
| CANCELLED | Идемпотентный `204`, изменений нет |
| PENDING | Локальный PENDING → CANCELLED |
| CONFIRMED | Сначала release `USER_CANCELLATION`, затем CONFIRMED → CANCELLED |

Если release при отмене CONFIRMED не удался, локальный статус остаётся CONFIRMED и клиент получает ошибку. Если локальное завершение после успешного release не удалось, ошибка также не маскируется.

### Идемпотентность

- `bookings.requestId` уникален. Повтор с теми же пользователем и бизнес-параметрами возвращает исходную бронь с `200`, без нового confirm.
- Тот же `requestId` с другими параметрами или другим пользователем даёт `409`.
- При автоподборе изменение входного `roomId` не меняет бизнес-параметры, поскольку это поле игнорируется.
- Уникальное ограничение и повторное чтение после гонки защищают от дубликатов при параллельных одинаковых POST.
- `room_reservations.bookingId` и `requestId` уникальны. Повтор confirm/release использует исходную операцию.
- Confirm не воскрешает RELEASED-операцию; изменённые номер, даты или идентификаторы при повторе отклоняются.

### Условные статусы

Изменяющие JPQL-запросы обновляют Booking только из ожидаемого статуса:

```text
PENDING   -> CONFIRMED
PENDING   -> CANCELLED
CONFIRMED -> CANCELLED
```

Это не позволяет позднему ответу вернуть терминальную бронь в PENDING или незаконно заменить CONFIRMED на CANCELLED. Повтор допустимого терминального результата обрабатывается идемпотентно, противоречивый переход отклоняется.

### Тайм-ауты и повторы

| Настройка | Значение по умолчанию |
|---|---:|
| Connect timeout | 2 с |
| Read timeout | 3 с |
| Confirm, всего попыток | 3 |
| Release, всего попыток | 4 |
| Начальная задержка confirm/release | 100 мс |
| Максимальная задержка confirm/release | 500 / 800 мс |

Повторяются только идемпотентные confirm/release после сетевых ошибок, тайм-аутов и ответов `500`, `502`, `503`, `504`. Задержка увеличивается экспоненциально и ограничена. Ответы `400`, `401`, `403`, `404`, `409` и другие постоянные ошибки автоматически не повторяются. У release отдельный лимит попыток.

<a id="security"></a>
## Безопасность: JWT, роли и Spring Security

### Роли и права

| Роль | Разрешения |
|---|---|
| USER | Просмотр отелей/номеров и операции только со своими бронями |
| ADMIN | Управление пользователями, отелями и номерами |
| INTERNAL | Только внутренние confirm/release |

Публичны регистрация, вход, Swagger/OpenAPI и Actuator health/info. Остальные операции требуют аутентификации и соответствующей роли. Правила INTERNAL объявлены раньше общих `/api/rooms/**`, поэтому ADMIN не получает доступ к confirm/release.

### Пользовательский JWT

Booking Service выпускает токен после регистрации или входа:

- алгоритм подписи HS256;
- срок действия ровно 3600 секунд;
- `sub` — строковый ID пользователя;
- `username` и `role`;
- `iss`, `iat`, `exp`.

Пароли хранятся только как BCrypt-хеши. При неверном username или пароле возвращается одинаковая ошибка `401`, не раскрывающая причину. Секрет HS256 должен содержать не менее 32 байт.

### Служебный JWT

Для confirm/release Booking Service выпускает отдельный токен:

- `sub=booking-service`;
- `role=INTERNAL`;
- срок действия 300 секунд;
- без пользовательского `username`.

Он не выдаётся через публичный API и не подменяется токеном пользователя.

### Проверка на backend

Booking и Hotel независимо настроены как stateless OAuth2 Resource Server. Каждый сам:

1. извлекает Bearer JWT;
2. проверяет HS256-подпись;
3. проверяет issuer и срок действия;
4. преобразует claim `role` в authority `ROLE_<роль>`;
5. применяет правила `SecurityFilterChain`.

Сессии, form login, HTTP Basic и logout отключены; CSRF отключён для stateless REST API. Gateway лишь передаёт `Authorization` и не заменяет backend-проверку.

Ошибки `401` и `403` также формируются как ProblemDetail. Секреты и ADMIN-параметры приходят через конфигурацию/окружение; пароли, хеши, полные JWT и секреты не возвращаются в API и не пишутся в логи. H2 Console отключена, Actuator открывает только health/info.

<a id="databases"></a>
## Структура баз данных

У Booking и Hotel независимые in-memory H2. Общей базы и общего JPA-модуля нет.

| Сервис | JDBC URL | Таблицы |
|---|---|---|
| Booking | `jdbc:h2:mem:bookingdb` | `users`, `bookings` |
| Hotel | `jdbc:h2:mem:hoteldb` | `hotels`, `rooms`, `room_reservations` |

Для обеих баз: `ddl-auto=create-drop`, `open-in-view=false`, H2 Console выключена. Схема создаётся Hibernate при старте, данные теряются при перезапуске процесса. Это учебная конфигурация, а не persistent production-хранилище.

### bookingdb

`users`:

| Поле | Назначение и ограничения |
|---|---|
| `id` | Первичный ключ |
| `username` | Уникальное имя пользователя |
| `password` | BCrypt-хеш длиной до 60 символов |
| `role` | Строка `USER` или `ADMIN` |

`bookings`:

| Поле | Назначение и ограничения |
|---|---|
| `id` | Первичный ключ |
| `user_id` | Обязательный FK на `users` |
| `room_id` | ID номера в Hotel Service; логическая связь, не FK между базами |
| `start_date`, `end_date` | Интервал бронирования |
| `status` | `PENDING`, `CONFIRMED`, `CANCELLED` |
| `request_id` | UUID идемпотентности, уникален |
| `auto_select` | Признак автоподбора |
| `created_at`, `updated_at` | Время создания и последнего изменения |

Связь `Booking → User` — многие к одному. Пользователь с историей не удаляется, чтобы сохранить целостность.

### hoteldb

`hotels`: `id`, `name`, `address`.

`rooms`:

| Поле | Назначение и ограничения |
|---|---|
| `id` | Первичный ключ |
| `hotel_id` | FK на `hotels` |
| `number` | Номер комнаты |
| `available` | Эксплуатационная доступность |
| `times_booked` | Историческая статистика подтверждений |

Пара `(hotel_id, number)` уникальна.

`room_reservations`:

| Поле | Назначение и ограничения |
|---|---|
| `id` | Первичный ключ |
| `booking_id` | ID брони из Booking, уникален, неизменяем |
| `request_id` | UUID операции, уникален, неизменяем |
| `room_id` | FK на `rooms` |
| `start_date`, `end_date` | Заблокированный интервал |
| `status` | `HELD` или `RELEASED` |
| `created_at` | Время создания записи |

`booking_id` и `request_id` здесь — логические идентификаторы, а не внешние ключи в другую БД. Связь между сервисами обеспечивается API и идемпотентной оркестрацией.

<a id="errors"></a>
## Обработка ошибок

Оба бизнес-сервиса используют `@RestControllerAdvice` и Spring `ProblemDetail`. Ответ имеет Content-Type `application/problem+json` и содержит стандартные `type`, `title`, `status`, `detail`, `instance`.

| Код | Когда используется |
|---|---|
| 400 | Некорректный JSON, Bean Validation, неверные параметры или интервал дат |
| 401 | JWT отсутствует/недействителен/просрочен; неверные учётные данные |
| 403 | JWT валиден, но роль не разрешает операцию |
| 404 | Отель, номер, пользователь или доступная владельцу бронь не найдены |
| 409 | Занятость, отсутствие свободных номеров, дубликат username/requestId, нарушение целостности |
| 503 | Hotel недоступен после повторов либо распределённую операцию не удалось безопасно завершить |

Пример:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Room is not available for the requested dates",
  "instance": "/booking",
  "requestId": "6a18c94f-f26b-4f3d-994d-665c49d8b51e"
}
```

Обрабатываются ошибки тела, path/query-параметров, отсутствующие ресурсы, бизнес-конфликты и нарушения ограничений БД. Bean Validation собирает понятные сообщения по полям; ошибки разбора не раскрывают внутренний stack trace клиенту.

Ошибки Spring Security обрабатываются отдельными authentication entry point/access denied handler в том же ProblemDetail-формате. Для чужой брони намеренно возвращается `404`, а не `403`, чтобы не подтверждать существование записи.

Booking преобразует ответы Hotel в контракт своих операций: `400` — неверные параметры, `404` — отсутствующий номер, `409` — конфликт доступности, сетевые и прочие неуспешные ответы — `503`. Сбой компенсации не скрывается успешным ответом.

<a id="logging"></a>
## Логирование и корреляция запросов

### Структурный формат

Логи Booking и Hotel выводятся в консоль в едином текстовом формате `key=value`, включающем:

- дату, время и часовой пояс;
- уровень и поток;
- `correlationId`, `requestId`, `bookingId` из SLF4J MDC;
- сокращённое имя логгера и сообщение.

Это структурированные текстовые логи, а не JSON-логи. Пустые идентификаторы не подменяются выдуманными значениями.

### Корреляция запросов

В проекте роль сквозного **traceId** выполняет **`correlationId`**; отдельного поля `traceId` нет.

1. Gateway принимает `X-Correlation-ID`, если он состоит из безопасных букв, цифр, `.`, `_`, `-` и имеет длину 1–128 символов.
2. Отсутствующее или некорректное значение заменяется UUID.
3. Gateway передаёт итоговое значение downstream и возвращает его в ответе.
4. Servlet-фильтры Booking и Hotel помещают его в MDC; при прямом обращении также могут создать UUID.
5. Booking дополнительно передаёт `X-Request-ID`, а после создания PENDING — `X-Booking-ID`.
6. Фильтры очищают MDC в `finally`; вложенные `MdcScope` восстанавливают прежний контекст после операции.

`requestId` связывает повторные попытки одной бизнес-операции, `bookingId` связывает локальную бронь и слот Hotel, `correlationId` связывает весь внешний HTTP-сценарий. Внутренние DTO также содержат `bookingId` и `requestId`.

### Ключевые события и ошибки

Booking логирует:

- создание PENDING и идемпотентный возврат;
- попытки, повторы и результаты confirm/release;
- CONFIRMED, CANCELLED и отмену пользователем;
- сбой подтверждения, успешную компенсацию;
- критическое сообщение при неуспешной компенсации и необходимости сверки.

Hotel логирует начало confirm, идемпотентный повтор, отказ доступности, HELD, release с причиной и RELEASED, включая release отсутствующей блокировки.

Для сетевых сбоев фиксируется безопасная категория `network_or_timeout`, для HTTP — статус. Пароли, BCrypt-хеши, секреты, полные JWT и чувствительные тела запросов в логи не включаются.

При запуске через скрипт логи находятся в `target/local-runtime/logs/`. Для поиска всей цепочки используйте фактический `bookingId`, `requestId` или `correlationId`, например:

```bash
grep 'bookingId=25' target/local-runtime/logs/booking-service.log \
  target/local-runtime/logs/hotel-service.log
```

<a id="test-design"></a>
## Реализация автоматических тестов

Тесты запускаются с JUnit 5, Spring Boot Test, AssertJ и Spring Security Test. Профиль `test` использует отдельные H2 `hoteltestdb`/`bookingtestdb` и отключает Eureka, поэтому работающие приложения не нужны.

### Web- и репозиторные тесты

MockMvc поднимает полный Spring-контекст и проверяет реальные контроллеры, Security, сервисы, MapStruct, репозитории и H2 без внешнего сервера. Покрываются:

- CRUD и валидация отелей, номеров и пользователей;
- регистрация, вход, BCrypt, claims и сроки JWT;
- роли USER/ADMIN/INTERNAL, подпись, issuer, просроченный токен;
- владение бронями, история, создание и отмена;
- границы пересечений, HELD/RELEASED и сортировка рекомендаций;
- ProblemDetail, OpenAPI и Swagger UI;
- приём correlation/request/booking ID и очистка MDC.

`RoomRepositoryTest` отдельно проверяет JPQL и ограничения на реальной H2. Тестовые транзакции откатываются там, где это применимо; идентификаторы берутся из фактически сохранённых сущностей.

### Моки и HTTP-заглушки

В web-тестах Booking удалённый `HotelServiceClient` заменяется заглушкой для изоляции локальной логики. Это позволяет проверять успешный confirm, пустые рекомендации, конфликт, ошибку release и компенсацию без запуска Hotel.

Для сбоев сети одной подмены метода недостаточно. `BookingHttpFailureIntegrationTest` поднимает локальный `com.sun.net.httpserver.HttpServer` на случайном loopback-порту — это HTTP-заглушка, эквивалентная WireMock по назначению, но без дополнительной зависимости. Тест использует настоящие `RestClient`, `HotelServiceClient`, координатор, INTERNAL JWT и H2.

Заглушка возвращает заданные статусы или задерживает ответ, а счётчики проверяют фактическое число HTTP-вызовов. Сценарии:

- успех после временных `503`;
- `409` и `403` без бессмысленных повторов;
- исчерпание confirm и отдельные повторы release;
- read timeout с неизвестным результатом и обязательный release;
- сбой локального CONFIRMED после успешного удалённого HELD;
- неуспешная компенсация, `503` и сохранение PENDING;
- повтор `requestId` без второго удалённого confirm.

### Конкурентные тесты

`RoomReservationConcurrencyIntegrationTest` и `BookingConcurrencyIntegrationTest` используют реальные транзакции H2, пулы потоков и `CyclicBarrier`, а не только Mockito.

Проверяются:

- параллельные confirm одного номера на пересекающиеся даты — одна HELD и один инкремент;
- непересекающиеся даты — независимые подтверждения;
- параллельные одинаковые confirm/release — одна операция и одна компенсация;
- параллельные POST с одинаковым `requestId` — одна Booking и один удалённый confirm;
- конфликт параметров при гонке `requestId`;
- защита терминальных статусов от регрессии.

### Наблюдаемость и Gateway

`OutputCaptureExtension` проверяет ключевые события и наличие correlation/request/booking ID в логах. `CorrelationIdWebFilterTest` покрывает сохранение корректного correlation ID и замену небезопасного значения UUID.

Итог: **129 проверок** — Hotel 68, Booking 59, Gateway 2. Это сочетание интеграционных, web-, репозиторных, реальных HTTP- и конкурентных тестов; «мок-тесты» не сводятся к изолированным Mockito-проверкам.

<a id="adr"></a>
## ADR по ключевым решениям

### ADR-1. Отдельные базы для Booking и Hotel

**Контекст:** сервисы должны быть независимыми, но участвовать в одном бронировании.

**Решение:** каждая служба имеет собственную H2; Hotel хранит собственную проекцию занятости `room_reservations`, а не читает `bookings`.

**Следствие:** нет связей и транзакций между БД, зато сохраняется автономность сервисов. Согласованность обеспечивается API, идемпотентностью и компенсацией.

### ADR-2. Локальная Saga вместо распределённой транзакции

**Контекст:** сбой между двумя базами не должен оставлять ложную бронь или вечный слот.

**Решение:** PENDING фиксируется отдельно, HTTP выполняется вне транзакции, CONFIRMED устанавливается после Hotel confirm, при ошибке выполняется release и CANCELLED.

**Следствие:** нет необходимости в XA/2PC. При неуспешной компенсации рассогласование явно видно через `503`, PENDING и критический лог; фоновая сверка остаётся эксплуатационной задачей за пределами учебного проекта.

### ADR-3. Идемпотентность по UUID и бизнес-ID

**Контекст:** тайм-ауты и повторы могут доставить один запрос несколько раз.

**Решение:** клиентский `requestId` уникален в Booking; `bookingId` и `requestId` уникальны в Hotel. Повтор возвращает исходный результат, изменённые параметры дают `409`.

**Следствие:** retry не создаёт дубликаты и не меняет статистику дважды. Клиент обязан сохранять `requestId` при повторе той же операции.

### ADR-4. PESSIMISTIC_WRITE для строки номера

**Контекст:** предварительная проверка свободности сама по себе не защищает от конкурентной гонки.

**Решение:** confirm/release одного номера сериализуются блокировкой строки Room; проверка пересечений и HELD-создание атомарны, а ограничения БД остаются последней защитой.

**Следствие:** пересекающиеся запросы не создают две активные блокировки. Операции с разными номерами не блокируют друг друга через общую Java-коллекцию или singleton-состояние.

### ADR-5. Resource Server на каждом backend

**Контекст:** Gateway не должен быть единственной границей безопасности.

**Решение:** Booking и Hotel самостоятельно проверяют HS256, issuer и exp; Gateway только маршрутизирует. Внутренние вызовы получают отдельный краткоживущий INTERNAL JWT.

**Следствие:** прямой вызов backend не обходит авторизацию, а пользовательский и служебный доступ разделены. Для симметричной подписи Booking и Hotel должны безопасно разделять секрет.

### ADR-6. Явные публичные маршруты Gateway

**Контекст:** не каждый backend-эндпойнт предназначен внешним клиентам.

**Решение:** Discovery Locator отключён, перечислены только публичные Path-маршруты, внутренние confirm/release не добавлены.

**Следствие:** новые операции не публикуются автоматически; внутренний release через Gateway возвращает `404`. За маршрутизацию и её OpenAPI-описание отвечают разные компоненты, поэтому Swagger находится у backend-сервисов.

### ADR-7. correlationId в MDC вместо отдельной трассировочной системы

**Контекст:** распределённый сценарий нужно диагностировать без внешней инфраструктуры.

**Решение:** `X-Correlation-ID` идёт через Gateway и сервисы; correlationId/requestId/bookingId попадают в MDC и структурный консольный лог.

**Следствие:** по идентификаторам восстанавливается цепочка в объединённых логах. Это не заменяет полноценные OpenTelemetry traces и метрики, но достаточно для локального учебного запуска.

### ADR-8. In-memory H2 как учебное хранилище

**Контекст:** требуется простой воспроизводимый запуск без установки СУБД.

**Решение:** `create-drop` и отдельные in-memory базы для runtime и тестов; консоль H2 не открывается.

**Следствие:** проект запускается без внешних зависимостей инфраструктуры, но все данные теряются при перезапуске. Для production потребуется persistent СУБД, миграции, управление секретами и эксплуатационная сверка незавершённых операций.
