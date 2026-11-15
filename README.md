# Rotation 180

Petite application Android qui retourne l'écran à 180° **uniquement** quand le téléphone est tenu tête en bas. Le reste du temps, elle ne fait rien : la rotation automatique d'origine d'Android (portrait / paysage) n'est pas modifiée.

*A tiny Android app that flips the screen upside down (180°) only while the phone is held upside down. It never touches the stock auto-rotation. The interface is in French.*

## Pourquoi

Beaucoup de téléphones refusent le portrait inversé. Les applis de rotation existantes prennent en général la main sur toutes les orientations, y compris le 90°. Celle-ci n'ajoute que le 180° manquant, pratique par exemple quand le téléphone est en charge et posé à l'envers sur sa tranche.

## Fonctionnement

- Un service en avant-plan lit l'accéléromètre (capteur coupé quand l'écran est éteint).
- Quand le téléphone est tête en bas, à ±40° près, l'appli pose une surcouche invisible de 1 pixel qui demande le portrait inversé.
- Quand il est redressé, la surcouche est retirée et Android reprend la main.
- Téléphone à plat : l'état en cours est conservé.
- Aucun réglage système n'est modifié. Si l'appli est arrêtée, l'écran revient de lui-même à son comportement normal.

## Fonctions

- Tolérance d'angle (±20° à ±70°) et délai avant bascule (100 à 1500 ms) réglables.
- Tuile dans les réglages rapides pour activer ou désactiver.
- Redémarrage automatique après un redémarrage du téléphone (compter une à deux minutes après l'allumage).
- Aucune connexion réseau, aucune collecte de données.

## Installation

1. Télécharger l'APK depuis la page [Releases](../../releases) et l'installer.
2. Ouvrir l'appli et accorder **« Afficher par-dessus d'autres applis »**.
3. Activer l'interrupteur **« Rotation 180° active »** et accepter la notification.
4. Pour que le service ne soit pas fermé par le système, autoriser l'activité en arrière-plan. Sur OnePlus / OxygenOS : fiche de l'appli → Utilisation de la batterie → « Autoriser l'activité en arrière-plan ».

Android 12 minimum. Testée sur un OnePlus Nord 5 sous Android 16.

## Limites connues

Android masque les surcouches des applis tierces sur certains écrans sensibles. L'écran ne se retourne donc pas :

- dans l'appli Paramètres ;
- sur les fenêtres de demande d'autorisation ;
- dans les applis qui bloquent les surcouches (certaines applis bancaires).

Tête en bas, tout est affiché en portrait inversé, y compris une appli qui demandait le paysage.

## Compilation

JDK 17 et SDK Android (plateforme 35) requis.

```
./gradlew assembleRelease
```

Sans clé de signature configurée, l'APK est signé avec la clé de débogage. Pour utiliser sa propre clé, ajouter dans `local.properties` :

```
rotation180.signing=/chemin/vers/signature.properties
```

avec, dans ce fichier, `storeFile`, `storePassword`, `keyAlias` et `keyPassword`.

## Licence

MIT, voir [LICENSE](LICENSE).
