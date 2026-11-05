#!/usr/bin/env bash
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USER_TOKEN=''
ADMIN_TOKEN=''
USER_NAME=''
ADMIN_NAME=''
LAST_HOTEL_ID=''
LAST_ROOM_ID=''
LAST_BOOKING_ID=''
LAST_BOOKING_BODY=''
RESPONSE_BODY=''
RESPONSE_STATUS=''

print_json() {
    local value=$1

    if [[ -z "$value" ]]; then
        printf '(пустое тело)\n'
    elif jq -e . >/dev/null 2>&1 <<<"$value"; then
        jq 'walk(if type == "object" then with_entries(if .key == "password" or .key == "accessToken" then .value = "***" else . end) else . end)' <<<"$value"
    else
        printf '%s\n' "$value"
    fi
}

send_request() {
    local method=$1
    local url=$2
    local token=${3:-}
    local body=${4:-}
    local response
    local curl_status
    local -a curl_args

    curl_args=(-sS --max-time 30 -X "$method" "$url" -w $'\n%{http_code}')
    if [[ -n "$token" ]]; then
        curl_args+=(-H "Authorization: Bearer $token")
    fi
    if [[ -n "$body" ]]; then
        curl_args+=(-H 'Content-Type: application/json' -d "$body")
    fi

    printf '\n=== HTTP-запрос ===\n'
    printf '%s %s\n' "$method" "$url"
    if [[ -n "$token" ]]; then
        printf 'Authorization: Bearer ***\n'
    fi
    if [[ -n "$body" ]]; then
        printf 'Content-Type: application/json\n'
        print_json "$body"
    fi

    response=$(curl "${curl_args[@]}")
    curl_status=$?
    if ((curl_status != 0)); then
        RESPONSE_BODY=''
        RESPONSE_STATUS='000'
        printf '\n=== Ошибка curl ===\nКод завершения: %s\n' "$curl_status" >&2
        return "$curl_status"
    fi

    RESPONSE_STATUS=${response##*$'\n'}
    RESPONSE_BODY=${response%$'\n'*}
    printf '\n=== HTTP-ответ ===\n'
    printf 'Статус: %s\n' "$RESPONSE_STATUS"
    print_json "$RESPONSE_BODY"
    return 0
}

read_value() {
    local prompt=$1
    local variable_name=$2
    local default_value=${3:-}
    local input

    if [[ -n "$default_value" ]]; then
        read -r -p "$prompt [$default_value]: " input
    else
        read -r -p "$prompt: " input
    fi
    printf -v "$variable_name" '%s' "${input:-$default_value}"
}

read_password() {
    local prompt=$1
    local variable_name=$2
    local default_value=${3:-}
    local input

    if [[ -n "$default_value" ]]; then
        printf '%s [********]: ' "$prompt"
    else
        printf '%s: ' "$prompt"
    fi
    read -r -s input
    printf '\n'
    printf -v "$variable_name" '%s' "${input:-$default_value}"
}

read_id() {
    local prompt=$1
    local variable_name=$2
    local default_value=${3:-}
    local value

    while true; do
        read_value "$prompt" value "$default_value"
        if [[ "$value" =~ ^[1-9][0-9]*$ ]]; then
            printf -v "$variable_name" '%s' "$value"
            return 0
        fi
        printf 'Введите положительное целое число.\n'
        default_value=''
    done
}

new_uuid() {
    if command -v uuidgen >/dev/null 2>&1; then
        uuidgen | tr '[:upper:]' '[:lower:]'
        return 0
    fi

    local hex
    hex=$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')
    printf '%s-%s-4%s-a%s-%s\n' \
        "${hex:0:8}" "${hex:8:4}" "${hex:12:3}" "${hex:16:3}" "${hex:20:12}"
}

require_token() {
    local token=$1
    local role=$2

    if [[ -z "$token" ]]; then
        printf 'Сначала выполните вход или регистрацию: JWT %s ещё не сохранён.\n' "$role"
        return 1
    fi
}

pause() {
    local unused
    read -r -p 'Нажмите Enter для продолжения...' unused
}

register_user() {
    local username password body

    read_value 'Username нового USER' username 'user@example.com'
    read_password 'Пароль (минимум 8 символов)' password 'password123'
    body=$(jq -n --arg username "$username" --arg password "$password" \
        '{username: $username, password: $password}')
    send_request POST "$BASE_URL/user/register" '' "$body" || return

    if [[ "$RESPONSE_STATUS" == '201' ]]; then
        USER_TOKEN=$(jq -er '.accessToken' <<<"$RESPONSE_BODY") || return
        USER_NAME=$username
        printf 'JWT USER сохранён в текущей сессии меню.\n'
    fi
}

login_user() {
    local username password body

    read_value 'Username USER' username "${USER_NAME:-user@example.com}"
    read_password 'Пароль' password 'password123'
    body=$(jq -n --arg username "$username" --arg password "$password" \
        '{username: $username, password: $password}')
    send_request POST "$BASE_URL/user/auth" '' "$body" || return

    if [[ "$RESPONSE_STATUS" == '200' ]]; then
        USER_TOKEN=$(jq -er '.accessToken' <<<"$RESPONSE_BODY") || return
        USER_NAME=$username
        printf 'JWT USER сохранён в текущей сессии меню.\n'
    fi
}

login_admin() {
    local username password body

    read_value 'Username ADMIN' username "${ADMIN_NAME:-admin@example.com}"
    read_password 'Пароль' password 'localAdminPassword123'
    body=$(jq -n --arg username "$username" --arg password "$password" \
        '{username: $username, password: $password}')
    send_request POST "$BASE_URL/user/auth" '' "$body" || return

    if [[ "$RESPONSE_STATUS" == '200' ]]; then
        ADMIN_TOKEN=$(jq -er '.accessToken' <<<"$RESPONSE_BODY") || return
        ADMIN_NAME=$username
        printf 'JWT ADMIN сохранён в текущей сессии меню.\n'
    fi
}

create_user() {
    require_token "$ADMIN_TOKEN" ADMIN || return
    local username password role body

    read_value 'Username' username 'new-user@example.com'
    read_password 'Пароль' password 'password123'
    read_value 'Роль USER или ADMIN' role 'USER'
    if [[ "$role" != 'USER' && "$role" != 'ADMIN' ]]; then
        printf 'Допустимы только роли USER и ADMIN.\n'
        return
    fi
    body=$(jq -n --arg username "$username" --arg password "$password" --arg role "$role" \
        '{username: $username, password: $password, role: $role}')
    send_request POST "$BASE_URL/user" "$ADMIN_TOKEN" "$body"
}

update_user() {
    require_token "$ADMIN_TOKEN" ADMIN || return
    local user_id username password role body

    read_id 'ID пользователя' user_id
    read_value 'Новый username (пусто — без изменения)' username
    read_password 'Новый пароль (пусто — без изменения)' password
    read_value 'Новая роль USER/ADMIN (пусто — без изменения)' role
    if [[ -n "$role" && "$role" != 'USER' && "$role" != 'ADMIN' ]]; then
        printf 'Допустимы только пустое значение, USER или ADMIN.\n'
        return
    fi
    body=$(jq -n --argjson id "$user_id" --arg username "$username" \
        --arg password "$password" --arg role "$role" '
        {id: $id}
        + if $username != "" then {username: $username} else {} end
        + if $password != "" then {password: $password} else {} end
        + if $role != "" then {role: $role} else {} end')
    send_request PATCH "$BASE_URL/user" "$ADMIN_TOKEN" "$body"
}

delete_user() {
    require_token "$ADMIN_TOKEN" ADMIN || return
    local user_id

    read_id 'ID пользователя' user_id
    send_request DELETE "$BASE_URL/user?id=$user_id" "$ADMIN_TOKEN"
}

create_hotel() {
    require_token "$ADMIN_TOKEN" ADMIN || return
    local name address body

    read_value 'Название отеля' name 'Grand Hotel'
    read_value 'Адрес' address 'Moscow, Tverskaya 1'
    body=$(jq -n --arg name "$name" --arg address "$address" '{name: $name, address: $address}')
    send_request POST "$BASE_URL/api/hotels" "$ADMIN_TOKEN" "$body" || return

    if [[ "$RESPONSE_STATUS" == '201' ]]; then
        LAST_HOTEL_ID=$(jq -er '.id' <<<"$RESPONSE_BODY") || return
        printf 'ID отеля сохранён для следующих операций: %s\n' "$LAST_HOTEL_ID"
    fi
}

list_hotels() {
    require_token "$USER_TOKEN" USER || return
    send_request GET "$BASE_URL/api/hotels" "$USER_TOKEN"
}

update_hotel() {
    require_token "$ADMIN_TOKEN" ADMIN || return
    local hotel_id name address body

    read_id 'ID отеля' hotel_id "$LAST_HOTEL_ID"
    read_value 'Новое название (пусто — без изменения)' name
    read_value 'Новый адрес (пусто — без изменения)' address
    body=$(jq -n --arg name "$name" --arg address "$address" '
        {}
        + if $name != "" then {name: $name} else {} end
        + if $address != "" then {address: $address} else {} end')
    send_request PATCH "$BASE_URL/api/hotels/$hotel_id" "$ADMIN_TOKEN" "$body"
}

delete_hotel() {
    require_token "$ADMIN_TOKEN" ADMIN || return
    local hotel_id

    read_id 'ID отеля' hotel_id "$LAST_HOTEL_ID"
    send_request DELETE "$BASE_URL/api/hotels/$hotel_id" "$ADMIN_TOKEN"
}

create_room() {
    require_token "$ADMIN_TOKEN" ADMIN || return
    local hotel_id number available body

    read_id 'ID отеля' hotel_id "${LAST_HOTEL_ID:-1}"
    read_value 'Номер комнаты' number '101'
    read_value 'Эксплуатационная доступность true/false' available 'true'
    if [[ "$available" != 'true' && "$available" != 'false' ]]; then
        printf 'Введите true или false.\n'
        return
    fi
    body=$(jq -n --argjson hotelId "$hotel_id" --arg number "$number" --argjson available "$available" \
        '{hotelId: $hotelId, number: $number, available: $available}')
    send_request POST "$BASE_URL/api/rooms" "$ADMIN_TOKEN" "$body" || return

    if [[ "$RESPONSE_STATUS" == '201' ]]; then
        LAST_ROOM_ID=$(jq -er '.id' <<<"$RESPONSE_BODY") || return
        printf 'ID номера сохранён для следующих операций: %s\n' "$LAST_ROOM_ID"
    fi
}

list_rooms() {
    require_token "$USER_TOKEN" USER || return
    local start_date end_date

    read_value 'Дата заезда yyyy-MM-dd' start_date '2026-11-10'
    read_value 'Дата выезда yyyy-MM-dd' end_date '2026-11-15'
    send_request GET "$BASE_URL/api/rooms?startDate=$start_date&endDate=$end_date" "$USER_TOKEN"
}

recommend_rooms() {
    require_token "$USER_TOKEN" USER || return
    local start_date end_date

    read_value 'Дата заезда yyyy-MM-dd' start_date '2026-11-10'
    read_value 'Дата выезда yyyy-MM-dd' end_date '2026-11-15'
    send_request GET "$BASE_URL/api/rooms/recommend?startDate=$start_date&endDate=$end_date" "$USER_TOKEN"
}

update_room() {
    require_token "$ADMIN_TOKEN" ADMIN || return
    local room_id number available body

    read_id 'ID номера' room_id "${LAST_ROOM_ID:-1}"
    read_value 'Новый номер комнаты (пусто — без изменения)' number
    read_value 'Новая доступность true/false (пусто — без изменения)' available
    if [[ -n "$available" && "$available" != 'true' && "$available" != 'false' ]]; then
        printf 'Допустимы только пустое значение, true или false.\n'
        return
    fi
    body=$(jq -n --arg number "$number" --arg available "$available" '
        {}
        + if $number != "" then {number: $number} else {} end
        + if $available == "true" then {available: true}
          else if $available == "false" then {available: false} else {} end end')
    send_request PATCH "$BASE_URL/api/rooms/$room_id" "$ADMIN_TOKEN" "$body"
}

delete_room() {
    require_token "$ADMIN_TOKEN" ADMIN || return
    local room_id

    read_id 'ID номера' room_id "${LAST_ROOM_ID:-1}"
    send_request DELETE "$BASE_URL/api/rooms/$room_id" "$ADMIN_TOKEN"
}

create_booking() {
    require_token "$USER_TOKEN" USER || return
    local auto_select=$1
    local request_id room_id start_date end_date body

    request_id=$(new_uuid)
    read_value 'requestId' request_id "$request_id"
    if [[ "$auto_select" == 'false' ]]; then
        read_id 'ID номера' room_id "${LAST_ROOM_ID:-1}"
    else
        room_id=''
    fi
    read_value 'Дата заезда yyyy-MM-dd' start_date '2026-11-10'
    read_value 'Дата выезда yyyy-MM-dd' end_date '2026-11-12'

    if [[ "$auto_select" == 'true' ]]; then
        body=$(jq -n --arg requestId "$request_id" --arg startDate "$start_date" --arg endDate "$end_date" \
            '{requestId: $requestId, roomId: null, startDate: $startDate, endDate: $endDate, autoSelect: true}')
    else
        body=$(jq -n --arg requestId "$request_id" --argjson roomId "$room_id" \
            --arg startDate "$start_date" --arg endDate "$end_date" \
            '{requestId: $requestId, roomId: $roomId, startDate: $startDate, endDate: $endDate, autoSelect: false}')
    fi

    send_request POST "$BASE_URL/booking" "$USER_TOKEN" "$body" || return
    if [[ "$RESPONSE_STATUS" == '201' || "$RESPONSE_STATUS" == '200' ]]; then
        LAST_BOOKING_ID=$(jq -er '.id' <<<"$RESPONSE_BODY") || return
        LAST_BOOKING_BODY=$body
        printf 'ID и тело бронирования сохранены. Пунктом 3 меню бронирований можно повторить тот же запрос.\n'
    fi
}

repeat_booking() {
    require_token "$USER_TOKEN" USER || return
    if [[ -z "$LAST_BOOKING_BODY" ]]; then
        printf 'Сначала успешно создайте бронирование через это меню.\n'
        return
    fi
    send_request POST "$BASE_URL/booking" "$USER_TOKEN" "$LAST_BOOKING_BODY"
}

booking_history() {
    require_token "$USER_TOKEN" USER || return
    send_request GET "$BASE_URL/bookings" "$USER_TOKEN"
}

get_booking() {
    require_token "$USER_TOKEN" USER || return
    local booking_id

    read_id 'ID бронирования' booking_id "$LAST_BOOKING_ID"
    send_request GET "$BASE_URL/booking/$booking_id" "$USER_TOKEN"
}

cancel_booking() {
    require_token "$USER_TOKEN" USER || return
    local booking_id

    read_id 'ID бронирования' booking_id "$LAST_BOOKING_ID"
    send_request DELETE "$BASE_URL/booking/$booking_id" "$USER_TOKEN"
}

check_health() {
    send_request GET "$BASE_URL/actuator/health"
    send_request GET 'http://localhost:8761/actuator/health'
    send_request GET 'http://localhost:8081/actuator/health'
    send_request GET 'http://localhost:8082/actuator/health'
}

show_session() {
    printf '\n=== Текущая сессия меню ===\n'
    printf 'Gateway: %s\n' "$BASE_URL"
    printf 'USER:  %s; JWT: %s\n' "${USER_NAME:-не задан}" "$([[ -n "$USER_TOKEN" ]] && printf 'сохранён' || printf 'отсутствует')"
    printf 'ADMIN: %s; JWT: %s\n' "${ADMIN_NAME:-не задан}" "$([[ -n "$ADMIN_TOKEN" ]] && printf 'сохранён' || printf 'отсутствует')"
    printf 'Последний hotelId: %s\n' "${LAST_HOTEL_ID:-не задан}"
    printf 'Последний roomId: %s\n' "${LAST_ROOM_ID:-не задан}"
    printf 'Последний bookingId: %s\n' "${LAST_BOOKING_ID:-не задан}"
    printf '\nSwagger Booking: http://localhost:8082/swagger-ui.html\n'
    printf 'Swagger Hotel:   http://localhost:8081/swagger-ui.html\n'
}

auth_menu() {
    local choice

    while true; do
        printf '\n=== Пользователи и аутентификация ===\n'
        printf '1. Регистрация USER (JWT сохраняется)\n'
        printf '2. Вход USER (JWT сохраняется)\n'
        printf '3. Вход ADMIN (JWT сохраняется)\n'
        printf '4. ADMIN: создать пользователя\n'
        printf '5. ADMIN: обновить пользователя\n'
        printf '6. ADMIN: удалить пользователя\n'
        printf '0. Назад\n'
        if ! read -r -p 'Выберите операцию: ' choice; then
            return
        fi
        case "$choice" in
            1) register_user; pause ;;
            2) login_user; pause ;;
            3) login_admin; pause ;;
            4) create_user; pause ;;
            5) update_user; pause ;;
            6) delete_user; pause ;;
            0) return ;;
            *) printf 'Неизвестный пункт меню.\n' ;;
        esac
    done
}

hotel_menu() {
    local choice

    while true; do
        printf '\n=== Отели и номера ===\n'
        printf '1. ADMIN: создать отель\n'
        printf '2. USER: список отелей\n'
        printf '3. ADMIN: обновить отель\n'
        printf '4. ADMIN: удалить отель\n'
        printf '5. ADMIN: создать номер\n'
        printf '6. USER: свободные номера на даты\n'
        printf '7. USER: рекомендованные номера\n'
        printf '8. ADMIN: обновить номер\n'
        printf '9. ADMIN: удалить номер\n'
        printf '0. Назад\n'
        if ! read -r -p 'Выберите операцию: ' choice; then
            return
        fi
        case "$choice" in
            1) create_hotel; pause ;;
            2) list_hotels; pause ;;
            3) update_hotel; pause ;;
            4) delete_hotel; pause ;;
            5) create_room; pause ;;
            6) list_rooms; pause ;;
            7) recommend_rooms; pause ;;
            8) update_room; pause ;;
            9) delete_room; pause ;;
            0) return ;;
            *) printf 'Неизвестный пункт меню.\n' ;;
        esac
    done
}

booking_menu() {
    local choice

    while true; do
        printf '\n=== Бронирования ===\n'
        printf '1. USER: создать с явным roomId\n'
        printf '2. USER: создать с автоподбором\n'
        printf '3. USER: повторить последний запрос (идемпотентность)\n'
        printf '4. USER: история бронирований\n'
        printf '5. USER: получить бронирование по ID\n'
        printf '6. USER: отменить бронирование\n'
        printf '0. Назад\n'
        if ! read -r -p 'Выберите операцию: ' choice; then
            return
        fi
        case "$choice" in
            1) create_booking false; pause ;;
            2) create_booking true; pause ;;
            3) repeat_booking; pause ;;
            4) booking_history; pause ;;
            5) get_booking; pause ;;
            6) cancel_booking; pause ;;
            0) return ;;
            *) printf 'Неизвестный пункт меню.\n' ;;
        esac
    done
}

run_all_tests() {
    local root_dir
    local test_status

    root_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
    printf '\nЗапускаются все автоматические тесты. Работающие сервисы останавливать не нужно.\n'
    "$root_dir/scripts/run-tests.sh"
    test_status=$?
    if ((test_status != 0)); then
        printf 'Прогон завершился с ошибкой. Подробности: target/test-report/index.html\n' >&2
    fi
    return "$test_status"
}

for command_name in curl jq; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        printf 'Не найдена обязательная команда: %s\n' "$command_name" >&2
        exit 1
    fi
done

if ! curl -fs --max-time 2 "$BASE_URL/actuator/health" >/dev/null 2>&1; then
    printf 'Gateway %s недоступен. Сначала запустите ./scripts/start-local.sh в первом терминале.\n' "$BASE_URL" >&2
    exit 1
fi

trap 'printf "\nМеню завершено. Сервисы продолжают работать.\n"; exit 0' INT
printf 'API Gateway доступен: %s\n' "$BASE_URL"
printf 'Пароли и JWT маскируются в выводе. Токены хранятся только до выхода из меню.\n'

while true; do
    printf '\n=== Главное меню ===\n'
    printf '1. Пользователи и аутентификация\n'
    printf '2. Отели и номера\n'
    printf '3. Бронирования\n'
    printf '4. Health всех сервисов\n'
    printf '5. Текущая сессия и адреса Swagger\n'
    printf '6. Запустить все автотесты и сформировать HTML-отчёт\n'
    printf '0. Выход\n'
    if ! read -r -p 'Выберите раздел: ' choice; then
        printf '\nМеню завершено.\n'
        exit 0
    fi
    case "$choice" in
        1) auth_menu ;;
        2) hotel_menu ;;
        3) booking_menu ;;
        4) check_health; pause ;;
        5) show_session; pause ;;
        6) run_all_tests; pause ;;
        0) printf 'Меню завершено. Сервисы продолжают работать.\n'; exit 0 ;;
        *) printf 'Неизвестный пункт меню.\n' ;;
    esac
done
