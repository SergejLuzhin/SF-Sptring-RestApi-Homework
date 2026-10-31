# Что уже сделано

Этот файл является журналом текущего прогресса проекта. Он нужен, чтобы отслеживать, какие пункты `homework-plan.md` уже выполнены, что именно было сделано и какие работы остались. Нумерация основных разделов совпадает с нумерацией плана.

**Текущий прогресс:** выполнены пункты 1–3 плана. Следующая работа — пункт 4, «Этап 2 — настроить Eureka и маршрутизацию Gateway».

## 1. Текущее состояние и целевая структура

Создан файл `homework-plan.md`, в котором весь проект разбит на последовательные этапы: от пустого репозитория до полностью работающей распределённой системы с безопасностью, тестами и документацией.

В план включены:

- создание многомодульной структуры Maven;
- настройка Eureka Server и API Gateway;
- реализация Hotel Management Service и Booking Service;
- JWT-аутентификация и ролевое разграничение доступа;
- двухшаговое подтверждение бронирования с компенсацией при ошибке;
- идемпотентность, тайм-ауты, повторные запросы и защита от конкурентного бронирования;
- сквозное логирование по `bookingId` и `requestId`;
- Swagger/OpenAPI;
- модульные, интеграционные, конкурентные и сквозные тесты;
- финальная проверка соответствия заданию.

В начало плана добавлено обязательное правило:

> Для каждого класса и каждого метода необходимо писать JavaDoc на русском языке.

Это правило уже применено к главным классам всех созданных приложений и должно соблюдаться во всём дальнейшем коде.

## 2. Предварительно зафиксировать контракты и правила

Файл `README.md` полностью наполнен описанием будущих API-контрактов и бизнес-правил. Это сделано до написания бизнес-логики, чтобы оба микросервиса использовали одинаковые форматы данных и одинаково понимали процесс бронирования.

### 2.1. Зафиксированы компоненты и порты

| Компонент | Имя в Eureka | Порт |
|---|---|---:|
| API Gateway | `api-gateway` | 8080 |
| Hotel Management Service | `hotel-service` | 8081 |
| Booking Service | `booking-service` | 8082 |
| Eureka Server | — | 8761 |

Определено, что внешние клиенты обращаются через Gateway. Межсервисные вызовы выполняются по логическому имени сервиса через Eureka, а не через жёстко записанные IP-адреса.

Внутренние операции `confirm-availability` и `release` не должны публиковаться через Gateway.

### 2.2. Зафиксированы общие форматы данных

В README определено:

- JSON является форматом запросов и ответов;
- даты передаются как `yyyy-MM-dd`;
- `requestId` имеет формат UUID;
- защищённые запросы используют заголовок `Authorization: Bearer <JWT>`;
- пользовательский JWT действует один час;
- ошибки возвращаются в формате `application/problem+json` через Spring `ProblemDetail`.

Пример зафиксированного ответа с ошибкой:

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

### 2.3. Описаны эндпойнты Booking Service

Зафиксированы публичные операции:

- `POST /user/register` — регистрация и получение JWT;
- `POST /user/auth` — вход и получение JWT;
- `POST /booking` — создание брони;
- `GET /bookings` — история текущего пользователя;
- `GET /booking/{id}` — получение своей брони;
- `DELETE /booking/{id}` — отмена своей брони.

Зафиксированы административные операции:

- `POST /user`;
- `PATCH /user`;
- `DELETE /user?id={id}`.

Для них определены роли, HTTP-коды успешных ответов и DTO. Например, запрос на создание брони имеет следующий вид:

```json
{
  "requestId": "6a18c94f-f26b-4f3d-994d-665c49d8b51e",
  "roomId": 10,
  "startDate": "2026-11-10",
  "endDate": "2026-11-15",
  "autoSelect": false
}
```

Установлено, что при `autoSelect=true` значение `roomId` игнорируется, а при `autoSelect=false` поле `roomId` обязательно.

Для идемпотентности определено:

- повтор с тем же `requestId` и теми же параметрами возвращает существующую бронь;
- повтор с тем же `requestId`, но другими параметрами отклоняется с кодом 409;
- повторная отмена уже отменённой брони не создаёт ошибку и возвращает 204.

### 2.4. Описаны эндпойнты Hotel Service

Зафиксированы операции с отелями:

- `POST /api/hotels`;
- `GET /api/hotels`;
- `PATCH /api/hotels/{id}`;
- `DELETE /api/hotels/{id}`.

Зафиксированы операции с номерами:

- `POST /api/rooms`;
- `GET /api/rooms`;
- `GET /api/rooms/recommend`;
- `PATCH /api/rooms/{id}`;
- `DELETE /api/rooms/{id}`.

Для получения свободных и рекомендованных номеров определены обязательные параметры:

```text
GET /api/rooms?startDate=2026-11-10&endDate=2026-11-15
GET /api/rooms/recommend?startDate=2026-11-10&endDate=2026-11-15
```

Также описаны внутренние вызовы:

- `POST /api/rooms/{id}/confirm-availability`;
- `POST /api/rooms/{id}/release`.

Для них предусмотрен отдельный служебный JWT с ролью INTERNAL.

### 2.5. Определены правила доступности номеров

Зафиксированы следующие правила:

- `startDate` должна быть раньше `endDate`;
- бронирование использует интервал `[startDate, endDate)`;
- дата выезда одного гостя может быть датой заезда следующего;
- пересечение дат определяется условием:

```text
startDate < existingEndDate && endDate > existingStartDate
```

- `available=false` означает вывод номера из эксплуатации;
- `available=true` не гарантирует свободность на конкретные даты;
- занятость определяется по активным записям о бронированиях/слотах;
- рекомендации сортируются по `timesBooked ASC, id ASC`.

Отдельно определено поведение `timesBooked`: счётчик увеличивается один раз при успешном подтверждении, компенсация неудачного создания отменяет это увеличение, а обычная отмена подтверждённой брони сохраняет историческую статистику.

### 2.6. Описан распределённый сценарий бронирования

В README зафиксирована последовательность без глобальной распределённой транзакции:

1. Booking Service проверяет запрос и `requestId`.
2. В отдельной локальной транзакции создаётся бронь `PENDING`.
3. Booking Service вызывает `confirm-availability` в Hotel Service.
4. Hotel Service атомарно проверяет доступность и создаёт слот.
5. После успеха Booking Service переводит бронь в `CONFIRMED`.
6. При ошибке или тайм-ауте Booking Service вызывает `release` и переводит бронь в `CANCELLED`.
7. После тайм-аута `release` вызывается даже при неизвестном результате confirm, поскольку удалённая блокировка могла уже появиться.

Это необходимо, чтобы ошибка между двумя независимыми базами не оставляла активную бронь или блокировку в противоречивом состоянии.

### 2.7. Зафиксированы правила безопасности и ошибок

Определены роли:

- USER — личные бронирования и просмотр отелей/номеров;
- ADMIN — управление пользователями, отелями и номерами;
- INTERNAL — только внутренние confirm/release.

Каждый backend-сервис должен самостоятельно проверять JWT. Gateway не заменяет эту проверку.

Также определено использование кодов 400, 401, 403, 404, 409 и 503, правила тайм-аутов, ограниченных повторов и конкурентной блокировки номера.

## 3. Этап 1 — собрать минимальный запускаемый каркас

Этап полностью реализован и проверен.

### 3.1. Создана многомодульная структура Maven

Корневой `pom.xml` преобразован в parent-проект с `packaging=pom` и четырьмя модулями:

```xml
<modules>
    <module>eureka-server</module>
    <module>api-gateway</module>
    <module>hotel-service</module>
    <module>booking-service</module>
</modules>
```

Зачем это нужно:

- все приложения собираются одной командой;
- версии Java, Spring Boot, Spring Cloud и библиотек управляются централизованно;
- каждый сервис при этом остаётся отдельным Spring Boot-приложением со своим JAR.

Текущая структура исходного кода:

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
│   └── src/main/
└── booking-service/
    ├── pom.xml
    └── src/main/
```

### 3.2. Зафиксированы основные версии и настройки компиляции

В родительском POM установлены:

- Java 17 через `maven.compiler.release`;
- Spring Boot 3.5.7;
- Spring Cloud 2025.0.0;
- MapStruct 1.6.3;
- Lombok MapStruct Binding 0.2.0.

Spring Cloud подключён через BOM:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-dependencies</artifactId>
    <version>${spring-cloud.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

BOM позволяет не указывать версию каждой Spring Cloud-зависимости отдельно и снижает риск несовместимых комбинаций.

MapStruct добавлен в `dependencyManagement`, потому что Spring Boot BOM не управляет его версией. Это было обнаружено при первой сборке и исправлено централизованно.

Настроены annotation processors для:

- Lombok;
- MapStruct;
- совместной работы Lombok и MapStruct.

### 3.3. Настроен системный Maven

Для сборки используется установленный в операционной системе Apache Maven 3.10.0. В переменной окружения `JAVA_HOME` выбрана Java 25, совместимая с текущими зависимостями проекта. Сборка выполняется обычной командой `mvn clean verify`; дополнительные скрипты запуска Maven не требуются.

### 3.4. Создан Eureka Server

Главный класс:

```text
eureka-server/src/main/java/skillfactory/luzhin/eurekaserver/EurekaServerApplication.java
```

На класс добавлены `@SpringBootApplication` и `@EnableEurekaServer`. Для класса и метода `main` написан JavaDoc.

В `application.yml` настроено:

- порт 8761;
- имя приложения `eureka-server`;
- отключение регистрации Eureka Server как клиента самого себя;
- отключение получения реестра самим сервером;
- Actuator health/info.

### 3.5. Создан API Gateway

Главный класс:

```text
api-gateway/src/main/java/skillfactory/luzhin/gateway/ApiGatewayApplication.java
```

Подключены:

- Spring Cloud Gateway Server WebFlux;
- Eureka Client;
- Actuator.

В `application.yml` заданы порт 8080, имя `api-gateway` и адрес Eureka:

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

Маршруты к бизнес-сервисам пока намеренно не добавлены: это относится к следующему этапу плана.

### 3.6. Создан Hotel Management Service

Главный класс:

```text
hotel-service/src/main/java/skillfactory/luzhin/hotel/HotelServiceApplication.java
```

Подключены:

- Spring Web;
- Spring Data JPA;
- Bean Validation;
- H2;
- Eureka Client;
- Actuator;
- Lombok и MapStruct.

Настроена отдельная in-memory база:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:hoteldb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
```

База называется `hoteldb` и не используется Booking Service. Это закладывает независимость хранения данных микросервисов.

### 3.7. Создан Booking Service

Главный класс:

```text
booking-service/src/main/java/skillfactory/luzhin/booking/BookingServiceApplication.java
```

Набор базовых зависимостей соответствует Hotel Service. Настроена собственная база `bookingdb`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:bookingdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
```

Для обоих бизнес-сервисов отключён `open-in-view` и настроено создание схемы H2 через Hibernate. Сущности и репозитории будут добавлены на следующих этапах.

### 3.8. Подключён Actuator

Во всех четырёх приложениях открыты только health и info:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

Health-эндпойнты нужны для простой проверки того, что приложение запустилось и готово принимать запросы. Остальные actuator-эндпойнты не публикуются, чтобы не открывать лишнюю служебную информацию.

### 3.9. Выполненная проверка

#### 3.9.1. Полная сборка

Выполнена команда:

```bash
mvn clean verify
```

Результат:

```text
SpringFrameWorkHomeWork  SUCCESS
eureka-server            SUCCESS
api-gateway              SUCCESS
hotel-service            SUCCESS
booking-service          SUCCESS
BUILD SUCCESS
```

Таким образом, проверены чтение всех POM, разрешение зависимостей, компиляция и создание исполняемых JAR-файлов.

#### 3.9.2. Реальный запуск приложений

После сборки были запущены JAR-файлы всех четырёх приложений. Проверены HTTP-запросы:

```text
http://localhost:8761/actuator/health
http://localhost:8080/actuator/health
http://localhost:8081/actuator/health
http://localhost:8082/actuator/health
```

Каждый запрос вернул:

```json
{"status":"UP"}
```

Это подтверждает не только компиляцию, но и фактический старт Eureka, Gateway, Hotel Service и Booking Service. После проверки тестовые процессы были остановлены.

#### 3.9.3. Проверка репозитория

Проверены `git status` и `git diff --check`. В итоговых изменениях остались только файлы, относящиеся к каркасу проекта. Автоматически изменённая IDE-настройка кодировки была возвращена в исходное состояние.

## Оставшиеся работы

На текущем этапе намеренно отсутствуют:

- маршруты Gateway;
- регистрация и проверка бизнес-операций через Eureka;
- JPA-сущности, репозитории и REST-контроллеры;
- пользователи и JWT;
- алгоритмы доступности и рекомендаций;
- создание и компенсация бронирований;
- OpenAPI;
- автоматические тесты бизнес-логики.

Следующий пункт плана — пункт 4, «Этап 2 — настроить Eureka и маршрутизацию Gateway»: регистрация сервисов, маршруты на `lb://hotel-service` и `lb://booking-service`, сохранение путей и `Authorization`, а также исключение внутренних эндпойнтов из публичной маршрутизации.
