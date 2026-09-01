# Stari GUI

Ove klase su originalni GUI i njihov sadržaj nije mijenjan. Ostaju u default Java packageu kako bi nastavile raditi s postojećim blockchain klasama.

Iz root foldera projekta pokretanje je:

```powershell
$sources = (Get-ChildItem blockchain -Recurse -File -Filter *.java).FullName
javac -encoding UTF-8 -d tmp/gui-build $sources
java -cp tmp/gui-build GUI_Main
```
