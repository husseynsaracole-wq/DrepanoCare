# DrepanoCare – projet Android

Projet Android natif en Kotlin + Jetpack Compose.

## Fonctions incluses
- Ajout, modification et suppression de médicaments
- Plusieurs horaires par médicament
- Cases à cocher pour enregistrer les prises
- Notifications de rappel quotidiennes Android
- Suivi du moral, de la douleur, de sa localisation, du stress et des notes
- Sauvegarde locale sur le téléphone et restauration des rappels après redémarrage

## Compiler l'APK
1. Installe Android Studio (version récente) sur un ordinateur.
2. Décompresse le dossier `DrepanoCare-Android`.
3. Dans Android Studio, choisis **Open** et sélectionne ce dossier.
4. Attends la synchronisation Gradle et l'installation des composants SDK Android 35.
5. Menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
6. L'APK sera généré dans `app/build/outputs/apk/debug/app-debug.apk`.

Pour une version de test, l'APK debug suffit. Pour distribuer l'application, il faudra créer une version signée depuis **Build > Generate Signed Bundle / APK**.

## À savoir
- Le projet n'est pas un dispositif médical.
- Les notifications doivent être autorisées dans Android.
- Android peut différer les alarmes selon les réglages d'économie de batterie. Les rappels sont configurés en répétition quotidienne approximative (`setInexactRepeating`) pour limiter les exigences d'accès aux alarmes exactes.
- Les bilans et traitements sont enregistrés localement dans les préférences privées de l'application. Il n'y a pas de compte ni de synchronisation cloud.
- Ne saisis que les traitements et horaires prescrits par ton équipe médicale.
- Vérifie le fonctionnement des notifications sur ton appareil avant de compter dessus pour une prise.

## Compilation sans ordinateur (GitHub Actions)
Le dossier `.github/workflows/android-apk.yml` permet une compilation dans le cloud.
Il faut déposer le contenu du projet dans un dépôt GitHub, puis ouvrir l'onglet **Actions** et lancer **Build DrepanoCare APK** (Run workflow). À la fin, télécharger l'artefact **DrepanoCare-debug-apk** depuis le résumé du workflow. Décompresser l'artefact ZIP pour obtenir `app-debug.apk`.
Sur téléphone, l'interface GitHub peut être plus facile en activant « Version pour ordinateur » dans Chrome.
