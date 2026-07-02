# tfidf_cosine_baseline / lab-cluster-66

- `msg-02517` `OUTAGE_STATUS` `REVIEW_SIGNAL_OR_DISCUSSION` минимальная проверка совместимого api у нас такая: /models потом короткий chat completion потом проверка stream/non-stream потом 401/429 сценарии.
- `msg-02529` `OUTAGE_STATUS` `REVIEW_SIGNAL_OR_DISCUSSION` как проверить что openai-compatible api реально работает: сделать get /models проверить что модель видна в списке отправить короткий chat completion сверить формат ответа и usage отдельно проверить 429/401 чтобы понять это лимит или битый ключ
