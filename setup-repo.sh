#!/bin/sh
# Заливает подготовленные файлы в GitHub и ставит тег v1.0.
# Токен НЕ пиши в чат: задай его через переменную окружения или введи в терминале.
set -eu

SRC="/data/user/0/cat.narezany.margycode/files/workspace/neymis-files"
REPO="s93240056-boop/neymis-files"
TMP="/data/data/cat.narezany.margycode/cache/tmp/opencode/neymis-files-push"

if [ -z "${TOKEN:-}" ]; then
    printf 'Введи токен GitHub: '
    stty -echo 2>/dev/null || true
    read -r TOKEN
    stty echo 2>/dev/null || true
    printf '\n'
fi

rm -rf "$TMP"
git clone "https://oauth2:${TOKEN}@github.com/${REPO}.git" "$TMP"

cp -R "$SRC"/. "$TMP"/
cd "$TMP"

git add -A
git -c user.name='Neymis' -c user.email='neymis@users.noreply.github.com' \
    commit -m 'Initial Neymis Files' || echo 'Нечего коммитить (возможно, уже залито)'
git push origin main
git tag -f v1.0
git push -f origin v1.0

echo 'ГОТОВО: файлы залиты, тег v1.0 отправлен. Проверь вкладку Actions в GitHub.'
