UPDATE rules
SET
    name = 'Отсечь совсем короткий шум',
    description = 'Сообщения короче 5 символов почти всегда мусор: пустые ответы, случайные буквы, односложные реакции.',
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'Exclude ultra short noise';

UPDATE rules
SET
    name = 'Отсечь короткий шум',
    description = 'Сообщения короче 8 символов обычно не несут полезной инструкции.',
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'Exclude tiny noise';

UPDATE rules
SET
    name = 'Отсечь реакции и флуд',
    description = 'Короткие реакции вроде «ок», «лол», «понял» не должны идти дальше по конвейеру.',
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'Exclude reaction chatter';

UPDATE rules
SET
    name = 'Отсечь короткие сообщения ботов',
    description = 'Короткие сообщения от ботов отсекаются до классификации.',
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'Exclude short bot noise';

UPDATE rules
SET
    name = 'Пропустить AI-сигналы',
    description = 'Сообщения про модели, лимиты, API, релизы, доступы и халяву помечаются как полезные кандидаты.',
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'Include AI value signals';

UPDATE rules
SET
    name = 'Пропустить how-to / гайды',
    description = 'Сообщения с маркерами инструкций, туториалов и гайдов явно пропускаются дальше.',
    updated_at = CURRENT_TIMESTAMP
WHERE name = 'Include how-to markers';
