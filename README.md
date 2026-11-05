# SF-Spring-RestApi-Homework

Распределённая система бронирования отелей на Spring Boot. Проект состоит из API Gateway, Hotel Management Service, Booking Service и Eureka Server.

Этот документ содержит инструкции для локального запуска и проверки, API-контракты и бизнес-правила. Изменения контрактов должны одновременно отражаться в README и OpenAPI-документации.

## Быстрый старт и самостоятельная проверка

Все команды ниже выполняются из корня репозитория. Для ручной проверки нужно запустить четыре приложения, дождаться их регистрации в Eureka и отправлять бизнес-запросы только через API Gateway на порт 8080. Автоматические тесты запускаются отдельно и не требуют работающих приложений.

### Требования

- JDK 17 или новее, совместимый с Spring Boot 3.5; компиляция выполняется с `--release 17`.
- Maven 3.9 или новее; Maven Wrapper в репозитории отсутствует.
- Свободные порты 8761, 8080, 8081 и 8082.
- Для примеров ниже — Bash, `curl` и `jq`. В macOS и Linux они позволяют выполнять команды без изменений; в Windows потребуется адаптировать присваивание переменных окружения к используемой оболочке.

Проверить наличие инструментов:

```bash
java -version
mvn --version
curl --version
jq --version
```

Обе H2-базы находятся в памяти: Hotel Service использует `hoteldb`, Booking Service — `bookingdb`. После перезапуска соответствующего приложения его данные удаляются.

### Конфигурация

| Переменная | Назначение |
|---|---|
| `JWT_SECRET` | Общий секрет HS256 для Booking и Hotel, не менее 32 байт |
| `JWT_ISSUER` | Общий ожидаемый издатель JWT, по умолчанию `booking-service` |
| `ADMIN_USERNAME` | Имя начального ADMIN в Booking Service |
| `ADMIN_PASSWORD` | Пароль начального ADMIN, не менее 8 символов |

Локальный секрет по умолчанию предназначен только для учебного запуска. Для отдельного окружения нужно задать собственный `JWT_SECRET` и одинаковые `JWT_SECRET`/`JWT_ISSUER` в Booking и Hotel. Начальный ADMIN создаётся только если заданы обе переменные `ADMIN_USERNAME` и `ADMIN_PASSWORD`; без них администратора можно создать позже через существующего ADMIN.

### Запуск одной задачей — рекомендуемый способ

В macOS, Linux, WSL или Git Bash достаточно выполнить из корня репозитория:

```bash
./scripts/start-local.sh
```

Скрипт автоматически:

1. собирает все модули командой `mvn -DskipTests package`;
2. задаёт локальные JWT и ADMIN-параметры, если они не переданы через окружение;
3. запускает Eureka, Hotel Service, Booking Service и API Gateway;
4. ждёт ответы `UP` от всех health-эндпойнтов;
5. проверяет через Gateway, что маршруты к обоим бизнес-сервисам уже готовы;
6. оставляет все сервисы работающими в текущей задаче.

После сообщения `Локальное окружение готово` можно открыть:

- Booking Swagger UI: `http://localhost:8082/swagger-ui.html`;
- Hotel Swagger UI: `http://localhost:8081/swagger-ui.html`;
- Eureka: `http://localhost:8761`.

Для входа начального ADMIN по умолчанию используются `admin@example.com` / `localAdminPassword123`. Их, а также `JWT_SECRET` и `JWT_ISSUER`, можно переопределить переменными окружения перед командой запуска. Логи всех процессов находятся в `target/local-runtime/logs/`.

Для повторного запуска без пересборки неизменённого кода:

```bash
SKIP_BUILD=1 ./scripts/start-local.sh
```

Задача должна оставаться запущенной во время проверки. **`Ctrl+C` в этом терминале остановит все четыре сервиса.** Отдельные PID-файлы, ручной поиск процессов и четыре терминала не требуются. В обычном Windows PowerShell без Bash используйте ручной вариант ниже.

### Интерактивное меню во втором терминале

Не останавливая первую задачу, из корня репозитория запустите:

```bash
./scripts/api-menu.sh
```

Меню позволяет выбирать все публичные операции по разделам:

- регистрация, вход USER/ADMIN и администрирование пользователей;
- создание, просмотр, обновление и удаление отелей и номеров;
- поиск свободных и рекомендованных номеров;
- ручное и автоматическое бронирование, повтор последнего запроса, история, получение и отмена брони;
- health всех сервисов и просмотр текущей сессии.

Для каждой операции выводятся HTTP-метод, полный URL, замаскированный заголовок Authorization, тело запроса, статус и тело ответа. Пароли и полные JWT заменяются на `***`. После регистрации или входа токены USER и ADMIN сохраняются отдельно в памяти меню и автоматически подставляются в нужные запросы. Последние `hotelId`, `roomId`, `bookingId` и тело успешного бронирования также используются как значения по умолчанию.

В большинстве полей можно нажать Enter, чтобы принять показанное значение. Для нового бронирования `requestId` генерируется автоматически, а пункт повторной отправки использует прежнее тело для демонстрации идемпотентности. Завершение меню через пункт `0` или `Ctrl+C` не останавливает сервисы.

### Альтернатива: запуск в четырёх терминалах

Сначала собрать исполняемые JAR из корня репозитория:

```bash
mvn clean package
```

Затем запустить приложения в четырёх отдельных терминалах в указанном порядке. Не закрывайте эти терминалы во время проверки. Экспорт переменных нужно повторить в каждом терминале, где они используются.

Терминал 1 — Eureka Server:

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

Вместо `spring-boot:run` можно запустить собранные JAR, сохранив показанные выше переменные окружения:

```bash
# Терминал 1
java -jar eureka-server/target/eureka-server-1.0-SNAPSHOT.jar

# Терминал 2
java -jar hotel-service/target/hotel-service-1.0-SNAPSHOT.jar

# Терминал 3
java -jar booking-service/target/booking-service-1.0-SNAPSHOT.jar

# Терминал 4
java -jar api-gateway/target/api-gateway-1.0-SNAPSHOT.jar
```

После запуска дождаться регистрации Hotel Service, Booking Service и API Gateway в Eureka. Реестр доступен по адресу `http://localhost:8761`. Проверить готовность всех процессов:

```bash
curl -fsS http://localhost:8761/actuator/health
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8082/actuator/health
curl -fsS http://localhost:8080/actuator/health
```

Ожидаемый ответ каждого приложения: `{"status":"UP"}`. Если бизнес-запрос сразу после запуска вернул 503, подождите завершения регистрации сервисов и повторите запрос.

### Проверка через Swagger UI и OpenAPI

Документация доступна напрямую в бизнес-сервисах без JWT:

| Сервис | Swagger UI | OpenAPI JSON |
|---|---|---|
| Booking Service | `http://localhost:8082/swagger-ui.html` | `http://localhost:8082/v3/api-docs` |
| Hotel Service | `http://localhost:8081/swagger-ui.html` | `http://localhost:8081/v3/api-docs` |

В Swagger UI кнопка **Authorize** принимает JWT без префикса `Bearer`. Для публичных операций выбран сервер Gateway `http://localhost:8080`; также можно выбрать прямой адрес сервиса. Внутренние confirm/release выделены тегом **INTERNAL: занятость номеров**, требуют служебный JWT с ролью INTERNAL и имеют только сервер `http://localhost:8081`. Они недоступны через Gateway. Сам Gateway не содержит Swagger UI и не агрегирует схемы backend-сервисов.

### Готовый пользовательский сценарий через Gateway

Все бизнес-запросы ниже выполняются только через `http://localhost:8080`. Пример использует ADMIN, созданный переменными окружения выше, и рассчитан на чистое состояние in-memory баз. Для повторного полного прогона перезапустите Hotel и Booking либо измените username, номер комнаты и оба `requestId`.

Задать базовый URL, зарегистрировать USER и получить его JWT:

```bash
BASE_URL='http://localhost:8080'

USER_TOKEN=$(curl -sS -X POST "$BASE_URL/user/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"user@example.com","password":"password123"}' | jq -r '.accessToken')
```

Отдельный вход и выпуск нового часового JWT:

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

Создать бронирование с явным выбором номера:

```bash
BOOKING_RESPONSE=$(curl -sS -X POST "$BASE_URL/booking" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"requestId\":\"6a18c94f-f26b-4f3d-994d-665c49d8b51e\",\"roomId\":$ROOM_ID,\"startDate\":\"2026-11-10\",\"endDate\":\"2026-11-12\",\"autoSelect\":false}")

echo "$BOOKING_RESPONSE" | jq
BOOKING_ID=$(echo "$BOOKING_RESPONSE" | jq -r '.id')
```

Успешный ответ имеет код 201 и статус CONFIRMED. Повтор того же POST с тем же `requestId` и телом возвращает код 200 и исходную бронь без нового слота; изменение параметров при том же `requestId` даёт 409.

Создать бронирование с автоподбором на непересекающиеся даты:

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

Отмена возвращает 204. Повторный DELETE той же брони также возвращает 204, а последующий GET показывает статус CANCELLED.

Для проверки запрета внутренних операций через публичный Gateway можно выполнить следующий запрос: ожидаемый ответ — 404, независимо от пользовательского JWT.

```bash
curl -i -X POST "$BASE_URL/api/rooms/$ROOM_ID/release" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"bookingId":1,"requestId":"6a18c94f-f26b-4f3d-994d-665c49d8b51e","reason":"COMPENSATION"}'
```

### Автоматические тесты и отчёты

Остановка запущенных приложений перед тестами не требуется: тесты используют отдельные H2-базы, профиль `test` и отключённый Eureka Client. Из корня репозитория доступны команды:

```bash
# Полная очистка, сборка и все проверки четырёх модулей
mvn clean verify

# Все тесты реактора без повторной упаковки JAR
mvn test

# Тесты одного бизнес-сервиса с необходимыми родительскими модулями
mvn -pl hotel-service -am test
mvn -pl booking-service -am test

# Только проверки OpenAPI и Swagger UI
mvn -pl booking-service,hotel-service -am \
  -Dtest=OpenApiDocumentationIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Для полного прогона с формированием HTML-отчётов выполнить:

```bash
mvn clean verify surefire-report:report
```

Успешный результат обозначается `BUILD SUCCESS`. Сейчас набор содержит 129 проверок: 68 в Hotel Service, 59 в Booking Service и 2 в API Gateway; в Eureka Server автотестов нет. Отчёты сохраняются в `target/`, который не включается в Git:

| Модуль | HTML-отчёт | Исходные TXT/XML-результаты |
|---|---|---|
| Hotel Service | `hotel-service/target/reports/surefire.html` | `hotel-service/target/surefire-reports/` |
| Booking Service | `booking-service/target/reports/surefire.html` | `booking-service/target/surefire-reports/` |
| API Gateway | `api-gateway/target/reports/surefire.html` | `api-gateway/target/surefire-reports/` |

HTML-файл можно открыть в браузере. В macOS для этого подходит команда:

```bash
open hotel-service/target/reports/surefire.html \
  booking-service/target/reports/surefire.html \
  api-gateway/target/reports/surefire.html
```

После уже выполненного `mvn test` или `mvn verify` HTML можно обновить без очистки каталогов командой `mvn surefire-report:report`. TXT-файлы содержат краткие результаты по классам, XML — машиночитаемые результаты каждой проверки. При падении теста ищите название класса, assertion и stack trace в консоли и в соответствующем TXT-файле.

### Остановка и повторный запуск

При запуске через `start-local.sh` нажмите `Ctrl+C` один раз в терминале задачи: скрипт остановит все запущенные им сервисы. При ручном запуске остановите каждое приложение в его терминале. Для следующего чистого ручного сценария достаточно перезапустить Hotel Service и Booking Service: их in-memory базы будут созданы заново. Если Booking Service остановлен, начальный ADMIN снова создаётся при наличии `ADMIN_USERNAME` и `ADMIN_PASSWORD`.

Правила границ транзакций описаны в разделе 6: PENDING фиксируется до HTTP-вызова, CONFIRMED — в отдельной локальной транзакции после ответа Hotel. По умолчанию connect timeout составляет 2 с, read timeout — 3 с; confirm выполняется максимум за 3 попытки, release — за 4. Повторы используют экспоненциальную задержку только для временных ошибок, а после сбоя confirm всегда предпринимается ограниченная компенсация release, включая тайм-аут с неизвестным результатом.

## 1. Компоненты и адреса

| Компонент | Имя в Eureka | Порт по умолчанию |
|---|---|---:|
| API Gateway | `api-gateway` | 8080 |
| Hotel Management Service | `hotel-service` | 8081 |
| Booking Service | `booking-service` | 8082 |
| Eureka Server | — | 8761 |

Внешние клиенты обращаются только к API Gateway. Gateway сохраняет исходный путь запроса, прокидывает заголовок `Authorization` и маршрутизирует запросы через `lb://hotel-service` или `lb://booking-service`.

Внутренние эндпойнты подтверждения и освобождения номера через Gateway не публикуются. Booking Service вызывает их напрямую по имени `hotel-service`, полученному из Eureka.

## 2. Общие соглашения API

- Формат запросов и ответов: `application/json`.
- Даты передаются в формате ISO 8601 `yyyy-MM-dd`.
- Дата и время создания возвращаются в ISO 8601 с часовым поясом UTC.
- Идентификаторы сущностей и бронирований — положительные числа, `requestId` — UUID.
- Каждый запрос на создание бронирования содержит клиентский `requestId`.
- Gateway принимает безопасный `X-Correlation-ID` длиной до 128 символов либо создаёт UUID, передаёт его backend-сервисам и возвращает в ответе. Booking Service дополнительно передаёт `X-Request-ID` и, после создания брони, `X-Booking-ID` во внутренних вызовах Hotel Service.
- Correlation ID, `requestId` и `bookingId` помещаются в MDC и включаются в шаблон консольного лога ключевых событий распределённого сценария.
- Для защищённых эндпойнтов используется заголовок `Authorization: Bearer <JWT>`.
- Пользовательский JWT действителен один час.
- Ошибки возвращаются как `application/problem+json` на основе Spring `ProblemDetail`.

Пример ошибки:

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

## 3. Контракт Booking Service

### 3.1. Эндпойнты

| Метод и путь | Назначение | Доступ | Успешный ответ |
|---|---|---|---|
| `POST /user/register` | Регистрация и выпуск JWT | Всем | 201 |
| `POST /user/auth` | Вход и выпуск JWT | Всем | 200 |
| `POST /user` | Создание пользователя | ADMIN | 201 |
| `PATCH /user` | Обновление пользователя | ADMIN | 200 |
| `DELETE /user?id={id}` | Удаление пользователя | ADMIN | 204 |
| `POST /booking` | Создание бронирования | USER | 201; повтор requestId — 200 |
| `GET /bookings` | История текущего пользователя | USER | 200 |
| `GET /booking/{id}` | Получение своей брони | USER | 200 |
| `DELETE /booking/{id}` | Отмена своей брони | USER | 204 |

Регистрация всегда создаёт пользователя с ролью USER. Передать роль ADMIN через публичную регистрацию нельзя.

### 3.2. Регистрация и вход

Запрос регистрации:

```json
{
  "username": "user@example.com",
  "password": "password123"
}
```

Запрос входа имеет те же поля. Ответ регистрации и входа:

```json
{
  "accessToken": "<JWT>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

Пароль должен быть непустым и содержать не менее 8 символов. В БД сохраняется только BCrypt-хеш. Пароль и его хеш никогда не возвращаются в ответах.

### 3.3. Администрирование пользователей

Создание пользователя:

```json
{
  "username": "admin@example.com",
  "password": "password123",
  "role": "ADMIN"
}
```

Обновление пользователя:

```json
{
  "id": 2,
  "username": "new-name@example.com",
  "password": "newPassword123",
  "role": "USER"
}
```

При PATCH обязателен только `id`; остальные поля изменяются, если переданы. Ответ на создание и обновление:

```json
{
  "id": 2,
  "username": "new-name@example.com",
  "role": "USER"
}
```

Username уникален. Если удаление пользователя нарушит целостность истории бронирований, сервис возвращает 409, а не удаляет историю каскадно.

### 3.4. Создание бронирования

Запрос с явным выбором номера:

```json
{
  "requestId": "6a18c94f-f26b-4f3d-994d-665c49d8b51e",
  "roomId": 10,
  "startDate": "2026-11-10",
  "endDate": "2026-11-15",
  "autoSelect": false
}
```

Запрос с автоподбором:

```json
{
  "requestId": "6a18c94f-f26b-4f3d-994d-665c49d8b51e",
  "roomId": null,
  "startDate": "2026-11-10",
  "endDate": "2026-11-15",
  "autoSelect": true
}
```

При `autoSelect=true` значение `roomId` игнорируется. При `autoSelect=false` поле `roomId` обязательно.

Ответ бронирования:

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

Допустимые статусы: `PENDING`, `CONFIRMED`, `CANCELLED`. Клиент получает успешный ответ только после удалённого подтверждения и локального перехода в CONFIRMED.

Повторный запрос с тем же `requestId` и идентичными бизнес-параметрами возвращает уже существующую бронь без повторного создания или блокировки номера. Тот же `requestId` с другими параметрами отклоняется с кодом 409.

`GET /bookings` возвращает массив DTO бронирований только текущего пользователя. Доступ к чужой брони запрещён; API не раскрывает её содержимое.

Повторная отмена уже отменённой брони идемпотентна и возвращает 204.

## 4. Контракт Hotel Management Service

### 4.1. Публичные и административные эндпойнты

| Метод и путь | Назначение | Доступ | Успешный ответ |
|---|---|---|---|
| `POST /api/hotels` | Создание отеля | ADMIN | 201 |
| `GET /api/hotels` | Список отелей | USER | 200 |
| `PATCH /api/hotels/{id}` | Обновление отеля | ADMIN | 200 |
| `DELETE /api/hotels/{id}` | Удаление отеля | ADMIN | 204 |
| `POST /api/rooms` | Создание номера | ADMIN | 201 |
| `GET /api/rooms` | Свободные номера на даты | USER | 200 |
| `GET /api/rooms/recommend` | Рекомендованные свободные номера | USER | 200 |
| `PATCH /api/rooms/{id}` | Обновление номера | ADMIN | 200 |
| `DELETE /api/rooms/{id}` | Удаление номера | ADMIN | 204 |

Для обоих GET-запросов списка номеров обязательны query-параметры `startDate` и `endDate`:

```text
GET /api/rooms?startDate=2026-11-10&endDate=2026-11-15
GET /api/rooms/recommend?startDate=2026-11-10&endDate=2026-11-15
```

### 4.2. DTO отелей и номеров

Создание или обновление отеля:

```json
{
  "name": "Grand Hotel",
  "address": "Moscow, Tverskaya 1"
}
```

Ответ отеля:

```json
{
  "id": 1,
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

Если `available` не передано при создании, используется `true`. Ответ номера:

```json
{
  "id": 10,
  "hotelId": 1,
  "number": "101",
  "available": true,
  "timesBooked": 4
}
```

PATCH отеля и номера изменяет только переданные поля. Номер комнаты уникален в пределах отеля. Удаление отеля или номера, связанного с активными или историческими записями о занятости, отклоняется с кодом 409.

### 4.3. Внутренние эндпойнты

Оба эндпойнта доступны только с служебным JWT с ролью INTERNAL и не маршрутизируются через Gateway.

`POST /api/rooms/{id}/confirm-availability`:

```json
{
  "bookingId": 25,
  "requestId": "6a18c94f-f26b-4f3d-994d-665c49d8b51e",
  "startDate": "2026-11-10",
  "endDate": "2026-11-15"
}
```

Успешный ответ — 200:

```json
{
  "bookingId": 25,
  "roomId": 10,
  "status": "HELD"
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

Возможные причины освобождения: `COMPENSATION` при неудачном создании брони и `USER_CANCELLATION` при отмене подтверждённой брони. Успешный ответ — 200:

```json
{
  "bookingId": 25,
  "roomId": 10,
  "status": "RELEASED"
}
```

Повторный confirm по той же операции возвращает исходный результат и не создаёт второй слот. Повторный release идемпотентен. Release после неуспешного confirm без установленной блокировки завершается без создания новых записей о занятости.

## 5. Правила доступности и рекомендаций

- Даты корректны только при `startDate < endDate`.
- Интервал бронирования — `[startDate, endDate)`: дата выезда доступна для заезда следующего гостя.
- Два интервала пересекаются, если `startDate < existingEndDate && endDate > existingStartDate`.
- `available=false` означает вывод номера из эксплуатации и запрещает новые бронирования независимо от дат.
- `available=true` не означает свободность: занятость определяется по активным записям `room_reservations` в Hotel Service.
- CANCELLED/RELEASED-слоты не участвуют в проверке пересечений.
- `/api/rooms` возвращает свободные номера без специальной сортировки.
- `/api/rooms/recommend` возвращает те же свободные номера с сортировкой `timesBooked ASC, id ASC`.
- При автоподборе Booking Service выбирает первый элемент рекомендаций. Если свободных номеров нет, возвращается 409.
- `timesBooked` увеличивается ровно один раз при успешном confirm. Компенсация неудачного создания отменяет это увеличение ровно один раз; последующая отмена подтверждённой брони пользователем исторический счётчик не уменьшает.

## 6. Согласованность бронирования

Глобальная распределённая транзакция не используется. Создание брони выполняется как последовательность локальных транзакций:

1. Проверить запрос, пользователя и `requestId`; при автоподборе получить рекомендации.
2. В Booking Service зафиксировать бронь PENDING в отдельной транзакции.
3. Вызвать confirm-availability в Hotel Service с `bookingId` и `requestId`.
4. Hotel Service атомарно проверяет доступность и пересечения, создаёт активный слот и обновляет `timesBooked`.
5. После успешного ответа Booking Service в новой локальной транзакции переводит PENDING в CONFIRMED.
6. При бизнес-конфликте, ошибке, исчерпании повторов или тайм-ауте Booking Service вызывает release и переводит PENDING в CANCELLED.
7. Release вызывается и после тайм-аута с неизвестным результатом, поскольку удалённая блокировка могла уже быть создана.
8. Если компенсация не удалась, сервис возвращает ошибку и логирует критическое рассогласование, а не сообщает об успешном завершении.

HTTP-вызовы не выполняются внутри открытой транзакции БД. Переходы из терминальных статусов CONFIRMED и CANCELLED обратно в PENDING запрещены.

## 7. Тайм-ауты, повторы и конкурентность

- Для всех вызовов между сервисами задаются connect timeout и read timeout.
- Число повторов ограничено; задержка между попытками увеличивается экспоненциально.
- Повторяются только идемпотентные операции при временных сетевых ошибках, тайм-аутах и подходящих ответах 5xx.
- Ответы 400, 401, 403 и бизнес-конфликт 409 автоматически не повторяются.
- Confirm и release используют один и тот же `bookingId`/`requestId`, чтобы повторная доставка не изменила результат дважды.
- Проверка пересечений и создание слота в Hotel Service атомарны; для сериализации операций над одним номером используется блокировка строки Room и ограничения БД.
- При конкурентных запросах на один номер и пересекающиеся даты успешной может быть только одна активная блокировка; остальные получают 409 либо отменяются по правилам согласованности.
- Все ключевые шаги логируются с `bookingId` и `requestId`. Пароли и JWT в логи не попадают.

## 8. Роли и безопасность

- USER: личные бронирования и просмотр отелей/номеров.
- ADMIN: управление пользователями, отелями и номерами.
- INTERNAL: только confirm-availability и release.
- Booking и Hotel самостоятельно проверяют подпись, issuer и срок действия JWT как OAuth2 Resource Server.
- Gateway не заменяет проверку токена backend-сервисами.
- Сессии не используются; приложения работают в stateless-режиме.
- JWT-секреты и начальные учётные данные ADMIN задаются через конфигурацию/переменные окружения. Реальные секреты не коммитятся.

## 9. Единые коды ошибок

| Код | Когда используется |
|---|---|
| 400 | Некорректный JSON, нарушение Bean Validation, неверный интервал дат |
| 401 | JWT отсутствует, недействителен или просрочен; неверные учётные данные при входе |
| 403 | Токен валиден, но роль не разрешает операцию |
| 404 | Отель, номер, пользователь или бронь не найдены |
| 409 | Пересечение дат, нет свободных номеров, дубликат username, конфликт requestId или ограничение целостности |
| 503 | Удалённый сервис недоступен после повторов либо согласованность не удалось восстановить компенсацией |
