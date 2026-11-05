#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
REPORT_DIR="$ROOT_DIR/target/test-report"
LOG_FILE="$REPORT_DIR/test-run.log"
HTML_FILE="$REPORT_DIR/index.html"
TEST_COMMAND='mvn -B -Dstyle.color=never clean verify surefire-report:report'

if ! command -v mvn >/dev/null 2>&1; then
    printf 'Не найдена обязательная команда: mvn\n' >&2
    exit 1
fi

cd "$ROOT_DIR"
TEMP_LOG=$(mktemp "$ROOT_DIR/.test-run.XXXXXX.log")
cleanup_temp_log() {
    if [[ -n "${TEMP_LOG:-}" && -f "$TEMP_LOG" ]]; then
        rm -f "$TEMP_LOG"
    fi
}
trap cleanup_temp_log EXIT

printf 'Запуск: %s\n' "$TEST_COMMAND"
printf 'Полный лог: %s\n\n' "$LOG_FILE"

mvn -B -Dstyle.color=never clean verify surefire-report:report 2>&1 | tee "$TEMP_LOG"
test_status=${PIPESTATUS[0]}

if ! mkdir -p "$REPORT_DIR" || ! mv "$TEMP_LOG" "$LOG_FILE"; then
    printf 'Не удалось сохранить лог тестового прогона в %s\n' "$REPORT_DIR" >&2
    exit 1
fi
trap - EXIT

if ((test_status == 0)); then
    result_title='BUILD SUCCESS'
    result_class='success'
else
    result_title='BUILD FAILED'
    result_class='failure'
fi

finished_at=$(date '+%Y-%m-%d %H:%M:%S %Z')

module_links=''
for module in hotel-service booking-service api-gateway; do
    module_report="$ROOT_DIR/$module/target/reports/surefire.html"
    if [[ -f "$module_report" ]]; then
        module_links+="<li><a href=\"../../$module/target/reports/surefire.html\">$module — подробный Surefire-отчёт</a></li>"
    else
        module_links+="<li>$module — HTML-отчёт не найден</li>"
    fi
done

cat >"$HTML_FILE" <<EOF
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Отчёт автоматических тестов</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 2rem; color: #1f2933; }
        main { max-width: 1200px; margin: 0 auto; }
        .status { padding: 1rem; border-radius: 6px; font-size: 1.25rem; font-weight: 700; }
        .success { background: #dcfce7; color: #166534; }
        .failure { background: #fee2e2; color: #991b1b; }
        code { background: #f3f4f6; padding: 0.2rem 0.4rem; }
        pre { background: #111827; color: #f9fafb; padding: 1rem; overflow: auto; white-space: pre-wrap; word-break: break-word; }
    </style>
</head>
<body>
<main>
    <h1>Отчёт автоматических тестов</h1>
    <p class="status $result_class">$result_title</p>
    <p><strong>Завершено:</strong> $finished_at</p>
    <p><strong>Код завершения Maven:</strong> $test_status</p>
    <p><strong>Команда:</strong> <code>$TEST_COMMAND</code></p>
    <h2>Подробные отчёты по модулям</h2>
    <ul>$module_links</ul>
    <h2>Полный лог прогона</h2>
    <pre>
EOF

if ! sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g' "$LOG_FILE" >>"$HTML_FILE"; then
    printf 'Не удалось добавить лог в HTML-отчёт: %s\n' "$HTML_FILE" >&2
    exit 1
fi

cat >>"$HTML_FILE" <<'EOF'
</pre>
</main>
</body>
</html>
EOF

printf '\n%s\n' "$result_title"
printf 'Единый HTML-отчёт: %s\n' "$HTML_FILE"
printf 'Для открытия в macOS: open target/test-report/index.html\n'
exit "$test_status"
