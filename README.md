# Geo GPX Cercar

Aplicació Android que ajuda a trobar rutes GPX properes a un punt del mapa.

## Què fa

1. Des de l'app **OruxMaps**, es pot enviar una coordenada a **Geo GPX Cercar** ("Compartir mapa", "Posició central mapa", "geo: Intent").
2. L'app rep aquesta coordenada i la mostra com a "centre" de cerca.
3. Es pot indicar un radi de cerca en quilòmetres (per defecte, 5 km).
4. En prémer "Cercar", l'app recorre la carpeta `/oruxmaps/tracklogs` (i totes les seves subcarpetes) del mòbil, buscant fitxers `.gpx`.
5. Per a cada fitxer GPX trobat, comprova si algun dels seus punts (traça, waypoint o ruta) queda dins del radi indicat respecte al centre.
6. Es mostra una llista dels fitxers GPX trobats, amb caselles de selecció, ordenats per distància o per la data real de la ruta (segons el botó "Ordenar").
7. Es poden marcar diverses rutes (o prémer "Seleccionar totes") i copiar-les a una carpeta especial `_seleccionades`; l'app passa automàticament a segon pla perquè OruxMaps quedi en primer pla i es puguin importar totes de cop.
8. El botó "Eliminar seleccionades" buida aquesta carpeta especial (no afecta les rutes originals).
9. Un botó "?" a la capçalera mostra una ajuda ràpida amb aquests mateixos passos, directament dins l'app.

## Per què serveix

Quan es rep una coordenada (per exemple, d'un punt d'interès o d'una ubicació compartida), permet saber ràpidament quines rutes GPX ja guardades passen a prop d'aquell punt, seleccionar-ne diverses de cop i importar-les totes juntes a OruxMaps, sense haver de cercar-les manualment entre tots els tracklogs emmagatzemats.

## Requisits tècnics

- Android 11 o superior.
- Permís **"Gestionar tots els fitxers"** (MANAGE_EXTERNAL_STORAGE), necessari per poder llegir la carpeta `/oruxmaps/tracklogs`. L'app el sol·licita automàticament si no està concedit.
- La pantalla es manté fixa en vertical.

## Versió

v.1.01 — 01/09/2026
