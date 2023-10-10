![SEarchit (1).png](https://i.postimg.cc/0NhCtQjL/SEarchit-1.png)

SearchIT поисковый движок на Java.

Возможности программы:

Dashboard - показывает статистику по леммам, страницам и сайтам

![img.png](https://i.postimg.cc/dVtZGYsr/img.png)

Management - при нажатии на кнопку Start indexing запускает полную индексацию всех сайтов и при нажатии Stop indexing остановить полную индексацию. Есть возможность проиндексировать отдельную страницу сайта в поле ввода и нажать на кнопку add/update

![img_1.png](https://i.postimg.cc/CKCy0W8r/img-1.png)

Search - делает поиск по введенному слову по всем проиндексированным сайтам либо по выбранному в выпадающем списке

![img_2.png](https://i.postimg.cc/L6fFvYXM/img-2.png)

Технологии:

1)Java 20

2)Spring 3.1.1

3)MySQL

4)Jsoup

5)org.apace.lucene.morphology

6)Lombok

Настройка:

1)Создать пустую базу данных search_engine и использовать кодировку utf8mb4

2)Скачать файл конфигурации application.yaml и установить логин и пароль

3)Указать сайты для индексации

4)Запустить приложение с командной строки jar файл с помощью команды java -jar SearchEngine-1.0.jar

5)Перейдите по адресу http://localhost:8080/ в браузере