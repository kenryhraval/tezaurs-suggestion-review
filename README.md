# Tēzaura ieteikumu perēklis

Darba vide lietotāju ieteikumu izvērtēšanai un šķirkļu vai nozīmju melnrakstu sagatavošanai. Apvieno dublikātu apstrādi, lietojuma izpēti korpusos un MI atbalstu piemēru grupēšanā un definīciju veidošanā.

![Backend](https://img.shields.io/badge/Backend-Java%20%2F%20Spring%20Boot-6DB33F)
![Frontend](https://img.shields.io/badge/Frontend-React%20%2F%20TypeScript-3178C6)
![PostgreSQL](https://img.shields.io/badge/Datub%C4%81ze-PostgreSQL-4169E1)

## Palaišana

Izveidojiet un aizpildiet `.env` pēc [parauga](.env.example). Iekopējiet videi atbilstošo compose failu. Lietotne sagaida eksistējošas `dict` un `incubator` izmantotajā datubāzē. Lietotnes migrācija tām līdzās izveido `review` shēmu. **Lokālajai videi un atsevišķam serverim nepieciešamas datu kopijas**, kuras pieejamas [tēzaurs.lv repozitorijā](https://github.com/LUMII-AILab/Tezaurs_platform). Pirms palaišanas lejuplādējiet `.pgsql.gz` failus un iekš `.env` norādiet to ceļus.

<details>
<summary>Lokālā vide</summary>

Compose fails palaiž datubāzi. Lietotni palaiž atsevišķi ar Maven un Vite.

Noklonējiet repozitoriju un atveriet tā direktoriju:

```bash
git clone https://github.com/kenryhraval/tezaurs-suggestion-review.git
```

Turpmākās komandas izpildiet repozitorija direktorijā:

```bash
docker compose -f compose.local.yml up -d
```

Atsevišķos termināļos:

```bash
set -a && source .env && set +a && ./backend/mvnw -f backend/pom.xml spring-boot:run
```

```bash
npm --prefix frontend ci && npm --prefix frontend run dev
```
</details>

<details>
<summary>Testēšanas vide</summary>

Izvietošanai personīgā serverī. Compose fails palaiž backend, frontend, datubāzi un proxy. Pirms palaišanas iekopējiet [Caddyfile](Caddyfile).

```bash
docker compose -f compose.standalone.deploy.yml up -d --pull always
```

</details>

<details>
<summary>Produkcijas vide</summary>

Izvietošanai esošajā tēzaurs.lv infrastruktūrā. Compose palaiž backend un frontend konteinerus. Izmanto eksistējošo datubāzi un proxy.

```bash
docker compose -f compose.production.deploy.yml up -d --pull always
```
</details>

API dokumentācija: `/swagger-ui.html`.
