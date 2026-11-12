# Ciclo

App Android personale di Sara per tracciare ciclo, pelle (rosacea e dermatite seborroica separate), umore e altro.
Sostituisce Clue: niente pubblicità, dati solo sul telefono.

- **Widget 4×2**: 🩸 ciclo (un tocco = livello), ⚡ dolori, 🌡️ pelle, 😊 umore, ➕ altro. I mini-menu si aprono sopra la home.
- **App**: giorno, calendario con previsioni, andamento (pelle per fase del ciclo, trigger, foto), riepilogo PDF per il medico.
- **Collegamento con Claude**: Claude scrive `inbox.json` sul ramo privato `inbox`; l'app lo scarica all'apertura.

## Installazione
Ogni modifica su `main` crea una nuova versione in **Releases**: scarica `Ciclo-vN.apk` dal telefono e installalo sopra la precedente
(stessa chiave di firma → i dati restano).

## Formato del collegamento (`inbox.json` sul ramo `inbox`)
```json
{ "batches": [ { "id": "unico", "mode": "merge|replace", "days": { "2026-11-12": { "period": ["medium"], "skin_symptoms": ["papules:2"] } } } ] }
```
Ogni pacchetto viene applicato una sola volta (per `id`). `replace` sostituisce le voci indicate per quel giorno (lista vuota = cancella).
Le chiavi delle voci e delle opzioni sono in `app/src/main/java/it/saranave/ciclo/data/Catalog.kt`.
