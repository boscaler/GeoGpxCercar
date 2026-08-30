# Geo GPX Finder

Aplicació Android que ajuda a trobar rutes GPX properes a un punt del mapa.

## Què fa

1. Des de l'app **OruxMaps**, es pot enviar una coordenada a **Geo GPX Finder** (mitjançant un enllaç `geo:`).
2. L'app rep aquesta coordenada i la mostra com a "centre" de cerca.
3. Es pot indicar un radi de cerca en quilòmetres (per defecte, 5 km).
4. En prémer "Cercar", l'app recorre la carpeta `/oruxmaps/tracklogs` (i totes les seves subcarpetes) del mòbil, buscant fitxers `.gpx`.
5. Per a cada fitxer GPX trobat, comprova si algun dels seus punts (traça, waypoint o ruta) queda dins del radi indicat respecte al centre.
6. Es mostra una llista dels fitxers GPX trobats, ordenats per distància.
7. En tocar un resultat de la llista, l'app l'obre directament a OruxMaps.

## Per què serveix

Quan es rep una coordenada (per exemple, d'un punt d'interès o d'una ubicació compartida), permet saber ràpidament si ja existeix alguna ruta GPX guardada que passi a prop d'aquell punt, sense haver de cercar manualment entre tots els tracklogs emmagatzemats.

## Requisits tècnics

- Android 11 o superior.
- Permís **"Gestionar tots els fitxers"** (MANAGE_EXTERNAL_STORAGE), necessari per poder llegir la carpeta `/oruxmaps/tracklogs`. L'app el sol·licita automàticament si no està concedit.
