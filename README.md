# Professor Publications App — Istruzioni di avvio

## 1. Descrizione dell'applicazione

Professor Publications App è un'applicazione desktop JavaFX sviluppata in Java 21.

L'app permette di:

* cercare un professore tramite IRIS ID o codice fiscale;
* recuperare le pubblicazioni istituzionali da IRIS/CINECA;
* visualizzare metadati bibliografici delle pubblicazioni;
* recuperare dati citazionali da Scopus;
* recuperare dati citazionali e documenti citanti da Google Scholar tramite SerpApi;
* esportare citazioni in formato BibTeX;
* gestire una rubrica locale di codici fiscali dei docenti.

L'applicazione non usa scraping diretto di Google Scholar. L'accesso a Scholar avviene tramite SerpApi.

---

## 2. Requisiti

Per avviare l'applicazione è necessario avere installato:

* Windows 10/11;
* Java 21 o superiore.

Verifica Java da PowerShell o Prompt dei comandi:

```powershell
java -version
```

Il risultato deve mostrare Java 21 o superiore, ad esempio:

```text
java version "21.0.11"
```

Se viene mostrato Java 17 o inferiore, installare Java 21 e configurare correttamente PATH/JAVA_HOME.

---

## 3. Installazione Java 21

È possibile installare Java 21 dal sito ufficiale Oracle:

```text
https://www.oracle.com/java/technologies/downloads/
```

Selezionare:

```text
Java SE Development Kit 21
Windows
x64 Installer
```

Dopo l'installazione, chiudere e riaprire PowerShell e verificare:

```powershell
java -version
```

Opzionale ma consigliato: configurare JAVA_HOME verso la cartella del JDK, ad esempio:

```text
C:\Program Files\Java\jdk-21.0.11
```

---

## 4. Struttura della cartella di distribuzione

La cartella di distribuzione contiene:

```text
prof-publications-app.jar
run-app.bat
README_AVVIO.md
lib/
```

Il file principale dell'applicazione è:

```text
prof-publications-app.jar
```

La cartella:

```text
lib/
```

contiene le dipendenze necessarie, tra cui JavaFX, SQLite JDBC, Jackson, SLF4J e Logback.

Non eliminare o spostare la cartella `lib`, altrimenti l'applicazione potrebbe non avviarsi.

---

## 5. Avvio dell'applicazione

Per avviare l'applicazione, fare doppio click su:

```text
run-app.bat
```

In alternativa, da PowerShell:

```powershell
cd percorso\della\cartella\dist
.\run-app.bat
```

Lo script controlla automaticamente:

* che il JAR sia presente;
* che la cartella `lib` sia presente;
* che Java sia installato;
* che la versione Java sia almeno 21.

Se Java non è compatibile, lo script mostra un messaggio chiaro e blocca l'avvio.

---

## 6. Configurazione API key e credenziali

Al primo avvio l'app può mostrare una finestra di configurazione iniziale.

Le credenziali/API key richieste sono:

### IRIS / CINECA

Necessarie per interrogare i servizi IRIS istituzionali.

Campi:

```text
Username IRIS REST
Password IRIS REST
```

### Scopus / Elsevier

Necessaria per recuperare dati citazionali Scopus.

Campo:

```text
SCOPUS_API_KEY
```

Campo opzionale:

```text
SCOPUS_INST_TOKEN
```

Nota: alcuni dati Scopus, in particolare l'elenco dei documenti citanti, possono dipendere dai permessi API, da un institutional token o dalla rete/VPN dell'Ateneo.

### Google Scholar tramite SerpApi

Necessaria per interrogare Google Scholar tramite SerpApi, senza scraping diretto.

Campo:

```text
SERPAPI_API_KEY
```

La disponibilità di risultati, citazioni e documenti citanti dipende dal piano SerpApi e dai dati restituiti da Google Scholar tramite SerpApi.

---

## 7. Dove vengono salvate le impostazioni

Le impostazioni vengono salvate localmente nel PC dell'utente.

Percorso:

```text
C:\Users\<utente>\.prof-publications-app\settings.properties
```

Questo file può contenere credenziali e API key personali.

Non caricare questo file su GitHub e non condividerlo.

---

## 8. Rubrica CF locale

La Rubrica CF è locale e personale.

Per motivi di privacy, l'app viene distribuita con una rubrica vuota.

I dati inseriti nella rubrica vengono salvati nel file:

```text
C:\Users\<utente>\.prof-publications-app\professors-cf.csv
```

Nota importante:

```text
I DATI RESTANO LOCALI
```

---

## 9. Database SQLite locale

L'app usa SQLite per una cache locale minima di pubblicazioni, citazioni e documenti citanti.

Il database viene creato nella cartella da cui viene avviata l'applicazione, con nome:

```text
prof-publications.db
```

Se l'app viene avviata dalla cartella `dist`, il database verrà creato in quella cartella.

Il database è una cache locale e può essere eliminato se si vuole forzare un nuovo recupero dei dati.

---

## 10. Funzionalità principali da testare

Dopo l'avvio, è possibile verificare l'app con questa sequenza:

1. Aprire l'applicazione con `run-app.bat`.
2. Aprire le Impostazioni dal pulsante con icona ingranaggio.
3. Verificare o inserire le credenziali/API key.
4. Cercare un professore tramite IRIS ID.
5. Premere `Refresh IRIS`.
6. Selezionare una pubblicazione dalla tabella.
7. Visualizzare il dettaglio della pubblicazione.
8. Premere `Refresh Scopus/Scholar`.
9. Visualizzare il numero di citazioni.
10. Aprire `Documenti citanti Scholar`.
11. Esportare una citazione in formato BibTeX tramite `.bib`.
12. Aprire la Rubrica CF.
13. Aggiungere, modificare o eliminare un docente locale.

---

## 11. Modalità limitata

Se una o più credenziali/API key non sono configurate, l'app può continuare a funzionare in modalità limitata.

Esempi:

* senza IRIS: ricerca e refresh istituzionale non disponibili;
* senza Scopus: citation count Scopus non disponibile;
* senza SerpApi: dati Scholar e documenti citanti Scholar non disponibili.

L'applicazione degrada senza bloccare l'interfaccia: quando una sorgente non è disponibile, le altre parti continuano a funzionare per quanto possibile.

---

## 12. Note di sicurezza

* Non condividere `settings.properties`.
* Non caricare API key o password su GitHub.
* Non inserire dati personali nella rubrica se non si è autorizzati a utilizzarli.
* Le API key vengono lette localmente e non vengono stampate nei log.
* I dati della rubrica CF restano locali sul PC dell'utente.

---

## 13. Avvio da IntelliJ IDEA

Per avviare il progetto da IntelliJ IDEA:

1. Aprire il progetto Maven.
2. Configurare SDK Java 21.
3. Verificare che Maven importi correttamente le dipendenze.
4. Avviare con Maven:

```powershell
mvn javafx:run
```

oppure usare una run configuration JavaFX/Maven equivalente.

---

## 14. Generazione della distribuzione

Per rigenerare la cartella distribuibile:

```powershell
mvn clean package
```

La distribuzione viene creata in:

```text
target/dist/
```

Contenuto atteso:

```text
target/dist/prof-publications-app.jar
target/dist/run-app.bat
target/dist/README_AVVIO.md
target/dist/lib/
```

---

## 15. Limitazioni note della versione

Questa è una versione v1 solida e presentabile per progetto individuale.

Limitazioni note:

* il recupero completo dei documenti citanti Scopus può dipendere da permessi API, institutional token o rete/VPN dell'Ateneo;
* Google Scholar è interrogato tramite SerpApi e può restituire dati variabili o parziali;
* il BibTeX viene recuperato/generato con fallback quando i metadati disponibili sono sufficienti;
* la deduplica avanzata e il mapping manuale completo dei profili Scholar sono possibili estensioni future.

---

## 16. Autore

Progetto sviluppato come applicazione desktop JavaFX per ricerca pubblicazioni, citazioni e BibTeX.

Tecnologie principali:

```text
Java 21
JavaFX 21
Maven
SQLite
IRIS/CINECA
Scopus API
SerpApi / Google Scholar
```
