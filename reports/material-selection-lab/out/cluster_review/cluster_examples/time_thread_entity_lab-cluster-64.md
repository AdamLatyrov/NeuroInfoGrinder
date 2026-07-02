# time_thread_entity / lab-cluster-64

- `msg-02511` `TECH_SIGNAL` `REVIEW_SIGNAL_OR_DISCUSSION` main небольшое но полезное правило: если совместимый api не отвечает на /models не тратьте время на sdk-отладку сначала проверьте base url ключ и доступность endpoint.
- `msg-02518` `TECH_SIGNAL` `REVIEW_SIGNAL_OR_DISCUSSION` main похоже многие путают совместимый api работает и sdk не падает . на практике сначала надо проверить протокол а уже потом клиентскую библиотеку.
- `msg-02525` `TECH_SIGNAL` `REVIEW_SIGNAL_OR_DISCUSSION` main полезный чек: для совместимых api сначала проверяйте /models потом один минимальный completion и только после этого подключайте sdk и ретраи.
- `msg-09047` `TECH_SIGNAL` `REVIEW_SIGNAL_OR_DISCUSSION` main nigtest-a06 у меня api иногда уходит в fallback на другую модель но в логах непонятно это лимит регион или проблема router endpoint.
- `msg-09051` `TECH_SIGNAL` `REVIEW_SIGNAL_OR_DISCUSSION` main nigtest-a02 мини-чеклист: если api начал отвечать медленно сначала проверь статус провайдера затем регион endpoint потом включи fallback на запасную модель отдельно залогируй latency http status и model id. если ошибка повторяется сравни ответ через curl 
