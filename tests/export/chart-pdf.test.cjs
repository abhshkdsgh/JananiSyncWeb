const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const context = vm.createContext({ window: {}, TextEncoder, Uint8Array });
vm.runInContext(fs.readFileSync('webApp/src/jsMain/resources/chart-export.js', 'utf8'), context);

test('PDF contains all pages and exact byte offsets for every object', () => {
    const image = Uint8Array.from([255, 216, 0, 128, 200, 255, 217]);
    const pdf = Buffer.from(context.createPdf([image, image]));
    const source = pdf.toString('latin1');
    assert.ok(source.startsWith('%PDF-1.4'));
    assert.match(source, /\/Count 2/);
    assert.equal((source.match(/\/Type \/Page\b/g) || []).length, 2);
    const startxref = Number(source.match(/startxref\n(\d+)/)[1]);
    assert.equal(source.slice(startxref, startxref + 4), 'xref');
    const entries = source.slice(startxref).split('\n').slice(3, 11);
    entries.forEach((entry, index) => {
        const offset = Number(entry.slice(0, 10));
        assert.ok(source.slice(offset).startsWith(`${index + 1} 0 obj\n`));
    });
    assert.equal(pdf.indexOf(image) >= 0, true);
    assert.match(source, /\/MediaBox \[0 0 595.28 841.89\]/);
});

test('blocked popup reports failure without rendering patient data', () => {
    context.window.open = () => null;
    assert.equal(context.window.jananiChartExport('[]', 'LCG'), false);
});
