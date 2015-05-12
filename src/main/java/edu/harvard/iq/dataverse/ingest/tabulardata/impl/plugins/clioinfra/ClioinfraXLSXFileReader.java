/*
 Copyright (C) 2005-2013, by the President and Fellows of Harvard College.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.

 Dataverse Network - A web application to share, preserve and analyze research data.
 Developed at the Institute for Quantitative Social Science, Harvard University.
 Version 3.0.
 */
package edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.clioinfra;


import edu.harvard.iq.dataverse.DataTable;
import edu.harvard.iq.dataverse.datavariable.DataVariable;
import edu.harvard.iq.dataverse.datavariable.VariableCategory;
import edu.harvard.iq.dataverse.ingest.tabulardata.TabularDataFileReader;
import edu.harvard.iq.dataverse.ingest.tabulardata.TabularDataIngest;
import edu.harvard.iq.dataverse.ingest.tabulardata.spi.TabularDataFileReaderSpi;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;


/**
 * Clioinfra parser.
 * <p/>
 * It utilizes Apache POI framework for reading XLSX data; and uses an
 * event-based, SAX model for parsing the extracted XML. This way spreadsheets
 * of any size can be converted into tab-delimited data with a fairly small
 * memory footprint.
 * <p/>
 * The expected datasheet layout is as follows:
 * <p/>
 * IndicatorName
 * Code	"Continent, Region, Country"	1500	1501	1502	1503	1504	1505	1506	1507
 * [Which is then followed by actual values... some may be empty]
 * 150	Europe
 * 155	Western Europe
 * 40	Austria
 * 56	Belgium
 * 280	Federal Republic of Germany (until 1990)
 * 250	France
 * 278	German Democratic Republic (until 1990)
 * 276	Germany
 * 438	Liechtenstein
 * 442	Luxembourg
 * 492	Monaco
 * 528	Netherlands
 * 756	Switzerland
 * <p/>
 * 154	Northern Europe
 * 248	Åland Islands
 * 830	Channel Islands
 * <p/>
 * <p/>
 * The datasheet should turn into a flat table like so:
 * 'indicator', 'indicatortid', 'unit', 'countrycode', 'countrytid', 'year', 'value'
 *
 * @author Lucien van Wouw <lwo@iisg.nl>
 */
public class ClioinfraXLSXFileReader extends TabularDataFileReader {

    private static final Logger dbglog = Logger.getLogger(ClioinfraXLSXFileReader.class.getPackage().getName());
    private static char DELIMITER_CHAR = '\t';
    private static int VAR_QUANTITY = 7;

    public ClioinfraXLSXFileReader(TabularDataFileReaderSpi originator) {
        super(originator);
    }

    private void init() throws IOException {

    }

    /**
     * Reads a ClioInfa structured XLSX file, converts it into a dataverse DataTable.
     *
     * @param stream   a <code>BufferedInputStream</code>.
     * @param dataFile
     * @return an <code>TabularDataIngest</code> object
     * @throws java.io.IOException if a reading error occurs.
     */
    @Override
    public TabularDataIngest read(BufferedInputStream stream, File dataFile) throws IOException {
        init();

        TabularDataIngest ingesteddata = new TabularDataIngest();
        DataTable dataTable = new DataTable();

        File firstPassTempFile = File.createTempFile("firstpass-", ".tab");
        PrintWriter firstPassWriter = new PrintWriter(firstPassTempFile.getAbsolutePath());
        try {
            processSheet(stream, dataTable, firstPassWriter);
        } catch (Exception ex) {
            throw new IOException("Could not parse Excel/XLSX spreadsheet. " + ex.getMessage());
        }

        if (dataTable.getCaseQuantity() == null || dataTable.getCaseQuantity().intValue() < 1) {
            String errorMessage;
            if (dataTable.getVarQuantity() == null || dataTable.getVarQuantity().intValue() < 1) {
                errorMessage = "No rows of data found in the Excel (XLSX) file.";
            } else {
                errorMessage = "Only one row of data (column name header?) detected in the Excel (XLSX) file.";
            }
            throw new IOException(errorMessage);
        }

        dataTable.setVarQuantity(Long.valueOf(VAR_QUANTITY));

        // 2nd pass:

        File tabFileDestination = File.createTempFile("data-", ".tab");
        PrintWriter finalWriter = new PrintWriter(tabFileDestination.getAbsolutePath());

        BufferedReader secondPassReader = new BufferedReader(new FileReader(firstPassTempFile));

        int lineCounter = 0;
        String line = null;
        String[] caseRow = new String[VAR_QUANTITY];
        String[] valueTokens;

        final LinkedHashMap<Integer, HashMap<String, Integer>> variableCategories = new LinkedHashMap(VAR_QUANTITY);
        while ((line = secondPassReader.readLine()) != null) {
            // chop the line:
            line = line.replaceFirst("[\r\n]*$", "");
            valueTokens = line.split("" + DELIMITER_CHAR, -2);

            if (valueTokens == null) {
                throw new IOException("Failed to read line " + (lineCounter + 1) + " during the second pass.");
            }

            if (valueTokens.length != VAR_QUANTITY) {
                throw new IOException("Reading mismatch, line " + (lineCounter + 1) + " during the second pass: "
                        + VAR_QUANTITY + " delimited values expected, " + valueTokens.length + " found.");
            }

            for (int i = 0; i < VAR_QUANTITY; i++) {

                HashMap<String, Integer> categories = variableCategories.get(i);
                if (categories == null) {
                    categories = new HashMap();
                    variableCategories.put(i, categories);
                }

                if (categories.containsKey(valueTokens[i])) {
                    categories.put(valueTokens[i], categories.get(valueTokens[i]) + 1);
                } else
                    categories.put(valueTokens[i], 1);


                if (dataTable.getDataVariables().get(i).isTypeNumeric()) {
                    if (valueTokens[i] == null || valueTokens[i].equals(".") || valueTokens[i].isEmpty() || valueTokens[i].equalsIgnoreCase("NA")) {
                        // Missing value - represented as an empty string in 
                        // the final tab file
                        caseRow[i] = "";
                    } else if (valueTokens[i].equalsIgnoreCase("NaN")) {
                        // "Not a Number" special value: 
                        caseRow[i] = "NaN";
                    } else if (valueTokens[i].equalsIgnoreCase("Inf")
                            || valueTokens[i].equalsIgnoreCase("+Inf")) {
                        // Positive infinity:
                        caseRow[i] = "Inf";
                    } else if (valueTokens[i].equalsIgnoreCase("-Inf")) {
                        // Negative infinity: 
                        caseRow[i] = "-Inf";
                    } else if (valueTokens[i].equalsIgnoreCase("null")) {
                        // By request from Gus - "NULL" is recognized as a 
                        // numeric zero: 
                        caseRow[i] = "0";
                    } else {
                        try {
                            new Double(valueTokens[i]);
                            caseRow[i] = valueTokens[i];
                        } catch (Exception ex) {
                            throw new IOException("Failed to parse a value recognized as numeric in the first pass! column: " + i + ", value: " + valueTokens[i]);
                        }
                    }
                } else {
                    // Treat as a String:
                    // Strings are stored in tab files quoted;                                                                                   
                    // Missing values are stored as tab-delimited nothing - 
                    // i.e., an empty string between two tabs (or one tab and 
                    // the new line);                                                                       
                    // Empty strings stored as "" (quoted empty string).

                    if (valueTokens[i] != null && !valueTokens[i].equals(".")) {
                        String charToken = valueTokens[i];
                        // Dealing with quotes: 
                        // remove the leading and trailing quotes, if present:
                        charToken = charToken.replaceFirst("^\"", "");
                        charToken = charToken.replaceFirst("\"$", "");
                        // escape the remaining ones:
                        charToken = charToken.replace("\"", "\\\"");
                        // final pair of quotes:
                        charToken = "\"" + charToken + "\"";
                        caseRow[i] = charToken;
                    } else {
                        caseRow[i] = "";
                    }
                }
            }

            finalWriter.println(StringUtils.join(caseRow, DELIMITER_CHAR));
            lineCounter++;


        }

        secondPassReader.close();
        finalWriter.close();

        // We set the variableCategories.
        for (int i = 0; i < VAR_QUANTITY; i++) {
            final DataVariable dataVariable = dataTable.getDataVariables().get(i);
            final HashMap<String, Integer> categories = variableCategories.get(i);
            for (String categoryName : categories.keySet()) {
                final int frequency = categories.get(categoryName);
                VariableCategory category = new VariableCategory();
                category.setValue(categoryName);
                category.setFrequency(Double.valueOf(frequency));
                dataVariable.getCategories().add(category);
            }

        }
        variableCategories.clear();

        if (dataTable.getCaseQuantity().intValue() != lineCounter) {
            throw new IOException("Mismatch between line counts in first and final passes!");
        }

        dataTable.setUnf("UNF:6:NOTCALCULATED");

        ingesteddata.setTabDelimitedFile(tabFileDestination);
        ingesteddata.setDataTable(dataTable);

        dbglog.fine("Produced temporary file " + ingesteddata.getTabDelimitedFile().getAbsolutePath());
        dbglog.fine("Found " + dataTable.getVarQuantity() + " variables, " + dataTable.getCaseQuantity() + " observations.");
        String varNames = null;
        for (int i = 0; i < VAR_QUANTITY; i++) {
            if (varNames == null) {
                varNames = dataTable.getDataVariables().get(i).getName();
            } else {
                varNames = varNames + ", " + dataTable.getDataVariables().get(i).getName();
            }
        }
        dbglog.fine("Variable names: " + varNames);


        return ingesteddata;

    }

    public void processSheet(String filename, DataTable dataTable, PrintWriter tempOut) throws Exception {
        BufferedInputStream xlsxInputStream = new BufferedInputStream(new FileInputStream(new File(filename)));
        processSheet(xlsxInputStream, dataTable, tempOut);
    }

    public void processSheet(InputStream inputStream, DataTable dataTable, PrintWriter tempOut) throws Exception {
        dbglog.info("entering processSheet");
        OPCPackage pkg = OPCPackage.open(inputStream);
        XSSFReader r = new XSSFReader(pkg);
        SharedStringsTable sst = r.getSharedStringsTable();

        XMLReader parser = fetchSheetParser(sst, dataTable, tempOut);

        // rId2 found by processing the Workbook
        // Seems to either be rId# or rSheet#
        InputStream sheet1 = r.getSheet("rId1");
        InputSource sheetSource = new InputSource(sheet1);
        parser.parse(sheetSource);
        sheet1.close();
    }

    public XMLReader fetchSheetParser(SharedStringsTable sst, DataTable dataTable, PrintWriter tempOut) throws SAXException {
        // An attempt to use org.apache.xerces.parsers.SAXParser resulted 
        // in some weird conflict in the app; the default XMLReader obtained 
        // from the XMLReaderFactory (from xml-apis.jar) appears to be working
        // just fine. however, 
        // TODO: verify why the app gets built with xml-apis-1.0.b2.jar; it's 
        // an old version - 1.4 seems to be the current release, and 2.0.2
        // (a new development?) appears to be available. We don't specifically
        // request this 1.0.* version, so another package must have it defined
        // as a dependency. We need to verify our dependencies, we most likely 
        // have some hard-coded versions in our pom.xml that are both old and 
        // unnecessary.
        // -- L.A. 4.0 alpha 1

        XMLReader xReader = XMLReaderFactory.createXMLReader();
        dbglog.fine("creating new SheetHandler;");
        ContentHandler handler = new SheetHandler(sst, dataTable, tempOut);
        xReader.setContentHandler(handler);
        return xReader;
    }

    private static class SheetHandler extends DefaultHandler {

        private DataTable dataTable;
        private SharedStringsTable sst;
        private String cellContents;
        private boolean nextIsString;
        private boolean variableHeader;
        //private List<String> variableNames;
        private String[] variableNames;
        private long caseCount;
        private int columnCount;
        boolean[] isNumericVariable;
        String[] dataRow;
        PrintWriter tempOut;

        private String indicator = null;
        private String code = null;
        private String ContinentRegionCountry = null;
        private int indicatortid = -1;
        private String unit = null;

        private String HEADER_CODE = "code";
        private String HEADER_CONTINENT_REGION_COUNTRY = "continent";
        private String COLUMN_INDICATOR = "indicator";
        private String COLUMN_INDICATOR_ID = "indicatortid";
        private String COLUMN_UNIT = "unit";
        private String COLUMN_COUNTRY_CODE = "countrycode";
        private String COLUMN_COUNTRY_ID = "countrytid";
        private String COLUMN_YEAR = "year";
        private String COLUMN_VALUE = "value";


        private SheetHandler(SharedStringsTable sst, DataTable dataTable, PrintWriter tempOut) {
            this.sst = sst;
            this.dataTable = dataTable;
            this.tempOut = tempOut;
            variableHeader = true;
            //variableNames = new ArrayList<String>(); 
            caseCount = 0;
            columnCount = 0;
        }

        public void startElement(String uri, String localName, String name,
                                 Attributes attributes) throws SAXException {
            dbglog.fine("entering startElement (" + name + ")");

            // first row encountered:
            if (variableHeader && name.equals("row")) {

                Long varCount = null;
                String rAttribute = attributes.getValue("t");
                if (rAttribute == null) {
                    dbglog.warning("Null r attribute in the first row element!");
                } else if (!rAttribute.equals("1")) {
                    dbglog.warning("Attribute r of the first row element is not \"1\"!");
                }

                String spansAttribute = attributes.getValue("spans");
                if (spansAttribute == null) {
                    dbglog.warning("Null spans attribute in the first row element!");
                }
                int colIndex = spansAttribute.indexOf(':');
                if (colIndex < 1 || (colIndex == spansAttribute.length() - 1)) {
                    dbglog.warning("Invalid spans attribute in the first row element: " + spansAttribute + "!");
                }
                try {
                    varCount = new Long(spansAttribute.substring(colIndex + 1, spansAttribute.length()));
                } catch (Exception ex) {
                    varCount = null;
                }

                if (varCount == null || varCount.intValue() < 1) {
                    throw new SAXException("Could not establish column count, or invalid column count encountered.");
                }

                dbglog.info("Established variable (column) count: " + varCount);

                dataTable.setVarQuantity(varCount);
                variableNames = new String[varCount.intValue()];
            }

            // c => cell
            if (name.equals("c")) {
                // try and establish the location index (column number) of this
                // cell, from the "r" attribute: 

                String indexAttribute = attributes.getValue("r");

                if (indexAttribute == null) {
                    dbglog.warning("Null r attribute in a cell element!");
                }
                if (!indexAttribute.matches(".*[0-9]")) {
                    dbglog.warning("Invalid index (r) attribute in a cell element: " + indexAttribute + "!");
                }
                columnCount = getColumnCount(indexAttribute.replaceFirst("[0-9].*$", ""));

                if (columnCount < 0) {
                    throw new SAXException("Could not establish position index of a cell element unambiguously!");
                }

                String cellType = attributes.getValue("t");
                if (cellType != null && cellType.equals("s")) {
                    nextIsString = true;
                } else {
                    nextIsString = false;
                }
            }
            // Clear contents cache
            cellContents = "";
        }

        private int getColumnCount(String columnTag) {
            int count = -1;
            if (columnTag.matches("[A-Z]{1,2}")) {
                final int first = (columnTag.length() == 1) ? 0 : 1 + columnTag.charAt(0) - 'A'; // If the length = 1, we treat it as 0A
                final int second = (columnTag.length() == 1) ? columnTag.charAt(0) - 'A' : columnTag.charAt(1) - 'A';
                count = 26 * first + second;
            }

            return count;
        }

        public void endElement(String uri, String localName, String name)
                throws SAXException {
            dbglog.fine("entering endElement (" + name + ")");
            // Process the content cache as required.
            // Do it now, as characters() may be called more than once
            if (nextIsString) {
                int idx = Integer.parseInt(cellContents);
                cellContents = new XSSFRichTextString(sst.getEntryAt(idx)).toString();
                nextIsString = false;
            }

            // v => contents of a cell
            // Output after we've seen the string contents
            if (name.equals("v")) {
                if (variableHeader) {
                    dbglog.fine("variable header mode; cell " + columnCount + ", cell contents: " + cellContents);

                    //variableNames.add(cellContents);
                    variableNames[columnCount] = cellContents;

                    // The indicator name at A1
                    if (indicator == null) {
                        indicator = cellContents;
                        // Assert indicator value.
                        indicatortid = 1; // To be determined
                        return;
                    }

                    // The unit at A2 or the code at A3. Empty rows are not detected here.
                    if (code == null) {
                        // Assert indicator value.
                        if (cellContents.toLowerCase().startsWith(HEADER_CODE)) {
                            code = cellContents;
                            return;
                        }

                        // So it may be the unit
                        unit = cellContents;
                        return;
                    }

                    if (ContinentRegionCountry == null) {
                        ContinentRegionCountry = cellContents.toLowerCase();
                        // Assert indicator value.
                        if (ContinentRegionCountry.startsWith(HEADER_CONTINENT_REGION_COUNTRY))
                            return;
                        else
                            throw new SAXException("Expect cell value to start with " + HEADER_CONTINENT_REGION_COUNTRY + " but got " + ContinentRegionCountry);
                    }

                    // These are years
                    return;

                } else {
                    dataRow[columnCount] = cellContents;
                    dbglog.fine("data row mode; cell " + columnCount + ", cell contents: " + cellContents);
                }
            }

            if (name.equals("row")) {
                if (variableHeader) {
                    if (code == null || ContinentRegionCountry == null)
                        return;

                    // 'indicator', 'indicatortid', 'unit', 'countrycode', 'countrytid', 'year', 'value'
                    isNumericVariable = new boolean[VAR_QUANTITY];
                    final List<DataVariable> variableList = new ArrayList<DataVariable>(VAR_QUANTITY);
                    variableList.add(addDataVariable(dataTable, COLUMN_INDICATOR, false, 0));
                    variableList.add(addDataVariable(dataTable, COLUMN_INDICATOR_ID, true, 1));
                    variableList.add(addDataVariable(dataTable, COLUMN_UNIT, false, 2));
                    variableList.add(addDataVariable(dataTable, COLUMN_COUNTRY_CODE, false, 3));
                    variableList.add(addDataVariable(dataTable, COLUMN_COUNTRY_ID, true, 4));
                    variableList.add(addDataVariable(dataTable, COLUMN_YEAR, true, 5));
                    variableList.add(addDataVariable(dataTable, COLUMN_VALUE, true, 6));

                    columnCount = dataTable.getVarQuantity().intValue();
                    columnCount = dataTable.getVarQuantity().intValue();

                    for (int i = 2; i < columnCount; i++) { // Must be numeric
                        try {
                            Integer.parseInt(variableNames[i]);
                        } catch (NumberFormatException e) {
                            throw new SAXException("Expect cell value to be numeric but got " + variableNames[i]);
                        }
                    }

                    dataTable.setDataVariables(variableList);
                    variableHeader = false;
                } else {
                    dbglog.fine("row mode;");
                    // go through the values and make an educated guess about the 
                    // data types:

                    for (int i = 0; i < dataRow.length; i++) {
                        if (i > 1 && dataRow[i] != null && !dataRow[i].isEmpty()) {
                            // print out the data row:
                            tempOut.println(indicator + DELIMITER_CHAR + indicatortid + DELIMITER_CHAR + unit + DELIMITER_CHAR + dataRow[1] + DELIMITER_CHAR + dataRow[0] + DELIMITER_CHAR + variableNames[i] + DELIMITER_CHAR + dataRow[i]);
                            // Should we set the category count ?
                            caseCount++;
                        }
                    }
                }
                columnCount = 0;
                dataRow = new String[dataTable.getVarQuantity().intValue()];
            }

            if (name.equals("sheetData")) {
                dataTable.setCaseQuantity(caseCount);
                tempOut.close();
            }
        }

        private DataVariable addDataVariable(DataTable dataTable, String varName, boolean isNumeric, int order) {

            final DataVariable dv = new DataVariable();
            dv.setName(varName);
            dv.setLabel(varName);
            dv.setInvalidRanges(new ArrayList());
            dv.setSummaryStatistics(new ArrayList());
            dv.setUnf("UNF:6:NOTCALCULATED");
            dv.setCategories(new ArrayList());

            dv.setTypeCharacter();
            dv.setIntervalDiscrete();

            if (isNumeric) {
                dv.setTypeNumeric();
                dv.setIntervalContinuous();
            }

            isNumericVariable[order] = isNumeric;

            dv.setFileOrder(order);
            dv.setDataTable(dataTable);

            return dv;
        }

        public void characters(char[] ch, int start, int length)
                throws SAXException {
            cellContents += new String(ch, start, length);
        }
    }

    public static void main(String[] args) throws Exception {
        ClioinfraXLSXFileReader testReader = new ClioinfraXLSXFileReader(new ClioinfraXLSXFileReaderSpi());
        DataTable dataTable;

        BufferedInputStream xlsxInputStream = new BufferedInputStream(new FileInputStream(new File(args[0])));

        TabularDataIngest dataIngest = testReader.read(xlsxInputStream, null);

        dataTable = dataIngest.getDataTable();

        System.out.println("Produced temporary file " + dataIngest.getTabDelimitedFile().getAbsolutePath());
        System.out.println("Found " + dataTable.getVarQuantity() + " variables, " + dataTable.getCaseQuantity() + " observations.");
        System.out.println("Variable names:");
        for (int i = 0; i < VAR_QUANTITY; i++) {
            final DataVariable dataVariable = dataTable.getDataVariables().get(i);
            System.out.println(dataVariable.getName());
            System.out.println("isIntervalNominal:  " + dataVariable.isIntervalNominal());
            System.out.println("isTypeNumeric:  " + dataVariable.isTypeNumeric());
            System.out.println("isIntervalContinuous:  " + dataVariable.isIntervalContinuous());
            System.out.println("isIntervalDiscrete:  " + dataVariable.isIntervalDiscrete());
            System.out.println("categories:");
            final Collection<VariableCategory> categories = dataVariable.getCategories();
            for (VariableCategory category : categories) {
                System.out.println(category.getValue() + ":" + category.getFrequency());
            }
        }
    }

}
