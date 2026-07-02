# time_thread_entity / lab-cluster-65

- `msg-02517` `OUTAGE_STATUS` `REVIEW_SIGNAL_OR_DISCUSSION` main минимальная проверка совместимого api у нас такая: /models потом короткий chat completion потом проверка stream/non-stream потом 401/429 сценарии.
- `msg-02528` `OUTAGE_STATUS` `REVIEW_SIGNAL_OR_DISCUSSION` main если gpt через совместимый api отвечает 401 почти всегда проблема в authorization: bearer ... неверном base url или отключенном ключе у провайдера.
- `msg-02529` `OUTAGE_STATUS` `REVIEW_SIGNAL_OR_DISCUSSION` main как проверить что openai-compatible api реально работает: сделать get /models проверить что модель видна в списке отправить короткий chat completion сверить формат ответа и usage отдельно проверить 429/401 чтобы понять это лимит или битый ключ
- `msg-07844` `OUTAGE_STATUS` `REVIEW_SIGNAL_OR_DISCUSSION` main как проверить openai-compatible api в cursor: 1 base url должен заканчиваться на /v1 2 auth header bearer должен брать ключ из переменной окружения 3 model id нужно сверить с ответом get /models у провайдера 4 если api возвращает 401 проблема почти всегда
