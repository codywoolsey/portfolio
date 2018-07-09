// Main control script of Media Server Tracking Google Sheet.


// Global Settings

// The total number of columns with actual data on the spreadsheet.
var NUMBER_OF_COLUMNS = 9;
// Holds constant values for important columns
var COLUMN_NUMBERS = {
  'DATE_COLUMN': 9,
  'PRIORITY_COLUMN': 7,
  'STATUS_COLUMN': 3
};
// Controls the rate at which entries in the date column will darken.
// A higher number means slower darkening.
var FADE_RATE = .15;
// The target color for background fade effect
var RGB_FADE_TARGET = {
  'red': 0,
  'blue': 0,
  'green': 0
};
var RGB_FADE_START = {
  'red': 255,
  'blue': 255,
  'green': 255
};

//---------------------------------------------------------------------------------------------------------------------------------------------------------

function generateScaledBGs() {
var sheet = SpreadsheetApp.getActiveSheet();
  var dmColumn = COLUMN_NUMBERS.DATE_COLUMN;
  var maxRow = sheet.getDataRange().getNumRows();
  for (var row = 2; row <= maxRow; row++) {
    var range = sheet.getRange(row, dmColumn);
    var cell = range.getCell(1, 1);
    if (!cell || cell.isBlank()) {
      break;
    }

    var color = getColorByAge(cell);
    Logger.log(color);
    cell.setBackgroundRGB(color.red, color.green, color.blue);
  }
}

function getColorByAge(cell) {
  var rgb = {};
  var dateModified = Date.parse(cell.getValue());
  var curDate = new Date();
  var difference = Math.sqrt(curDate - dateModified) / 1000;
  rgb.red = Math.round(difference * Math.abs(RGB_FADE_TARGET.red - RGB_FADE_START.red) * FADE_RATE);
  rgb.green = Math.round(difference * Math.abs(RGB_FADE_TARGET.green - RGB_FADE_START.green) * FADE_RATE);
  rgb.blue = Math.round(difference * Math.abs(RGB_FADE_TARGET.blue - RGB_FADE_START.blue) * FADE_RATE);
  return fixBadColors(rgb);
}

function millisToHours(millis) {
  return millis / 1000 / 60 / 60;
}

function fixBadColors(rgb) {
  Logger.log('Start color: ' + rgb.green);
  var keys = Object.keys(rgb);
  for (var i = 0; i < keys.length; i++) {
    var key = keys[i];
    var val = rgb[key];
    if (val > RGB_FADE_TARGET[key]) {
      rgb[key] = RGB_FADE_TARGET[key];
    } else if (val < 0) {
      rgb[key] = 0;
    }
  }

  Logger.log("RGB: " + rgb.green);
  return rgb;
}

function getHexColor(range) {
  SpreadsheetApp.getActiveSheet().getRange(range).getBackground();
}

function onEdit() {
  var sheet = SpreadsheetApp.getActiveSheet();
  var curCell = sheet.getActiveCell();
  var rowNum = curCell.getRow();
  var colNum = curCell.getColumn();
  updateLastModified(sheet, sheet.getRange(rowNum, 1, 1, NUMBER_OF_COLUMNS));
  if (!isImportantCell(curCell)) {
    return;
  }

  if (colNum == COLUMN_NUMBERS.STATUS_COLUMN) {
    setCellValueConditionally(
      curCell, 'Completed',
      getCell(sheet, rowNum, COLUMN_NUMBERS.PRIORITY_COLUMN), '0'
    );
  } else if (colNum == COLUMN_NUMBERS.PRIORITY_COLUMN) {
    setCellValueConditionally(
      getCell(sheet, rowNum, COLUMN_NUMBERS.STATUS_COLUMN), '0',
      curCell, 'Completed'
    );
  }
}

function updateLastModified(sheet, row) {
  var cell = row.getCell(1, COLUMN_NUMBERS.DATE_COLUMN);
  cell.setValue(new Date());
  var rgb = getColorByAge(sheet, cell);
  cell.setBackgroundRGB(rgb.red, rgb.green, rgb.blue);
}

function getCell(sheet, row, col) {
  if (row < 1 || col > NUMBER_OF_COLUMNS) {
    return;
  }

  var range = sheet.getRange(row, col, 1, NUMBER_OF_COLUMNS);
  return range.getCell(1, col);
}

function isImportantCell(cell) {
  if (cell.getRow() <= 1) {
    return false;
  }

  var colNum = cell.getColumn();
  var keys = Object.keys(COLUMN_NUMBERS);
  for (var keyIndex = 0; keyIndex < keys.length; keyIndex++) {
    var curVal = COLUMN_NUMBERS[keys[keyIndex]];
    if (curVal == colNum) {
      return true;
    }
  }

  return false;
}

function setCellValueConditionally(ifCell, ifValue, thenCell, thenValue) {
  Logger.log('Conditionally setting cell value.');
  Logger.log('Cell value: ' + ifCell.getValue() + ' Value: ' + ifValue + '. Targeted Value: ' + thenValue);
  if (ifCell.getValue() == ifValue) {
    Logger.log('They match!');
    thenCell.setValue(thenValue)
  }
}

function setDefaultPriorities() {
  var sheet = SpreadsheetApp.getActiveSheet();
  var maxRows = getMaxRows(sheet);
  var startRow = 2;
  var column = sheet.getRange(startRow, COLUMN_NUMBERS.PRIORITY_COLUMN, maxRows - startRow, 1);
  for (var row = startRow; row <= maxRows; row++) {
    setDefaultCellValue(column.getCell(row, 1), '0');
  }
}

function getMaxRows(sheet) {
  return sheet.getMaxRows();
}

function setDefaultCellValue(cell, value) {
  if (cell && cell.isBlank()) {
    cell.setValue(value);
  }
}