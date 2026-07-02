# tfidf_cosine_baseline / lab-cluster-69

- `msg-02528` `OUTAGE_STATUS` `REVIEW_SIGNAL_OR_DISCUSSION` если gpt через совместимый api отвечает 401 почти всегда проблема в authorization: bearer ... неверном base url или отключенном ключе у провайдера.
- `msg-07844` `OUTAGE_STATUS` `REVIEW_SIGNAL_OR_DISCUSSION` как проверить openai-compatible api в cursor: 1 base url должен заканчиваться на /v1 2 auth header bearer должен брать ключ из переменной окружения 3 model id нужно сверить с ответом get /models у провайдера 4 если api возвращает 401 проблема почти всегда в кл
