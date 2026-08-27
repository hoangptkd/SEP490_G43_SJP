const DEFAULT_COLUMN_WIDTHS = [36, 140, 100, 140, 150, 180, 80, 130, 200, 60];

function escapeXml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

export function sanitizeFileName(name: string): string {
  return name.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '').replace(/\s+/g, ' ').trim().slice(0, 80) || 'export';
}

function sanitizeSheetName(name: string): string {
  return name.replace(/[:\\/?*[\]]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 31) || 'Ung vien';
}

function statusStyleId(statusLabel: string): string {
  const label = statusLabel.toLowerCase();
  if (label.includes('trúng tuyển')) return 'StatusAccepted';
  if (label.includes('mới nộp')) return 'StatusNew';
  if (label.includes('chờ') || label.includes('xử lý')) return 'StatusPending';
  if (label.includes('rút gọn')) return 'StatusShortlisted';
  if (label.includes('từ chối')) return 'StatusRejected';
  return 'Data';
}

function cellXml(value: string | number | null | undefined, styleId: string): string {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return `<Cell ss:StyleID="${styleId}"><Data ss:Type="Number">${value}</Data></Cell>`;
  }
  return `<Cell ss:StyleID="${styleId}"><Data ss:Type="String">${escapeXml(value == null ? '' : String(value))}</Data></Cell>`;
}

export function buildExcelXml(options: {
  sheetName: string;
  title: string;
  headers: string[];
  rows: Array<Array<string | number | null | undefined>>;
  columnWidths?: number[];
}): string {
  const { sheetName, title, headers, rows, columnWidths = DEFAULT_COLUMN_WIDTHS } = options;
  const colCount = headers.length;
  const columns = headers
    .map((_, index) => `<Column ss:Index="${index + 1}" ss:Width="${columnWidths[index] ?? 100}"/>`)
    .join('');

  const headerCells = headers.map((header) => cellXml(header, 'Header')).join('');
  const dataRows = rows.map((row, rowIndex) => {
    const rowStyle = rowIndex % 2 === 0 ? 'Data' : 'Alt';
    const cells = row.map((value, colIndex) => {
      if (colIndex === 7) return cellXml(value, statusStyleId(String(value ?? '')));
      if (colIndex === 0 || colIndex === 9) return cellXml(value, `${rowStyle}Center`);
      return cellXml(value, rowStyle);
    }).join('');
    return `<Row ss:Height="20">${cells}</Row>`;
  }).join('');

  return `<?xml version="1.0" encoding="UTF-8"?>
<?mso-application progid="Excel.Sheet"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:o="urn:schemas-microsoft-com:office:office"
 xmlns:x="urn:schemas-microsoft-com:office:excel"
 xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:html="http://www.w3.org/TR/REC-html40">
 <Styles>
  <Style ss:ID="Title">
   <Font ss:Bold="1" ss:Size="14" ss:Color="#064E3B"/>
   <Alignment ss:Vertical="Center" ss:WrapText="1"/>
  </Style>
  <Style ss:ID="Header">
   <Font ss:Bold="1" ss:Color="#FFFFFF" ss:Size="11"/>
   <Interior ss:Color="#047857" ss:Pattern="Solid"/>
   <Alignment ss:Horizontal="Center" ss:Vertical="Center" ss:WrapText="1"/>
   <Borders>
    <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#065F46"/>
   </Borders>
  </Style>
  <Style ss:ID="Data">
   <Font ss:Size="11" ss:Color="#0F172A"/>
   <Alignment ss:Vertical="Center" ss:WrapText="1"/>
   <Borders>
    <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#E2E8F0"/>
   </Borders>
  </Style>
  <Style ss:ID="Alt">
   <Font ss:Size="11" ss:Color="#0F172A"/>
   <Interior ss:Color="#F8FAFC" ss:Pattern="Solid"/>
   <Alignment ss:Vertical="Center" ss:WrapText="1"/>
   <Borders>
    <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#E2E8F0"/>
   </Borders>
  </Style>
  <Style ss:ID="DataCenter">
   <Font ss:Size="11" ss:Color="#0F172A"/>
   <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
   <Borders>
    <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#E2E8F0"/>
   </Borders>
  </Style>
  <Style ss:ID="AltCenter">
   <Font ss:Size="11" ss:Color="#0F172A"/>
   <Interior ss:Color="#F8FAFC" ss:Pattern="Solid"/>
   <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
   <Borders>
    <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#E2E8F0"/>
   </Borders>
  </Style>
  <Style ss:ID="StatusAccepted">
   <Font ss:Bold="1" ss:Size="11" ss:Color="#047857"/>
   <Interior ss:Color="#D1FAE5" ss:Pattern="Solid"/>
   <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
  </Style>
  <Style ss:ID="StatusNew">
   <Font ss:Bold="1" ss:Size="11" ss:Color="#1D4ED8"/>
   <Interior ss:Color="#DBEAFE" ss:Pattern="Solid"/>
   <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
  </Style>
  <Style ss:ID="StatusPending">
   <Font ss:Bold="1" ss:Size="11" ss:Color="#B45309"/>
   <Interior ss:Color="#FEF3C7" ss:Pattern="Solid"/>
   <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
  </Style>
  <Style ss:ID="StatusShortlisted">
   <Font ss:Bold="1" ss:Size="11" ss:Color="#6D28D9"/>
   <Interior ss:Color="#EDE9FE" ss:Pattern="Solid"/>
   <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
  </Style>
  <Style ss:ID="StatusRejected">
   <Font ss:Bold="1" ss:Size="11" ss:Color="#B91C1C"/>
   <Interior ss:Color="#FEE2E2" ss:Pattern="Solid"/>
   <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
  </Style>
 </Styles>
 <Worksheet ss:Name="${escapeXml(sanitizeSheetName(sheetName))}">
  <Table ss:ExpandedColumnCount="${colCount}" ss:ExpandedRowCount="${rows.length + 2}">
   ${columns}
   <Row ss:Height="28">
    <Cell ss:MergeAcross="${Math.max(colCount - 1, 0)}" ss:StyleID="Title"><Data ss:Type="String">${escapeXml(title)}</Data></Cell>
   </Row>
   <Row ss:Height="24">${headerCells}</Row>
   ${dataRows}
  </Table>
  <WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel">
   <FreezePanes/>
   <FrozenNoSplit/>
   <SplitHorizontal>2</SplitHorizontal>
   <TopRowBottomPane>2</TopRowBottomPane>
   <ActivePane>2</ActivePane>
  </WorksheetOptions>
 </Worksheet>
</Workbook>`;
}

export function downloadExcelFile(xml: string, fileName: string): void {
  const blob = new Blob([xml], { type: 'application/vnd.ms-excel;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName.endsWith('.xls') ? fileName : `${fileName}.xls`;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
