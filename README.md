![SEarchit (1).png](https://i.postimg.cc/0NhCtQjL/SEarchit-1.png)

SearchIT search engine in Java.

Program features:

Dashboard - shows statistics on lemmas, pages and sites

![img.png](https://i.postimg.cc/dVtZGYsr/img.png)

Management - by clicking (Start indexing) button, it starts full indexing of all sites and by clicking (Stop indexing) button it stops the full indexing. It is also possible to index a separate page of the site in the input field and click on the (add/update) button

![img_1.png](https://i.postimg.cc/CKCy0W8r/img-1.png)

Search - searches for the entered word across all indexed sites or selected in the drop-down list

![img_2.png](https://i.postimg.cc/L6fFvYXM/img-2.png)

Technologies:

1)Java 20

2)Spring 3.1.1

3)PostgreSQL

4)Jsoup

5)org.apace.lucene.morphology

6)Lombok

Setup:

1)Create an empty search_engine database in PostgreSQL

2)Download the application.yaml configuration file and set the login and password

3)Enter the sites for indexing in the application.yaml configuration file

4)Run the application from the command line jar file using the command (java -jar SearchEngine-1.0.jar)

5)Go to http://localhost:8080/ in a browser