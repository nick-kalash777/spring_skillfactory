# Бронирование отелей

Учебный проект SkillFactory по Spring Boot. Это несколько микросервисов: в одном хранятся отели и номера, в другом пользователи и бронирования.

Сервисы:

- eureka-server (порт 8761) — чтобы сервисы находили друг друга
- gateway (порт 8080) — через него идут все запросы
- hotel-service (порт 8081) — отели и номера
- booking-service (порт 8082) — пользователи, вход и бронирования

Нужны Java 17 и Maven.

## Как запустить

Собрать проект (тесты тоже прогонятся):

```
mvn clean install
```

Потом запустить сервисы по очереди, каждый в отдельном терминале:

```
java -jar eureka-server/target/eureka-server-1.0.0.jar
java -jar hotel-service/target/hotel-service-1.0.0.jar
java -jar booking-service/target/booking-service-1.0.0.jar
java -jar gateway/target/gateway-1.0.0.jar
```

При старте добавляются тестовые данные: пользователи `admin`/`admin` и `user`/`user`, три отеля и 8 номеров.

## Как пользоваться

Сначала логинимся и получаем токен:

```
POST http://localhost:8080/api/bookings/user/auth
{"username": "user", "password": "user"}
```

Дальше токен передаём в заголовке `Authorization: Bearer <token>`. Токен живёт 1 час.

Пример бронирования (номер подберётся сам):

```
POST http://localhost:8080/api/bookings/booking
{"requestId": "req-1", "autoSelect": true, "startDate": "2030-12-01", "endDate": "2030-12-03"}
```

Основные запросы:

- `POST /api/bookings/user/register` — регистрация
- `POST /api/bookings/user/auth` — вход
- `POST /api/bookings/booking` — создать бронирование
- `GET /api/bookings/bookings` — мои бронирования
- `DELETE /api/bookings/booking/{id}` — отменить бронирование
- `GET /api/hotels` — список отелей
- `GET /api/rooms?startDate=...&endDate=...` — свободные номера
- `GET /api/rooms/recommend?startDate=...&endDate=...` — рекомендованные номера (сначала те, что бронировали реже)
- `GET /api/hotels/stats`, `GET /api/rooms/stats` — статистика (только ADMIN)

Добавлять, менять и удалять отели, номера и пользователей может только ADMIN. Полный список запросов есть в Swagger.

## Как работает бронирование

1. booking-service сохраняет бронирование со статусом PENDING.
2. Потом просит hotel-service занять номер на эти даты.
3. Если номер свободен — статус CONFIRMED. Если занят — пробуем другой номер или отменяем бронирование (CANCELLED).
4. Если hotel-service не отвечает, бронирование отменяется, а блокировка номера снимается.

Один и тот же `requestId` второй раз не создаст новое бронирование, вернётся старое. Запросы к hotel-service повторяются до 3 раз, а если он долго не отвечает, booking-service на 10 секунд перестаёт его вызывать и сразу возвращает 503.

## Полезные ссылки

- Swagger: http://localhost:8081/swagger-ui.html и http://localhost:8082/swagger-ui.html
- H2 консоль: http://localhost:8081/h2-console (`jdbc:h2:mem:hoteldb`) и http://localhost:8082/h2-console (`jdbc:h2:mem:bookingdb`)
