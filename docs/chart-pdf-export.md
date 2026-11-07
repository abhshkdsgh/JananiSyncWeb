# Clinical chart PDF export

Open an admission's Labour Care Guide or Partograph and select **Export PDF / Print**. Allow the preview window if the browser blocks pop-ups. Choose **Download PDF** for a file, or **Print / Save as PDF** for the browser print dialog. Use A4 portrait; disable browser headers/footers when printing. Settings also describes where to find these actions.

The export recreates the supplied paper layouts from saved chart observations, independently of the app's appearance settings. The LCG includes support, baby and maternal observations, progress grids, medication and shared decisions, with first/second-stage labels. The modified WHO partograph includes FHR, fluid/moulding, cervical dilation/descent, alert/action lines, duration-patterned contractions, medication, pulse/BP, temperature and urine lanes. Missing readings remain blank. A question mark denotes a contraction count recorded without a duration category. Long cell text is retained in numbered continuation notes. LCG charts paginate every 12 columns; partographs continue in 12-hour windows.

Rendering and PDF creation happen locally in the browser; no external PDF service receives the records. Export is disabled while records are loading, locked, saving, or reporting a feed error. Unsaved entry previews are excluded. The PDF uses high-resolution page images to preserve Unicode text and graph symbols; its text is not selectable. Native browser printing retains the SVG rendering. The renderer follows the supplied layouts, but is not an official WHO-generated document.

Contractions use vector dot, diagonal-stripe, or solid shading in each recorded box for both half-hours. Explicitly cleared readings stay blank. Second-half readings crossing a 12-hour boundary appear on the next chart page, including graphs and text lanes.

Validation:

```
node --test tests/export/chart-pdf.test.cjs
CHROME_BIN='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' ./gradlew :shared:jsBrowserTest :shared:compileKotlinWasmJs :webApp:jsProcessResources --offline -Pkotlin.incremental.js.ir=false
```
