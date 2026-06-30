# java-explore-with-me
Дополнительная фукциональность.

Feature : **rating_events** (лайки/дизлайки, рейтинг мероприятий)

# Оглавление

- [java-explore-with-me](#java-explore-with-me)
- [Оглавление](#оглавление)
- [Эндпоинты](#эндпоинты)
  - [1. Поставить *"like"*](#1-поставить-like)
  - [2. Поставить *"dislike"*](#2-поставить-dislike)
  - [3. Убрать *"like/dislike"*](#3-убрать-likedislike)
  - [4. Получить рейтинги мероприиятии](#4-получить-рейтинги-мероприиятии)
  - [5. Получить рейтинг мероприиятий пользователя](#5-получить-рейтинг-мероприиятий-пользователя)
  - [6. Получить список событий по рейтингу](#6-получить-список-событий-по-рейтингу)

# Эндпоинты

## 1. Поставить *"like"*

*Краткое описание:* 
    
Поставить *like* на мероприиятии **{eventId}**

*Команда:* 

```POSTMAN
POST /private/events/{userId}/{eventId}/like 
```

**Возвращает данные в формате:**

```JSON
[
    {
    "id" : id,
    "eventId" : eventId,
    "userId" : userId,
    "rating" : like,
    "timestamp" : timestamp
    }
]
```

При выполнении команды необходимо учесть следующие условия:

1. Пользователь **{userId}** и мероприятие **{eventId}** существуют. Иначе выбрасывается ошибка **NOT_FOUND**. (например *NotFoundException*)

2. Оценку можно поставить мероприятию **{eventId}** которое уже состоялось. Иначе выбрасывается ошибка **BAD_REQUEST**. (например *ConditionsNotMetException*)

3. Пользователь **{userId}** был на мероприятие **{eventId}**. Иначе выбрасывается ошибка **BAD_REQUEST**. (например *ConditionsNotMetException*)

4. Пользователь **{userId}** может поставить оценку только 1 раз. Иначе выбрасывается ошибка **CONFLICT**. (например *AlreadyExistsException*) 
    
## 2. Поставить *"dislike"*

*Краткое описание:* 
    
Поставить *dislike* на мероприятие **{eventId}**

*Команда:* 

```POSTMAN
POST /private/events/{userId}/{eventId}/dislike
```

**Возвращает данные в формате:**

```JSON
[
    {
    "id" : id,
    "eventId" : eventId,
    "userId" : userId,
    "rating" : dislike,
    "timestamp" : timestamp
    }
]
```

При выполнении команды необходимо учесть следующие условия:

1. Пользователь **{userId}** и мероприятие **{eventId}** существуют. Иначе выбрасывается ошибка **NOT_FOUND**. (например *NotFoundException*)

2. Оценку можно поставить мероприятию **{eventId}** которое уже состоялось. Иначе выбрасывается ошибка **BAD_REQUEST**. (например *ConditionsNotMetException*)

3. Пользователь **{userId}** был на мероприятие **{eventId}**. Иначе выбрасывается ошибка **BAD_REQUEST**. (например *ConditionsNotMetException*)

4. Пользователь **{userId}** может поставить оценку только 1 раз. Иначе выбрасывается ошибка **CONFLICT**. (например *AlreadyExistsException*) 

##  3. Убрать *"like/dislike"*

*Краткое описание:* 

Удалить *like/dislike* на мероприятие **{eventId}**

*Команда:* 

```POSTMAN
DELETE /private/events/{userId}/{eventId}/deleteRating
```

При выполнении команды необходимо учесть следующие условия:

1. Пользователь **{userId}** и мероприятия **{eventId}** существуют. Иначе выбрасывается ошибка **NOT_FOUND**. (например *NotFoundException*)
    
##  4. Получить рейтинги мероприятия

*Краткое описание:*

Получить рейтинги мероприятия **{eventId}**

*Команда:* 

```POSTMAN
GET /public/events/{eventId}/eventRating
```

**Возвращает данные в формате:**

```JSON
[
    {
    "eventId" : eventId,
    "like_count" : like_count,
    "dislike_count" : dislike_cont
    }
]
```

При выполнении команды необходимо учесть следующие условия:

1. Мероприятие **{eventId}** существуют. Иначе выбрасывается ошибка **NOT_FOUND**. (например *NotFoundException*)

##  5. Получить списка рейтинга пользователя

*Краткое описание:* 

Получить список оценок пользователя **{userId}**.

*Команда:* 

```POSTMAN
GET /public/events/{userId}/userRating?rating={rating}&from={from}&size={size}
```

**from={from}** необязателен и имеет значение по умолчанию *0*. Определяет сколько объектов надо пропустить перед выводом.

**size={size}** необязателен и имеет значение по умолчанию *10*. Определяет сколько объектов необходимо получить.

В зависимости от **rating={rating}** возможны следующие варианты:
    
1. **rating** = *like*

Получает список оценок *"like"* пользователя **{userId}**. Отсортированные в порядке убывания по времени

**Возвращает данные в формате:**

```JSON
[
    {
    "id" : id,
    "eventId" : eventId,
    "userId" : userId,
    "rating" : like,
    "timestamp" : timestamp
    },
    {
    "id" : id,
    "eventId" : eventId,
    "userId" : userId,
    "rating" : like,
    "timestamp" : timestamp
    },
    ...
]
```

2. **rating** = *dislike*

Получает список оценок *"dislike"* пользователя **{userId}**. Отсортированные в порядке убывания по времени

**Возвращает данные в формате:**

```JSON
[
    {
    "id" : id,
    "eventId" : eventId,
    "userId" : userId,
    "rating" : dislike,
    "timestamp" : timestamp
    },
    {
    "id" : id,
    "eventId" : eventId,
    "userId" : userId,
    "rating" : dislike,
    "timestamp" : timestamp
    },
    ...
]
```

3. **rating** не указан

Получает список всех оценок пользователя **{userId}**. Отсортированные в порядке убывания по времени

**Возвращает данные в формате:**

```JSON
[
    {
    "id" : id,
    "eventId" : eventId,
    "userId" : userId,
    "rating" : dislike,
    "timestamp" : timestamp
    },
    {
    "id" : id,
    "eventId" : eventId,
    "userId" : userId,
    "rating" : like,
    "timestamp" : timestamp
    },
    ...
]
```

При выполнении команды необходимо учесть следующие условия:

1. Пользователь **{userId}** существуют. Иначе выбрасывается ошибка **NOT_FOUND**. (например *NotFoundException*)

2. Необязательность параметров **{rating}**, **{from}**, **{size}**


##  6. Получить список событий по рейтингу

*Краткое описание:* 
    
Получить список мероприятий которым пользователи поставили наибольшую среднюю оценку **(сумма(*like) - сумма(dislike))**.

*Команда:* 
```POSTMAN
GET /public/events/rating?from={from}&size={size}&order={order} 
```

**from={from}** необязателен и имеет значение по умолчанию *0*. Определяет сколько объектов надо пропустить перед выводом.

**size={size}** необязателен и имеет значение по умолчанию *10*. Определяет сколько объектов необходимо получить.

**order={order}** используется для задания направления сортировки. По умолчанию используется *DESC*, сортировка по убыванию средней оценки. Так-же возможен вариант *ASC* - сортировка по возрастанию средней оценки.

**Возвращает данные в формате:**

```JSON
[
    {
    "eventId" : eventId,
    "rating_count" : rating_count
    },
    {
    "eventId" : eventId,
    "rating_count" : rating_count
    },
    ...
]
```

При выполнении команды необходимо учесть следующие условия:

1. Необязательность параметров **{from}**, **{size}**, **{order}** 