/* Local-only chart rendering. No patient data is sent to a PDF service. */
window.jananiChartExport = function (serializedPages, title) {
    const preview = window.open('', '_blank');
    if (!preview) return false;
    preview.opener = null;
    const doc = preview.document;
    doc.title = title;
    const style = doc.createElement('style');
    style.textContent = `@page {size:A4 portrait;margin:0} body{margin:0;background:#ddd;font:16px Arial} nav{padding:16px;background:white;position:sticky;top:0} button{padding:10px;margin-right:12px} .page{display:block;width:210mm;height:297mm;margin:12px auto;background:white} @media print{nav{display:none}.page{margin:0;break-after:page}.page:last-child{break-after:auto}body{background:white}}`;
    doc.head.append(style);
    const nav = doc.createElement('nav');
    const download = doc.createElement('button'); download.textContent = 'Download PDF';
    const print = doc.createElement('button'); print.textContent = 'Print / Save as PDF';
    const status = doc.createElement('span'); status.textContent = 'Preparing pages…';
    nav.append(download, print, status); doc.body.append(nav);
    download.disabled = print.disabled = true;
    const urls = [];
    const images = JSON.parse(serializedPages).map(svg => {
        const url = URL.createObjectURL(new Blob([svg], {type:'image/svg+xml;charset=utf-8'}));
        urls.push(url);
        const img = doc.createElement('img'); img.className = 'page'; img.alt = title;
        const ready = new Promise((resolve, reject) => {img.onload = resolve; img.onerror = reject;});
        img.src = url; doc.body.append(img);
        return {img, ready};
    });
    preview.addEventListener('pagehide', () => urls.forEach(url => URL.revokeObjectURL(url)), {once:true});
    print.onclick = () => preview.print();
    download.onclick = async () => {
        download.disabled = true; status.textContent = 'Creating PDF…';
        try {
            // Rasterise at approximately 288 dpi; Unicode and graph symbols retain browser font shaping.
            const pictures = images.map(({img}) => {
                const canvas = doc.createElement('canvas'); canvas.width = 2380; canvas.height = 3368;
                const ctx = canvas.getContext('2d'); if (!ctx) throw Error('Canvas unavailable');
                ctx.fillStyle = 'white'; ctx.fillRect(0, 0, canvas.width, canvas.height);
                ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
                const raw = atob(canvas.toDataURL('image/jpeg', 0.96).split(',')[1]);
                return Uint8Array.from(raw, c => c.charCodeAt(0));
            });
            const pdf = createPdf(pictures);
            const url = URL.createObjectURL(new Blob([pdf], {type:'application/pdf'}));
            const a = doc.createElement('a'); a.href = url;
            a.download = title.replace(/[^a-z0-9 -]/gi, '').replace(/ +/g, '-') + '.pdf';
            doc.body.append(a); a.click(); a.remove();
            preview.setTimeout(() => URL.revokeObjectURL(url), 60000);
            status.textContent = 'PDF downloaded.';
        } catch (error) {
            status.textContent = 'PDF download failed. Use Print / Save as PDF, or retry.';
        } finally { download.disabled = false; }
    };
    Promise.all(images.map(p => p.ready)).then(() => {
        download.disabled = print.disabled = false;
        status.textContent = 'A4 portrait · saved observations. Close this window when finished.';
    }).catch(() => {status.textContent = 'Unable to render pages. Close this window and retry.';});
    return true;
};

function createPdf(images) {
    const encoder = new TextEncoder();
    const chunks = []; const offsets = [0]; let length = 0;
    const write = value => {const bytes = typeof value === 'string' ? encoder.encode(value) : value; chunks.push(bytes); length += bytes.length;};
    const object = (id, content) => {offsets[id] = length; write(`${id} 0 obj\n${content}\nendobj\n`);};
    write('%PDF-1.4\n');
    object(1, '<< /Type /Catalog /Pages 2 0 R >>');
    object(2, `<< /Type /Pages /Count ${images.length} /Kids [${images.map((_, i) => `${3 + i * 3} 0 R`).join(' ')}] >>`);
    images.forEach((bytes, i) => {
        const id = 3 + i * 3;
        object(id, `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595.28 841.89] /Resources << /XObject << /Im ${id + 1} 0 R >> >> /Contents ${id + 2} 0 R >>`);
        offsets[id + 1] = length;
        write(`${id + 1} 0 obj\n<< /Type /XObject /Subtype /Image /Width 2380 /Height 3368 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${bytes.length} >>\nstream\n`);
        write(bytes); write('\nendstream\nendobj\n');
        const content = 'q\n595.28 0 0 841.89 0 0 cm\n/Im Do\nQ\n';
        object(id + 2, `<< /Length ${encoder.encode(content).length} >>\nstream\n${content}endstream`);
    });
    const xref = length;
    write(`xref\n0 ${offsets.length}\n0000000000 65535 f \n`);
    for (let i = 1; i < offsets.length; i++) write(`${String(offsets[i]).padStart(10, '0')} 00000 n \n`);
    write(`trailer\n<< /Size ${offsets.length} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`);
    const result = new Uint8Array(length); let at = 0;
    for (const chunk of chunks) {result.set(chunk, at); at += chunk.length;}
    return result;
}
