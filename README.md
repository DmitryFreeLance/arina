# MAX Helpdesk Bot

Java-бот для MAX под сценарий приёма заявок на неисправности:

- приветствует пользователя и показывает стартовое меню
- создаёт заявку пошагово: ФИО, компания, тип проблемы, описание
- отправляет заявку администраторам
- позволяет администраторам принять, отклонить с причиной или отметить исполненной
- уведомляет пользователя о каждом изменении статуса
- содержит админ-панель с пользователями, списком открытых заявок, пагинацией, рассылкой и выдачей прав администратора

## Что важно по MAX API

- проект уже переведён на `https://platform-api2.max.ru`
- токен передаётся только через заголовок `Authorization`
- контейнер добавляет сертификаты Минцифры в системное и Java truststore
- для production используется webhook, а не long polling

Источники:

- [MAX API overview](https://dev.max.ru/docs-api)
- [POST /messages](https://dev.max.ru/docs-api/methods/POST/messages)
- [POST /answers](https://dev.max.ru/docs-api/methods/POST/answers)
- [POST /subscriptions](https://dev.max.ru/docs-api/methods/POST/subscriptions)
- [Госуслуги: сертификаты](https://www.gosuslugi.ru/crt)

## Переменные окружения

Смотрите шаблон в [.env.example](/Users/dmitry/Desktop/arina/.env.example:1).

Ключевые переменные:

- `BOT_TOKEN` - токен бота MAX
- `WEBHOOK_URL` - публичный HTTPS URL webhook, например `https://example.ru/webhook`
- `WEBHOOK_SECRET` - секрет для заголовка `X-Max-Bot-Api-Secret`
- `COMPANY_NAME` - название управляющей компании в приветственном тексте
- `ADMIN_IDS` - список `user_id` через запятую для первичных администраторов
- `DB_PATH` - путь к файловой H2 базе внутри контейнера

## Сборка

```bash
mvn clean package
```

## Docker build

```bash
docker build -t max-helpdesk-bot:latest .
```

## Docker run

```bash
docker run -d \
  --name max-helpdesk-bot \
  --restart unless-stopped \
  -p 8080:8080 \
  -e BOT_TOKEN="your_max_bot_token" \
  -e WEBHOOK_URL="https://your-domain.ru/webhook" \
  -e WEBHOOK_SECRET="superSecret123" \
  -e COMPANY_NAME='ООО "УК Тамплиеры"' \
  -e ADMIN_IDS="123456789,987654321" \
  -e SERVER_PORT="8080" \
  -e DB_PATH="/data/max-bot-db" \
  -e DB_PASSWORD="" \
  -e PAGE_SIZE="8" \
  -e BOT_API_BASE_URL="https://platform-api2.max.ru" \
  -e APP_BASE_URL="https://your-domain.ru" \
  -v max-helpdesk-data:/data \
  max-helpdesk-bot:latest
```

## Как это работает

### Пользовательский сценарий

1. Пользователь нажимает `Начать` или отправляет `/start`.
2. Бот присылает красивое стартовое сообщение и кнопку `Заявка на неисправность`.
3. Бот собирает ФИО, компанию, тип проблемы и описание.
4. После отправки заявки бот подтверждает приём и уведомляет о дальнейших изменениях.

### Админский сценарий

1. Админ видит кнопку `Админ панель`.
2. В панели можно открыть:
   - список неисполненных заявок
   - список пользователей
   - рассылку
   - выдачу прав администратора
3. Для каждой заявки доступны действия:
   - `Принять`
   - `Отклонить`
   - `Отметить исполненной`
4. При отклонении бот запрашивает причину и пересылает её пользователю.

## Webhook

После запуска приложение пытается:

1. получить профиль бота через `GET /me`
2. удалить прежнюю подписку на указанный `WEBHOOK_URL`
3. зарегистрировать новую подписку через `POST /subscriptions`

Публичный endpoint приложения:

- `GET /health`
- `POST /webhook`

## Примечание

Формат входящих событий MAX может со временем расширяться. В проекте парсинг сделан с запасом через `JsonNode`, чтобы бот был устойчивее к мелким изменениям схемы.
