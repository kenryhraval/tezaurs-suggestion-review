# Tēzaura ieteikumu perēklis

Darba vide _Tēzaura_ lietotāju ieteikumu izskatīšanai: pirmapstrāde, dublikātu
apvienošana, pārbaude Tēzaurā, lietojuma izpēte korpusos un šķirkļu vai nozīmju
melnrakstu sagatavošana. Katram perēklim ir sava kopīgojama saite.

Aizmugures sistēma veidota ar Java un Spring Boot, bet lietotāja saskarne – ar React un TypeScript.
Dati glabājas PostgreSQL. 

## Palaišana

```bash
docker pull ghcr.io/kenryhraval/tezaurs-suggestion-review-backend:latest
docker pull ghcr.io/kenryhraval/tezaurs-suggestion-review-frontend:latest
```

Lietotne paredz, ka vienā PostgreSQL datubāzē atrodas _Tēzaura_ `dict` un
`incubator` shēmas. Datubāzes struktūra pieejama
[_Tēzaura_ platformas repozitorijā](https://github.com/LUMII-AILab/Tezaurs_platform).
`review` shēmu izveido lietotnes migrācija.

Servera puses konteineram jānorāda vides mainīgie `DATABASE_URL`
(JDBC savienojuma adrese), `DATABASE_USERNAME` un `DATABASE_PASSWORD`.
Servera puse klausās portā 8080, lietotāja saskarne – portā 80.
Saskarnes konteiners pārsūta API pieprasījumus uz `backend:8080`, tāpēc abiem
konteineriem jāatrodas kopīgā tīklā un servera pusei jābūt sasniedzamai ar nosaukumu `backend`.

Autentifikāciju nodrošina izvietošanas vides reversais starpniekserveris,
kas pārsūta `Authorization` galveni; lietotne pati paroles nepārbauda.
API dokumentācija pieejama ceļā `/swagger-ui.html`.
