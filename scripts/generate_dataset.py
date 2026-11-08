"""GreenPlanner AI v2: разнообразный синтетический корпус команд на русском.

Важно: это НЕ записи настоящих пользователей. Вторая версия содержит
короткие, разговорные и многофразовые формулировки. Независимый вручную
составленный holdout (manual_holdout.jsonl) не используется для обучения.

Все значения slot и позиция span извлекаются точно из сформированной строки.
"""
from __future__ import annotations

import argparse
import json
import random
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Форма фрагмента подбирается с учётом предлога. Все ключи с одинаковой
# семантикой получают одинаковый label при разметке.
VALUES = {
    "TASK": [
        "купить молоко", "купить продукты", "зайти за хлебом", "сходить в магазин",
        "забрать посылку", "получить заказ из пункта выдачи", "купить корм коту",
        "вынести мусор", "поменять лампочку в прихожей", "погулять с собакой",
        "постирать спортивную форму", "разобрать вещи после поездки",
        "заказать продукты на неделю", "отнести обувь в ремонт", "забрать ключи",
        "оплатить коммунальные услуги", "оплатить интернет", "пополнить транспортную карту",
        "заплатить за квартиру", "обновить подписку", "проверить расходы за месяц",
        "позвонить маме", "написать преподавателю", "ответить на письмо коллеги",
        "поздравить сестру с днём рождения", "встретиться с друзьями",
        "забрать ребёнка с занятий", "заехать к родителям", "позвонить врачу",
        "договориться о встрече", "купить подарок другу",
        "сделать лабораторную", "закончить диплом", "сдать курсовую",
        "подготовить презентацию", "повторить материал к экзамену",
        "отправить научному руководителю черновик", "оформить список литературы",
        "проверить расписание пар", "сдать отчёт по практике",
        "написать программу для лабораторной", "разобраться с ошибкой сборки",
        "собрать документы для заявления", "выучить английские слова",
        "созвониться с командой", "подготовить отчёт для руководителя",
        "проверить задачи по проекту", "отправить договор",
        "обновить резюме", "подать заявку на стажировку",
        "сходить в спортзал", "сделать зарядку", "пойти на пробежку",
        "записаться к стоматологу", "пройти обследование",
        "забрать результаты анализов", "проверить аптечку",
        "купить витамины", "записаться на стрижку", "сходить на тренировку",
        "купить билеты на поезд", "забронировать гостиницу",
        "проверить время отправления автобуса", "собрать рюкзак",
        "зарядить наушники", "заехать на заправку", "помыть машину",
        "заменить батарейки", "починить велосипед", "прибраться на кухне",
        "купить фильтр для воды", "прочитать две главы книги",
        "сдать посылку на почту", "заказать лекарства",
        "проверить документы перед поездкой", "запланировать отпуск",
    ],
    "EVENT": [
        "встречу с преподавателем", "тренировку", "созвон с коллегами",
        "визит к стоматологу", "встречу с заказчиком", "защиту лабораторной",
        "поход в бассейн", "занятие английским", "собеседование",
        "встречу с другом", "поездку к родителям", "посещение врача",
        "сдачу отчёта", "покупку продуктов", "консультацию по диплому",
        "запись в парикмахерскую", "ремонт велосипеда", "репетицию выступления",
        "обсуждение проекта", "получение посылки", "посещение тренажёрного зала",
    ],
    "DATE_WHEN": [
        "сегодня", "завтра", "послезавтра", "в понедельник", "во вторник",
        "в среду", "в четверг", "в пятницу", "в субботу", "в воскресенье",
        "на выходных", "в конце недели", "в начале следующей недели",
        "через два дня", "через три дня", "через неделю", "на следующей неделе",
        "12 ноября", "16 ноября", "25 ноября", "3 декабря", "15 декабря",
        "завтра утром", "сегодня вечером", "в пятницу после обеда",
        "в следующий понедельник", "во второй половине дня",
    ],
    "DATE_ON": [
        "завтра", "послезавтра", "пятницу", "среду", "четверг", "субботу",
        "понедельник", "следующий вторник", "следующую среду",
        "выходные", "конец недели", "12 ноября", "16 ноября", "3 декабря",
        "следующую неделю", "вечер пятницы",
    ],
    "DATE_UNTIL": [
        "завтра", "пятницы", "субботы", "воскресенья", "конца недели",
        "следующего понедельника", "12 ноября", "3 декабря", "выходных",
    ],
    "TIME": [
        "07:00", "08:00", "09:30", "10:00", "11:00", "12:30", "13:00",
        "14:00", "15:30", "16:00", "17:00", "18:00", "18:30", "19:00",
        "19:30", "20:00", "20:30", "21:00", "22:00",
    ],
    "PLACE_NEAR": [
        "дома", "работы", "университета", "офиса", "дома родителей",
        "дома друга", "магазина", "аптеки", "остановки", "метро",
        "спортзала", "библиотеки", "парка", "поликлиники", "гаража",
    ],
    "PLACE_LOC": [
        "дома", "на работе", "в университете", "в офисе", "у родителей",
        "у друга", "в магазине", "рядом с аптекой", "возле остановки",
        "в спортзале", "в библиотеке", "в парке", "в гараже",
    ],
    "ORIGIN_PLACE": [
        "работы", "учёбы", "университета", "пар", "тренировки",
        "смены", "занятий", "встречи", "практики", "лекции",
    ],
    "DESTINATION_PLACE": [
        "дома", "работы", "университета", "дома родителей", "офиса",
        "магазина", "парка", "гаража", "спортзала", "библиотеки",
    ],
    "PLACE_TYPE_ONE": [
        "аптеку", "фитнес-клуб", "спортивный зал", "супермаркет",
        "продуктовый магазин", "банкомат", "пункт выдачи заказов",
        "кафе", "столовую", "пекарню", "парикмахерскую",
        "бассейн", "заправку", "библиотеку", "цветочный магазин",
        "книжный магазин", "ремонтную мастерскую", "поликлинику",
    ],
    "PLACE_TYPE_MANY": [
        "аптеки", "спортзалы", "фитнес-клубы", "магазины",
        "супермаркеты", "банкоматы", "пункты выдачи", "кафе",
        "столовые", "пекарни", "бассейны", "библиотеки",
        "автосервисы", "цветочные магазины", "парикмахерские",
    ],
    "TOPIC": [
        "историю Древнего Рима", "квантовую физику", "погоду на Марсе",
        "правила футбола", "работу двигателя", "теорию вероятностей",
        "рецепт борща", "искусство фотографии", "японскую культуру",
        "историю шахмат", "новости науки", "расписание кинотеатра",
        "породы собак", "задачи по математике", "книги Достоевского",
        "значение слова «апатия»", "правила дорожного движения",
        "функции нейронов", "геологию", "историю кино",
    ],
}

ANNOTATE = {
    "TASK": "TASK", "EVENT": "TASK", "DATE_WHEN": "DATE",
    "DATE_ON": "DATE", "DATE_UNTIL": "DATE", "TIME": "TIME",
    "PLACE_NEAR": "PLACE", "PLACE_LOC": "PLACE",
    "ORIGIN_PLACE": "ORIGIN_PLACE", "DESTINATION_PLACE": "DESTINATION_PLACE",
    "PLACE_TYPE_ONE": "PLACE_TYPE", "PLACE_TYPE_MANY": "PLACE_TYPE",
    "TOPIC": "UNKNOWN_TOPIC",
}

# TRAIN / VALIDATION / TEST используют разные исходные формулировки. Доли
# стилей short / conversational / narrative можно оценить через поле style.
# Запись: style|шаблон. Каждая запись — отдельная семья формулировок.
RAW = {
"CREATE_TASK": {
"train": """
short|добавь дело {TASK}
short|создай задачу: {TASK}
short|запиши в список {TASK}
short|поставь задачу {TASK} на {DATE_ON}
short|запланируй {TASK} {DATE_WHEN}
short|добавь {TASK} на {DATE_ON}, время {TIME}
short|зафиксируй в делах {TASK}
short|внеси {TASK} в мои планы
conversational|слушай, запиши мне {TASK}
conversational|можешь добавить в задачи {TASK}?
conversational|мне надо {TASK}, занеси это в список
conversational|давай запланируем {TASK} {DATE_WHEN}
conversational|пожалуйста, запиши, что мне нужно {TASK}
conversational|а можешь поставить мне на {DATE_ON} задачу {TASK}?
conversational|хочу не забыть, что собирался {TASK}, добавь дело
conversational|закинь в планировщик {TASK} на {DATE_ON}
conversational|у меня в планах {TASK}, добавь это как задачу
conversational|добавь, пожалуйста, дело {TASK} с дедлайном {DATE_WHEN}
conversational|слушай, нужно {TASK}, желательно {DATE_WHEN}, запиши
conversational|создай мне отдельное дело: {TASK}, примерно в {TIME}
narrative|я постоянно забываю, что нужно {TASK}. Давай создадим для этого задачу на {DATE_ON}
narrative|у меня много дел на неделе. Добавь ещё одно: {TASK} {DATE_WHEN}
narrative|чтобы не держать всё в голове, запиши мне {TASK}, желательно {DATE_WHEN}
narrative|я тут вспомнил про {TASK}. Просто внеси это в мои задачи
narrative|у меня есть свободное окно {DATE_WHEN}, хочу успеть {TASK}. Создай задачу
narrative|планирую {TASK}, но боюсь забыть. Добавь дело в список
narrative|придумал, чем займусь {DATE_WHEN}: {TASK}. Давай зафиксируем
narrative|в списке дел не хватает одной вещи: {TASK}. Можешь добавить?
""",
"validation": """
short|новая задача: {TASK} {DATE_WHEN}
short|дополни список дел пунктом {TASK}
conversational|добавь-ка мне на {DATE_ON} дело {TASK}
conversational|хочу поставить себе задачу {TASK}
conversational|занеси, пожалуйста, в план {TASK} к {TIME}
narrative|мы с тобой составляли план на неделю. Не забудь включить туда {TASK}
narrative|вспомнил ещё одну вещь, которую нужно сделать: {TASK}. Запиши
narrative|не хочу откладывать, давай внесём {TASK} в мой список дел
""",
"test": """
short|создай пункт в планах: {TASK}
short|запланировать {TASK} {DATE_WHEN}
conversational|давай добавим {TASK}, а то потом вылетит из головы
conversational|мне бы записать {TASK}, можешь помочь?
conversational|поставь в мои дела на {DATE_ON}: {TASK}
narrative|в голове куча всего, поэтому лучше сделай отдельную задачу на {TASK}
narrative|кажется, забыл добавить {TASK}. Внеси это в план, пожалуйста
narrative|пока не отвлёкся, хочу завести новое дело: {TASK} {DATE_WHEN}
"""},
"UPDATE_TASK": {
"train": """
short|перенеси {EVENT} на {DATE_ON}
short|сдвинь {EVENT} на {DATE_ON}
short|поменяй дату задачи {TASK} на {DATE_ON}
short|перенеси задачу {TASK} на {DATE_ON}
short|передвинь срок у задачи {TASK} на {DATE_ON}
short|измени время дела {TASK} на {TIME}
short|поставь для задачи {TASK} новое время {TIME}
short|перенеси дедлайн по задаче {TASK} на {DATE_ON}
conversational|давай перенесём {EVENT} на {DATE_ON}
conversational|можешь сдвинуть {EVENT} на {DATE_ON}?
conversational|не успеваю {TASK}, перенеси это дело на {DATE_ON}
conversational|давай лучше {TASK} {DATE_WHEN}, поменяй срок
conversational|поставь мне {EVENT} на {DATE_ON} вместо прежней даты
conversational|уточни время у задачи {TASK}, теперь это {TIME}
conversational|поменяй, пожалуйста, время {EVENT} на {TIME}
conversational|срок по задаче {TASK} изменился, поставь {DATE_WHEN}
conversational|я передумал, давай перенесём {EVENT} на {DATE_ON}
conversational|можешь перенести {EVENT}? Новая дата: {DATE_WHEN}
conversational|мне удобнее {DATE_WHEN}, так что поменяй дату задачи {TASK}
conversational|сделай так, чтобы {EVENT} была позже: на {DATE_ON}
narrative|сегодня не успеваю {TASK}. Давай сдвинем эту задачу на {DATE_ON}
narrative|планы неожиданно поменялись, и {EVENT} придётся перенести на {DATE_ON}
narrative|я неправильно указал время задачи {TASK}. Поставь вместо него {TIME}
narrative|в прошлый раз мы поставили {EVENT} слишком рано. Перенеси на {DATE_ON}
narrative|не получается закончить {TASK} вовремя. Сдвинь дедлайн на {DATE_ON}
narrative|в расписании накладка. Пожалуйста, перенеси {EVENT} на {DATE_ON}
narrative|решил заняться этим позже: {TASK}. Измени срок на {DATE_ON}
narrative|с датой ошибся. У задачи {TASK} теперь срок {DATE_WHEN}, исправь
""",
"validation": """
short|отложи {EVENT} до {DATE_UNTIL}
short|перекинь {EVENT} на {DATE_ON}
conversational|встречаться сейчас не получится, сдвинь {EVENT} на {DATE_ON}
conversational|можно поменять время у задачи {TASK} на {TIME}?
conversational|давай передвинем дедлайн {TASK} на {DATE_ON}
narrative|не хочу отменять {EVENT}, просто перенеси на {DATE_ON}
narrative|вспомнил, что буду занят. Исправь дату у {EVENT}: теперь {DATE_WHEN}
narrative|я явно переоценил свои силы с задачей {TASK}. Переставь её на {DATE_ON}
""",
"test": """
short|обнови срок выполнения {TASK}: {DATE_WHEN}
short|сделай {EVENT} на {DATE_ON} позже
conversational|а может {EVENT} лучше перенесём на {DATE_ON}?
conversational|передвинь выполнение {TASK} на {DATE_ON}, пожалуйста
conversational|я бы хотел изменить время {EVENT} на {TIME}
narrative|на {DATE_ON} будет удобнее, чем сейчас. Перепланируй {EVENT}
narrative|по календарю не складывается, нужно {EVENT} перенести на {DATE_ON}
narrative|выполнить {TASK} вовремя не успеваю, поэтому обнови срок: {DATE_WHEN}
"""},
"DELETE_TASK": {
"train": """
short|удали задачу {TASK}
short|убери из списка {TASK}
short|отмени задачу {TASK}
short|удали пункт {TASK} из дел
short|убери {EVENT} из планировщика
short|сотри напоминание о том, чтобы {TASK}
short|вычеркни из планов {TASK}
short|очисти список от задачи {TASK}
conversational|слушай, убери, пожалуйста, {TASK} из списка
conversational|можешь удалить задачу {TASK}?
conversational|нам больше не нужно {TASK}, давай уберём
conversational|передумал насчёт {EVENT}, удали это дело
conversational|выкинь из планировщика пункт {TASK}
conversational|мне уже не актуально {TASK}, можно удалить?
conversational|убери задачу про {TASK}, она лишняя
conversational|хочу отменить {EVENT}, убери из задач
conversational|не надо больше напоминать про {TASK}, удали напоминание
conversational|удалишь, пожалуйста, дело {TASK}?
narrative|планы изменились, теперь не хочу {TASK}. Удали задачу
narrative|зря добавил дело {TASK}, оно уже не нужно. Убери
narrative|проверил расписание и понял, что {EVENT} отменяется. Удали её
narrative|это было случайно записано: {TASK}. Пожалуйста, удали пункт
narrative|по этой задаче всё отменилось, {TASK} больше не надо. Удаляй
narrative|у меня больше нет необходимости {TASK}. Можешь убрать это из списка?
narrative|мне не нужна задача о том, чтобы {TASK}. Удали её окончательно
narrative|я создал лишнее дело: {TASK}. Хочу избавиться от него
""",
"validation": """
short|удалить {EVENT} из списка
short|вычеркни {TASK}
conversational|давай просто отменим задачу {TASK}
conversational|выброси из моих дел {EVENT}
conversational|можно больше не хранить пункт {TASK}?
narrative|понял, что {EVENT} теперь не состоится. Удали это из календаря
narrative|старые планы неактуальны. Убери конкретно задачу {TASK}
narrative|кажется, я записал лишнее: {TASK}. Удалишь?
""",
"test": """
short|убрать напоминание {TASK}
short|сними задачу {TASK} с моего плана
conversational|отмени-ка {EVENT}, пожалуйста
conversational|давай удалим {TASK} и не будем к этому возвращаться
conversational|можешь убрать именно это дело — {TASK}?
narrative|не хочу больше видеть задачу {TASK} в списке. Она потеряла смысл
narrative|тот пункт про {TASK} теперь не нужен. Сотри его
narrative|я решил отказаться от {EVENT}. Убери её из моего календаря
"""},
"COMPLETE_TASK": {
"train": """
short|отметь задачу {TASK} выполненной
short|пометь дело {TASK} как готовое
short|я сделал {TASK}
short|поставь галочку у задачи {TASK}
short|заверши задачу {TASK}
short|закрой дело {TASK}
short|задачу {TASK} можно завершать
short|переведи {TASK} в выполненные
conversational|слушай, {TASK} уже сделано, отметь
conversational|можешь поставить галочку напротив {TASK}?
conversational|я наконец-то успел {TASK}, закрой задачу
conversational|отметь, пожалуйста, что я закончил {TASK}
conversational|с делом {TASK} разобрался, пометь как завершённое
conversational|готово, {TASK} выполнено, обнови статус
conversational|ну всё, я закончил {TASK}, убери из невыполненных
conversational|переведи задачу про {TASK} в выполненные
conversational|могу отчитаться: успел {TASK}, отмечай
conversational|задача {TASK} закрыта, поставь ей статус выполнено
narrative|всё-таки сделал то, что планировал: {TASK}. Отметь это как выполненное
narrative|хочу убрать из незаконченных дел {TASK}, потому что уже закончил
narrative|сегодня дошли руки до {TASK}. Можешь отметить, что готово?
narrative|я уже разобрался с задачей {TASK}, больше напоминать не нужно. Отметь выполненной
narrative|осталась мелочь: поставь галочку напротив {TASK}, я всё завершил
narrative|только что закончил {TASK}. Поменяй статус задачи на выполнено
narrative|срок ещё не наступил, но я успел {TASK}. Отметь выполнение
narrative|в списке это висит как незавершённое, хотя я уже успел {TASK}. Исправь
""",
"validation": """
short|задача {TASK} выполнена, отметь
short|поставь статус готово для {TASK}
conversational|я сделал то дело — {TASK}, пометь его завершённым
conversational|чекни {TASK}, оно готово
conversational|можешь закрыть пункт {TASK}?
narrative|наконец решился и закончил {TASK}. Поставь отметку о выполнении
narrative|не хочу забывать о прогрессе, поэтому пометь задачу {TASK} как завершённую
narrative|отчитываюсь, дело {TASK} уже закончено. Убери из активных
""",
"test": """
short|считай {TASK} готовым
short|галочка на задачу {TASK}
conversational|мы это сделали — {TASK}. Можешь отметить?
conversational|выполнение задачи {TASK} подтверждаю, обнови статус
conversational|всё, {TASK} завершено, отметь в приложении
narrative|пока есть минутка, обнови список: {TASK} уже выполнено
narrative|я ведь уже закончил {TASK}. Почему это ещё в делах? Отметь выполненным
narrative|с этим разобрались: {TASK}. Сделай, пожалуйста, отметку в планировщике
"""},
"GET_TASKS": {
"train": """
short|покажи мои задачи {DATE_WHEN}
short|какие дела у меня {DATE_WHEN}?
short|что нужно сделать {DATE_WHEN}?
short|список задач на {DATE_ON}
short|покажи невыполненные дела
short|перечисли, что у меня запланировано {DATE_WHEN}
short|остались ли у меня задачи?
short|выведи мои текущие дела
conversational|слушай, что у меня по задачам {DATE_WHEN}?
conversational|можешь показать список дел на {DATE_ON}?
conversational|а что я там себе записывал {DATE_WHEN}?
conversational|напомни список моих текущих задач
conversational|хочу проверить, какие дела ещё не сделал
conversational|покажи, пожалуйста, все задачи с дедлайном {DATE_WHEN}
conversational|есть что-то срочное в моих делах {DATE_WHEN}?
conversational|сейчас какие задачи у меня висят?
conversational|давай глянем список невыполненного
conversational|проверь, что осталось сделать {DATE_WHEN}
narrative|не помню, что вчера записывал. Можешь показать мои задачи на {DATE_ON}?
narrative|только освободился и хочу понять, что ещё нужно сделать. Выведи дела
narrative|кажется, на {DATE_ON} я много всего запланировал. Покажи список задач
narrative|боюсь пропустить важное. Покажи, что у меня осталось невыполненным
narrative|пока еду домой, хочу посмотреть дела на {DATE_ON}. Покажи их
narrative|я запутался в задачах. Скажи, какие из них ещё активны
narrative|проверь мои дела и выведи всё, что должен сделать {DATE_WHEN}
narrative|последние дни было много записей, помоги вспомнить список задач
""",
"validation": """
short|выведи список текущих задач
short|есть дела на {DATE_ON}?
conversational|покажи, что на сегодня ещё не сделано
conversational|можешь проверить мой список дел?
conversational|на какие задачи мне обратить внимание {DATE_WHEN}?
narrative|уже не помню, что на себя взял. Скажи, какие задачи открыты
narrative|хочу разобраться, что осталось сделать. Покажи все незакрытые пункты
narrative|мне кажется, я что-то забыл записанное. Покажи мои планы в виде списка задач
""",
"test": """
short|что по моим делам?
short|сводка задач на {DATE_ON}
conversational|не подскажешь, что я ещё должен сделать {DATE_WHEN}?
conversational|скинь список того, что у меня не выполнено
conversational|какие мои задачи ещё ждут выполнения?
narrative|я хочу закрыть хвосты за неделю, сначала покажи все оставшиеся задачи
narrative|совсем потерял нить дел. Выведи, пожалуйста, то, что ещё предстоит сделать
narrative|можешь напомнить, какие пункты из моего списка ещё актуальны?
"""},
"CREATE_REMINDER": {
"train": """
short|напомни {TASK} {DATE_WHEN}
short|напомни {TASK} в {TIME}
short|напомни {TASK} {DATE_WHEN} в {TIME}
short|поставь напоминание {TASK} {DATE_WHEN}
short|напомни {TASK} около {PLACE_NEAR}
short|уведомь меня, когда буду возле {PLACE_NEAR}: {TASK}
short|напомни {TASK} после {ORIGIN_PLACE}, когда буду около {DESTINATION_PLACE}
short|сделай геонапоминание {TASK} возле {PLACE_NEAR}
conversational|слушай, не дай мне забыть {TASK} {DATE_WHEN}
conversational|можешь напомнить про {TASK} в {TIME}?
conversational|когда буду недалеко от {PLACE_NEAR}, напомни {TASK}
conversational|мне надо {TASK}, пни меня об этом {DATE_WHEN}
conversational|пожалуйста, напомни {TASK}, как только окажусь возле {PLACE_NEAR}
conversational|как выйду после {ORIGIN_PLACE} и подойду к {DESTINATION_PLACE}, напомни {TASK}
conversational|хочу уведомление про {TASK} {DATE_WHEN} после {TIME}
conversational|можешь поставить напоминалку {TASK} при выходе из района {PLACE_NEAR}?
conversational|напомни мне о задаче {TASK}, когда буду {PLACE_LOC}
conversational|напоминание на {DATE_ON}: {TASK}, примерно в {TIME}
narrative|обычно забываю {TASK}. Сделай так, чтобы возле {PLACE_NEAR} пришло уведомление
narrative|сегодня голова кругом. Напомни {TASK} {DATE_WHEN}, пожалуйста
narrative|после {ORIGIN_PLACE} буду ехать домой. Когда окажусь возле {DESTINATION_PLACE}, напомни {TASK}
narrative|боюсь не успеть {TASK}. Можно напомнить мне об этом в {TIME}?
narrative|я хочу по дороге вспомнить, что надо {TASK}. Напомни возле {PLACE_NEAR}
narrative|часто забываю об этом: {TASK}. Сделай уведомление {DATE_WHEN}
narrative|как только окажусь около {PLACE_NEAR}, дай знать, что нужно {TASK}
narrative|хочу, чтобы после {ORIGIN_PLACE} пришло напоминание про {TASK}, когда буду возле {DESTINATION_PLACE}
""",
"validation": """
short|не забудь напомнить {TASK} в {TIME}
short|напоминалка: {TASK} возле {PLACE_NEAR}
conversational|подай сигнал, когда окажусь возле {PLACE_NEAR}: {TASK}
conversational|сделай мне уведомление про {TASK} {DATE_WHEN}
conversational|когда подойду к {PLACE_NEAR}, не дай забыть {TASK}
narrative|вечером после {ORIGIN_PLACE} буду недалеко от {DESTINATION_PLACE}. Напомни, что я собирался {TASK}
narrative|сами задачи я помню, а вот сделать вовремя сложно. Напомни {TASK} {DATE_WHEN}
narrative|хочу не забыть {TASK} в пути. Пусть уведомление придёт возле {PLACE_NEAR}
""",
"test": """
short|уведомление на {DATE_ON}: {TASK}
short|сигнал возле {PLACE_NEAR}: {TASK}
conversational|дай знать в {TIME}, что я должен {TASK}
conversational|пожалуйста, пингани меня про {TASK} {DATE_WHEN}
conversational|на подходе к {PLACE_NEAR} напомни мне {TASK}
narrative|после {ORIGIN_PLACE} я обычно забываю обо всём. Около {DESTINATION_PLACE} напомни {TASK}
narrative|пока не пришёл {DATE_WHEN}, поставь мне напоминание о том, что нужно {TASK}
narrative|хочу получать уведомление, когда буду {PLACE_LOC}, чтобы не забыть {TASK}
"""},
"FIND_PLACE": {
"train": """
short|найди {PLACE_TYPE_ONE} рядом со мной
short|покажи {PLACE_TYPE_MANY} поблизости
short|найди {PLACE_TYPE_MANY} возле {PLACE_NEAR}
short|поищи {PLACE_TYPE_ONE} возле {PLACE_NEAR}
short|где ближайшие {PLACE_TYPE_MANY}?
short|подбери {PLACE_TYPE_ONE} недалеко от {PLACE_NEAR}
short|найди {PLACE_TYPE_MANY} рядом с домом
short|покажи на карте {PLACE_TYPE_MANY} вокруг {PLACE_NEAR}
conversational|слушай, где здесь можно найти {PLACE_TYPE_ONE}?
conversational|можешь найти {PLACE_TYPE_ONE} около {PLACE_NEAR}?
conversational|а что есть из {PLACE_TYPE_MANY} неподалёку?
conversational|хочу посмотреть {PLACE_TYPE_MANY} возле {PLACE_NEAR}
conversational|пожалуйста, подскажи, где ближайшие {PLACE_TYPE_MANY}
conversational|подбери мне {PLACE_TYPE_ONE}, желательно возле {PLACE_NEAR}
conversational|посмотри, какие есть {PLACE_TYPE_MANY} в этом районе
conversational|где можно найти {PLACE_TYPE_ONE} по пути домой?
conversational|поищи на карте {PLACE_TYPE_MANY} недалеко от {PLACE_NEAR}
conversational|есть поблизости {PLACE_TYPE_MANY}? Покажи варианты
narrative|нужно кое-куда заехать, но сначала поищи рядом {PLACE_TYPE_MANY}
narrative|не знаю этот район. Можешь посмотреть на карте {PLACE_TYPE_MANY}?
narrative|собираюсь после работы заехать куда-нибудь. Покажи {PLACE_TYPE_MANY} возле {PLACE_NEAR}
narrative|хочу выбрать место получше. Найди ближайшие {PLACE_TYPE_MANY}
narrative|у меня немного времени, поэтому покажи {PLACE_TYPE_ONE} поблизости
narrative|я сейчас недалеко от {PLACE_NEAR}. Есть ли тут {PLACE_TYPE_MANY}?
narrative|хочу понять, какие {PLACE_TYPE_MANY} есть вокруг. Поищи
narrative|не знаю, куда обратиться. Найди {PLACE_TYPE_ONE} рядом со мной
""",
"validation": """
short|посмотри {PLACE_TYPE_MANY} в окрестностях
short|где найти {PLACE_TYPE_ONE} поблизости?
conversational|а можешь посмотреть на карте, где {PLACE_TYPE_MANY}?
conversational|подскажи, куда сходить: ищу {PLACE_TYPE_ONE} рядом
conversational|хочу глянуть {PLACE_TYPE_MANY} около {PLACE_NEAR}
narrative|в новом районе плохо ориентируюсь. Поищи здесь {PLACE_TYPE_MANY}
narrative|не хочется ехать далеко, подбери {PLACE_TYPE_ONE} неподалёку
narrative|у меня возникла необходимость найти {PLACE_TYPE_ONE}. Покажи ближайший вариант
""",
"test": """
short|что за {PLACE_TYPE_MANY} есть рядом?
short|найди на карте {PLACE_TYPE_ONE}
conversational|ищу {PLACE_TYPE_ONE} по соседству, найдёшь?
conversational|открой список {PLACE_TYPE_MANY} поблизости
conversational|глянь, есть ли {PLACE_TYPE_MANY} в районе {PLACE_NEAR}
narrative|я недавно сюда приехал и ещё плохо знаю город. Найди поблизости {PLACE_TYPE_MANY}
narrative|хочу не тратить время на дорогу. Покажи самые близкие {PLACE_TYPE_MANY}
narrative|сориентируй меня по району: где найти {PLACE_TYPE_ONE}?
"""},
"GET_SCHEDULE": {
"train": """
short|покажи расписание на {DATE_ON}
short|как выглядит мой день {DATE_WHEN}?
short|какие окна есть у меня {DATE_WHEN}?
short|оцени мою загруженность {DATE_WHEN}
short|когда у меня свободное время {DATE_WHEN}?
short|покажи календарь на {DATE_ON}
short|сколько свободных часов у меня {DATE_WHEN}?
short|составь сводку расписания {DATE_WHEN}
conversational|слушай, а как у меня расписан день {DATE_WHEN}?
conversational|покажи, пожалуйста, свободные промежутки {DATE_WHEN}
conversational|можешь оценить, насколько я занят {DATE_WHEN}?
conversational|когда могу найти часик для себя {DATE_WHEN}?
conversational|а есть у меня свободное окно {DATE_WHEN}?
conversational|давай глянем мой календарь на {DATE_ON}
conversational|насколько плотно у меня забит день {DATE_WHEN}?
conversational|сориентируй по моему расписанию {DATE_WHEN}
conversational|в какие часы {DATE_WHEN} у меня ничего нет?
conversational|можешь показать план дня по времени {DATE_WHEN}?
narrative|хочу вписать тренировку, но не знаю, когда свободен. Посмотри расписание {DATE_WHEN}
narrative|завтра много дел, а мне нужен свободный час. Покажи окна {DATE_WHEN}
narrative|не понимаю, когда смогу отдохнуть. Оцени занятость на {DATE_ON}
narrative|собираюсь договориться о встрече. Покажи свободное время {DATE_WHEN}
narrative|мне нужно прикинуть, успеваю ли всё. Посмотри мой план на {DATE_ON}
narrative|в календаре всё перемешалось. Расскажи, как распределено время {DATE_WHEN}
narrative|хочу проверить, нет ли накладок в расписании на {DATE_ON}
narrative|сегодня трудно ориентироваться. Помоги разобрать мой календарь {DATE_WHEN}
""",
"validation": """
short|расскажи, как устроен мой график {DATE_WHEN}
short|есть свободные слоты на {DATE_ON}?
conversational|проверь мою занятость {DATE_WHEN}, пожалуйста
conversational|а когда у меня перерыв {DATE_WHEN}?
conversational|можешь разложить расписание по часам {DATE_WHEN}?
narrative|друзья предлагают встретиться, но я не знаю, когда свободен. Проверь календарь {DATE_WHEN}
narrative|сделать всё нужно, а времени мало. Покажи загруженность на {DATE_ON}
narrative|хочу найти свободное время для дела. Посмотри расписание {DATE_WHEN}
""",
"test": """
short|покажи временные окна на {DATE_ON}
short|как у меня забит календарь {DATE_WHEN}?
conversational|когда могу заняться собой {DATE_WHEN}, если смотреть расписание?
conversational|покажи, во сколько у меня начинается и заканчивается занятость {DATE_WHEN}
conversational|насколько плотный график {DATE_WHEN}?
narrative|собирался куда-нибудь сходить, но боюсь пересечения по времени. Посмотри календарь {DATE_WHEN}
narrative|мне нужно выбрать время для встречи. Проверь, когда свободен {DATE_WHEN}
narrative|не хочу пересекаться с парами и работой. Покажи свободные часы {DATE_WHEN}
"""},
"BREAK_DOWN_TASK": {
"train": """
short|разбей {TASK} на шаги
short|распиши этапы для {TASK}
short|сделай чек-лист для {TASK}
short|подели задачу {TASK} на части
short|составь пошаговый план, как {TASK}
short|придумай подзадачи для {TASK}
short|декомпозируй задачу {TASK}
short|набросай порядок действий для {TASK}
conversational|помоги понять, с чего начать {TASK}
conversational|можешь разбить {TASK} на маленькие шаги?
conversational|хочу нормально организовать {TASK}, распиши по пунктам
conversational|сделай, пожалуйста, небольшой план, чтобы {TASK}
conversational|давай разделим {TASK} на несколько этапов
conversational|как по шагам лучше {TASK}? Составь список
conversational|не знаю, как подступиться к {TASK}, помоги разбить на части
conversational|пожалуйста, сделай для {TASK} список подзадач
conversational|распиши, что делать сначала и что потом, чтобы {TASK}
conversational|хочу мини-план по делу {TASK}
narrative|задача кажется слишком большой: {TASK}. Разбей её на посильные этапы
narrative|начинаю {TASK}, но пока не понимаю порядок действий. Составь чек-лист
narrative|мне проще двигаться небольшими шагами. Распиши, как {TASK}
narrative|постоянно откладываю {TASK}, потому что не знаю, с чего начать. Помоги разложить
narrative|до срока ещё есть время, но лучше продумать план. Разбей {TASK} на этапы
narrative|помоги сделать план по пунктам для дела {TASK}, чтобы ничего не упустить
narrative|хочу постепенно {TASK}. Предложи последовательность небольших задач
narrative|за один вечер всё не сделаю. Распиши этапы, чтобы {TASK}
""",
"validation": """
short|раздели {TASK} на подзадачи
short|декомпозиция задачи {TASK}
conversational|а можно план действий, чтобы {TASK}?
conversational|разложи мне {TASK} по маленьким действиям
conversational|предложи этапы работы над задачей {TASK}
narrative|голова кругом от объёма работы: {TASK}. Помоги разбить на части
narrative|не хочу пытаться делать всё сразу. Подготовь этапы для {TASK}
narrative|давай сначала составим пошаговый список, по которому я смогу {TASK}
""",
"test": """
short|структурируй {TASK} по шагам
short|план выполнения: {TASK}
conversational|подскажи последовательность действий для {TASK}
conversational|как аккуратно разбить дело {TASK} на маленькие кусочки?
conversational|хочу план по пунктам для {TASK}
narrative|дело сложное, и я буксую на старте. Составь шаги для {TASK}
narrative|чтобы не хвататься за всё подряд, помоги структурировать {TASK}
narrative|нужно разумно распределить работу над {TASK}. Сделай список этапов
"""},
"UNKNOWN": {
"train": """
short|расскажи про {TOPIC}
short|объясни {TOPIC}
short|что ты знаешь про {TOPIC}?
short|расскажи интересный факт про {TOPIC}
short|объясни простыми словами {TOPIC}
short|дай справку про {TOPIC}
short|хочу узнать про {TOPIC}
short|сделай краткий рассказ про {TOPIC}
conversational|слушай, что скажешь про {TOPIC}?
conversational|можешь объяснить мне {TOPIC}?
conversational|а не расскажешь про {TOPIC}?
conversational|мне просто интересно почитать про {TOPIC}
conversational|подскажи, как разобраться в теме {TOPIC}
conversational|я не очень понимаю {TOPIC}, объясни
conversational|как думаешь, что важнее знать про {TOPIC}?
conversational|помоги разобраться в вопросе про {TOPIC}, но без планирования задач
conversational|расскажи что-нибудь любопытное про {TOPIC}
conversational|можешь привести пример по теме {TOPIC}?
narrative|я смотрел видео про {TOPIC} и не всё понял. Объясни смысл
narrative|просто хочу немного отвлечься от дел и поговорить про {TOPIC}
narrative|у меня вопрос не про планирование. Можешь рассказать про {TOPIC}?
narrative|задали интересный вопрос про {TOPIC}, можешь коротко объяснить?
narrative|в интернете спорят про {TOPIC}. Объясни, о чём речь
narrative|решил расширить кругозор. Расскажи мне про {TOPIC}
narrative|я сегодня не хочу ничего добавлять в список задач. Просто расскажи про {TOPIC}
narrative|есть минутка поболтать? Хотел узнать что-нибудь про {TOPIC}
""",
"validation": """
short|дай информацию про {TOPIC}
short|справка по теме {TOPIC}
conversational|помоги понять тему {TOPIC}
conversational|кратко объясни, что означает {TOPIC}
conversational|расскажи пару слов про {TOPIC}
narrative|это никак не связано с моим расписанием, но я хочу узнать про {TOPIC}
narrative|сегодня просто любопытно: {TOPIC}. Можешь рассказать?
narrative|если не сложно, объясни что-нибудь интересное про {TOPIC}
""",
"test": """
short|расскажи подробнее про {TOPIC}
short|кто-нибудь объяснит {TOPIC}?
conversational|в двух словах можешь про {TOPIC}?
conversational|хочу получше понять {TOPIC}, поможешь?
conversational|можешь рассказать про {TOPIC}, а не создавать задачу?
narrative|я сейчас занимаюсь совсем другим, так что просто ответь на вопрос про {TOPIC}
narrative|ты, наверное, разбираешься в {TOPIC}. Расскажи по-человечески
narrative|нет нужды ничего планировать, просто хотелось понять тему {TOPIC}
"""},
}

# Иногда обычная голосовая речь начинается с нейтрального обращения.
PREFIXES = ["", "", "", "", "так, ", "слушай, ", "а ещё ", "пожалуйста, ", "ну, ", "кстати, "]
SUFFIXES = ["", "", "", "", " если можно", " пожалуйста", " ок?", " спасибо"]


def parse_templates():
    parsed = {}
    for intent, splits in RAW.items():
        parsed[intent] = {}
        for split, block in splits.items():
            arr = []
            for line in block.strip().splitlines():
                style, pat = line.strip().split("|", 1)
                assert style in {"short", "conversational", "narrative"}
                for key in re.findall(r"\{(\w+)\}", pat):
                    assert key in VALUES, (intent, key)
                arr.append((style, pat))
            parsed[intent][split] = arr
    return parsed

TEMPLATES = parse_templates()
ANNOTATABLE = set(ANNOTATE.values())


def fill_template(template: str, rng: random.Random):
    parts = []
    spans = []
    at = 0
    for match in re.finditer(r"\{([A-Z_]+)\}", template):
        parts.append(template[at:match.start()])
        name = match.group(1)
        value = rng.choice(VALUES[name])
        start = sum(map(len, parts))
        parts.append(value)
        spans.append({"label": ANNOTATE[name], "text": value, "start": start, "end": start + len(value)})
        at = match.end()
    parts.append(template[at:])
    return "".join(parts), spans


def decorate(text, slots, rng, style):
    # Префиксы и суффиксы нужны для лексической вариативности.  Тот же стиль
    # может встречаться в разных выборках, но ни одна исходная фраза-шаблон — нет.
    prefix = rng.choice(PREFIXES) if style != "short" else rng.choice(["", "", "", "", "так, ", "слушай, ", "пожалуйста, "])
    suffix = rng.choice(SUFFIXES) if style != "short" else rng.choice(["", "", "", "", " пожалуйста", " если можно"])
    if prefix:
        # Не добавляем вступление перед уже существующим аналогичным вступлением.
        if text[:8].casefold().startswith(("слушай", "а ещё", "кстати", "пожалуйста")):
            prefix = ""
    text = prefix + text
    for s in slots:
        s["start"] += len(prefix)
        s["end"] += len(prefix)
    if suffix and ("пожалуйста" in text.lower() or "если можно" in text.lower()):
        if suffix in (" пожалуйста", " если можно"):
            suffix = ""
    if suffix:
        ending = "?" if text.endswith("?") else ("!" if text.endswith("!") else ".")
        stem = text.rstrip("?.! ")
        if suffix == " ок?":
            text = stem + ", ок?"
        elif suffix in (" пожалуйста", " спасибо", " если можно"):
            text = stem + "," + suffix + ending
    return text, slots


def make_dataset(train_per_class=600, val_per_class=100, test_per_class=100, seed=2026):
    """Возвращает dict с тремя сбалансированными выборками, без дублей."""
    rng = random.Random(seed)
    splits = {name: [] for name in ("train", "validation", "test")}
    counts = {"train": train_per_class, "validation": val_per_class, "test": test_per_class}
    seen = set()
    for intent, split_templates in TEMPLATES.items():
        for split, templates in split_templates.items():
            n = counts[split]
            if n < 1:
                raise ValueError("Число примеров должно быть положительным")
            # Без параметров — по одному примеру каждого шаблона. Остаток
            # распределяем между вариативными шаблонами с заполняемыми слотами.
            fixed = [(i, t) for i, t in enumerate(templates) if not re.search(r"\{[A-Z_]+\}", t[1])]
            dynamic = [(i, t) for i, t in enumerate(templates) if re.search(r"\{[A-Z_]+\}", t[1])]
            if n < len(fixed) or not dynamic:
                raise ValueError(f"Недостаточно динамических шаблонов для {intent}/{split}")
            planned = fixed + [dynamic[i % len(dynamic)] for i in range(n - len(fixed))]
            rng.shuffle(planned)
            for j, (pattern_i, (style, template)) in enumerate(planned):
                record = None
                for attempt in range(2000):
                    text, slots = fill_template(template, rng)
                    text, slots = decorate(text, slots, rng, style)
                    key = re.sub(r"\s+", " ", text.strip()).casefold()
                    if key in seen:
                        continue
                    seen.add(key)
                    record = {
                        "id": f"{split}-{intent.lower()}-{j:04d}",
                        "text": text,
                        "intent": intent,
                        "slots": slots,
                        "template_id": f"{intent}-{split}-{pattern_i:02d}",
                        "source": "synthetic",
                        "style": style,
                    }
                    break
                if record is None:
                    raise RuntimeError(f"Не удалось получить уникальную фразу {intent}, {split}, {j}")
                splits[split].append(record)
    for rows in splits.values():
        rng.shuffle(rows)
    return splits


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--train-per-class", type=int, default=600)
    p.add_argument("--val-per-class", type=int, default=100)
    p.add_argument("--test-per-class", type=int, default=100)
    p.add_argument("--seed", type=int, default=2026)
    args = p.parse_args()
    data = make_dataset(args.train_per_class, args.val_per_class, args.test_per_class, args.seed)
    (ROOT / "data").mkdir(exist_ok=True)
    for split, rows in data.items():
        with (ROOT / "data" / f"{split}.jsonl").open("w", encoding="utf-8") as f:
            for row in rows:
                f.write(json.dumps(row, ensure_ascii=False) + "\n")
        counts = Counter(r["style"] for r in rows)
        print(f"{split}: {len(rows)} фраз; стили {dict(counts)}")
    print("Всего синтетических:", sum(len(x) for x in data.values()))
    print("Для независимой оценки: data/manual_holdout.jsonl")


if __name__ == "__main__":
    main()
