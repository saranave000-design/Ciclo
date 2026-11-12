# Come lavoriamo su Ciclo (da leggere PRIMA di ogni aggiornamento)

0. NON partire a modificare l'app finché Sara non dà il via. Non creare una nuova versione a ogni suggerimento: salvarlo in `TODO.md` e chiedere se aggiornare subito.
1. Ogni suggerimento non fatto subito va scritto in `TODO.md`.
2. I dati sanitari di Sara NON vanno mai su `main`: solo sul ramo `inbox` (file `inbox.json`), che si può riscrivere/svuotare.
3. Quando Sara chiede di "segnare" qualcosa: aggiungere un pacchetto con id nuovo a `inbox.json` sul ramo `inbox`.
4. Se la build fallisce, leggere il ramo `log` (`build-log.txt`).
