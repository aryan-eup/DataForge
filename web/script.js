/**
 * DataForge — High-Performance Synthetic Test Data Generator & Analytics Platform
 * Multi-plot visual analytics engine and REST client (Pure Vanilla JS).
 */

// Global Application State
let activeJobId = null;
let activeSchema = null;
let currentHeaders = [];
let currentRows = [];
let filteredRows = [];
let activeStatsData = null;
let currentChartType = 'bar'; // 'bar', 'donut', 'area', 'box'
let schemasMetadata = {};

// DOM Elements
const schemaSelect = document.getElementById('schemaSelect');
const rowCountInput = document.getElementById('rowCount');
const columnCheckboxes = document.getElementById('columnCheckboxes');
const btnGenerate = document.getElementById('btnGenerate');
const progressContainer = document.getElementById('progressContainer');
const progressBar = document.getElementById('progressBar');
const progressStatus = document.getElementById('progressStatus');
const progressPercent = document.getElementById('progressPercent');

const resultsSection = document.getElementById('resultsSection');
const activeJobMeta = document.getElementById('activeJobMeta');
const btnCopyJson = document.getElementById('btnCopyJson');
const btnCopyCsv = document.getElementById('btnCopyCsv');
const btnExportCsv = document.getElementById('btnExportCsv');
const btnExportSql = document.getElementById('btnExportSql');
const tableSearch = document.getElementById('tableSearch');
const sortColSelect = document.getElementById('sortColSelect');
const btnSortAsc = document.getElementById('btnSortAsc');
const btnSortDesc = document.getElementById('btnSortDesc');
const statsColSelect = document.getElementById('statsColSelect');
const btnComputeStats = document.getElementById('btnComputeStats');
const btnCloseStats = document.getElementById('btnCloseStats');

const statsPanel = document.getElementById('statsPanel');
const statsTitle = document.getElementById('statsTitle');
const statTotal = document.getElementById('statTotal');
const statDistinct = document.getElementById('statDistinct');
const statDuplicates = document.getElementById('statDuplicates');
const statAverage = document.getElementById('statAverage');
const statMedian = document.getElementById('statMedian');
const statStdDev = document.getElementById('statStdDev');
const chartContainer = document.getElementById('chartContainer');
const tabBoxPlot = document.getElementById('tabBoxPlot');
const freqList = document.getElementById('freqList');

const tableHeader = document.getElementById('tableHeader');
const tableBody = document.getElementById('tableBody');
const tableSummary = document.getElementById('tableSummary');

const historyBody = document.getElementById('historyBody');
const btnRefreshHistory = document.getElementById('btnRefreshHistory');
const notification = document.getElementById('notification');

// Initialize on load
document.addEventListener('DOMContentLoaded', () => {
    initPresets();
    fetchSchemas();
    fetchHistory();

    schemaSelect.addEventListener('change', () => renderColumnCheckboxes(schemaSelect.value));
    btnGenerate.addEventListener('click', onGenerate);

    document.getElementById('btnSelectAll').addEventListener('click', () => setAllCheckboxes(true));
    document.getElementById('btnDeselectAll').addEventListener('click', () => setAllCheckboxes(false));

    btnCopyJson.addEventListener('click', copyJsonToClipboard);
    btnCopyCsv.addEventListener('click', copyCsvToClipboard);
    btnExportCsv.addEventListener('click', () => downloadExport('csv'));
    btnExportSql.addEventListener('click', () => downloadExport('sql'));

    tableSearch.addEventListener('input', onTableSearch);
    btnSortAsc.addEventListener('click', () => sortTable(true));
    btnSortDesc.addEventListener('click', () => sortTable(false));

    btnComputeStats.addEventListener('click', computeStats);
    btnCloseStats.addEventListener('click', () => statsPanel.classList.add('hidden'));

    // Chart Tabs
    document.querySelectorAll('.chart-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            document.querySelectorAll('.chart-tab').forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            currentChartType = tab.getAttribute('data-chart');
            if (activeStatsData) renderActiveChart();
        });
    });

    btnRefreshHistory.addEventListener('click', fetchHistory);
});

// Row presets buttons (50, 250, 1K, 5K)
function initPresets() {
    document.querySelectorAll('.btn-preset').forEach(btn => {
        btn.addEventListener('click', () => {
            rowCountInput.value = btn.getAttribute('data-rows');
        });
    });
}

function showNotification(message, isError = false) {
    notification.textContent = message;
    notification.className = 'notification ' + (isError ? 'error' : 'success');
    notification.classList.remove('hidden');
    setTimeout(() => {
        notification.classList.add('hidden');
    }, 4500);
}

// 1. Fetch available schemas
async function fetchSchemas() {
    try {
        const res = await fetch('/schemas');
        if (!res.ok) throw new Error('Failed to load schemas.');
        schemasMetadata = await res.json();
        renderColumnCheckboxes(schemaSelect.value);
    } catch (err) {
        console.warn('Fallback schema loaded:', err);
        schemasMetadata = {
            students: {
                name: 'students',
                fields: [
                    { name: 'id' }, { name: 'name' }, { name: 'email' },
                    { name: 'age' }, { name: 'phone' }, { name: 'city' },
                    { name: 'enrollment_date' }, { name: 'gpa' }
                ]
            },
            employees: {
                name: 'employees',
                fields: [
                    { name: 'id' }, { name: 'name' }, { name: 'email' },
                    { name: 'department' }, { name: 'salary' }, { name: 'phone' },
                    { name: 'city' }, { name: 'hire_date' }
                ]
            },
            products: {
                name: 'products',
                fields: [
                    { name: 'id' }, { name: 'product_name' }, { name: 'category' },
                    { name: 'price' }, { name: 'stock' }, { name: 'sku' },
                    { name: 'created_date' }
                ]
            }
        };
        renderColumnCheckboxes(schemaSelect.value);
    }
}

function renderColumnCheckboxes(schemaName) {
    columnCheckboxes.innerHTML = '';
    const schema = schemasMetadata[schemaName];
    if (!schema || !schema.fields) return;

    schema.fields.forEach(field => {
        const label = document.createElement('label');
        label.className = 'checkbox-label';

        const cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.value = field.name;
        cb.checked = true;

        label.appendChild(cb);
        label.appendChild(document.createTextNode(field.name));
        columnCheckboxes.appendChild(label);
    });
}

function setAllCheckboxes(checked) {
    columnCheckboxes.querySelectorAll('input[type="checkbox"]').forEach(cb => {
        cb.checked = checked;
    });
}

function getSelectedColumns() {
    const selected = [];
    columnCheckboxes.querySelectorAll('input[type="checkbox"]:checked').forEach(cb => {
        selected.push(cb.value);
    });
    return selected;
}

// 2. Generate Data with live progress polling
async function onGenerate() {
    const schema = schemaSelect.value;
    const rows = parseInt(rowCountInput.value, 10);
    const cols = getSelectedColumns();

    if (isNaN(rows) || rows < 1 || rows > 100000) {
        showNotification('Row count must be between 1 and 100,000.', true);
        return;
    }
    if (cols.length === 0) {
        showNotification('Please select at least one attribute column.', true);
        return;
    }

    btnGenerate.disabled = true;
    progressContainer.classList.remove('hidden');
    progressBar.style.width = '0%';
    progressPercent.textContent = '0%';
    progressStatus.textContent = `Partitioning ${rows} rows across 4 parallel threads...`;

    const pollInterval = setInterval(async () => {
        try {
            const pRes = await fetch('/progress');
            if (pRes.ok) {
                const pData = await pRes.json();
                progressBar.style.width = pData.percentage + '%';
                progressPercent.textContent = pData.percentage + '%';
                progressStatus.textContent = `Generated ${pData.current} of ${pData.total} rows...`;
            }
        } catch (ignored) {}
    }, 120);

    try {
        const url = `/generate?schema=${encodeURIComponent(schema)}&rows=${rows}&cols=${encodeURIComponent(cols.join(','))}`;
        const res = await fetch(url);
        clearInterval(pollInterval);

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.error || 'Data generation failed.');
        }

        const data = await res.json();
        progressBar.style.width = '100%';
        progressPercent.textContent = '100%';
        progressStatus.textContent = 'Dataset forged & stored in MySQL!';

        setTimeout(() => {
            progressContainer.classList.add('hidden');
        }, 1200);

        showNotification(`Success! Job #${data.jobId} created with ${data.rows.length} rows.`);

        activeJobId = data.jobId;
        activeSchema = data.schemaName;
        currentHeaders = data.header;
        currentRows = data.rows;
        filteredRows = currentRows;

        renderResultsTable(data.jobId, data.schemaName, data.header, data.rows);
        fetchHistory();

        // Automatically trigger stats on first available numeric or categorical column
        const defaultCol = data.header.find(h => ['age', 'salary', 'price', 'gpa', 'category', 'department'].includes(h.toLowerCase())) || data.header[0];
        statsColSelect.value = defaultCol;
        computeStats();

    } catch (err) {
        clearInterval(pollInterval);
        progressContainer.classList.add('hidden');
        showNotification(err.message, true);
    } finally {
        btnGenerate.disabled = false;
    }
}

// 3. Render Results Table
function renderResultsTable(jobId, schemaName, headers, rows) {
    resultsSection.classList.remove('hidden');

    activeJobMeta.textContent = `Job #${jobId} • Schema: ${schemaName.toUpperCase()} • ${rows.length} total records`;

    // Populate dropdowns
    sortColSelect.innerHTML = '';
    statsColSelect.innerHTML = '';
    headers.forEach(h => {
        const opt1 = document.createElement('option');
        opt1.value = h;
        opt1.textContent = h;
        sortColSelect.appendChild(opt1);

        const opt2 = document.createElement('option');
        opt2.value = h;
        opt2.textContent = h;
        statsColSelect.appendChild(opt2);
    });

    tableHeader.innerHTML = '<th class="row-index">#</th>';
    headers.forEach(h => {
        const th = document.createElement('th');
        th.textContent = h;
        tableHeader.appendChild(th);
    });

    renderTableRows(filteredRows);
}

function renderTableRows(rowsToDisplay) {
    tableBody.innerHTML = '';
    const renderLimit = Math.min(rowsToDisplay.length, 250);

    for (let i = 0; i < renderLimit; i++) {
        const tr = document.createElement('tr');
        const tdIdx = document.createElement('td');
        tdIdx.className = 'row-index';
        tdIdx.textContent = (i + 1);
        tr.appendChild(tdIdx);

        rowsToDisplay[i].forEach(cell => {
            const td = document.createElement('td');
            td.textContent = cell;
            tr.appendChild(td);
        });
        tableBody.appendChild(tr);
    }

    tableSummary.textContent = (rowsToDisplay.length > renderLimit)
        ? `Showing first ${renderLimit} of ${rowsToDisplay.length} rows (table search active). Full dataset safely stored in MySQL & export files.`
        : `Showing ${rowsToDisplay.length} matching rows.`;
}

// Instant table search
function onTableSearch() {
    const q = tableSearch.value.trim().toLowerCase();
    if (!q) {
        filteredRows = currentRows;
    } else {
        filteredRows = currentRows.filter(row =>
            row.some(val => val && val.toLowerCase().includes(q))
        );
    }
    renderTableRows(filteredRows);
}

// 4. Clipboard Operations
function copyJsonToClipboard() {
    if (!currentRows || currentRows.length === 0) return;
    const jsonObjects = currentRows.map(row => {
        const obj = {};
        currentHeaders.forEach((h, idx) => {
            obj[h] = row[idx];
        });
        return obj;
    });
    navigator.clipboard.writeText(JSON.stringify(jsonObjects, null, 2))
        .then(() => showNotification('Copied dataset to clipboard as JSON!'))
        .catch(() => showNotification('Failed to copy to clipboard', true));
}

function copyCsvToClipboard() {
    if (!currentRows || currentRows.length === 0) return;
    const csvContent = [
        currentHeaders.join(','),
        ...currentRows.map(r => r.map(c => `"${(c||'').replace(/"/g, '""')}"`).join(','))
    ].join('\n');
    navigator.clipboard.writeText(csvContent)
        .then(() => showNotification('Copied dataset to clipboard as CSV!'))
        .catch(() => showNotification('Failed to copy to clipboard', true));
}

// 5. Download Export
function downloadExport(format) {
    if (!activeJobId) {
        showNotification('No active dataset selected for export.', true);
        return;
    }
    window.location.href = `/export?id=${activeJobId}&format=${format}`;
}

// 6. Sort Table
async function sortTable(ascending) {
    if (!activeJobId) return;
    const col = sortColSelect.value;
    try {
        const res = await fetch(`/job?id=${activeJobId}&sort=${encodeURIComponent(col)}&asc=${ascending}`);
        if (!res.ok) throw new Error('Sorting failed.');
        const data = await res.json();
        currentRows = data.rows;
        tableSearch.value = '';
        filteredRows = currentRows;
        renderResultsTable(data.jobId, data.schemaName, data.header, data.rows);
        showNotification(`Dataset sorted by "${col}" (${ascending ? 'Ascending' : 'Descending'}).`);
    } catch (err) {
        showNotification(err.message, true);
    }
}

// 7. Compute & Render Multi-Chart Statistics
async function computeStats() {
    if (!activeJobId) return;
    const col = statsColSelect.value;

    try {
        const res = await fetch(`/stats?id=${activeJobId}&col=${encodeURIComponent(col)}`);
        if (!res.ok) throw new Error('Failed to compute analytics.');
        const stats = await res.json();
        activeStatsData = stats;

        statsTitle.textContent = `Statistical Breakdown: "${stats.column}"`;
        statTotal.textContent = stats.total.toLocaleString();
        statDistinct.textContent = stats.distinct.toLocaleString();
        statDuplicates.textContent = stats.hasDuplicates ? 'Detected' : 'None (Unique)';
        statAverage.textContent = stats.isNumeric ? stats.average : 'N/A (Text)';
        statMedian.textContent = (stats.isNumeric && stats.median !== null) ? stats.median : 'N/A';
        statStdDev.textContent = (stats.isNumeric && stats.stdDev !== null) ? stats.stdDev : 'N/A';

        // Toggle box plot tab availability based on data type
        if (!stats.isNumeric) {
            tabBoxPlot.style.display = 'none';
            if (currentChartType === 'box') {
                currentChartType = 'bar';
                document.querySelectorAll('.chart-tab').forEach(t => t.classList.toggle('active', t.getAttribute('data-chart') === 'bar'));
            }
        } else {
            tabBoxPlot.style.display = 'inline-block';
        }

        // Render top frequent items list
        freqList.innerHTML = '';
        if (stats.topFrequent && stats.topFrequent.length > 0) {
            stats.topFrequent.forEach(item => {
                const row = document.createElement('div');
                row.className = 'freq-row';
                row.innerHTML = `
                    <span class="freq-val">${escapeHtml(item.value)}</span>
                    <div class="freq-badge-group">
                        <span class="freq-count">${item.count.toLocaleString()} rows</span>
                        <span class="freq-pct">${item.percentage}%</span>
                    </div>
                `;
                freqList.appendChild(row);
            });
        }

        // Render graphical chart
        renderActiveChart();
        statsPanel.classList.remove('hidden');

    } catch (err) {
        showNotification(err.message, true);
    }
}

// -------------------------------------------------------------
// Interactive SVG Chart Plotting Engine
// -------------------------------------------------------------
function renderActiveChart() {
    if (!activeStatsData) return;
    chartContainer.innerHTML = '';

    switch (currentChartType) {
        case 'bar':
            renderBarChart(activeStatsData);
            break;
        case 'donut':
            renderDonutChart(activeStatsData);
            break;
        case 'area':
            renderAreaChart(activeStatsData);
            break;
        case 'box':
            renderBoxPlot(activeStatsData);
            break;
        default:
            renderBarChart(activeStatsData);
    }
}

// Chart 1: Interactive Bar Histogram
function renderBarChart(stats) {
    const isBinned = stats.isNumeric && stats.histogramBins && stats.histogramBins.length > 0;
    const items = isBinned
        ? stats.histogramBins.map(b => ({ label: b.label, count: b.count }))
        : stats.topFrequent.map(f => ({ label: f.value, count: f.count, pct: f.percentage }));

    if (items.length === 0) {
        chartContainer.innerHTML = '<p class="empty-state">No distribution data available to plot.</p>';
        return;
    }

    const svgWidth = 840;
    const svgHeight = 260;
    const padLeft = 70;
    const padRight = 30;
    const padTop = 30;
    const padBottom = 60;
    const plotWidth = svgWidth - padLeft - padRight;
    const plotHeight = svgHeight - padTop - padBottom;

    const maxCount = Math.max(...items.map(i => i.count), 1);
    const barWidth = Math.max(16, (plotWidth / items.length) * 0.65);
    const gap = plotWidth / items.length;

    let svg = `<svg class="svg-chart" viewBox="0 0 ${svgWidth} ${svgHeight}">
        <defs>
            <linearGradient id="forgeBarGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#ff7828" />
                <stop offset="100%" stop-color="#d85006" />
            </linearGradient>
            <linearGradient id="forgeBarHover" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#f59e0b" />
                <stop offset="100%" stop-color="#ff7828" />
            </linearGradient>
        </defs>`;

    // Grid lines & Y-axis ticks
    const yTicks = 4;
    for (let t = 0; t <= yTicks; t++) {
        const val = Math.round((maxCount / yTicks) * t);
        const yPos = padTop + plotHeight - (plotHeight * (t / yTicks));
        svg += `<line x1="${padLeft}" y1="${yPos}" x2="${svgWidth - padRight}" y2="${yPos}" class="chart-grid-line" />`;
        svg += `<text x="${padLeft - 10}" y="${yPos + 4}" class="chart-label" text-anchor="end">${val}</text>`;
    }

    // Baseline axis
    svg += `<line x1="${padLeft}" y1="${padTop + plotHeight}" x2="${svgWidth - padRight}" y2="${padTop + plotHeight}" class="chart-axis-line" />`;

    // Bars
    items.forEach((item, idx) => {
        const barHeight = Math.max(4, (item.count / maxCount) * plotHeight);
        const xPos = padLeft + (idx * gap) + (gap - barWidth) / 2;
        const yPos = padTop + plotHeight - barHeight;

        // Bar rect with hover tooltip
        svg += `<rect class="chart-bar-rect" x="${xPos}" y="${yPos}" width="${barWidth}" height="${barHeight}" rx="4" fill="url(#forgeBarGrad)">
            <title>${item.label}: ${item.count.toLocaleString()} occurrences</title>
        </rect>`;

        // Count label on top of bar
        svg += `<text x="${xPos + barWidth / 2}" y="${yPos - 6}" class="chart-val-label" text-anchor="middle">${item.count}</text>`;

        // X-axis Category Label (truncated if long)
        const displayLabel = item.label.length > 12 ? item.label.substring(0, 10) + '..' : item.label;
        svg += `<text x="${xPos + barWidth / 2}" y="${padTop + plotHeight + 18}" class="chart-label" text-anchor="middle">${escapeHtml(displayLabel)}</text>`;
    });

    svg += `</svg>`;
    chartContainer.innerHTML = svg;
}

// Chart 2: Interactive Donut Share Chart
function renderDonutChart(stats) {
    const items = stats.topFrequent;
    if (!items || items.length === 0) {
        chartContainer.innerHTML = '<p class="empty-state">No category shares to plot.</p>';
        return;
    }

    const svgSize = 300;
    const cx = 150;
    const cy = 150;
    const outerR = 110;
    const innerR = 65;

    const colors = [
        '#ff7828', '#f59e0b', '#38bdf8', '#10b981', '#a855f7',
        '#ec4899', '#3b82f6', '#14b8a6', '#f43f5e', '#8b5cf6'
    ];

    const total = items.reduce((sum, item) => sum + item.count, 0);
    let startAngle = 0;

    let paths = '';
    items.forEach((item, idx) => {
        const sliceAngle = (item.count / total) * 2 * Math.PI;
        const endAngle = startAngle + sliceAngle;

        const x1 = cx + outerR * Math.cos(startAngle);
        const y1 = cy + outerR * Math.sin(startAngle);
        const x2 = cx + outerR * Math.cos(endAngle);
        const y2 = cy + outerR * Math.sin(endAngle);

        const ix1 = cx + innerR * Math.cos(endAngle);
        const iy1 = cy + innerR * Math.sin(endAngle);
        const ix2 = cx + innerR * Math.cos(startAngle);
        const iy2 = cy + innerR * Math.sin(startAngle);

        const largeArc = sliceAngle > Math.PI ? 1 : 0;
        const color = colors[idx % colors.length];

        paths += `<path d="M ${x1} ${y1} A ${outerR} ${outerR} 0 ${largeArc} 1 ${x2} ${y2} L ${ix1} ${iy1} A ${innerR} ${innerR} 0 ${largeArc} 0 ${ix2} ${iy2} Z"
            fill="${color}" stroke="#101620" stroke-width="2" class="chart-bar-rect">
            <title>${item.value}: ${item.count} (${item.percentage}%)</title>
        </path>`;

        startAngle = endAngle;
    });

    let legendHtml = `<div style="display:flex; flex-direction:column; gap:6px; max-height:220px; overflow-y:auto;">`;
    items.forEach((item, idx) => {
        const color = colors[idx % colors.length];
        legendHtml += `
            <div style="display:flex; align-items:center; gap:8px; font-size:0.82rem;">
                <span style="width:12px; height:12px; border-radius:3px; background:${color};"></span>
                <span style="color:#f1f5f9; font-weight:500;">${escapeHtml(item.value)}</span>
                <span style="color:#94a3b8; margin-left:auto;">${item.percentage}%</span>
            </div>
        `;
    });
    legendHtml += `</div>`;

    chartContainer.innerHTML = `
        <div style="display:flex; flex-wrap:wrap; align-items:center; justify-content:center; gap:30px; padding:10px 0;">
            <svg width="${svgSize}" height="${svgSize}" viewBox="0 0 ${svgSize} ${svgSize}">
                ${paths}
                <circle cx="${cx}" cy="${cy}" r="${innerR}" fill="#101620" />
                <text x="${cx}" y="${cy - 4}" text-anchor="middle" fill="#f1f5f9" font-size="14" font-weight="700">SHARES</text>
                <text x="${cx}" y="${cy + 16}" text-anchor="middle" fill="#ff7828" font-size="11" font-weight="600">${items.length} Slices</text>
            </svg>
            <div style="flex:1; min-width:200px;">
                <h4 style="font-size:0.85rem; color:#94a3b8; margin-bottom:8px; text-transform:uppercase;">Category Proportions</h4>
                ${legendHtml}
            </div>
        </div>
    `;
}

// Chart 3: Smooth Density & Distribution Area Curve
function renderAreaChart(stats) {
    const isBinned = stats.isNumeric && stats.histogramBins && stats.histogramBins.length > 0;
    const items = isBinned
        ? stats.histogramBins.map(b => ({ label: b.label, count: b.count }))
        : stats.topFrequent.map(f => ({ label: f.value, count: f.count }));

    if (items.length < 2) {
        renderBarChart(stats);
        return;
    }

    const svgWidth = 840;
    const svgHeight = 260;
    const padLeft = 70;
    const padRight = 30;
    const padTop = 30;
    const padBottom = 60;
    const plotWidth = svgWidth - padLeft - padRight;
    const plotHeight = svgHeight - padTop - padBottom;

    const maxCount = Math.max(...items.map(i => i.count), 1);
    const step = plotWidth / (items.length - 1);

    const points = items.map((item, idx) => {
        const x = padLeft + (idx * step);
        const y = padTop + plotHeight - (item.count / maxCount) * plotHeight;
        return { x, y, item };
    });

    let linePath = `M ${points[0].x} ${points[0].y}`;
    for (let i = 1; i < points.length; i++) {
        linePath += ` L ${points[i].x} ${points[i].y}`;
    }

    const areaPath = `${linePath} L ${points[points.length - 1].x} ${padTop + plotHeight} L ${points[0].x} ${padTop + plotHeight} Z`;

    let svg = `<svg class="svg-chart" viewBox="0 0 ${svgWidth} ${svgHeight}">
        <defs>
            <linearGradient id="areaGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#ff7828" stop-opacity="0.45" />
                <stop offset="100%" stop-color="#ff7828" stop-opacity="0.02" />
            </linearGradient>
        </defs>`;

    // Grid lines
    for (let t = 0; t <= 4; t++) {
        const val = Math.round((maxCount / 4) * t);
        const yPos = padTop + plotHeight - (plotHeight * (t / 4));
        svg += `<line x1="${padLeft}" y1="${yPos}" x2="${svgWidth - padRight}" y2="${yPos}" class="chart-grid-line" />`;
        svg += `<text x="${padLeft - 10}" y="${yPos + 4}" class="chart-label" text-anchor="end">${val}</text>`;
    }
    svg += `<line x1="${padLeft}" y1="${padTop + plotHeight}" x2="${svgWidth - padRight}" y2="${padTop + plotHeight}" class="chart-axis-line" />`;

    // Area & Line
    svg += `<path d="${areaPath}" fill="url(#areaGrad)" />`;
    svg += `<path d="${linePath}" fill="none" stroke="#ff7828" stroke-width="3" stroke-linecap="round" />`;

    // Data points & Labels
    points.forEach(pt => {
        svg += `<circle cx="${pt.x}" cy="${pt.y}" r="4.5" fill="#f59e0b" stroke="#0a0e14" stroke-width="2">
            <title>${pt.item.label}: ${pt.item.count} records</title>
        </circle>`;
        const displayLabel = pt.item.label.length > 10 ? pt.item.label.substring(0, 8) + '..' : pt.item.label;
        svg += `<text x="${pt.x}" y="${padTop + plotHeight + 18}" class="chart-label" text-anchor="middle">${escapeHtml(displayLabel)}</text>`;
    });

    svg += `</svg>`;
    chartContainer.innerHTML = svg;
}

// Chart 4: Box & Whisker Plot for Numeric Distribution
function renderBoxPlot(stats) {
    if (!stats.isNumeric || stats.min === null || stats.max === null) {
        chartContainer.innerHTML = '<p class="empty-state">Box & Whisker is only available for numerical attributes.</p>';
        return;
    }

    const min = parseFloat(stats.min);
    const max = parseFloat(stats.max);
    const q1 = parseFloat(stats.q1 || min);
    const median = parseFloat(stats.median || (min + max) / 2);
    const q3 = parseFloat(stats.q3 || max);
    const mean = parseFloat(stats.average || median);

    const svgWidth = 840;
    const svgHeight = 220;
    const padLeft = 90;
    const padRight = 60;
    const cy = 100;
    const boxHeight = 50;
    const plotWidth = svgWidth - padLeft - padRight;
    const range = Math.max(1, max - min);

    const toX = (val) => padLeft + ((val - min) / range) * plotWidth;

    const xMin = toX(min);
    const xQ1 = toX(q1);
    const xMed = toX(median);
    const xQ3 = toX(q3);
    const xMax = toX(max);
    const xMean = toX(mean);

    let svg = `<svg class="svg-chart" viewBox="0 0 ${svgWidth} ${svgHeight}">
        <!-- Whiskers -->
        <line x1="${xMin}" y1="${cy}" x2="${xQ1}" y2="${cy}" stroke="#94a3b8" stroke-width="2" />
        <line x1="${xQ3}" y1="${cy}" x2="${xMax}" y2="${cy}" stroke="#94a3b8" stroke-width="2" />

        <!-- Min & Max vertical end bars -->
        <line x1="${xMin}" y1="${cy - 16}" x2="${xMin}" y2="${cy + 16}" stroke="#94a3b8" stroke-width="2" />
        <line x1="${xMax}" y1="${cy - 16}" x2="${xMax}" y2="${cy + 16}" stroke="#94a3b8" stroke-width="2" />

        <!-- Interquartile Range (IQR) Box -->
        <rect x="${xQ1}" y="${cy - boxHeight/2}" width="${Math.max(4, xQ3 - xQ1)}" height="${boxHeight}"
            rx="6" fill="rgba(255, 120, 40, 0.25)" stroke="#ff7828" stroke-width="2" />

        <!-- Median Line -->
        <line x1="${xMed}" y1="${cy - boxHeight/2}" x2="${xMed}" y2="${cy + boxHeight/2}" stroke="#f59e0b" stroke-width="3" />

        <!-- Mean Point Indicator (Diamond) -->
        <polygon points="${xMean},${cy - 8} ${xMean + 7},${cy} ${xMean},${cy + 8} ${xMean - 7},${cy}" fill="#38bdf8" />

        <!-- Metrics labels below axis -->
        <text x="${xMin}" y="${cy + 36}" class="chart-val-label" text-anchor="middle">Min: ${min}</text>
        <text x="${xQ1}" y="${cy + 36}" class="chart-val-label" text-anchor="middle">Q1: ${q1}</text>
        <text x="${xMed}" y="${cy - 34}" class="chart-val-label" text-anchor="middle" fill="#f59e0b">Median: ${median}</text>
        <text x="${xQ3}" y="${cy + 36}" class="chart-val-label" text-anchor="middle">Q3: ${q3}</text>
        <text x="${xMax}" y="${cy + 36}" class="chart-val-label" text-anchor="middle">Max: ${max}</text>

        <!-- Legend / Info -->
        <text x="${padLeft}" y="30" class="chart-label" fill="#94a3b8">
            Distribution Range: ${min} to ${max} (IQR: ${(q3 - q1).toFixed(1)}) | Mean: ${mean.toFixed(2)}
        </text>
    </svg>`;

    chartContainer.innerHTML = svg;
}

// -------------------------------------------------------------
// History Management (View, Rename, Delete)
// -------------------------------------------------------------
async function fetchHistory() {
    try {
        const res = await fetch('/history');
        if (!res.ok) throw new Error('Failed to load history.');
        const history = await res.json();

        historyBody.innerHTML = '';
        if (history.length === 0) {
            historyBody.innerHTML = '<tr><td colspan="5" class="empty-state">No past generation jobs logged in MySQL.</td></tr>';
            return;
        }

        history.forEach(job => {
            const tr = document.createElement('tr');

            const tdId = document.createElement('td');
            tdId.textContent = `#${job.id}`;
            tr.appendChild(tdId);

            const tdSchema = document.createElement('td');
            tdSchema.textContent = job.schemaName;
            tr.appendChild(tdSchema);

            const tdRows = document.createElement('td');
            tdRows.textContent = job.rowCount.toLocaleString();
            tr.appendChild(tdRows);

            const tdDate = document.createElement('td');
            tdDate.textContent = job.createdAt || '-';
            tr.appendChild(tdDate);

            const tdActions = document.createElement('td');
            tdActions.className = 'history-actions';

            const btnView = document.createElement('button');
            btnView.className = 'btn-sm';
            btnView.textContent = '👁 View';
            btnView.addEventListener('click', () => viewJob(job.id));

            const btnRename = document.createElement('button');
            btnRename.className = 'btn-sm';
            btnRename.textContent = '✏️ Rename';
            btnRename.addEventListener('click', () => renameJob(job.id, job.schemaName));

            const btnDelete = document.createElement('button');
            btnDelete.className = 'btn-sm btn-danger';
            btnDelete.textContent = '🗑 Delete';
            btnDelete.addEventListener('click', () => deleteJob(job.id));

            tdActions.appendChild(btnView);
            tdActions.appendChild(btnRename);
            tdActions.appendChild(btnDelete);
            tr.appendChild(tdActions);

            historyBody.appendChild(tr);
        });

    } catch (err) {
        historyBody.innerHTML = `<tr><td colspan="5" class="empty-state">Failed to load history: ${err.message}</td></tr>`;
    }
}

async function viewJob(jobId) {
    try {
        const res = await fetch(`/job?id=${jobId}`);
        if (!res.ok) throw new Error('Failed to load job details.');
        const data = await res.json();

        activeJobId = data.jobId;
        activeSchema = data.schemaName;
        currentHeaders = data.header;
        currentRows = data.rows;
        tableSearch.value = '';
        filteredRows = currentRows;

        renderResultsTable(data.jobId, data.schemaName, data.header, data.rows);
        showNotification(`Dataset Job #${data.jobId} loaded.`);
        resultsSection.scrollIntoView({ behavior: 'smooth' });

        statsColSelect.value = data.header[0];
        computeStats();
    } catch (err) {
        showNotification(err.message, true);
    }
}

async function renameJob(jobId, currentName) {
    const newName = prompt(`Enter new label for Job #${jobId}:`, currentName);
    if (!newName || newName.trim() === '' || newName.trim() === currentName) return;

    try {
        const res = await fetch(`/job?id=${jobId}&name=${encodeURIComponent(newName.trim())}`, {
            method: 'PUT'
        });
        if (!res.ok) throw new Error('Failed to rename job.');
        showNotification(`Job #${jobId} renamed to "${newName.trim()}".`);
        fetchHistory();
        if (activeJobId === jobId) {
            activeJobMeta.textContent = `Job #${jobId} • Schema: ${newName.trim().toUpperCase()} • ${currentRows.length} total records`;
        }
    } catch (err) {
        showNotification(err.message, true);
    }
}

async function deleteJob(jobId) {
    if (!confirm(`Are you sure you want to delete Job #${jobId} and its associated rows from MySQL?`)) return;

    try {
        const res = await fetch(`/job?id=${jobId}`, {
            method: 'DELETE'
        });
        if (!res.ok) throw new Error('Failed to delete job.');
        showNotification(`Job #${jobId} deleted successfully.`);
        fetchHistory();
        if (activeJobId === jobId) {
            resultsSection.classList.add('hidden');
            activeJobId = null;
        }
    } catch (err) {
        showNotification(err.message, true);
    }
}

function escapeHtml(text) {
    if (!text) return '';
    return text.toString()
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
