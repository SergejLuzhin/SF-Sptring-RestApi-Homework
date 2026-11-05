#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
RUNTIME_DIR="$ROOT_DIR/target/local-runtime"
LOG_DIR="$RUNTIME_DIR/logs"

cd "$ROOT_DIR"
mkdir -p "$LOG_DIR"

export JWT_SECRET="${JWT_SECRET:-local-booking-service-jwt-secret-change-me-32-bytes}"
export JWT_ISSUER="${JWT_ISSUER:-booking-service}"
export ADMIN_USERNAME="${ADMIN_USERNAME:-admin@example.com}"
export ADMIN_PASSWORD="${ADMIN_PASSWORD:-localAdminPassword123}"

SERVICE_NAMES=()
SERVICE_PIDS=()
SERVICE_LOGS=()

cleanup() {
    exit_code=$?
    trap - EXIT INT TERM

    if ((${#SERVICE_PIDS[@]} > 0)); then
        printf '\nОстановка локальных сервисов...\n'
        for ((index=${#SERVICE_PIDS[@]} - 1; index >= 0; index--)); do
            kill "${SERVICE_PIDS[$index]}" 2>/dev/null || true
        done
        for pid in "${SERVICE_PIDS[@]}"; do
            wait "$pid" 2>/dev/null || true
        done
    fi

    exit "$exit_code"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

check_requirements() {
    for command_name in java mvn curl; do
        if ! command -v "$command_name" >/dev/null 2>&1; then
            printf 'Не найдена обязательная команда: %s\n' "$command_name" >&2
            exit 1
        fi
    done
}

check_processes() {
    for index in "${!SERVICE_PIDS[@]}"; do
        if ! kill -0 "${SERVICE_PIDS[$index]}" 2>/dev/null; then
            printf '\nСервис %s неожиданно завершился. Проверьте лог: %s\n' \
                "${SERVICE_NAMES[$index]}" "${SERVICE_LOGS[$index]}" >&2
            return 1
        fi
    done
}

start_service() {
    local service_name=$1
    local jar_path=$2

    if [[ ! -f "$jar_path" ]]; then
        printf 'Не найден JAR: %s. Запустите задачу без SKIP_BUILD=1.\n' "$jar_path" >&2
        exit 1
    fi

    local log_path="$LOG_DIR/$service_name.log"
    : >"$log_path"
    java -jar "$jar_path" >"$log_path" 2>&1 &

    SERVICE_NAMES+=("$service_name")
    SERVICE_PIDS+=("$!")
    SERVICE_LOGS+=("$log_path")
    printf 'Запущен %-15s PID=%s\n' "$service_name" "$!"
}

wait_for_health() {
    local service_name=$1
    local port=$2

    for ((attempt = 1; attempt <= 90; attempt++)); do
        if response=$(curl -fs --max-time 2 "http://localhost:$port/actuator/health" 2>/dev/null) \
            && [[ "$response" == *'"status":"UP"'* ]]; then
            printf 'Готов %-17s http://localhost:%s\n' "$service_name" "$port"
            return 0
        fi

        check_processes
        sleep 1
    done

    printf 'Сервис %s не перешёл в состояние UP за 90 секунд. Лог: %s\n' \
        "$service_name" "$LOG_DIR/$service_name.log" >&2
    return 1
}

wait_for_gateway_routes() {
    for ((attempt = 1; attempt <= 90; attempt++)); do
        hotel_code=$(curl -s --max-time 2 -o /dev/null -w '%{http_code}' \
            'http://localhost:8080/api/hotels' || true)
        booking_code=$(curl -s --max-time 2 -o /dev/null -w '%{http_code}' \
            -X POST 'http://localhost:8080/user/auth' \
            -H 'Content-Type: application/json' -d '{}' || true)

        if [[ "$hotel_code" == '401' && "$booking_code" == '400' ]]; then
            printf 'Маршруты Gateway готовы: Hotel Service и Booking Service обнаружены.\n'
            return 0
        fi

        check_processes
        sleep 1
    done

    printf 'Gateway не обнаружил backend-сервисы за 90 секунд. Проверьте логи в %s\n' "$LOG_DIR" >&2
    return 1
}

check_requirements

if [[ "${SKIP_BUILD:-0}" != '1' ]]; then
    printf 'Сборка модулей без запуска автотестов...\n'
    mvn -DskipTests package
else
    printf 'Сборка пропущена: используются существующие JAR.\n'
fi

start_service 'eureka-server' "$ROOT_DIR/eureka-server/target/eureka-server-1.0-SNAPSHOT.jar"
wait_for_health 'eureka-server' 8761

start_service 'hotel-service' "$ROOT_DIR/hotel-service/target/hotel-service-1.0-SNAPSHOT.jar"
start_service 'booking-service' "$ROOT_DIR/booking-service/target/booking-service-1.0-SNAPSHOT.jar"
wait_for_health 'hotel-service' 8081
wait_for_health 'booking-service' 8082

start_service 'api-gateway' "$ROOT_DIR/api-gateway/target/api-gateway-1.0-SNAPSHOT.jar"
wait_for_health 'api-gateway' 8080
wait_for_gateway_routes

printf '\nЛокальное окружение готово.\n'
printf 'Gateway:        http://localhost:8080\n'
printf 'Eureka:         http://localhost:8761\n'
printf 'Booking Swagger: http://localhost:8082/swagger-ui.html\n'
printf 'Hotel Swagger:   http://localhost:8081/swagger-ui.html\n'
printf 'ADMIN_USERNAME:  %s\n' "$ADMIN_USERNAME"
printf 'Логи:            %s\n' "$LOG_DIR"
printf '\nВо втором терминале запустите: ./scripts/api-menu.sh\n'
printf 'Для остановки всех сервисов нажмите Ctrl+C.\n'

while true; do
    check_processes
    sleep 2
done
