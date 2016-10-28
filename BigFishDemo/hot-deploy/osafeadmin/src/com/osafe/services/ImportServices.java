package com.osafe.services;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventLocator;
import javax.xml.namespace.QName;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import javolution.util.FastList;
import javolution.util.FastMap;
import javolution.util.FastSet;
import jxl.Cell;
import jxl.CellView;
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.format.Colour;
import jxl.format.UnderlineStyle;
import jxl.read.biff.BiffException;
import jxl.write.Label;
import jxl.write.NumberFormats;
import jxl.write.WritableCellFormat;
import jxl.write.WritableFont;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;

import org.apache.commons.fileupload.util.Streams;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.ofbiz.base.crypto.HashCrypt;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.FileUtil;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.ObjectType;
import org.ofbiz.base.util.StringUtil;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilFormatOut;
import org.ofbiz.base.util.UtilIO;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilNumber;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilURL;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.string.FlexibleStringExpander;
import org.ofbiz.common.login.LoginServices;
import org.ofbiz.datafile.DataFile;
import org.ofbiz.datafile.DataFile2EntityXml;
import org.ofbiz.datafile.DataFileException;
import org.ofbiz.datafile.ModelDataFile;
import org.ofbiz.datafile.ModelDataFileReader;
import org.ofbiz.datafile.Record;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.DelegatorFactory;
import org.ofbiz.entity.GenericDataSourceException;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityFunction;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.config.DatasourceInfo;
import org.ofbiz.entity.config.EntityConfigUtil;
import org.ofbiz.entity.datasource.GenericHelperInfo;
import org.ofbiz.entity.jdbc.SQLProcessor;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.order.order.OrderReadHelper;
import org.ofbiz.party.contact.ContactHelper;
import org.ofbiz.party.contact.ContactMechWorker;
import org.ofbiz.product.product.ProductWorker;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;

import com.osafe.constants.Constants;
import com.osafe.feeds.FeedsUtil;
import com.osafe.feeds.osafefeeds.*;
import com.osafe.util.OsafeAdminUtil;
import com.osafe.util.OsafeProductLoaderHelper;


public class ImportServices {

    public static final String module = ImportServices.class.getName();
    private static final ResourceBundle OSAFE_PROP = UtilProperties.getResourceBundle("OsafeProperties.xml", Locale.getDefault());
    private static final ResourceBundle OSAFE_ADMIN_PROP = UtilProperties.getResourceBundle("osafeAdmin", Locale.getDefault());
    private static final Long FEATURED_PRODUCTS_CATEGORY = Long.valueOf(10054);
    private static final SimpleDateFormat _sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final DecimalFormat _df = new DecimalFormat("##.00");
    private static Delegator _delegator = DelegatorFactory.getDelegator(null);
    private static LocalDispatcher _dispatcher = null;
    private static Locale _locale =null;
    private static String localeString = "";
    
    public static final int scale = UtilNumber.getBigDecimalScale("order.decimals");
    public static final int rounding = UtilNumber.getBigDecimalRoundingMode("order.rounding");
    
    public static final WritableFont cellFont = new WritableFont(WritableFont.TIMES, 10);
    public static final WritableCellFormat cellFormat = new WritableCellFormat(cellFont,NumberFormats.TEXT);
    
    private static Map<String, ?> context = FastMap.newInstance();
	
	private static String XmlFilePath = FlexibleStringExpander.expandString(UtilProperties.getPropertyValue("osafeAdmin.properties", "image-location-preference-file"), context);
	
	public static List<Map<Object, Object>> imageLocationPrefList = OsafeManageXml.getListMapsFromXmlFile(XmlFilePath);
	
	private static String schemaLocation = FlexibleStringExpander.expandString("${sys:getProperty('ofbiz.home')}/hot-deploy/osafeadmin/dtd/feeds/bigfishfeed.xsd", context);
	
	private static final String resource = "OSafeAdminUiLabels";
	private static Set sFeatureGroupExists = FastSet.newInstance();
	private static Map mFeatureValueExists = FastMap.newInstance();
	private static Map mProductFeatureCatGrpApplFromDateExists = FastMap.newInstance();
	private static Map mProductFeatureCategoryApplFromDateExists = FastMap.newInstance();
	private static Map mProductFeatureGroupApplFromDateExists = FastMap.newInstance();
	
    public static Map<String, Object> importProcess(DispatchContext ctx, Map<String, ?> context) {
        LocalDispatcher dispatcher = ctx.getDispatcher();
        Map<String, Object> result = ServiceUtil.returnSuccess();
        try {
            // dispatcher.runSync("import[ClientServices", context);
        } catch (Exception e) {
            Debug.logError(e, module);
        }

        return result;
    }

    private static DataFile getImportDataFile(Map<String, ?> context) {
        DataFile importDataFile = null;
        String definitionFileName = (String) context.get("definitionFileName");
        String definitionName = (String) context.get("definitionName");
        String dataFileName = (String) context.get("dataFileName");

        String importDir = OSAFE_PROP.getString("importDir");
        URL definitionUrl = null;
        String fullDefinitionFilePath = importDir;
        fullDefinitionFilePath += "definition" + File.separator;
        fullDefinitionFilePath += definitionFileName + ".xml";

        URL dataFileUrl = null;
        String fullDataFilePath = importDir;
        fullDataFilePath += "data" + File.separator + "csv" + File.separator;
        fullDataFilePath += dataFileName + ".csv";

        definitionUrl = UtilURL.fromFilename(fullDefinitionFilePath);
        dataFileUrl = UtilURL.fromFilename(fullDataFilePath);

        try {

            importDataFile = DataFile.readFile(dataFileUrl, definitionUrl, definitionName);
        } catch (DataFileException e) {
            Debug.logError(e, "Error Loading File", module);
        }
        return importDataFile;
    }

    public static Map<String, Object> importDataFileCsvToXml(DispatchContext ctx, Map<String, ?> context) {
        Map<String, Object> result = FastMap.newInstance();
        String sImpSize = "0";

        String exportXmlFileName = (String) context.get("exportXmlFileName");

        String exportDir = OSAFE_PROP.getString("exportDir");
        String fullExportPath = exportDir;
        fullExportPath += exportXmlFileName + "_" + UtilDateTime.nowDateString() + ".xml";

        DataFile importDataFile = getImportDataFile(context);

        if (importDataFile != null) {

            List<Record> records = importDataFile.getRecords();

            sImpSize = "" + records.size();
            try {
                DataFile2EntityXml.writeToEntityXml(fullExportPath, importDataFile);
            } catch (DataFileException e) {
                Debug.logError(e, "Error Saving File", module);
            }

        }

        result.put("recordSize", sImpSize);
        return result;

    }

	public static Map<String, Object> importCsvToXml(DispatchContext ctx, Map context){
        /*
        * This Service is used to generate XML file from CSV file using
        * entity definition
        */
        LocalDispatcher dispatcher = ctx.getDispatcher();
        Map<String, Object> result = ServiceUtil.returnSuccess();

        String sourceCsvFileLoc = (String)context.get("sourceCsvFileLoc");
        String definitionFileLoc = (String)context.get("definitionFileLoc");
        String targetXmlFileLoc = (String)context.get("targetXmlFileLoc");

        Collection definitionFileNames = null;
        File outXmlDir = null;
        URL definitionUrl= null;
        definitionUrl = UtilURL.fromFilename(definitionFileLoc);

        if (definitionUrl != null) {
            try {
                ModelDataFileReader reader = ModelDataFileReader.getModelDataFileReader(definitionUrl);
                if (reader != null) {
                    definitionFileNames = (Collection)reader.getDataFileNames();
                    context.put("definitionFileNames", definitionFileNames);
                }
            } catch(Exception ex) {
                Debug.logError(ex.getMessage(), module);
            }
        }
        if (targetXmlFileLoc != null) {
            outXmlDir = new File(targetXmlFileLoc);
            if (!outXmlDir.exists()) {
                outXmlDir.mkdir();
            }
        }
        if (sourceCsvFileLoc != null) {
            File inCsvDir = new File(sourceCsvFileLoc);
            if (!inCsvDir.exists()) {
                inCsvDir.mkdir();
                }
            if (inCsvDir.isDirectory() && inCsvDir.canRead() && outXmlDir.isDirectory() && outXmlDir.canWrite()) {
                File[] fileArray = inCsvDir.listFiles();
                URL dataFileUrl = null;
                for (File file: fileArray) {
                    if(file.getName().toUpperCase().endsWith("CSV")) {
                        String fileNameWithoutExt = FilenameUtils.removeExtension(file.getName());
                        String definationName = OSAFE_PROP.getString(Constants.IMPOERT_XLS_ENTITY_PROPERTY_MAPPING_PREFIX+UtilValidate.stripWhitespace(fileNameWithoutExt));
                        if(definitionFileNames.contains(definationName)) {
                            dataFileUrl = UtilURL.fromFilename(file.getPath());
                            DataFile dataFile = null;
                            if(dataFileUrl != null && definitionUrl != null && definitionFileNames != null) {
                                try {
                                    dataFile = DataFile.readFile(dataFileUrl, definitionUrl, definationName);
                                    context.put("dataFile", dataFile);
                                }catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            if(dataFile != null) {
                                ModelDataFile modelDataFile = (ModelDataFile)dataFile.getModelDataFile();
                                context.put("modelDataFile", modelDataFile);
                            }
                            if (dataFile != null && definationName != null) {
                                try {
                                    DataFile2EntityXml.writeToEntityXml(new File(outXmlDir, definationName +".xml").getPath(), dataFile);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            definitionFileNames.remove(definationName);
                        }
                        else {
                            Debug.log("======csv file name which not according to import defination file================="+file.getName()+"====");
                        }
                    }
                }
            }
        }
        return result;
    }
     /**
     * service for generating the xml data files from xls data file using import entity defination 
     * take the xls file location path, output xml data files directory path and import entity defination xml file location
     * working first upload the xls data file ,generate csv files from xls data file
     * using service importCsvToXml generate xml data files. 
     * this service support only 2003 Excel sheet format
     */
    public static Map<String, Object> importXLSFileAndGenerateXML(DispatchContext ctx, Map<String, ?> context) {

        LocalDispatcher dispatcher = ctx.getDispatcher();
        List<String> messages = FastList.newInstance();
        InputStream ins = null;
        File inputWorkbook = null, baseDataDir = null, csvDir = null;

        String definitionFileLoc = (String)context.get("definitionFileLoc");
        String xlsDataFilePath = (String)context.get("xlsDataFile");
        String xmlDataDirPath = (String)context.get("xmlDataDir");

        if (UtilValidate.isNotEmpty(xlsDataFilePath) && UtilValidate.isNotEmpty(xmlDataDirPath)) {
            try {
                // ######################################
                // make the input stram for xls data file
                // ######################################
                URL xlsDataFileUrl = UtilURL.fromFilename(xlsDataFilePath);
                ins = xlsDataFileUrl.openStream();

                if (ins != null && (xlsDataFilePath.toUpperCase().endsWith("XLS"))) {
                    baseDataDir = new File(xmlDataDirPath);
                    if (baseDataDir.isDirectory() && baseDataDir.canWrite()) {

                        // ############################################
                        // move the existing xml files in dump directory
                        // ############################################
                        File dumpXmlDir = null;
                        File[] fileArray = baseDataDir.listFiles();
                        for (File file: fileArray) {
                            try {
                                if (file.getName().toUpperCase().endsWith("XML")) {
                                    if (dumpXmlDir == null) {
                                        dumpXmlDir = new File(baseDataDir, Constants.DUMP_XML_DIRECTORY_PREFIX+UtilDateTime.nowDateString());
                                    }
                                    FileUtils.copyFileToDirectory(file, dumpXmlDir);
                                    file.delete();
                                }
                            } catch (IOException ioe) {
                                Debug.logError(ioe, module);
                            } catch (Exception exc) {
                                Debug.logError(exc, module);
                            }
                        }
                        // ######################################
                        //save the temp xls data file on server 
                        // ######################################
                        try {
                            inputWorkbook = new File(baseDataDir,  UtilDateTime.nowAsString()+"."+FilenameUtils.getExtension(xlsDataFilePath));
                            if (inputWorkbook.createNewFile()) {
                                Streams.copy(ins, new FileOutputStream(inputWorkbook), true, new byte[1]); 
                            }
                            } catch (IOException ioe) {
                                Debug.logError(ioe, module);
                            } catch (Exception exc) {
                                Debug.logError(exc, module);
                            }
                    }
                    else {
                        messages.add("xml data dir path not found or can't be write");
                    }
                }
                else {
                    messages.add(" path specified for Excel sheet file is wrong , doing nothing.");
                }

            } catch (IOException ioe) {
                Debug.logError(ioe, module);
            } catch (Exception exc) {
                Debug.logError(exc, module);
            }
        }
        else {
            messages.add("No path specified for Excel sheet file or xml data direcotry, doing nothing.");
        }

        // ######################################
        //read the temp xls file and generate csv 
        // ######################################
        if (inputWorkbook != null && baseDataDir  != null) {
            try {
                csvDir = new File(baseDataDir,  UtilDateTime.nowDateString()+"_CSV_"+UtilDateTime.nowAsString());
                if (!csvDir.exists() ) {
                    csvDir.mkdirs();
                }

                WorkbookSettings ws = new WorkbookSettings();
                ws.setLocale(new Locale("en", "EN"));
                Workbook wb = Workbook.getWorkbook(inputWorkbook,ws);
                // Gets the sheets from workbook
                for (int sheet = 0; sheet < wb.getNumberOfSheets(); sheet++) {
                    BufferedWriter bw = null; 
                    try {
                        Sheet s = wb.getSheet(sheet);

                        //File to store data in form of CSV
                        File csvFile = new File(csvDir, s.getName().trim()+".csv");
                        if (csvFile.createNewFile()) {
                            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile), "UTF-8"));

                            Cell[] row = null;
                            //loop start from 1 because of discard the header row
                            for (int rowCount = 1 ; rowCount < s.getRows() ; rowCount++) {
                                StringBuilder  rowString = new StringBuilder();
                                row = s.getRow(rowCount);
                                if (row.length > 0) {
                                    rowString.append(row[0].getContents());
                                    for (int colCount = 1; colCount < row.length; colCount++) {
                                        rowString.append(",");
                                        rowString.append(row[colCount].getContents());
                                     }
                                     if(UtilValidate.isNotEmpty(StringUtil.replaceString(rowString.toString(), ",", "").trim())) {
                                         bw.write(rowString.toString());
                                         bw.newLine();
                                     }
                                }
                            }
                        }
                    } catch (IOException ioe) {
                        Debug.logError(ioe, module);
                    } catch (Exception exc) {
                        Debug.logError(exc, module);
                    } 
                    finally {
                        try {
                            if (bw != null) {
                                bw.flush();
                                bw.close();
                            }
                        } catch (IOException ioe) {
                            Debug.logError(ioe, module);
                        }
                        bw = null;
                    }
                }

            } catch (BiffException be) {
                Debug.logError(be, module);
            } catch (Exception exc) {
                Debug.logError(exc, module);
            }
        }

        // ####################################################################################################################
        //Generate xml files from csv directory using importCsvToXml service 
        //Delete temp xls file and csv directory
        // ####################################################################################################################
        if(csvDir != null) {

            // call service for generate xml files from csv directory
            Map importCsvToXmlParams = UtilMisc.toMap("sourceCsvFileLoc", csvDir.getPath(),
                                                      "definitionFileLoc", definitionFileLoc,
                                                      "targetXmlFileLoc", baseDataDir.getPath());
            try {
                Map result = dispatcher.runSync("importCsvToXml", importCsvToXmlParams);

            } catch(Exception exc) {
                Debug.logError(exc, module);
            }

            //Delete temp xls file and csv directory 
            try {
                inputWorkbook.delete();
                FileUtils.deleteDirectory(csvDir);

            } catch (IOException ioe) {
                Debug.logError(ioe, module);
            } catch (Exception exc) {
                Debug.logError(exc, module);
            }
            
            messages.add("file saved in xml base dir.");
        }
        else {
            messages.add("input parameter is wrong , doing nothing.");
        }

        // send the notification
        Map<String, Object> resp = UtilMisc.toMap("messages", (Object) messages);
        return resp;
    }
    /**
     * service for generating the xml data files and execute the insert script in database 
     * from xls data file using import entity defination 
     * take the xls file location path, output xml data files directory path and import entity defination xml file location
     * working first upload the xls data file ,generate csv files from xls data file
     * using service importCsvToXml generate xml data files.
     * save the all generated xml in new directory using service  importXLSFileAndGenerateXML
     */
    public static Map<String, Object> importXLSFileAndRunInsertInDataBase(DispatchContext ctx, Map<String, ?> context) {

        LocalDispatcher dispatcher = ctx.getDispatcher();
        List<String> messages = FastList.newInstance();

        String definitionFileLoc = (String)context.get("definitionFileLoc");
        String xlsDataFilePath = (String)context.get("xlsDataFile");
        String xmlDataDirPath = (String)context.get("xmlDataDir");

        if (UtilValidate.isNotEmpty(xlsDataFilePath) && UtilValidate.isNotEmpty(xmlDataDirPath)) {

            File baseDataDir = new File(xmlDataDirPath);
            if (baseDataDir.isDirectory() && baseDataDir.canWrite()) {
                // ######################################################
                // call service for generate xml files from Excel File
                // ######################################################
                Map importXLSFileAndGenerateXMLParams = UtilMisc.toMap("definitionFileLoc", definitionFileLoc,
                                                                       "xlsDataFile", xlsDataFilePath,
                                                                       "xmlDataDir", xmlDataDirPath);
                try {
                    
                    Map result = dispatcher.runSync("importXLSFileAndGenerateXML", importXLSFileAndGenerateXMLParams);
                    
                    List<String> serviceMsg = (List)result.get("messages");
                    for (String msg: serviceMsg) {
                        messages.add(msg);
                    }
                } catch (Exception exc) {
                    Debug.logError(exc, module);
                }

                // ########################################################################################################
                // call service for insert row in database  from generated xml data files by calling service entityImportDir
                // ########################################################################################################
                 Map entityImportDirParams = UtilMisc.toMap("path", xmlDataDirPath, 
                                                             "userLogin", context.get("userLogin"));
                 try {
                     Map result = dispatcher.runSync("entityImportDir", entityImportDirParams);
                     
                     List<String> serviceMsg = (List)result.get("messages");
                     for (String msg: serviceMsg) {
                         messages.add(msg);
                     }
                 } catch (Exception exc) {
                     Debug.logError(exc, module);
                 }

                 // ##############################################
                 // move the generated xml files in done directory
                 // ##############################################
                 File doneXmlDir = null;
                 File[] fileArray = baseDataDir.listFiles();
                 for (File file: fileArray) {
                     try {
                         if (file.getName().toUpperCase().endsWith("XML")) {
                             if (doneXmlDir == null) {
                                 doneXmlDir = new File(baseDataDir, Constants.DONE_XML_DIRECTORY_PREFIX+UtilDateTime.nowDateString());
                             }
                             FileUtils.copyFileToDirectory(file, doneXmlDir);
                             file.delete();
                         }
                     } catch (IOException ioe) {
                         Debug.logError(ioe, module);
                     } catch (Exception exc) {
                         Debug.logError(exc, module);
                     }
                 }
            }
            else {
                messages.add("xml data dir path not found or can't be write");
            }
        }
        else {
            messages.add("No path specified for Excel sheet file or xml data direcotry, doing nothing.");
        }
        // send the notification
        Map<String, Object> resp = UtilMisc.toMap("messages", (Object) messages);
        return resp;
    }
    
    public static Map<String, Object> importProductXls(DispatchContext ctx, Map<String, ?> context) 
    {
        LocalDispatcher dispatcher = ctx.getDispatcher();
        _delegator = ctx.getDelegator();
        List<String> messages = FastList.newInstance();

        String xlsDataFilePath = (String)context.get("xlsDataFile");
        String xmlDataDirPath = (String)context.get("xmlDataDir");
        GenericValue userLogin = (GenericValue)context.get("userLogin");
        String loadImagesDirPath=(String)context.get("productLoadImagesDir");
        String imageUrl = (String)context.get("imageUrl");
        Boolean removeAll = (Boolean) context.get("removeAll");
        Boolean autoLoad = (Boolean) context.get("autoLoad");
        String productStoreId = (String) context.get("productStoreId");
        
        if (removeAll == null) removeAll = Boolean.FALSE;
        if (autoLoad == null) autoLoad = Boolean.FALSE;

        File inputWorkbook = null;
        File baseDataDir = null;
        BufferedWriter fOutProduct=null;
        if (UtilValidate.isNotEmpty(xlsDataFilePath) && UtilValidate.isNotEmpty(xmlDataDirPath)) 
        {
            try 
            {
                URL xlsDataFileUrl = UtilURL.fromFilename(xlsDataFilePath);
                InputStream ins = xlsDataFileUrl.openStream();

                if (ins != null && (xlsDataFilePath.toUpperCase().endsWith("XLS"))) 
                {
                    baseDataDir = new File(xmlDataDirPath);
                    if (baseDataDir.isDirectory() && baseDataDir.canWrite()) 
                    {
                        
                        // ######################################
                        //save the temp xls data file on server 
                        // ######################################
                        try 
                        {
                            inputWorkbook = new File(baseDataDir,  UtilDateTime.nowAsString()+"."+FilenameUtils.getExtension(xlsDataFilePath));
                            if (inputWorkbook.createNewFile()) 
                            {
                                Streams.copy(ins, new FileOutputStream(inputWorkbook), true, new byte[1]); 
                            }
                            } 
                        catch (IOException ioe) 
                        {
                                Debug.logError(ioe, module);
                        } catch (Exception exc) 
                            {
                                Debug.logError(exc, module);
                            }
                    }
                    else 
                    {
                        messages.add("xml data dir path not found or can't be write");
                    }
                }
                else 
                {
                    messages.add(" path specified for Excel sheet file is wrong , doing nothing.");
                }

            } 
            catch (IOException ioe) 
            {
                Debug.logError(ioe, module);
            } 
            catch (Exception exc) 
            {
                Debug.logError(exc, module);
            }
        }
        else 
        {
            messages.add("No path specified for Excel sheet file or xml data direcotry, doing nothing.");
        }
        
        String bigfishXmlFile = UtilDateTime.nowAsString()+".xml";
            
        String importDataPath = System.getProperty("ofbiz.home") + "/runtime/tmp/upload/bigfishXmlFile/";
        
        if (!new File(importDataPath).exists()) 
        {
            new File(importDataPath).mkdirs();
        }
        
        File tempFile = new File(importDataPath, "temp" + bigfishXmlFile);
        
        
        // ######################################
        //read the temp xls file and generate Bigfish xml 
        // ######################################
        if (inputWorkbook != null && baseDataDir  != null) 
        {
            try 
            {

                WorkbookSettings ws = new WorkbookSettings();
                ws.setLocale(new Locale("en", "EN"));
                Workbook wb = Workbook.getWorkbook(inputWorkbook,ws);
                
                ObjectFactory factory = new ObjectFactory();
                
                BigFishProductFeedType bfProductFeedType = factory.createBigFishProductFeedType();
                
                // Gets the sheets from workbook
                for (int sheet = 0; sheet < wb.getNumberOfSheets(); sheet++) 
                {
                    BufferedWriter bw = null; 
                    try 
                    {
                        Sheet s = wb.getSheet(sheet);
                        String sTabName=s.getName();
                        
                        if (sheet == 1)
                        {
                            ProductCategoryType productCategoryType = factory.createProductCategoryType();
                	        List productCategoryList =  productCategoryType.getCategory();
                            List<Map<String, Object>> dataRows = ImportServices.buildProductCategoryDataRows(s);
                            ImportServices.generateProductCategoryXML(factory, productCategoryList,  dataRows);
                	  	    bfProductFeedType.setProductCategory(productCategoryType);
                        }
                        if (sheet == 2)
                        {
                            ProductsType productsType = factory.createProductsType();
                	  	    List productList = productsType.getProduct();
                        	List<Map<String, Object>>  dataRows = ImportServices.buildProductDataRows(s);
                	  	    ImportServices.generateProductXML(factory, productList, dataRows);
                	  	    bfProductFeedType.setProducts(productsType);
                        }
                        if (sheet == 3)
                        {
                            ProductAssociationType productAssociationType = factory.createProductAssociationType();
                	  	    List productAssocList = productAssociationType.getAssociation();
                        	List<Map<String, Object>>  dataRows = ImportServices.buildProductAssocDataRows(s);
                	  	    ImportServices.generateProductAssocXML(factory, productAssocList, dataRows);
                	  	    bfProductFeedType.setProductAssociation(productAssociationType);
                        }
                        if (sheet == 4)
                        {
                            ProductFacetCatGroupType productFacetCatGroupType = factory.createProductFacetCatGroupType();
                	  	    List facetGroupList = productFacetCatGroupType.getFacetCatGroup();
                        	List<Map<String, Object>>  dataRows = ImportServices.buildFacetGroupDataRows(s);
                	  	    ImportServices.generateFacetGroupXML(factory, facetGroupList, dataRows);
                	  	    bfProductFeedType.setProductFacetGroup(productFacetCatGroupType);
                        }
                        if (sheet == 5)
                        {
                            ProductFacetValueType productFacetValueType = factory.createProductFacetValueType();
                	  	    List facetValueList = productFacetValueType.getFacetValue();
                        	List<Map<String, Object>>  dataRows = ImportServices.buildFacetValueDataRows(s);
                	  	    ImportServices.generateFacetValueXML(factory, facetValueList, dataRows);
                	  	    bfProductFeedType.setProductFacetValue(productFacetValueType);
                        }
                        if (sheet == 6)
                        {
                            ProductManufacturerType productManufacturerType = factory.createProductManufacturerType();
                	  	    List manufacturerList = productManufacturerType.getManufacturer();
                        	List<Map<String, Object>>  dataRows = ImportServices.buildManufacturerDataRows(s);
                	  	    ImportServices.generateManufacturerXML(factory, manufacturerList, dataRows);
                	  	    bfProductFeedType.setProductManufacturer(productManufacturerType);
                        }

                        //File to store data in form of CSV
                    } catch (Exception exc) {
                        Debug.logError(exc, module);
                    } 
                    finally 
                    {
                        try 
                        {
                            if (fOutProduct != null) 
                            {
                            	fOutProduct.close();
                            }
                        } 
                        catch (IOException ioe) 
                        {
                            Debug.logError(ioe, module);
                        }
                    }
                }

                FeedsUtil.marshalObject(new JAXBElement<BigFishProductFeedType>(new QName("", "BigFishProductFeed"), BigFishProductFeedType.class, null, bfProductFeedType), tempFile);
          	    
          	    new File(importDataPath, bigfishXmlFile).delete();
                File renameFile =new File(importDataPath, bigfishXmlFile);
                RandomAccessFile out = new RandomAccessFile(renameFile, "rw");
                InputStream inputStr = new FileInputStream(tempFile);
                byte[] bytes = new byte[102400];
                int bytesRead;
                while ((bytesRead = inputStr.read(bytes)) != -1)
                {
                    out.write(bytes, 0, bytesRead);
                }
                out.close();
                inputStr.close();
                
                Map<String, Object> importClientProductTemplateCtx = null;
                Map result  = FastMap.newInstance();
                importClientProductTemplateCtx = UtilMisc.toMap("xmlDataFile", renameFile.toString(), "xmlDataDir", xmlDataDirPath,"productLoadImagesDir", loadImagesDirPath, "imageUrl", imageUrl, "removeAll",removeAll,"autoLoad",autoLoad,"userLogin",userLogin,"productStoreId",productStoreId);
                result = dispatcher.runSync("importClientProductXMLTemplate", importClientProductTemplateCtx);
                if(UtilValidate.isNotEmpty(result.get("responseMessage")) && result.get("responseMessage").equals("error"))
                {
               	    return ServiceUtil.returnError(result.get("errorMessage").toString());
                }
                messages = (List)result.get("messages");

            } 
            catch (BiffException be) 
            {
                Debug.logError(be, module);
            } 
            catch (Exception exc) 
            {
                Debug.logError(exc, module);
            }
            finally 
            {
                inputWorkbook.delete();
            }
        }
        Map<String, Object> resp = UtilMisc.toMap("messages", (Object) messages);
        return resp;
        
    }   
    
    public static Map<String, Object> exportProductXls(DispatchContext ctx, Map<String, ?> context) {
        _delegator = ctx.getDelegator();
        _dispatcher = ctx.getDispatcher();
        _locale = (Locale) context.get("locale");
        List<String> messages = FastList.newInstance();

        String productStoreId = (String) context.get("productStoreId");
        String browseRootProductCategoryId = (String) context.get("browseRootProductCategoryId");
        String isSampleFile = (String) context.get("sampleFile");
        String fileName="clientProductImport.xls";

        WritableWorkbook workbook = null;
        
       try {
    	        if (UtilValidate.isNotEmpty(isSampleFile) && isSampleFile.equals("Y"))
    	        {
    	        	fileName="sampleClientProductImport.xls";
    	        }
                String importDataPath = FlexibleStringExpander.expandString(OSAFE_ADMIN_PROP.getString("ecommerce-import-data-path"),context);
                File file = new File(importDataPath, "temp" + fileName);
                WorkbookSettings wbSettings = new WorkbookSettings();
                wbSettings.setLocale(new Locale("en", "EN"));
                workbook = Workbook.createWorkbook(file, wbSettings);
                int iRows=0;
                Map mWorkBookHeadCaptions = createWorkBookHeaderCaptions();

                WritableSheet excelSheetModHistory = createWorkBookSheet(workbook,"Mod History", 0);
            	createWorkBookHeaderRow(excelSheetModHistory, buildModHistoryHeader(),mWorkBookHeadCaptions);
            	createWorkBookRow(excelSheetModHistory, _sdf.format(UtilDateTime.nowDate()), 0, 1);
            	createWorkBookRow(excelSheetModHistory, "system", 1, 1);
            	createWorkBookRow(excelSheetModHistory, "Auto Generated Product Import Template", 2, 1);
            	
            	WritableSheet excelSheetCategory = createWorkBookSheet(workbook,"Category", 1);
            	createWorkBookHeaderRow(excelSheetCategory, buildCategoryHeader(),mWorkBookHeadCaptions);
            	
                WritableSheet excelSheetProduct = createWorkBookSheet(workbook,"Product", 2);
            	createWorkBookHeaderRow(excelSheetProduct, buildProductHeader(),mWorkBookHeadCaptions);

                WritableSheet excelSheetProductAssoc = createWorkBookSheet(workbook,"Product Association", 3);
            	createWorkBookHeaderRow(excelSheetProductAssoc, buildProductAssocHeader(),mWorkBookHeadCaptions);
            	
            	WritableSheet excelSheetFacetGroup = createWorkBookSheet(workbook,"Facet Group", 4);
            	createWorkBookHeaderRow(excelSheetFacetGroup, buildProductFacetGroupHeader(),mWorkBookHeadCaptions);
            	
            	WritableSheet excelSheetFacetValue = createWorkBookSheet(workbook,"Facet Value", 5);
            	createWorkBookHeaderRow(excelSheetFacetValue, buildProductFacetValueHeader(),mWorkBookHeadCaptions);

            	WritableSheet excelSheetManufacturer = createWorkBookSheet(workbook,"Manufacturer", 6);
            	createWorkBookHeaderRow(excelSheetManufacturer, buildManufacturerHeader(),mWorkBookHeadCaptions);

    	        if (UtilValidate.isNotEmpty(isSampleFile) && isSampleFile.equals("Y"))
    	        {
                	createWorkBookRow(excelSheetModHistory, _sdf.format(UtilDateTime.nowDate()), 0, 2);
                	createWorkBookRow(excelSheetModHistory, "system", 1, 2);
                	createWorkBookRow(excelSheetModHistory,"(" +  iRows + ") Product Categories Generated", 2, 2);
    	        	
                	createWorkBookRow(excelSheetModHistory, _sdf.format(UtilDateTime.nowDate()), 0, 3);
                	createWorkBookRow(excelSheetModHistory, "system", 1, 3);
                	createWorkBookRow(excelSheetModHistory,"(" +  iRows + ") Products Generated", 2, 3);
                	
                	createWorkBookRow(excelSheetModHistory, _sdf.format(UtilDateTime.nowDate()), 0, 4);
                	createWorkBookRow(excelSheetModHistory, "system", 1, 4);
                	createWorkBookRow(excelSheetModHistory,"(" +  iRows + ") Product Associations Generated", 2, 4);
                	
                	createWorkBookRow(excelSheetModHistory, _sdf.format(UtilDateTime.nowDate()), 0, 5);
                	createWorkBookRow(excelSheetModHistory, "system", 1, 5);
                	createWorkBookRow(excelSheetModHistory,"(" +  iRows + ") Facet Groups Generated", 2, 5);
                	
                	createWorkBookRow(excelSheetModHistory, _sdf.format(UtilDateTime.nowDate()), 0, 6);
                	createWorkBookRow(excelSheetModHistory, "system", 1, 6);
                	createWorkBookRow(excelSheetModHistory,"(" +  iRows + ") Facet Values Generated", 2, 6);

                	createWorkBookRow(excelSheetModHistory, _sdf.format(UtilDateTime.nowDate()), 0, 7);
                	createWorkBookRow(excelSheetModHistory, "system", 1, 7);
                	createWorkBookRow(excelSheetModHistory,"(" +  iRows + ") Manufacturers Generated", 2, 7);
                	
    	        }
    	        else
    	        {
                    List<Map<String, Object>> dataRows = buildProductCategoryDataRows(context);
                    generateProductCategoryXLS(excelSheetCategory, dataRows);
                    createWorkBookRow(excelSheetModHistory, _sdf.format(UtilDateTime.nowDate()), 0, 2);
                    createWorkBookRow(excelSheetModHistory, "system", 1, 2);
                    createWorkBookRow(excelSheetModHistory,"(" + dataRows.size() + ") Product Categories Generated", 2, 2);

                    dataRows = buildProductDataRows(context);
                    generateProductXLS(excelSheetProduct, dataRows);
                    createWorkBookRow(excelSheetModHistory, _sdf.format(UtilDateTime.nowDate()), 0, 3);
                    createWorkBookRow(excelSheetModHistory, "system", 1, 3);
                    createWorkBookRow(excelSheetModHistory,"(" + dataRows.size() + ") Products Generated", 2, 3);

                    dataRows = buildProductAssocDataRows(context);
                    generateProductAssocXLS(excelSheetProductAssoc, dataRows);
                    createWorkBookRow(excelSheetModHistory, _sdf.format(UtilDateTime.nowDate()), 0, 4);
                    createWorkBookRow(excelSheetModHistory, "system", 1, 4);
                    createWorkBookRow(excelSheetModHistory,"(" + dataRows.size() + ") Product Associations Generated", 2, 4);

                    dataRows = buildFacetGroupDataRows(context);
                    generateFacetGroupXLS(excelSheetFacetGroup, dataRows);
                    createWorkBookRow(excelSheetModHistory, _sdf.format(UtilDateTime.nowDate()), 0, 5);
                    createWorkBookRow(excelSheetModHistory, "system", 1, 5);
                    createWorkBookRow(excelSheetModHistory,"(" + dataRows.size() + ") Facet Groups Generated", 2, 5);

                    dataRows = buildFacetValueDataRows(context);
                    generateFacetValueXLS(excelSheetFacetValue, dataRows);
                    createWorkBookRow(excelSheetModHistory, _sdf.format(UtilDateTime.nowDate()), 0, 6);
                    createWorkBookRow(excelSheetModHistory, "system", 1, 6);
                    createWorkBookRow(excelSheetModHistory,"(" + dataRows.size() + ") Facet Values Generated", 2, 6);

                    dataRows = buildManufacturerDataRows(context);
                    generateManufacturerXLS(excelSheetManufacturer, dataRows);
                    createWorkBookRow(excelSheetModHistory, _sdf.format(UtilDateTime.nowDate()), 0, 7);
                    createWorkBookRow(excelSheetModHistory, "system", 1, 7);
                    createWorkBookRow(excelSheetModHistory,"(" +  dataRows.size() + ") Manufacturers Generated", 2, 7);
    	        }
    	        
            	workbook.write();
                workbook.close();
                
                new File(importDataPath, fileName).delete();
                File renameFile =new File(importDataPath, fileName);
                RandomAccessFile out = new RandomAccessFile(renameFile, "rw");
		        InputStream inputStr = new FileInputStream(file);
		        byte[] bytes = new byte[102400];
		        int bytesRead;
		        while ((bytesRead = inputStr.read(bytes)) != -1)
		        {
		            out.write(bytes, 0, bytesRead);
		        }
		        out.close();
		        inputStr.close();
                
                // Gets the sheets from workbook

       }catch (Exception exc) 
        {
                Debug.logError(exc, module);
        }
        finally 
        {
            if (workbook != null) 
            {
                try {
                    workbook.close();
                } catch (Exception exc) {
                    //Debug.warning();
                }
            }
        }
      
        Map<String, Object> resp = UtilMisc.toMap("messages", (Object) messages);
        return resp;
        
    }   

    private static void writeXmlHeader(BufferedWriter bfOutFile) {
    	try {
    		bfOutFile.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    		bfOutFile.newLine();
    		bfOutFile.write("<entity-engine-xml>");
    		bfOutFile.newLine();
            bfOutFile.flush();
    		
    		
    	}
    	 catch (Exception e)
    	 {
    	 }
    }
    private static void writeXmlFooter(BufferedWriter bfOutFile) {
    	try {
    		bfOutFile.write("</entity-engine-xml>");
            bfOutFile.flush();
            bfOutFile.close();
    		
    	}
    	 catch (Exception e)
    	 {
    	 }
    }

    private static List createWorkBookHeaderRow(WritableSheet excelSheet,List headerCols,Map headerCaptions) {
    	
    	try {
            CellView cv = new CellView();
            cv.setAutosize(true);
            WritableFont headerFont = new WritableFont(WritableFont.TIMES, 12, WritableFont.BOLD,false,UnderlineStyle.NO_UNDERLINE, Colour.WHITE);
            WritableCellFormat headerFormat = new WritableCellFormat(headerFont,NumberFormats.TEXT);
            headerFormat.setBackground(Colour.DARK_BLUE);
            int row=0;
            WritableCellFormat textFormat = new WritableCellFormat(NumberFormats.TEXT);
            cv.setFormat(textFormat);
            for (int colCount = 0; colCount < headerCols.size(); colCount++) 
            {
              String headerCaption = (String)headerCaptions.get(headerCols.get(colCount));
              if (UtilValidate.isEmpty(headerCaption))
              {
            	  headerCaption = headerCols.get(colCount).toString();
              }
              Label label  = new Label(colCount, row,headerCaption, headerFormat);
              excelSheet.addCell(label);
              cv.setSize(headerCaption.length());
              excelSheet.setColumnView(colCount, cv);
            }
            
    		
    		
    	} catch (Exception e) {
            Debug.logError(e, module);

    	}
   	    return headerCols;
        
       }
    
    private static void createWorkBookRow(WritableSheet excelSheet,Object rowValue,int colIdx,int rowIdx) {
    	String rowContent="";
        CellView cv = new CellView();
        cv.setAutosize(true);
       	try 
        {
    		if (UtilValidate.isNotEmpty(rowValue) && !(rowValue.toString().equals("null")))
    		{
    			rowContent = rowValue.toString();
    		}
    		else
    		{
    			rowContent="";
    		}
            
            Label label  = new Label(colIdx, rowIdx, rowContent, cellFormat);
            excelSheet.addCell(label);
            cv.setSize(rowContent.length());
            excelSheet.setColumnView(colIdx, cv);
    	}
       	catch (Exception e) 
        {
            Debug.logError(e, module);

    	}
        
       }
    private static WritableSheet createWorkBookSheet(WritableWorkbook workbook,String sheetName,int sheetIdx) {

    	WritableSheet excelSheet=null;    	
    	try 
    	{
    		
            workbook.createSheet(sheetName,sheetIdx);
            excelSheet=workbook.getSheet(sheetIdx);
    		
    	} 
    	catch (Exception e) 
    	{
            Debug.logError(e, module);

    	}
   	    return excelSheet;
        
       }
    
    private static Map createWorkBookHeaderCaptions() {
        Map headerCols = FastMap.newInstance();
   	    headerCols.put("masterProductId","Master Product ID");
   	    headerCols.put("productId","Product ID");
   	    headerCols.put("productCategoryId","Category ID");
   	    headerCols.put("parentCategoryId","Parent Category Id");
   	    headerCols.put("categoryName","Category Name");
   	    headerCols.put("description","Description");
   	    headerCols.put("plpImageName","PLP Image Name");
   	    headerCols.put("plpText","Additional PLP Text");
   	    headerCols.put("pdpText","Additional PDP Text");
   	    headerCols.put("productIdTo","Product Id To");
   	    headerCols.put("productAssocType","Product Association Type");   	    
   	    headerCols.put("internalName","Internal Name");
   	    headerCols.put("productName","Product Name");
   	    headerCols.put("salesPitch","Sales Pitch");
   	    headerCols.put("longDescription","Long Description");
   	    headerCols.put("specialInstructions","Special Instr");
   	    headerCols.put("deliveryInfo","Delivery Info");
   	    headerCols.put("directions","Directions");
   	    headerCols.put("termsConditions","Terms & Cond");
   	    headerCols.put("ingredients","Ingredients");
   	    headerCols.put("warnings","Warnings");
   	    headerCols.put("plpLabel","PLP Label");
   	    headerCols.put("pdpLabel","PDP Label");
   	    headerCols.put("listPrice","List Price");
   	    headerCols.put("defaultPrice","Sales Price");
   	    headerCols.put("selectabeFeature_1","Selectable Features #1");
   	    headerCols.put("plpSwatchImage","Product Swatch Image for PLP [SWATCH_IMAGE]");
   	    headerCols.put("pdpSwatchImage","Product Swatch Image for PDP [SWATCH_IMAGE]");
   	    headerCols.put("selectabeFeature_2","Selectable Features #2");
   	    headerCols.put("selectabeFeature_3","Selectable Features #3");
   	    headerCols.put("selectabeFeature_4","Selectable Features #4");
	    headerCols.put("selectabeFeature_5","Selectable Features #5");
   	    headerCols.put("descriptiveFeature_1","Descriptive Features #1");
   	    headerCols.put("descriptiveFeature_2","Descriptive Features #2");
   	    headerCols.put("descriptiveFeature_3","Descriptive Features #3");
   	    headerCols.put("descriptiveFeature_4","Descriptive Features #4");
	    headerCols.put("descriptiveFeature_5","Descriptive Features #5");
   	    headerCols.put("smallImage","PLP Image");
   	    headerCols.put("smallImageAlt","PLP Image Alt");
   	    headerCols.put("thumbImage","PDP Thumbnail Image");
   	    headerCols.put("largeImage","PDP Regular Image");
   	    headerCols.put("detailImage","PDP Large Image");
   	    headerCols.put("addImage1","PDP Alt-1 Thumbnail Image");
   	    headerCols.put("xtraLargeImage1","PDP Alt-1 Regular Image");
   	    headerCols.put("xtraDetailImage1","PDP Alt-1 Large Image");
   	    headerCols.put("addImage2","PDP Alt-2 Thumbnail Image");
   	    headerCols.put("xtraLargeImage2","PDP Alt-2 Regular Image");
   	    headerCols.put("xtraDetailImage2","PDP Alt-2 Large Image");
   	    headerCols.put("addImage3","PDP Alt-3 Thumbnail Image");
   	    headerCols.put("xtraLargeImage3","PDP Alt-3 Regular Image");
   	    headerCols.put("xtraDetailImage3","PDP Alt-3 Large Image");
   	    headerCols.put("addImage4","PDP Alt-4 Thumbnail Image");
   	    headerCols.put("xtraLargeImage4","PDP Alt-4 Regular Image");
   	    headerCols.put("xtraDetailImage4","PDP Alt-4 Large Image");
   	    headerCols.put("addImage5","PDP Alt-5 Thumbnail Image");
   	    headerCols.put("xtraLargeImage5","PDP Alt-5 Regular Image");
   	    headerCols.put("xtraDetailImage5","PDP Alt-5 Large Image");
   	    headerCols.put("addImage6","PDP Alt-6 Thumbnail Image");
   	    headerCols.put("xtraLargeImage6","PDP Alt-6 Regular Image");
   	    headerCols.put("xtraDetailImage6","PDP Alt-6 Large Image");
   	    headerCols.put("addImage7","PDP Alt-7 Thumbnail Image");
   	    headerCols.put("xtraLargeImage7","PDP Alt-7 Regular Image");
   	    headerCols.put("xtraDetailImage7","PDP Alt-7 Large Image");
   	    headerCols.put("addImage8","PDP Alt-8 Thumbnail Image");
   	    headerCols.put("xtraLargeImage8","PDP Alt-8 Regular Image");
   	    headerCols.put("xtraDetailImage8","PDP Alt-8 Large Image");
   	    headerCols.put("addImage9","PDP Alt-9 Thumbnail Image");
   	    headerCols.put("xtraLargeImage9","PDP Alt-9 Regular Image");
   	    headerCols.put("xtraDetailImage9","PDP Alt-9 Large Image");
   	    headerCols.put("addImage10","PDP Alt-10 Thumbnail Image");
   	    headerCols.put("xtraLargeImage10","PDP Alt-10 Regular Image");
   	    headerCols.put("xtraDetailImage10","PDP Alt-10 Large Image");
   	    headerCols.put("productHeight","Product Height");
   	    headerCols.put("productWidth","Product Width");
   	    headerCols.put("productDepth","Product Depth");
   	    headerCols.put("returnable","Returnable");
   	    headerCols.put("taxable","Taxable");
   	    headerCols.put("chargeShipping","Charge Shipping");
   	    headerCols.put("introDate","Introduction Date");
   	    headerCols.put("discoDate","Discontinued Date");
   	    headerCols.put("manufacturerId","Manufacturer ID");
   	    headerCols.put("partyId","Manufacturer ID");
   	    headerCols.put("date","Date");
   	    headerCols.put("who","Who");
   	    headerCols.put("changes","Changes");
   	    headerCols.put("manufacturerName","Name");
   	    headerCols.put("address1","Address");
   	    headerCols.put("city","City/Town");
   	    headerCols.put("state","State/Province");
   	    headerCols.put("zip","ZipPostCode");
   	    headerCols.put("country","Country");
   	    headerCols.put("shortDescription","Short Description");
   	    headerCols.put("manufacturerImage","Manufacturer Image");
   	    headerCols.put("featureId","Feature");
   	    headerCols.put("facetGroupId","Facet Group ID");
   	    headerCols.put("productCategoryId","Product Category ID");
   	    headerCols.put("sequenceNum","Sequence Num");
	   	headerCols.put("tooltip","Tooltip");
	   	headerCols.put("minDisplay","Min Display");
	   	headerCols.put("maxDisplay","Max Display");
	   	headerCols.put("fromDate","From Date");
	   	headerCols.put("thruDate","Thru Date");
	   	headerCols.put("facetValueId","Facet Value ID");
	   	headerCols.put("plpSwatchUrl","PLP Swatch URL");
	    headerCols.put("pdpSwatchUrl","PDP Swatch URL");
   	    headerCols.put("plpSwatchImage","PLP Swatch Image");
   	    headerCols.put("pdpSwatchImage","PDP Swatch Image");
   	    headerCols.put("goodIdentificationSkuId","SKU#");
   	    headerCols.put("goodIdentificationGoogleId","Google-ID");
   	    headerCols.put("goodIdentificationIsbnId","ISBN");
   	    headerCols.put("goodIdentificationManufacturerId","Manufacturer Number");
   	    headerCols.put("pdpVideoUrl","Product Video");
   	    headerCols.put("pdpVideo360Url","Product 360 Video");
   	    headerCols.put("sequenceNum","Sequence Number");
	    headerCols.put("bfInventoryTot","BF Inventory Total");
	    headerCols.put("bfInventoryWhs","BF Inventory Warehouse");
	    headerCols.put("multiVariant","PDP Select Multi Variant");
	    headerCols.put("weight","Product Weight");
	    headerCols.put("giftMessage","Check Out Gift Message");
	    headerCols.put("pdpQtyMin","PDP Min Quantity");
	    headerCols.put("pdpQtyMax","PDP Max Quantity");
	    headerCols.put("pdpQtyDefault","PDP Default Quantity");
	    headerCols.put("pdpInStoreOnly","PDP In Store Only (Y/N)");
   	    return headerCols;
    }

    private static int createProductCategoryWorkSheetFromEbay(WritableSheet excelSheet,String browseRootProductCategoryId,List dataRows) {
        int iRowIdx=1;
    	try {
    		int iColIdx=0;
            HashMap productCategoryExists = new HashMap();
            for (int i=0 ; i < dataRows.size() ; i++) 
            {
            	Map mRow = (Map)dataRows.get(i);
            	iColIdx=0;
                String productCategoryId = (String)mRow.get("ebayCategoryList");
                String productCategoryDescription = (String)mRow.get("attribute7Value");
                if (UtilValidate.isNotEmpty(productCategoryId) && !productCategoryExists.containsKey(productCategoryId) && !productCategoryExists.containsValue(productCategoryDescription))
                {
                    productCategoryExists.put(productCategoryId, productCategoryDescription);
                    createWorkBookRow(excelSheet,productCategoryId, iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet,browseRootProductCategoryId,iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet,productCategoryDescription,iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet,productCategoryDescription,iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet,productCategoryDescription,iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet,null, iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet,null, iColIdx++, iRowIdx);
                    iRowIdx++;
                }
            }
    		
    	} catch (Exception e) 
    	{
            Debug.logError(e, module);
    		
    	}
    	return (iRowIdx -1);
    }

    private static int createProductWorkSheetFromEbay(WritableSheet excelSheet,String browseRootProductCategoryId,List dataRows) {
        int iRowIdx=1;
    	try {
    		int iColIdx=0;
            HashMap productCategoryExists = new HashMap();
            HashMap productParent = new HashMap();
            List<String> pathElements=null;
            Map productVariants= FastMap.newInstance();
            List productRows= FastList.newInstance();
            for (int i=0 ; i < dataRows.size() ; i++) 
            {
            	Map mRow = (Map)dataRows.get(i);
                String productCategoryId = (String)mRow.get("ebayCategoryList");
                String productCategoryDescription = (String)mRow.get("attribute7Value");
                if (UtilValidate.isNotEmpty(productCategoryId) && !productCategoryExists.containsKey(productCategoryId) && !productCategoryExists.containsValue(productCategoryDescription))
                {
                    productCategoryExists.put(productCategoryId, productCategoryDescription);
                }
            	String productId=(String)mRow.get("inventoryNumber");
            	productId=makeOfbizId(productId);
            	String parentProductId="";
                String parent=(String)mRow.get("variantParentSku");
                if (UtilValidate.isNotEmpty(parent) && "PARENT".equals(parent.toUpperCase()))
                {
                	productRows = FastList.newInstance();
                	parentProductId=productId;
                }
                else
                {
                    productRows.add(mRow);
                }
                productVariants.put(parentProductId, productRows);
                
            }
    		
            for (int i=0 ; i < dataRows.size() ; i++) 
            {
            	Map mRow = (Map)dataRows.get(i);
            	String productId=(String)mRow.get("inventoryNumber");
            	productId=makeOfbizId(productId);
            	if (UtilValidate.isNotEmpty(productId))
            	{
                  String parent=(String)mRow.get("variantParentSku");
                  if (UtilValidate.isNotEmpty(parent) && "PARENT".equals(parent.toUpperCase()))
                  {
                		
              		iColIdx=0;
                  	createWorkBookRow(excelSheet,productId,iColIdx++, iRowIdx);
                	String productCategoryId=(String)mRow.get("attribute7Value");
            		Iterator prodCatIter = productCategoryExists.keySet().iterator();
                	while (prodCatIter.hasNext())
                	{
                		String catKey =(String) prodCatIter.next();
                		String catValue =(String)productCategoryExists.get(catKey);
                		if (catValue.equals(productCategoryId))
                		{
                			productCategoryId=catKey;
                			break;
                		}

                	}
                    createWorkBookRow(excelSheet,productCategoryId, iColIdx++, iRowIdx);
                	createWorkBookRow(excelSheet,(String)mRow.get("inventoryNumber"),iColIdx++, iRowIdx);
                	createWorkBookRow(excelSheet,(String)mRow.get("auctionTitle"),iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet, null, iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet,(String)mRow.get("description"), iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet, null, iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet, null, iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet, null, iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet, null, iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet, null, iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet, null, iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet, null, iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet, null, iColIdx++, iRowIdx);
    /*              createWorkBookRow(excelSheet, getProductContent(productId,"SPECIALINSTRUCTIONS",lProductContent), iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet, getProductContent(productId,"DELIVERY_INFO",lProductContent), iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet, getProductContent(productId,"DIRECTIONS",lProductContent), iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet, getProductContent(productId,"TERMS_AND_CONDS",lProductContent), iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet, getProductContent(productId,"INGREDIENTS",lProductContent), iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet, getProductContent(productId,"WARNINGS",lProductContent), iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet, getProductContent(productId,"PLP_LABEL",lProductContent), iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet, getProductContent(productId,"PDP_LABEL",lProductContent), iColIdx++, iRowIdx);
    */                
                    createWorkBookRow(excelSheet,(String)mRow.get("buyItNowPrice"), iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet,(String)mRow.get("retailPrice"), iColIdx++, iRowIdx);
                    List prodVariants = (List)productVariants.get(productId);
                    if (prodVariants.isEmpty())
                    {
                    	prodVariants.add(mRow);
                    	iColIdx = createWorkBookProductFeaturesFromEbay(excelSheet,prodVariants,iColIdx,iRowIdx);
                    }
                    else
                    {
                    	iColIdx = createWorkBookProductFeaturesFromEbay(excelSheet, prodVariants, iColIdx, iRowIdx);
                    }
                    String pictureUrls =(String)mRow.get("pictureUrls");
                    String[] imageURLs = pictureUrls.split(",");
//                    imageURL =getProductContent(productId,"SMALL_IMAGE_URL",lProductContent);
                    if (UtilValidate.isNotEmpty(imageURLs[0]))
                    {
                        pathElements = StringUtil.split(imageURLs[0], "/");
                        createWorkBookRow(excelSheet,pathElements.get(pathElements.size() - 1), iColIdx++, iRowIdx);
                    }
                    else
                    {
                        createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    	
                    }
//                    imageURL =getProductContent(productId,"THUMBNAIL_IMAGE_URL",lProductContent);
                    if (UtilValidate.isNotEmpty(imageURLs[0]))
                    {
                        pathElements = StringUtil.split(imageURLs[0], "/");
                        createWorkBookRow(excelSheet,pathElements.get(pathElements.size() - 1), iColIdx++, iRowIdx);
                    }
                    else
                    {
                        createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    	
                    }
//                    imageURL =getProductContent(productId,"LARGE_IMAGE_URL",lProductContent);
                    if (UtilValidate.isNotEmpty(imageURLs[0]))
                    {
                        pathElements = StringUtil.split(imageURLs[0], "/");
                        createWorkBookRow(excelSheet,pathElements.get(pathElements.size() - 1), iColIdx++, iRowIdx);
                    }
                    else
                    {
                        createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    	
                    }
//                    imageURL =getProductContent(productId,"DETAIL_IMAGE_URL",lProductContent);
                    if (UtilValidate.isNotEmpty(imageURLs[0]))
                    {
                        pathElements = StringUtil.split(imageURLs[0], "/");
                        createWorkBookRow(excelSheet,pathElements.get(pathElements.size() - 1), iColIdx++, iRowIdx);
                    }
                    else
                    {
                        createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    	
                    }
//                    imageURL =getProductContent(productId,"ADDITIONAL_IMAGE_1",lProductContent);
                    if (imageURLs.length > 1 && UtilValidate.isNotEmpty(imageURLs[1]))
                    {
                        pathElements = StringUtil.split(imageURLs[1], "/");
                        createWorkBookRow(excelSheet,pathElements.get(pathElements.size() - 1), iColIdx++, iRowIdx);
                    }
                    else
                    {
                        createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    	
                    }
//                    imageURL =getProductContent(productId,"XTRA_IMG_1_LARGE",lProductContent);
                    if (imageURLs.length > 1 && UtilValidate.isNotEmpty(imageURLs[1]))
                    {
                        pathElements = StringUtil.split(imageURLs[1], "/");
                        createWorkBookRow(excelSheet,pathElements.get(pathElements.size() - 1), iColIdx++, iRowIdx);
                    }
                    else
                    {
                        createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    	
                    }
//                    imageURL =getProductContent(productId,"XTRA_IMG_1_DETAIL",lProductContent);
                    if (imageURLs.length > 1 && UtilValidate.isNotEmpty(imageURLs[1]))
                    {
                        pathElements = StringUtil.split(imageURLs[1], "/");
                        createWorkBookRow(excelSheet,pathElements.get(pathElements.size() - 1), iColIdx++, iRowIdx);
                    }
                    else
                    {
                        createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    	
                    }
//                    imageURL =getProductContent(productId,"ADDITIONAL_IMAGE_2",lProductContent);
                    if (imageURLs.length > 2 && UtilValidate.isNotEmpty(imageURLs[2]))
                    {
                        pathElements = StringUtil.split(imageURLs[2], "/");
                        createWorkBookRow(excelSheet,pathElements.get(pathElements.size() - 1), iColIdx++, iRowIdx);
                    }
                    else
                    {
                        createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    	
                    }
//                    imageURL =getProductContent(productId,"XTRA_IMG_2_LARGE",lProductContent);
                    if (imageURLs.length > 2 && UtilValidate.isNotEmpty(imageURLs[2]))
                    {
                        pathElements = StringUtil.split(imageURLs[2], "/");
                        createWorkBookRow(excelSheet,pathElements.get(pathElements.size() - 1), iColIdx++, iRowIdx);
                    }
                    else
                    {
                        createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    	
                    }
//                    imageURL =getProductContent(productId,"XTRA_IMG_2_DETAIL",lProductContent);
                    if (imageURLs.length > 2 && UtilValidate.isNotEmpty(imageURLs[2]))
                    {
                        pathElements = StringUtil.split(imageURLs[2], "/");
                        createWorkBookRow(excelSheet,pathElements.get(pathElements.size() - 1), iColIdx++, iRowIdx);
                    }
                    else
                    {
                        createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    	
                    }
//                    imageURL =getProductContent(productId,"ADDITIONAL_IMAGE_3",lProductContent);
                    if (imageURLs.length > 3 && UtilValidate.isNotEmpty(imageURLs[3]))
                    {
                        pathElements = StringUtil.split(imageURLs[3], "/");
                        createWorkBookRow(excelSheet,pathElements.get(pathElements.size() - 1), iColIdx++, iRowIdx);
                    }
                    else
                    {
                        createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    	
                    }
//                    imageURL =getProductContent(productId,"XTRA_IMG_3_LARGE",lProductContent);
                    if (imageURLs.length > 3 && UtilValidate.isNotEmpty(imageURLs[3]))
                    {
                        pathElements = StringUtil.split(imageURLs[3], "/");
                        createWorkBookRow(excelSheet,pathElements.get(pathElements.size() - 1), iColIdx++, iRowIdx);
                    }
                    else
                    {
                        createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    	
                    }
//                    imageURL =getProductContent(productId,"XTRA_IMG_3_DETAIL",lProductContent);
                    if (imageURLs.length > 3 && UtilValidate.isNotEmpty(imageURLs[3]))
                    {
                        pathElements = StringUtil.split(imageURLs[3], "/");
                        createWorkBookRow(excelSheet,pathElements.get(pathElements.size() - 1), iColIdx++, iRowIdx);
                    }
                    else
                    {
                        createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    	
                    }
//                    imageURL =getProductContent(productId,"ADDITIONAL_IMAGE_4",lProductContent);
                    if (imageURLs.length > 4 && UtilValidate.isNotEmpty(imageURLs[4]))
                    {
                        pathElements = StringUtil.split(imageURLs[4], "/");
                        createWorkBookRow(excelSheet,pathElements.get(pathElements.size() - 1), iColIdx++, iRowIdx);
                    }
                    else
                    {
                        createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    	
                    }
//                    imageURL =getProductContent(productId,"XTRA_IMG_4_LARGE",lProductContent);
                    if (imageURLs.length > 4 && UtilValidate.isNotEmpty(imageURLs[4]))
                    {
                        pathElements = StringUtil.split(imageURLs[4], "/");
                        createWorkBookRow(excelSheet,pathElements.get(pathElements.size() - 1), iColIdx++, iRowIdx);
                    }
                    else
                    {
                        createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    	
                    }
//                    imageURL =getProductContent(productId,"XTRA_IMG_4_DETAIL",lProductContent);
                    if (imageURLs.length > 4 && UtilValidate.isNotEmpty(imageURLs[4]))
                    {
                        pathElements = StringUtil.split(imageURLs[4], "/");
                        createWorkBookRow(excelSheet,pathElements.get(pathElements.size() - 1), iColIdx++, iRowIdx);
                    }
                    else
                    {
                        createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
                    	
                    }

                	createWorkBookRow(excelSheet,null,iColIdx++, iRowIdx);
                	createWorkBookRow(excelSheet,(String)mRow.get("width"),iColIdx++, iRowIdx);
                	createWorkBookRow(excelSheet,(String)mRow.get("length"),iColIdx++, iRowIdx);
                	createWorkBookRow(excelSheet,(String)mRow.get("returnable"),iColIdx++, iRowIdx);
                	createWorkBookRow(excelSheet,null,iColIdx++, iRowIdx);
                	createWorkBookRow(excelSheet,null,iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet,null, iColIdx++, iRowIdx);
                    createWorkBookRow(excelSheet,null, iColIdx++, iRowIdx);
                	createWorkBookRow(excelSheet,null,iColIdx++, iRowIdx);
                    
                    iRowIdx++;
                  }
            	}
            }
    	} catch (Exception e) 
    	{
            Debug.logError(e, module);
    		
    	}
    	return (iRowIdx -1);
    }

    private static int createWorkBookProductFeaturesFromEbay(WritableSheet excelSheet,List<Map> productFeatures,int iColIdx,int iRowIdx) {
   	 
    	try {
        	StringBuffer selFeatures =new StringBuffer();
        	int listSize=productFeatures.size();
        	int iListIdx=0;
        	int iSelFeatCnt=0;
        	int iDesFeatCnt=0;
        	String sLastFeatureValue="";
    		if (UtilValidate.isNotEmpty(productFeatures))
    		{
                for (Map productFeatureAndAppl : productFeatures) 
                {
    	        	String productFeatureTypeId=(String)productFeatureAndAppl.get("attribute2Name");
    	        	if (UtilValidate.isNotEmpty(productFeatureTypeId))
    	        	{
    	        		productFeatureTypeId=productFeatureTypeId.trim();
    	        	}
                	if (UtilValidate.isNotEmpty(productFeatureTypeId) && iListIdx ==0)
                	{
                		selFeatures.append(productFeatureTypeId + ":");
                	}
    	        	String productFeatureValue=(String)productFeatureAndAppl.get("attribute2Value");
    	        	if (UtilValidate.isNotEmpty(productFeatureValue))
    	        	{
    	        		productFeatureValue=productFeatureValue.trim();
    	        	}
    	        	
    	        	if (UtilValidate.isNotEmpty(productFeatureValue) && !productFeatureValue.equals(sLastFeatureValue))
    	        	{
                    	sLastFeatureValue=productFeatureValue;
                		selFeatures.append(productFeatureValue + ",");
                    	iListIdx++;
    	        		
    	        	}
                }
    			
            	if (iListIdx > 0)
            	{
                    selFeatures.setLength(selFeatures.length() - 1);
                    createWorkBookRow(excelSheet,selFeatures.toString(), iColIdx++, iRowIdx);
                	iSelFeatCnt++;
            	}
            	iListIdx=0;
            	selFeatures.setLength(0);
                for (Map productFeatureAndAppl : productFeatures) 
                {
    	        	String productFeatureTypeId=(String)productFeatureAndAppl.get("attribute5Name");
    	        	if (UtilValidate.isNotEmpty(productFeatureTypeId))
    	        	{
    	        		productFeatureTypeId=productFeatureTypeId.trim();
    	        	}
                	if (UtilValidate.isNotEmpty(productFeatureTypeId) && iListIdx ==0)
                	{
                		selFeatures.append(productFeatureTypeId + ":");
                	}
    	        	String productFeatureValue=(String)productFeatureAndAppl.get("attribute5Value");
    	        	if (UtilValidate.isNotEmpty(productFeatureValue))
    	        	{
    	        		productFeatureValue=productFeatureValue.trim();
    	        	}
    	        	if (UtilValidate.isNotEmpty(productFeatureValue) && !productFeatureValue.equals(sLastFeatureValue))
    	        	{
                    	sLastFeatureValue=productFeatureValue;
                		selFeatures.append(productFeatureValue + ",");
                    	iListIdx++;
    	        		
    	        	}
                }
                
            	if (iListIdx > 0)
            	{
                    selFeatures.setLength(selFeatures.length() - 1);
                    createWorkBookRow(excelSheet,selFeatures.toString(), iColIdx++, iRowIdx);
                	iSelFeatCnt++;
            	}
                for (int i=iSelFeatCnt;i < 3;i++)
                {
                	createWorkBookRow(excelSheet,"",iColIdx++,iRowIdx);
                }
                
            	iListIdx=0;
            	selFeatures.setLength(0);
                for (Map productFeatureAndAppl : productFeatures) 
                {
    	        	String productFeatureTypeId=(String)productFeatureAndAppl.get("attribute6Name");
    	        	if (UtilValidate.isNotEmpty(productFeatureTypeId))
    	        	{
    	        		productFeatureTypeId=productFeatureTypeId.trim();
    	        	}
                	if (UtilValidate.isNotEmpty(productFeatureTypeId) && iListIdx ==0)
                	{
                		selFeatures.append(productFeatureTypeId + ":");
                	}
    	        	String productFeatureValue=(String)productFeatureAndAppl.get("attribute6Value");
    	        	if (UtilValidate.isNotEmpty(productFeatureValue))
    	        	{
    	        		productFeatureValue=productFeatureValue.trim();
    	        	}
    	        	if (UtilValidate.isNotEmpty(productFeatureValue) && !productFeatureValue.equals(sLastFeatureValue))
    	        	{
                    	sLastFeatureValue=productFeatureValue;
                		selFeatures.append(productFeatureValue + ",");
                    	iListIdx++;
    	        		
    	        	}
                }
                
            	if (iListIdx > 0)
            	{
                    selFeatures.setLength(selFeatures.length() - 1);
                    createWorkBookRow(excelSheet,selFeatures.toString(), iColIdx++, iRowIdx);
                	iDesFeatCnt++;
            	}
            	iListIdx=0;
            	selFeatures.setLength(0);
                for (Map productFeatureAndAppl : productFeatures) 
                {
    	        	String productFeatureTypeId=(String)productFeatureAndAppl.get("attribute10Name");
    	        	if (UtilValidate.isNotEmpty(productFeatureTypeId))
    	        	{
    	        		productFeatureTypeId=productFeatureTypeId.trim();
    	        	}
                	if (UtilValidate.isNotEmpty(productFeatureTypeId) && iListIdx ==0)
                	{
                		selFeatures.append(productFeatureTypeId + ":");
                	}
    	        	String productFeatureValue=(String)productFeatureAndAppl.get("attribute10Value");
    	        	if (UtilValidate.isNotEmpty(productFeatureValue))
    	        	{
    	        		productFeatureValue=productFeatureValue.trim();
    	        	}
    	        	if (UtilValidate.isNotEmpty(productFeatureValue) && !productFeatureValue.equals(sLastFeatureValue))
    	        	{
                    	sLastFeatureValue=productFeatureValue;
                		selFeatures.append(productFeatureValue + ",");
                    	iListIdx++;
    	        		
    	        	}
                }
                
            	if (iListIdx > 0)
            	{
                    selFeatures.setLength(selFeatures.length() - 1);
                    createWorkBookRow(excelSheet,selFeatures.toString(), iColIdx++, iRowIdx);
                	iDesFeatCnt++;
            	}

            	iListIdx=0;
            	selFeatures.setLength(0);
                for (Map productFeatureAndAppl : productFeatures) 
                {
    	        	String productFeatureTypeId=(String)productFeatureAndAppl.get("attribute11Name");
    	        	if (UtilValidate.isNotEmpty(productFeatureTypeId))
    	        	{
    	        		productFeatureTypeId=productFeatureTypeId.trim();
    	        	}
                	if (UtilValidate.isNotEmpty(productFeatureTypeId) && iListIdx ==0)
                	{
                		selFeatures.append(productFeatureTypeId + ":");
                	}
    	        	String productFeatureValue=(String)productFeatureAndAppl.get("attribute11Value");
    	        	if (UtilValidate.isNotEmpty(productFeatureValue))
    	        	{
    	        		productFeatureValue=productFeatureValue.trim();
    	        	}
    	        	if (UtilValidate.isNotEmpty(productFeatureValue) && !productFeatureValue.equals(sLastFeatureValue))
    	        	{
                    	sLastFeatureValue=productFeatureValue;
                		selFeatures.append(productFeatureValue + ",");
                    	iListIdx++;
    	        		
    	        	}
                }
                
            	if (iListIdx > 0)
            	{
                    selFeatures.setLength(selFeatures.length() - 1);
                    createWorkBookRow(excelSheet,selFeatures.toString(), iColIdx++, iRowIdx);
                	iDesFeatCnt++;
            	}
            	
    		}
           
        	for (int i=iDesFeatCnt;i < 3;i++)
        	{
                createWorkBookRow(excelSheet,"", iColIdx++, iRowIdx);
        		
        	}
    		
    	} catch (Exception e) 
    	{
            Debug.logError(e, module);

    	}
   	    return iColIdx;
        
       }

    public static List buildCategoryHeader() {
        List headerCols = FastList.newInstance();
   	    headerCols.add("productCategoryId");
   	    headerCols.add("parentCategoryId");
   	    headerCols.add("categoryName");
   	    headerCols.add("description");
   	    headerCols.add("longDescription");
   	    headerCols.add("plpImageName");
   	    headerCols.add("plpText");
   	    headerCols.add("pdpText");
   	    headerCols.add("fromDate");
   	    headerCols.add("thruDate");
   	    
   	    return headerCols;
        
       }
    public static List buildProductHeader() {
        List headerCols = FastList.newInstance();
   	    headerCols.add("masterProductId");
   	    headerCols.add("productId");
   	    headerCols.add("productCategoryId");
   	    headerCols.add("internalName");
   	    headerCols.add("productName");
   	    headerCols.add("salesPitch");
   	    headerCols.add("longDescription");
   	    headerCols.add("specialInstructions");
   	    headerCols.add("deliveryInfo");
   	    headerCols.add("directions");
   	    headerCols.add("termsConditions");
   	    headerCols.add("ingredients");
   	    headerCols.add("warnings");
   	    headerCols.add("plpLabel");
   	    headerCols.add("pdpLabel");
   	    headerCols.add("listPrice");
   	    headerCols.add("defaultPrice");
   	    headerCols.add("selectabeFeature_1");
   	    headerCols.add("plpSwatchImage");
   	    headerCols.add("pdpSwatchImage");
   	    headerCols.add("selectabeFeature_2");
   	    headerCols.add("selectabeFeature_3");
   	    headerCols.add("selectabeFeature_4");
	    headerCols.add("selectabeFeature_5");
   	    headerCols.add("descriptiveFeature_1");
   	    headerCols.add("descriptiveFeature_2");
   	    headerCols.add("descriptiveFeature_3");
   	    headerCols.add("descriptiveFeature_4");
	    headerCols.add("descriptiveFeature_5");
   	    headerCols.add("smallImage");
   	    headerCols.add("smallImageAlt");
   	    headerCols.add("thumbImage");
   	    headerCols.add("largeImage");
   	    headerCols.add("detailImage");
   	    headerCols.add("addImage1");
   	    headerCols.add("xtraLargeImage1");
   	    headerCols.add("xtraDetailImage1");
   	    headerCols.add("addImage2");
   	    headerCols.add("xtraLargeImage2");
   	    headerCols.add("xtraDetailImage2");
   	    headerCols.add("addImage3");
   	    headerCols.add("xtraLargeImage3");
   	    headerCols.add("xtraDetailImage3");
   	    headerCols.add("addImage4");
   	    headerCols.add("xtraLargeImage4");
   	    headerCols.add("xtraDetailImage4");
   	    headerCols.add("addImage5");
   	    headerCols.add("xtraLargeImage5");
   	    headerCols.add("xtraDetailImage5");
   	    headerCols.add("addImage6");
   	    headerCols.add("xtraLargeImage6");
   	    headerCols.add("xtraDetailImage6");
   	    headerCols.add("addImage7");
   	    headerCols.add("xtraLargeImage7");
   	    headerCols.add("xtraDetailImage7");
   	    headerCols.add("addImage8");
   	    headerCols.add("xtraLargeImage8");
   	    headerCols.add("xtraDetailImage8");
   	    headerCols.add("addImage9");
   	    headerCols.add("xtraLargeImage9");
   	    headerCols.add("xtraDetailImage9");
   	    headerCols.add("addImage10");
   	    headerCols.add("xtraLargeImage10");
   	    headerCols.add("xtraDetailImage10");
   	    headerCols.add("productHeight");
   	    headerCols.add("productWidth");
   	    headerCols.add("productDepth");
   	    headerCols.add("returnable");
   	    headerCols.add("taxable");
   	    headerCols.add("chargeShipping");
   	    headerCols.add("introDate");
   	    headerCols.add("discoDate");
   	    headerCols.add("manufacturerId");
   	    headerCols.add("goodIdentificationSkuId");
   	    headerCols.add("goodIdentificationGoogleId");
   	    headerCols.add("goodIdentificationIsbnId");
   	    headerCols.add("goodIdentificationManufacturerId");
   	    headerCols.add("pdpVideoUrl");
   	    headerCols.add("pdpVideo360Url");
   	    headerCols.add("sequenceNum");
	    headerCols.add("bfInventoryTot");
	    headerCols.add("bfInventoryWhs");
   	    headerCols.add("multiVariant");
   	    headerCols.add("weight");
   	    headerCols.add("giftMessage");
   	    headerCols.add("pdpQtyMin");
   	    headerCols.add("pdpQtyMax");
   	    headerCols.add("pdpQtyDefault");
   	    headerCols.add("pdpInStoreOnly");
   	    return headerCols;
    }

    public static List buildManufacturerHeader() 
    {
        List headerCols = FastList.newInstance();
   	    headerCols.add("partyId");
   	    headerCols.add("manufacturerName");
   	    headerCols.add("address1");
   	    headerCols.add("city");
   	    headerCols.add("state");
   	    headerCols.add("zip");
   	    headerCols.add("country");
   	    headerCols.add("shortDescription");
   	    headerCols.add("longDescription");
   	    headerCols.add("manufacturerImage");
   	    return headerCols;
    }    

    public static List buildProductFacetGroupHeader() {
        List headerCols = FastList.newInstance();
   	    headerCols.add("facetGroupId");
   	    headerCols.add("description");
   	    headerCols.add("productCategoryId");
   	    headerCols.add("sequenceNum");
   	    headerCols.add("tooltip");
   	    headerCols.add("minDisplay");
	   	headerCols.add("maxDisplay");
	   	headerCols.add("fromDate");
	   	headerCols.add("thruDate");
   	    return headerCols;
       }
    
    public static List buildProductFacetValueHeader() {
        List headerCols = FastList.newInstance();
   	    headerCols.add("facetGroupId");
   	    headerCols.add("facetValueId");
   	    headerCols.add("description");
   	    headerCols.add("sequenceNum");
   	    headerCols.add("plpSwatchUrl");
   	    headerCols.add("pdpSwatchUrl");
	   	headerCols.add("fromDate");
	   	headerCols.add("thruDate");
   	    return headerCols;
       }

    public static List buildProductAssocHeader() {
        List headerCols = FastList.newInstance();
   	    headerCols.add("productId");
   	    headerCols.add("productIdTo");
   	    headerCols.add("productAssocType");
   	    headerCols.add("fromDate");
   	    headerCols.add("thruDate");
   	    return headerCols;
        
       }

    private static List buildModHistoryHeader() {
        List headerCols = FastList.newInstance();
   	    headerCols.add("date");
   	    headerCols.add("who");
   	    headerCols.add("changes");
   	    return headerCols;
        
       }
    
    public static List buildEbayProductHeader() {
        List headerCols = FastList.newInstance();
   	    headerCols.add("auctionTitle");
   	    headerCols.add("inventoryNumber");
   	    headerCols.add("quantityUpdateType");
   	    headerCols.add("quantity");
   	    headerCols.add("startingBid");
   	    headerCols.add("reserve");
   	    headerCols.add("weight");
   	    headerCols.add("isbn");
   	    headerCols.add("upc");
   	    headerCols.add("ean");
   	    headerCols.add("asin");
   	    headerCols.add("mpn");
   	    headerCols.add("shortDescription");
   	    headerCols.add("description");
   	    headerCols.add("manufacturer");
   	    headerCols.add("brand");
   	    headerCols.add("condition");
   	    headerCols.add("warranty");
   	    headerCols.add("sellerCost");
   	    headerCols.add("profitMargin");
   	    headerCols.add("buyItNowPrice");
   	    headerCols.add("retailPrice");
   	    headerCols.add("secondChanceOfferPrice");
   	    headerCols.add("pictureUrls");
   	    headerCols.add("taxProduct");
   	    headerCols.add("supplierCode");
   	    headerCols.add("supplierPo");
   	    headerCols.add("warehouseLocation");
   	    headerCols.add("receivedInventory");
   	    headerCols.add("inventorySubtitle");
   	    headerCols.add("relationshipName");
   	    headerCols.add("variantParentSku");
   	    headerCols.add("adTemplateName");
   	    headerCols.add("postingTemplateName");
   	    headerCols.add("scheduleName");
   	    headerCols.add("ebayCategoryList");
   	    headerCols.add("ebayStoreCategoryName");
   	    headerCols.add("labels");
   	    headerCols.add("dcCode");
   	    headerCols.add("doNotConsolidate");
   	    headerCols.add("channelAdvisorStoreTitle");
   	    headerCols.add("channelAdvisorStoreDescription");
   	    headerCols.add("storeMetaDescription");
   	    headerCols.add("channelAdvisorStorePrice");
   	    headerCols.add("channelAdvisorStoreCategoryId");
   	    headerCols.add("classification");
   	    headerCols.add("attribute1Name");
   	    headerCols.add("attribute1Value");
   	    headerCols.add("attribute2Name");
   	    headerCols.add("attribute2Value");
   	    headerCols.add("attribute3Name");
   	    headerCols.add("attribute3Value");
   	    headerCols.add("attribute4Name");
   	    headerCols.add("attribute4Value");
   	    headerCols.add("attribute5Name");
   	    headerCols.add("attribute5Value");
   	    headerCols.add("attribute6Name");
   	    headerCols.add("attribute6Value");
   	    headerCols.add("attribute7Name");
   	    headerCols.add("attribute7Value");
   	    headerCols.add("attribute8Name");
   	    headerCols.add("attribute8Value");
   	    headerCols.add("attribute9Name");
   	    headerCols.add("attribute9Value");
   	    headerCols.add("attribute10Name");
   	    headerCols.add("attribute10Value");
   	    headerCols.add("attribute11Name");
   	    headerCols.add("attribute11Value");
   	    headerCols.add("attribute12Name");
   	    headerCols.add("attribute12Value");
   	    headerCols.add("attribute13Name");
   	    headerCols.add("attribute13Value");
   	    headerCols.add("attribute14Name");
   	    headerCols.add("attribute14Value");
   	    headerCols.add("attribute15Name");
   	    headerCols.add("attribute15Value");
   	    headerCols.add("attribute16Name");
   	    headerCols.add("attribute16Value");
   	    headerCols.add("attribute17Name");
   	    headerCols.add("attribute17Value");
   	    headerCols.add("attribute18Name");
   	    headerCols.add("attribute18Value");
   	    headerCols.add("attribute19Name");
   	    headerCols.add("attribute19Value");
   	    headerCols.add("attribute20Name");
   	    headerCols.add("attribute20Value");
   	    headerCols.add("attribute21Name");
   	    headerCols.add("attribute21Value");
   	    headerCols.add("attribute22Name");
   	    headerCols.add("attribute22Value");
   	    headerCols.add("attribute23Name");
   	    headerCols.add("attribute23Value");
   	    headerCols.add("attribute24Name");
   	    headerCols.add("attribute24Value");
   	    headerCols.add("attribute25Name");
   	    headerCols.add("attribute25Value");
   	    headerCols.add("attribute26Name");
   	    headerCols.add("attribute26Value");
   	    headerCols.add("attribute27Name");
   	    headerCols.add("attribute27Value");
   	    headerCols.add("attribute28Name");
   	    headerCols.add("attribute28Value");
   	    headerCols.add("attribute29Name");
   	    headerCols.add("attribute29Value");
   	    headerCols.add("attribute30Name");
   	    headerCols.add("attribute30Value");
   	    headerCols.add("attribute31Name");
   	    headerCols.add("attribute31Value");
   	    headerCols.add("attribute32Name");
   	    headerCols.add("attribute32Value");
   	    headerCols.add("attribute33Name");
   	    headerCols.add("attribute33Value");
   	    headerCols.add("attribute34Name");
   	    headerCols.add("attribute34Value");
   	    headerCols.add("attribute35Name");
   	    headerCols.add("attribute35Value");
   	    headerCols.add("attribute36Name");
   	    headerCols.add("attribute36Value");
   	    headerCols.add("attribute37Name");
   	    headerCols.add("attribute37Value");
   	    headerCols.add("attribute38Name");
   	    headerCols.add("attribute39Value");
   	    headerCols.add("attribute40Name");
   	    headerCols.add("attribute40Value");
   	    headerCols.add("harmonizedCode");
   	    headerCols.add("height");
   	    headerCols.add("length");
   	    headerCols.add("width");
   	    headerCols.add("shipZoneName");
   	    headerCols.add("shipCarrierCode");
   	    headerCols.add("shipClassCode");
   	    headerCols.add("shipRateFirstItem");
   	    headerCols.add("shipHandlingFirstItem");
   	    headerCols.add("shipRateAdditionalItem");
   	    headerCols.add("shipHandlingAdditionalItem");
   	    headerCols.add("recommendedBrowseNode");
   	    
   	    return headerCols;
        
       }
    
    public static List buildProductRatingHeader() 
    {
        List headerCols = FastList.newInstance();
   	    headerCols.add("productId");
   	    headerCols.add("ratingScore");
   	    
   	    return headerCols;
        
    }
    
    public static List buildStoreHeader() 
    {
	    List headerCols = FastList.newInstance();
	   	headerCols.add("storeId");
	   	headerCols.add("storeCode");
	   	headerCols.add("storeName");
	   	headerCols.add("country");
	   	headerCols.add("address1");
	   	headerCols.add("address2");
	   	headerCols.add("address3");
	   	headerCols.add("cityOrTown");
	   	headerCols.add("stateOrProvince");
	   	headerCols.add("zipOrPostcode");
	   	headerCols.add("telephoneNumber");
	   	headerCols.add("status");
	   	headerCols.add("openingHours");
	   	headerCols.add("storeNotice");
	   	headerCols.add("contentSpot");
	   	headerCols.add("geoCodeLong");
	   	headerCols.add("geoCodeLat");
   	
   	    return headerCols;
    }
    
    public static List buildOrderStatusUpdateHeader() 
    {
	    List headerCols = FastList.newInstance();
	   	headerCols.add("orderId");
	   	headerCols.add("orderStatus");
	   	headerCols.add("orderShipDate");
	   	headerCols.add("orderShipCarrier");
	   	headerCols.add("orderShipMethod");
	   	headerCols.add("orderTrackingNumber");
	   	headerCols.add("orderNote");
	   	
   	    return headerCols;
    }
    
    public static List buildDataRows(List headerCols,Sheet s) {
		List dataRows = FastList.newInstance();

		try {

            for (int rowCount = 1 ; rowCount < s.getRows() ; rowCount++) 
            {
            	Cell[] row = s.getRow(rowCount);
             if (row.length > 0) 
             {
            	Map mRows = FastMap.newInstance();
                for (int colCount = 0; colCount < headerCols.size(); colCount++) {
                	String colContent=null;
                
                	 try {
                		 colContent=row[colCount].getContents().toString();
                	 }
                	   catch (Exception e) {
                		   colContent="";
                		   
                	   }
                  mRows.put(headerCols.get(colCount),colContent);
                }
                //mRows = formatProductXLSData(mRows);
                dataRows.add(mRows);
             }
            }
			
    		
    
    	}
      	 catch (Exception e) {
   	         }
      	return dataRows;
       }

    private static void buildProductCategory(List dataRows,String xmlDataDirPath,String loadImagesDirPath, String imageUrl) {

        File fOutFile =null;
        BufferedWriter bwOutFile=null;
        String categoryImageName=null;
		try {
			
	        fOutFile = new File(xmlDataDirPath, "000-ProductCategory.xml");
            if (fOutFile.createNewFile()) 
            {
            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));

                writeXmlHeader(bwOutFile);
                
                for (int i=0 ; i < dataRows.size() ; i++) 
                {
                    StringBuilder  rowString = new StringBuilder();
                    rowString.append("<" + "ProductCategory" + " ");
	            	 Map mRow = (Map)dataRows.get(i);
                     rowString.append("productCategoryId" + "=\"" + mRow.get("productCategoryId") + "\" ");
                     rowString.append("productCategoryTypeId" + "=\"" + "CATALOG_CATEGORY" + "\" ");
                     rowString.append("primaryParentCategoryId" + "=\"" + mRow.get("parentCategoryId") + "\" ");
                     rowString.append("categoryName" + "=\"" + (String)mRow.get("categoryName") + "\" ");
                     if(mRow.get("description") != null) 
                     {
                    	 rowString.append("description" + "=\"" + (String)mRow.get("description") + "\" "); 
                     }
                     if(mRow.get("longDescription") != null) 
                     {
                    	 rowString.append("longDescription" + "=\"" + (String)mRow.get("longDescription") + "\" "); 
                     }
                     
                     categoryImageName=(String)mRow.get("plpImageName");
     	             
                     if (UtilValidate.isNotEmpty(categoryImageName))
                     {
                    	 if (!UtilValidate.isUrl(categoryImageName)) 
                  		 {
                    		 Map<Object, Object> imageLocationMap = new HashMap<Object, Object>();
                        	 
                         	 for(Map<Object, Object> imageLocationPref : imageLocationPrefList) 
                         	 {
                         		imageLocationMap.put(imageLocationPref.get("key"), imageLocationPref.get("value"));
                         	 }
                         	 String defaultImageDirectory = (String)imageLocationMap.get("DEFAULT_IMAGE_DIRECTORY");
                         	 if(UtilValidate.isNotEmpty(defaultImageDirectory)) 
                         	 {
                         		categoryImageName = defaultImageDirectory + categoryImageName;
                         	 } 
                  		 }
                         
                         rowString.append("categoryImageUrl" + "=\"" + categoryImageName + "\" ");
                     }
                     else
                     {
                         rowString.append("categoryImageUrl" + "=\"" + "" + "\" ");
                     }
                     rowString.append("linkOneImageUrl" + "=\"" + "" + "\" ");
                     rowString.append("linkTwoImageUrl" + "=\"" + "" + "\" ");
                     rowString.append("detailScreen" + "=\"" + "" + "\" ");
                     rowString.append("/>");
                    bwOutFile.write(rowString.toString());
                    bwOutFile.newLine();
                    try
                    {
	                    String fromDate = _sdf.format(UtilDateTime.nowTimestamp());
	                    if (UtilValidate.isNotEmpty(mRow.get("fromDate")))
	                    {
	                    	String sFromDate=(String)mRow.get("fromDate");
	                   	 	java.util.Date formattedFromDate=OsafeAdminUtil.validDate(sFromDate);
	                   	 	fromDate =_sdf.format(formattedFromDate);
	                    }
	                    List<GenericValue> productCategoryRollups = _delegator.findByAnd("ProductCategoryRollup", UtilMisc.toMap("productCategoryId",mRow.get("productCategoryId"),"parentProductCategoryId",mRow.get("parentCategoryId")),UtilMisc.toList("-fromDate"));
	                    if(UtilValidate.isNotEmpty(productCategoryRollups)) 
	                    {
	                    	productCategoryRollups = EntityUtil.filterByDate(productCategoryRollups);
	                    	if(UtilValidate.isNotEmpty(productCategoryRollups))
	                    	{
	                    	    GenericValue productCategoryRollup = EntityUtil.getFirst(productCategoryRollups);
	                    	    fromDate = _sdf.format(new Date(productCategoryRollup.getTimestamp("fromDate").getTime()));
	                    	}
	                    }
	                    
	                    rowString.setLength(0);
	                    rowString.append("<" + "ProductCategoryRollup" + " ");
	                    rowString.append("productCategoryId" + "=\"" + mRow.get("productCategoryId") + "\" ");
	                    rowString.append("parentProductCategoryId" + "=\"" + mRow.get("parentCategoryId") + "\" ");
	                    rowString.append("fromDate" + "=\"" + fromDate + "\" ");
	                    String thruDate=(String)mRow.get("thruDate");
	                    if(UtilValidate.isNotEmpty(thruDate)) 
	                    {
	                    	java.util.Date formattedThuDate=OsafeAdminUtil.validDate(thruDate);
	                    	String sThruDate =_sdf.format(formattedThuDate);
	                    	rowString.append("thruDate" + "=\"" + sThruDate + "\" ");	
	                    }
	                    rowString.append("sequenceNum" + "=\"" + ((i +1) *10) + "\" ");
	                    rowString.append("/>");
	                   bwOutFile.write(rowString.toString());
	                   bwOutFile.newLine();
                   }
                   catch(Exception ex)
                   {
                       Debug.logError(ex, module);
                   }
                   addCategoryContentRow(rowString, mRow, bwOutFile, "text", "PLP_ESPOT_CONTENT", "plpText");
                   addCategoryContentRow(rowString, mRow, bwOutFile, "text", "PDP_ADDITIONAL", "pdpText");
 	            	
	            }
                bwOutFile.flush();
         	    writeXmlFooter(bwOutFile);
            }
    	}
      	 catch (Exception e) {
   	         }
         finally {
             try {
                 if (bwOutFile != null) {
                	 bwOutFile.close();
                 }
             } catch (IOException ioe) {
                 Debug.logError(ioe, module);
             }
         }
      	 
    }
    
    private static void buildManufacturer(List dataRows,String xmlDataDirPath,String loadImagesDirPath, String imageUrl, String productStoreId) {
    	if (UtilValidate.isNotEmpty(productStoreId))
	   	{
	        File fOutFile =null;
	        BufferedWriter bwOutFile=null;
        
			try {
				
		        fOutFile = new File(xmlDataDirPath, "020-Manufacturer.xml");
	            if (fOutFile.createNewFile()) {
	            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));
	
	                writeXmlHeader(bwOutFile);
	                
	                for (int i=0 ; i < dataRows.size() ; i++) {
	                     StringBuilder  rowString = new StringBuilder();
		            	 Map mRow = (Map)dataRows.get(i);
		            	 String partyId=(String) mRow.get("partyId");
		            	 if (UtilValidate.isNotEmpty(partyId))
		            	 {
		            		 rowString.append("<" + "Party" + " ");
		                     rowString.append("partyId" + "=\"" + partyId + "\" ");
		                     rowString.append("partyTypeId" + "=\"" + "PARTY_GROUP" + "\" ");
		                     rowString.append("statusId" + "=\"" + "PARTY_ENABLED" + "\" ");
		                     rowString.append("/>");
		                     bwOutFile.write(rowString.toString());
		                     bwOutFile.newLine();
		                     
		                     rowString.setLength(0);
		                     rowString.append("<" + "PartyRole" + " ");
		                     rowString.append("partyId" + "=\"" + partyId + "\" ");
		                     rowString.append("roleTypeId" + "=\"" + "MANUFACTURER" + "\" ");
		                     rowString.append("/>");
		                     bwOutFile.write(rowString.toString());
		                     bwOutFile.newLine();
	
		                     rowString.setLength(0);
		                     rowString.append("<" + "PartyGroup" + " ");
		                     rowString.append("partyId" + "=\"" + partyId + "\" ");
		                     rowString.append("groupName" + "=\"" + (String)mRow.get("manufacturerName") + "\" ");
		                     rowString.append("/>");
		                     bwOutFile.write(rowString.toString());
		                     bwOutFile.newLine();
		                     
		                     List<GenericValue> productStoreRoles = _delegator.findByAnd("ProductStoreRole", UtilMisc.toMap("partyId",partyId,"roleTypeId","MANUFACTURER","productStoreId",productStoreId),UtilMisc.toList("-fromDate"));
		 	                 if(UtilValidate.isNotEmpty(productStoreRoles)) 
		 	                 {
		 	                	productStoreRoles = EntityUtil.filterByDate(productStoreRoles);
		 	                 }
		 	                 if(UtilValidate.isEmpty(productStoreRoles))
			                 {
		 	                	rowString.setLength(0);
			                     rowString.append("<" + "ProductStoreRole" + " ");
			                     rowString.append("partyId" + "=\"" + partyId + "\" ");
			                     rowString.append("roleTypeId" + "=\"" + "MANUFACTURER" + "\" ");
			                     rowString.append("productStoreId" + "=\"" + productStoreId + "\" ");
			                     rowString.append("fromDate" + "=\"" +  _sdf.format(UtilDateTime.nowTimestamp()) + "\" ");
			                     rowString.append("/>");
			                     bwOutFile.write(rowString.toString());
			                     bwOutFile.newLine();
			                 }
		                     
		         			 String contactMechId=_delegator.getNextSeqId("ContactMech");
		                     rowString.setLength(0);
		                     rowString.append("<" + "ContactMech" + " ");
		                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
		                     rowString.append("contactMechTypeId" + "=\"" + "POSTAL_ADDRESS" + "\" ");
		                     rowString.append("/>");
		                     bwOutFile.write(rowString.toString());
		                     bwOutFile.newLine();
	
		                     rowString.setLength(0);
		                     rowString.append("<" + "PartyContactMech" + " ");
		                     rowString.append("partyId" + "=\"" + partyId + "\" ");
		                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
		                     rowString.append("fromDate" + "=\"" +  _sdf.format(UtilDateTime.nowTimestamp()) + "\" ");
		                     rowString.append("/>");
		                     bwOutFile.write(rowString.toString());
		                     bwOutFile.newLine();
		                     
		                     rowString.setLength(0);
		                     rowString.append("<" + "PartyContactMechPurpose" + " ");
		                     rowString.append("partyId" + "=\"" + partyId + "\" ");
		                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
		                     rowString.append("contactMechPurposeTypeId" + "=\"" + "GENERAL_LOCATION" + "\" ");
		                     rowString.append("fromDate" + "=\"" +  _sdf.format(UtilDateTime.nowTimestamp()) + "\" ");
		                     rowString.append("/>");
		                     bwOutFile.write(rowString.toString());
		                     bwOutFile.newLine();
		                     
		                     rowString.setLength(0);
		                     rowString.append("<" + "PostalAddress" + " ");
		                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
		                     rowString.append("toName" + "=\"" + (String)mRow.get("manufacturerName") + "\" ");
		                     if(mRow.get("address1") != null) {
		                    	 rowString.append("address1" + "=\"" +  (String)mRow.get("address1") + "\" "); 
		                     }
		                     if(mRow.get("city") != null) {
		                    	 rowString.append("city" + "=\"" +  (String)mRow.get("city") + "\" ");
		                     }
		                     if(mRow.get("state") != null) {
		                    	 rowString.append("stateProvinceGeoId" + "=\"" +  mRow.get("state") + "\" ");
		                     }
		                     if(mRow.get("zip") != null) {
		                    	 rowString.append("postalCode" + "=\"" +  mRow.get("zip") + "\" ");
		                     }
		                     if(mRow.get("country") != null) {
		                    	 rowString.append("countryGeoId" + "=\"" +  (String)mRow.get("country") + "\" ");
		                     }
		                     
		                     rowString.append("/>");
		                     bwOutFile.write(rowString.toString());
		                     bwOutFile.newLine();
		                     
		                     addPartyContentRow(rowString, mRow, bwOutFile, "text", "DESCRIPTION", "shortDescription",loadImagesDirPath,imageUrl,"shortDescriptionThruDate");
		                     addPartyContentRow(rowString, mRow, bwOutFile, "text", "LONG_DESCRIPTION", "longDescription",loadImagesDirPath,imageUrl,"longDescriptionThruDate");
		                     addPartyContentRow(rowString, mRow, bwOutFile, "image", "PROFILE_IMAGE_URL", "manufacturerImage",loadImagesDirPath,imageUrl, "manufacturerImageThruDate");
		                     addPartyContentRow(rowString, mRow, bwOutFile, "text", "PROFILE_NAME", "manufacturerName",loadImagesDirPath,imageUrl,"manufacturerNameThruDate");
		            	 }
	                     
	 	            	
		            }
	                bwOutFile.flush();
	         	    writeXmlFooter(bwOutFile);
	            }
	            
				
	    
	    	}
	      	 catch (Exception e) {
	   	         }
	         finally {
	             try {
	                 if (bwOutFile != null) {
	                	 bwOutFile.close();
	                 }
	             } catch (IOException ioe) {
	                 Debug.logError(ioe, module);
	             }
	         }
	    }
      	 
    }
    private static void buildProduct(List dataRows,String xmlDataDirPath ) {

        File fOutFile =null;
        BufferedWriter bwOutFile=null;
        StringBuilder  rowString = new StringBuilder();
        String masterProductId=null;
        String productId=null;
        
		try {

	        fOutFile = new File(xmlDataDirPath, "030-Product.xml");
            if (fOutFile.createNewFile()) {
            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));
            	String currencyUomId = UtilProperties.getPropertyValue("general.properties", "currency.uom.id.default", "USD");
            	String priceFromDate = _sdf.format(UtilDateTime.nowTimestamp());
                writeXmlHeader(bwOutFile);
                
                for (int i=0 ; i < dataRows.size() ; i++) 
                {
	            	 Map mRow = (Map)dataRows.get(i);
	            	 masterProductId = (String)mRow.get("masterProductId");
	            	 productId = (String)mRow.get("productId");
	            	 String[] productCategoryIds = null;
	            	 if ((UtilValidate.isEmpty(productId)) || (UtilValidate.isNotEmpty(productId) && masterProductId.equals(productId)))
	            	 {
	                     rowString.setLength(0);
	                     rowString.append("<" + "Product" + " ");
		            	 rowString.append("productId" + "=\"" + masterProductId + "\" ");
	                     rowString.append("productTypeId" + "=\"" + "FINISHED_GOOD" + "\" ");
	                     String productCategoryId = (String)mRow.get("productCategoryId");
	                     if(UtilValidate.isNotEmpty(productCategoryId)) {
	                    	 productCategoryIds = productCategoryId.split(",");
	                         String primaryProductCategoryId =productCategoryIds[0].trim();
	                         rowString.append("primaryProductCategoryId" + "=\"" + primaryProductCategoryId + "\" ");
	                     }
	                     if(mRow.get("manufacturerId") != null) {
	                    	 rowString.append("manufacturerPartyId" + "=\"" + mRow.get("manufacturerId") + "\" ");
	                     }
	                     if(mRow.get("internalName") != null) {
	                    	 rowString.append("internalName" + "=\"" + (String)mRow.get("internalName") + "\" ");
	                     }
	                     rowString.append("brandName" + "=\"" + "" + "\" ");
	                     
	                     try
	                     {
	                    	 String fromDate=(String)mRow.get("introDate");
	                    	 if (UtilValidate.isNotEmpty(fromDate))
	                    	 {
	                    		 java.util.Date formattedFromDate=OsafeAdminUtil.validDate(fromDate);
		                    	 String sFromDate =_sdf.format(formattedFromDate);
	                    		 rowString.append("introductionDate" + "=\"" + sFromDate + "\" ");
	                    	 }
	                    	 else
	                    	 {
	                    		 rowString.append("introductionDate" + "=\"" + "" + "\" ");
	                    	 }
	                    	 if(mRow.get("productName") != null) 
	                    	 {
	                    		 rowString.append("productName" + "=\"" + (String)mRow.get("productName") + "\" ");
	                    	 }
		                     String thruDate=(String)mRow.get("discoDate");
		        			 if (UtilValidate.isNotEmpty(thruDate))
		        			 {
		        				 java.util.Date formattedThuDate=OsafeAdminUtil.validDate(thruDate);
		                    	 String sThruDate =_sdf.format(formattedThuDate);
		                         rowString.append("salesDiscontinuationDate" + "=\"" + sThruDate + "\" ");
		        			 }
		        			 else
		        			 {
		                         rowString.append("salesDiscontinuationDate" + "=\"" + "" + "\" ");
		        			 }
		                 }
	                     catch(Exception ex)
	                     {
	                         Debug.logError(ex, module);
	                     }
	                     rowString.append("requireInventory" + "=\"" + "N"+ "\" ");
	                     if(mRow.get("returnable") != null) {
	                         rowString.append("returnable" + "=\"" + mRow.get("returnable") + "\" ");
	                     }
	                     if(mRow.get("taxable") != null) {
	                         rowString.append("taxable" + "=\"" + mRow.get("taxable") + "\" ");
	                     }
	                     if(mRow.get("chargeShipping") != null) {
	                         rowString.append("chargeShipping" + "=\"" + mRow.get("chargeShipping") + "\" ");
	                     }
	                     if(mRow.get("productHeight") != null) {
	                         rowString.append("productHeight" + "=\"" + mRow.get("productHeight") + "\" ");
	                     }
	                     if(mRow.get("productWidth") != null) {
	                         rowString.append("productWidth" + "=\"" + mRow.get("productWidth") + "\" ");
	                     }
	                     if(mRow.get("productDepth") != null) {
	                         rowString.append("productDepth" + "=\"" + mRow.get("productDepth") + "\" ");
	                     }
	                     if(mRow.get("weight") != null) {
	                         rowString.append("weight" + "=\"" + mRow.get("weight") + "\" ");
	                     }
	                     String isVirtual="N";
	                     
	                     if(UtilValidate.isNotEmpty(productId) && masterProductId.equals(productId)) {
	                    	 isVirtual="Y";
	                     }
	 	            	 
	                     rowString.append("isVirtual" + "=\"" + isVirtual + "\" ");
	                     rowString.append("isVariant" + "=\"" + "N" + "\" ");
	                     rowString.append("/>");
	                     bwOutFile.write(rowString.toString());
	                     bwOutFile.newLine();
	                     if(UtilValidate.isNotEmpty(productCategoryIds)) {
	                    	 for (int j=0;j < productCategoryIds.length;j++)
		                     {
		                    	 String sequenceNum = (String)mRow.get("sequenceNum");
		                    	 String productCategoryFromDate = _sdf.format(UtilDateTime.nowTimestamp());
			         			 
			         			 if(UtilValidate.isEmpty(sequenceNum)) 
			         			 {
			         				sequenceNum = "10";
			         			 }
							     if(UtilValidate.isNotEmpty(productCategoryIds[j].trim())) 
							     {
							     
							     if(UtilValidate.isNotEmpty(mRow.get(productCategoryIds[j].trim()+"_sequenceNum"))) 
							     {
							         sequenceNum =  (String) mRow.get(productCategoryIds[j].trim()+"_sequenceNum");
							     }
							     if(UtilValidate.isEmpty(mRow.get(productCategoryIds[j].trim()+"_fromDate"))) 
							     {
							    	 List<GenericValue> productCategoryMembers = _delegator.findByAnd("ProductCategoryMember", UtilMisc.toMap("productCategoryId",productCategoryIds[j].trim(),"productId",masterProductId),UtilMisc.toList("-fromDate"));
					                 if(UtilValidate.isNotEmpty(productCategoryMembers))
					                 {
					                	 productCategoryMembers = EntityUtil.filterByDate(productCategoryMembers);
					                	 if(UtilValidate.isNotEmpty(productCategoryMembers))
					                	 {
					                    	GenericValue productCategoryMember = EntityUtil.getFirst(productCategoryMembers);
					                    	productCategoryFromDate = _sdf.format(new Date(productCategoryMember.getTimestamp("fromDate").getTime()));
					                	 }
					                 }
							     } 
							     else 
							     {
							    	 productCategoryFromDate = (String) mRow.get(productCategoryIds[j].trim()+"_fromDate");
					      			 java.util.Date formattedFromDate=OsafeAdminUtil.validDate(productCategoryFromDate);
					      			 productCategoryFromDate =_sdf.format(formattedFromDate);
							     }
							     
		                         rowString.setLength(0);
		                         rowString.append("<" + "ProductCategoryMember" + " ");
		                         rowString.append("productCategoryId" + "=\"" + productCategoryIds[j].trim()+ "\" ");
		                         rowString.append("productId" + "=\"" + masterProductId+ "\" ");
		            			 rowString.append("fromDate" + "=\"" + productCategoryFromDate + "\" ");
		            			 if (UtilValidate.isNotEmpty(mRow.get(productCategoryIds[j].trim()+"_thruDate")))
		            			 {
		            				 String productCategoryThruDate = (String) mRow.get(productCategoryIds[j].trim()+"_thruDate");
					      			 java.util.Date formattedFromDate=OsafeAdminUtil.validDate(productCategoryThruDate);
					      			 productCategoryThruDate =_sdf.format(formattedFromDate);
		                             rowString.append("thruDate" + "=\"" + productCategoryThruDate + "\" ");
		            			 }
		                         rowString.append("comments" + "=\"" + "" + "\" ");
		                         rowString.append("sequenceNum" + "=\"" + sequenceNum + "\" ");
		                         rowString.append("quantity" + "=\"" + "" + "\" ");
		                         rowString.append("/>");
		                         bwOutFile.write(rowString.toString());
		                         bwOutFile.newLine();
								 }
		                     	
		                     }
	                     }
	                     
	                    if(UtilValidate.isNotEmpty(mRow.get("listPriceCurrency"))) 
	                    {
		                   	currencyUomId = (String) mRow.get("listPriceCurrency");
		                }
	                    
	                    if(UtilValidate.isEmpty(mRow.get("listPriceFromDate"))) 
	                    {
		                    List<GenericValue> productListPrices = _delegator.findByAnd("ProductPrice", UtilMisc.toMap("productId",masterProductId,"productPriceTypeId","LIST_PRICE", "productPricePurposeId","PURCHASE", "currencyUomId", currencyUomId, "productStoreGroupId", "_NA_"),UtilMisc.toList("-fromDate"));
		                    if(UtilValidate.isNotEmpty(productListPrices))
		                    {
		                    	productListPrices = EntityUtil.filterByDate(productListPrices);
		                    	if(UtilValidate.isNotEmpty(productListPrices)) 
		                    	{
		                    	    GenericValue productListPrice = EntityUtil.getFirst(productListPrices);
		                    	    priceFromDate = _sdf.format(new Date(productListPrice.getTimestamp("fromDate").getTime()));
		                    	}
		                    }
		                } 
	                    else 
		                {
		                	priceFromDate = (String) mRow.get("listPriceFromDate");
		                    java.util.Date formattedFromDate=OsafeAdminUtil.validDate(priceFromDate);
		                  	priceFromDate =_sdf.format(formattedFromDate);
		                }
	                    if(mRow.get("listPrice") != null) {
		                    rowString.setLength(0);
		                    rowString.append("<" + "ProductPrice" + " ");
		                    rowString.append("productId" + "=\"" + masterProductId+ "\" ");
		                    rowString.append("productPriceTypeId" + "=\"" + "LIST_PRICE" + "\" ");
		                    rowString.append("productPricePurposeId" + "=\"" + "PURCHASE" + "\" ");
		                    rowString.append("currencyUomId" + "=\"" + currencyUomId + "\" ");
		                    rowString.append("productStoreGroupId" + "=\"" + "_NA_" + "\" ");
		                    rowString.append("price" + "=\"" + mRow.get("listPrice") + "\" ");
		                    rowString.append("fromDate" + "=\"" + priceFromDate + "\" ");
		                    if(UtilValidate.isNotEmpty(mRow.get("listPriceThruDate"))) {
		                    	String priceThruDate = (String) mRow.get("listPriceThruDate");
		                        java.util.Date formattedFromDate=OsafeAdminUtil.validDate(priceThruDate);
		                        priceThruDate =_sdf.format(formattedFromDate);
		                    	rowString.append("thruDate" + "=\"" + priceThruDate + "\" ");
		                    }
		                    rowString.append("/>");
		                    bwOutFile.write(rowString.toString());
		                    bwOutFile.newLine();
	                    }
	                    if(UtilValidate.isNotEmpty(mRow.get("defaultPriceCurrency"))) {
	                    	currencyUomId = (String) mRow.get("defaultPriceCurrency");
	                    }
	                    if(UtilValidate.isEmpty(mRow.get("defaultPriceFromDate"))) {
		                    List<GenericValue> productDefaultPrices = _delegator.findByAnd("ProductPrice", UtilMisc.toMap("productId",masterProductId,"productPriceTypeId","DEFAULT_PRICE", "productPricePurposeId","PURCHASE", "currencyUomId", currencyUomId, "productStoreGroupId", "_NA_"),UtilMisc.toList("-fromDate"));
		                    if(UtilValidate.isNotEmpty(productDefaultPrices)){
		                    	productDefaultPrices = EntityUtil.filterByDate(productDefaultPrices);
		                    	if(UtilValidate.isNotEmpty(productDefaultPrices)) {
		                    	    GenericValue productDefaultPrice = EntityUtil.getFirst(productDefaultPrices);
		                    	    priceFromDate = _sdf.format(new Date(productDefaultPrice.getTimestamp("fromDate").getTime()));
		                    	}
		                    }
		                } else {
		                	priceFromDate = (String) mRow.get("defaultPriceFromDate");
		                   	java.util.Date formattedFromDate=OsafeAdminUtil.validDate(priceFromDate);
		                   	priceFromDate =_sdf.format(formattedFromDate);
		                }
	                    if(mRow.get("defaultPrice") != null) {
		                    rowString.setLength(0);
		                    rowString.append("<" + "ProductPrice" + " ");
		                    rowString.append("productId" + "=\"" + masterProductId+ "\" ");
		                    rowString.append("productPriceTypeId" + "=\"" + "DEFAULT_PRICE" + "\" ");
		                    rowString.append("productPricePurposeId" + "=\"" + "PURCHASE" + "\" ");
		                    rowString.append("currencyUomId" + "=\"" + currencyUomId + "\" ");
		                    rowString.append("productStoreGroupId" + "=\"" + "_NA_" + "\" ");
		                    rowString.append("price" + "=\"" + mRow.get("defaultPrice") + "\" ");
		                    rowString.append("fromDate" + "=\"" + priceFromDate + "\" ");
		                    if(UtilValidate.isNotEmpty(mRow.get("defaultPriceThruDate"))) {
		                    	String priceThruDate = (String) mRow.get("defaultPriceThruDate");
		                        java.util.Date formattedFromDate=OsafeAdminUtil.validDate(priceThruDate);
		                        priceThruDate =_sdf.format(formattedFromDate);
		                    	rowString.append("thruDate" + "=\"" + priceThruDate + "\" ");
		                    }
		                    rowString.append("/>");
		                    bwOutFile.write(rowString.toString());
		                    bwOutFile.newLine();
	                    }
	            	 }
                    
	            }
                bwOutFile.flush();
         	    writeXmlFooter(bwOutFile);
            }
            
			
    
    	}
      	 catch (Exception e) {
      		e.printStackTrace();
   	     }
         finally {
             try {
                 if (bwOutFile != null) {
                	 bwOutFile.close();
                 }
             } catch (IOException ioe) {
                 Debug.logError(ioe, module);
             }
         }
      	 
    }
    private static void buildProductVariant(List dataRows,String xmlDataDirPath,String loadImagesDirPath, String imageUrl, Boolean removeAll) {

        File fOutFile =null;
        BufferedWriter bwOutFile=null;
        Map mFeatureTypeMap = FastMap.newInstance();
        
        StringBuilder  rowString = new StringBuilder();
        
		try {
			
	        fOutFile = new File(xmlDataDirPath, "040-ProductVariant.xml");
            if (fOutFile.createNewFile()) {
            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));
                writeXmlHeader(bwOutFile);
                
                for (int i=0 ; i < dataRows.size() ; i++) 
                {
              	    Map mRow = (Map)dataRows.get(i);
              	    String productId=(String)mRow.get("masterProductId");
      			    String featureProductId=(String)mRow.get("productId");
      			    String fromDate=(String)mRow.get("introDate");
      			    String sFromDate = "";
      			    if(UtilValidate.isNotEmpty(fromDate))
      			    {
      			        java.util.Date formattedFromDate=OsafeAdminUtil.validDate(fromDate);
      			        sFromDate =_sdf.format(formattedFromDate);
      			    }	
      			    String thruDate=(String)mRow.get("discoDate");
      			    String sThruDate = "";
      			    if(UtilValidate.isNotEmpty(thruDate))
    			    {
      				    java.util.Date formattedThuDate=OsafeAdminUtil.validDate(thruDate);
      			        sThruDate =_sdf.format(formattedThuDate);  
    			    }
      	            
              	    mFeatureTypeMap.clear();
              	    int iSeq = 0;
              	    
              	    //not a variant product
              	    if (UtilValidate.isEmpty(featureProductId) || productId.equals(featureProductId))
              	    {
              	    	continue;
              	    }
              	    
              	    addProductVariantRow(rowString, bwOutFile, mRow, loadImagesDirPath,imageUrl,productId, featureProductId,sFromDate,sThruDate, iSeq, removeAll);
              	      
	            }
                bwOutFile.flush();
         	    writeXmlFooter(bwOutFile);
            }
    	}
      	 catch (Exception e) {
   	         }
         finally {
             try {
                 if (bwOutFile != null) {
                	 bwOutFile.close();
                 }
             } catch (IOException ioe) {
                 Debug.logError(ioe, module);
             }
         }
      	 
       }
    
    private static void buildProductGoodIdentification(List dataRows,String xmlDataDirPath ) {

        File fOutFile =null;
        BufferedWriter bwOutFile=null;
        StringBuilder  rowString = new StringBuilder();
        String masterProductId=null;
        String productId=null;
        
		try {

	        fOutFile = new File(xmlDataDirPath, "045-ProductGoodIdentification.xml");
            if (fOutFile.createNewFile()) {
            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));
                writeXmlHeader(bwOutFile);
                
                for (int i=0 ; i < dataRows.size() ; i++) 
                {
	            	 Map mRow = (Map)dataRows.get(i);
	            	 masterProductId=(String)mRow.get("masterProductId");
	            	 productId = (String)mRow.get("productId");
	            	 if ((UtilValidate.isEmpty(productId)) || (UtilValidate.isNotEmpty(productId) && masterProductId.equals(productId)))
	            	 {
        				 addProductGoodIdentificationRow(rowString, mRow, bwOutFile, masterProductId,"goodIdentificationSkuId","SKU");
        				 addProductGoodIdentificationRow(rowString, mRow, bwOutFile, masterProductId,"goodIdentificationGoogleId", "GOOGLE_ID");
        				 addProductGoodIdentificationRow(rowString, mRow, bwOutFile, masterProductId,"goodIdentificationIsbnId", "ISBN");
        				 addProductGoodIdentificationRow(rowString, mRow, bwOutFile, masterProductId,"goodIdentificationManufacturerId", "MANUFACTURER_ID_NO");
	            	 }
	            	 else
	            	 {
	            		 //Add Variant Product Good Identification
	            		 if (UtilValidate.isNotEmpty(productId) && !(masterProductId.equals(productId)))
	            		 {
	        				 addProductGoodIdentificationRow(rowString, mRow, bwOutFile, productId,"goodIdentificationSkuId","SKU");
	        				 addProductGoodIdentificationRow(rowString, mRow, bwOutFile, productId,"goodIdentificationGoogleId", "GOOGLE_ID");
	        				 addProductGoodIdentificationRow(rowString, mRow, bwOutFile, productId,"goodIdentificationIsbnId", "ISBN");
	        				 addProductGoodIdentificationRow(rowString, mRow, bwOutFile, productId,"goodIdentificationManufacturerId", "MANUFACTURER_ID_NO");
	            			 
	            		 }
	            				 
	            	 }
                    
	            }
                bwOutFile.flush();
         	    writeXmlFooter(bwOutFile);
            }
            
			
            
    	}
      	 catch (Exception e) {
   	         }
         finally {
             try {
                 if (bwOutFile != null) {
                	 bwOutFile.close();
                 }
             } catch (IOException ioe) {
                 Debug.logError(ioe, module);
             }
         }
      	 
       }
    
    private static void buildProductAttribute(List dataRows,String xmlDataDirPath ) {

        File fOutFile =null;
        BufferedWriter bwOutFile=null;
        StringBuilder  rowString = new StringBuilder();
        String productId=null;
        String masterProductId=null;
        
		try {

	        fOutFile = new File(xmlDataDirPath, "075-ProductAttribute.xml");
            if (fOutFile.createNewFile()) {
            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));
                writeXmlHeader(bwOutFile);
                
                for (int i=0 ; i < dataRows.size() ; i++) 
                {
	            	 Map mRow = (Map)dataRows.get(i);
	            	 masterProductId = (String)mRow.get("masterProductId");
	            	 productId =(String)mRow.get("productId");
	            	 if ((UtilValidate.isEmpty(productId)) || (UtilValidate.isNotEmpty(productId) && masterProductId.equals(productId)))
	            	 {
	            		 addProductAttributeRow(rowString, mRow, bwOutFile, masterProductId,"bfInventoryTot","BF_INVENTORY_TOT");
        				 addProductAttributeRow(rowString, mRow, bwOutFile, masterProductId,"bfInventoryWhs","BF_INVENTORY_WHS");
        				 addProductAttributeRow(rowString, mRow, bwOutFile, masterProductId,"multiVariant","PDP_SELECT_MULTI_VARIANT");
        				 addProductAttributeRow(rowString, mRow, bwOutFile, masterProductId,"giftMessage","CHECKOUT_GIFT_MESSAGE");
        				 addProductAttributeRow(rowString, mRow, bwOutFile, masterProductId,"pdpQtyMin","PDP_QTY_MIN");
        				 addProductAttributeRow(rowString, mRow, bwOutFile, masterProductId,"pdpQtyMax","PDP_QTY_MAX");
        				 addProductAttributeRow(rowString, mRow, bwOutFile, masterProductId,"pdpQtyDefault","PDP_QTY_DEFAULT");
        				 addProductAttributeRow(rowString, mRow, bwOutFile, masterProductId,"pdpInStoreOnly","PDP_IN_STORE_ONLY");
	            	 }
	            	 if (UtilValidate.isNotEmpty(productId) && !masterProductId.equals(productId))
	            	 {
        				 addProductAttributeRow(rowString, mRow, bwOutFile, productId,"bfInventoryTot","BF_INVENTORY_TOT");
        				 addProductAttributeRow(rowString, mRow, bwOutFile, productId,"bfInventoryWhs","BF_INVENTORY_WHS");
        				 addProductAttributeRow(rowString, mRow, bwOutFile, productId,"multiVariant","PDP_SELECT_MULTI_VARIANT");
        				 addProductAttributeRow(rowString, mRow, bwOutFile, productId,"giftMessage","CHECKOUT_GIFT_MESSAGE");
        				 addProductAttributeRow(rowString, mRow, bwOutFile, productId,"pdpQtyMin","PDP_QTY_MIN");
        				 addProductAttributeRow(rowString, mRow, bwOutFile, productId,"pdpQtyMax","PDP_QTY_MAX");
        				 addProductAttributeRow(rowString, mRow, bwOutFile, productId,"pdpQtyDefault","PDP_QTY_DEFAULT");
        				 addProductAttributeRow(rowString, mRow, bwOutFile, productId,"pdpInStoreOnly","PDP_IN_STORE_ONLY");
	            	 }
                    
	            }
                bwOutFile.flush();
         	    writeXmlFooter(bwOutFile);
            }
            
			
            
    	}
      	 catch (Exception e) {
   	         }
         finally {
             try {
                 if (bwOutFile != null) {
                	 bwOutFile.close();
                 }
             } catch (IOException ioe) {
                 Debug.logError(ioe, module);
             }
         }
      	 
       }
    

    private static void addProductVariantRow(StringBuilder rowString,BufferedWriter bwOutFile,Map mRow,String loadImagesDirPath, String imageUrl, String masterProductId,String featureProductId,String sFromDate,String sThruDate,int iSeq, Boolean removeAll) {
    	String currencyUomId = UtilProperties.getPropertyValue("general.properties", "currency.uom.id.default", "USD");
    	String priceFromDate = _sdf.format(UtilDateTime.nowTimestamp());
    	try 
    	{
    		
		   rowString.setLength(0);
           rowString.append("<" + "Product" + " ");
           rowString.append("productId" + "=\"" + featureProductId + "\" ");
           rowString.append("productTypeId" + "=\"" + "FINISHED_GOOD" + "\" ");
           rowString.append("isVirtual" + "=\"" + "N" + "\" ");
           rowString.append("isVariant" + "=\"" + "Y" + "\" ");
	      if (UtilValidate.isNotEmpty(sFromDate))
		  {
             rowString.append("introductionDate" + "=\"" + sFromDate + "\" ");
		  }
		  else
		  {
             rowString.append("introductionDate" + "=\"" + "" + "\" ");
		  }
		  if (UtilValidate.isNotEmpty(sThruDate))
		  {
                 rowString.append("salesDiscontinuationDate" + "=\"" + sThruDate + "\" ");
		  }
		  else
		  {
                 rowString.append("salesDiscontinuationDate" + "=\"" + "" + "\" ");
		  }
       
          if(mRow.get("manufacturerId") != null) 
          {
         	 rowString.append("manufacturerPartyId" + "=\"" + mRow.get("manufacturerId") + "\" ");
          }
          if(mRow.get("internalName") != null) 
          {
         	 rowString.append("internalName" + "=\"" + (String)mRow.get("internalName") + "\" ");
          }
          rowString.append("brandName" + "=\"" + "" + "\" ");
          if(mRow.get("productName") != null) 
          {
         	 rowString.append("productName" + "=\"" + (String)mRow.get("productName") + "\" ");
          }
          else
          {
              rowString.append("productName" + "=\"" + "" + "\" ");
          }
          if(mRow.get("returnable") != null) 
          {
              rowString.append("returnable" + "=\"" + mRow.get("returnable") + "\" ");
          }
          if(mRow.get("taxable") != null) 
          {
              rowString.append("taxable" + "=\"" + mRow.get("taxable") + "\" ");
          }
          if(mRow.get("chargeShipping") != null) 
          {
              rowString.append("chargeShipping" + "=\"" + mRow.get("chargeShipping") + "\" ");
          }
          if(mRow.get("productHeight") != null) 
          {
              rowString.append("productHeight" + "=\"" + mRow.get("productHeight") + "\" ");
          }
          if(mRow.get("productWidth") != null) 
          {
              rowString.append("productWidth" + "=\"" + mRow.get("productWidth") + "\" ");
          }
          if(mRow.get("productDepth") != null) 
          {
              rowString.append("productDepth" + "=\"" + mRow.get("productDepth") + "\" ");
          }
          if(mRow.get("weight") != null) 
          {
              rowString.append("weight" + "=\"" + mRow.get("weight") + "\" ");
          }

           rowString.append("/>");
           bwOutFile.write(rowString.toString());
           bwOutFile.newLine();
           
           List<GenericValue> productAssocList = _delegator.findByAnd("ProductAssoc", UtilMisc.toMap("productId", masterProductId, "productIdTo", featureProductId, "productAssocTypeId", "PRODUCT_VARIANT"));
           if(UtilValidate.isNotEmpty(productAssocList)) 
           {
        	   productAssocList = EntityUtil.filterByDate(productAssocList, true);
           }
           if(UtilValidate.isEmpty(productAssocList) || removeAll) 
           {
               rowString.setLength(0);
               rowString.append("<" + "ProductAssoc" + " ");
               rowString.append("productId" + "=\"" + masterProductId+ "\" ");
               rowString.append("productIdTo" + "=\"" + featureProductId + "\" ");
               rowString.append("productAssocTypeId" + "=\"" + "PRODUCT_VARIANT" + "\" ");
               rowString.append("fromDate" + "=\"" + _sdf.format(UtilDateTime.nowTimestamp()) + "\" ");
               rowString.append("sequenceNum" + "=\"" + ((iSeq +1) *10) + "\" ");
               rowString.append("/>");
               bwOutFile.write(rowString.toString());
               bwOutFile.newLine();
           }
           
           String sPrice =(String)mRow.get("listPrice");
           
           if (UtilValidate.isNotEmpty(sPrice))
           {
        	   if(UtilValidate.isNotEmpty(mRow.get("listPriceCurrency"))) 
        	   {
                   currencyUomId = (String) mRow.get("listPriceCurrency");
               }
        	   if(UtilValidate.isEmpty(mRow.get("listPriceFromDate"))) 
        	   {
                   List<GenericValue> productListPrices = _delegator.findByAnd("ProductPrice", UtilMisc.toMap("productId",featureProductId,"productPriceTypeId","LIST_PRICE", "productPricePurposeId","PURCHASE", "currencyUomId", currencyUomId, "productStoreGroupId", "_NA_"),UtilMisc.toList("-fromDate"));
                   if(UtilValidate.isNotEmpty(productListPrices)) 
                   {
                	   productListPrices = EntityUtil.filterByDate(productListPrices);
                   	   if(UtilValidate.isNotEmpty(productListPrices)) 
                   	   {
                   	       GenericValue productListPrice = EntityUtil.getFirst(productListPrices);
                   	       priceFromDate = _sdf.format(new Date(productListPrice.getTimestamp("fromDate").getTime()));
                       }
                   }
               } 
        	   else 
               {
               	 priceFromDate = (String) mRow.get("listPriceFromDate");
                 java.util.Date formattedFromDate=OsafeAdminUtil.validDate(priceFromDate);
               	 priceFromDate =_sdf.format(formattedFromDate);
               }
        	   
               rowString.setLength(0);
               rowString.append("<" + "ProductPrice" + " ");
               rowString.append("productId" + "=\"" + featureProductId+ "\" ");
               rowString.append("productPriceTypeId" + "=\"" + "LIST_PRICE" + "\" ");
               rowString.append("productPricePurposeId" + "=\"" + "PURCHASE" + "\" ");
               rowString.append("currencyUomId" + "=\"" + currencyUomId + "\" ");
               rowString.append("productStoreGroupId" + "=\"" + "_NA_" + "\" ");
               rowString.append("price" + "=\"" +  sPrice + "\" ");
               rowString.append("fromDate" + "=\"" + priceFromDate + "\" ");
               if(UtilValidate.isNotEmpty(mRow.get("listPriceThruDate"))) 
               {
            	   String priceThruDate = (String) mRow.get("listPriceThruDate");
                   java.util.Date formattedFromDate=OsafeAdminUtil.validDate(priceThruDate);
                   priceThruDate =_sdf.format(formattedFromDate);
            	   rowString.append("thruDate" + "=\"" + priceThruDate + "\" ");
               }
               rowString.append("/>");
               bwOutFile.write(rowString.toString());
               bwOutFile.newLine();
        	   
           }
           
           sPrice =(String)mRow.get("defaultPrice");
           if (UtilValidate.isNotEmpty(sPrice))
           {
        	   if(UtilValidate.isNotEmpty(mRow.get("defaultPriceCurrency"))) 
        	   {
                   currencyUomId = (String) mRow.get("defaultPriceCurrency");
               }
        	   if(UtilValidate.isEmpty(mRow.get("defaultPriceFromDate"))) 
        	   {
                   List<GenericValue> productDefaultPrices = _delegator.findByAnd("ProductPrice", UtilMisc.toMap("productId",featureProductId,"productPriceTypeId","DEFAULT_PRICE", "productPricePurposeId","PURCHASE", "currencyUomId", currencyUomId, "productStoreGroupId", "_NA_"),UtilMisc.toList("-fromDate"));
                   if(UtilValidate.isNotEmpty(productDefaultPrices))
                   {
                       productDefaultPrices = EntityUtil.filterByDate(productDefaultPrices);
                       if(UtilValidate.isNotEmpty(productDefaultPrices))
                       {
                   	       GenericValue productDefaultPrice = EntityUtil.getFirst(productDefaultPrices);
                   	       priceFromDate = _sdf.format(new Date(productDefaultPrice.getTimestamp("fromDate").getTime()));
                       }
               	   }
               } 
        	   else 
        	   {
               	   priceFromDate = (String) mRow.get("defaultPriceFromDate");
               	   java.util.Date formattedFromDate=OsafeAdminUtil.validDate(priceFromDate);
               	   priceFromDate =_sdf.format(formattedFromDate);
               }
               rowString.setLength(0);
               rowString.append("<" + "ProductPrice" + " ");
               rowString.append("productId" + "=\"" + featureProductId+ "\" ");
               rowString.append("productPriceTypeId" + "=\"" + "DEFAULT_PRICE" + "\" ");
               rowString.append("productPricePurposeId" + "=\"" + "PURCHASE" + "\" ");
               rowString.append("currencyUomId" + "=\"" + currencyUomId + "\" ");
               rowString.append("productStoreGroupId" + "=\"" + "_NA_" + "\" ");
               rowString.append("price" + "=\"" + sPrice+ "\" ");
               rowString.append("fromDate" + "=\"" + priceFromDate + "\" ");
               if(UtilValidate.isNotEmpty(mRow.get("defaultPriceThruDate"))) 
               {
            	   String priceThruDate = (String) mRow.get("defaultPriceThruDate");
                   java.util.Date formattedFromDate=OsafeAdminUtil.validDate(priceThruDate);
                   priceThruDate =_sdf.format(formattedFromDate);
            	   rowString.append("thruDate" + "=\"" + priceThruDate + "\" ");
               }
               rowString.append("/>");
               bwOutFile.write(rowString.toString());
               bwOutFile.newLine();
        	   
           }
           
    	}
    	 catch (Exception e) {
    		 
    	 }
    }

    private static Map addProductFeatureImageRow(StringBuilder rowString,BufferedWriter bwOutFile,Map mFeatureTypeMap,Map mFeatureIdImageExists,String featureImage,String colName,String featureDataResourceTypeId,String loadImagesDirPath, String imageUrl) {
    	
    	try {
        		Set featureTypeSet = mFeatureTypeMap.keySet();
        		Iterator iterFeatureType = featureTypeSet.iterator();
        		while (iterFeatureType.hasNext())
        		{
        			String featureType =(String)iterFeatureType.next();
        			String featureTypeId = StringUtil.removeSpaces(featureType).toUpperCase();
        			if (featureTypeId.length() > 20)
        			{
        				featureTypeId=featureTypeId.substring(0,20);
        			}
        			FastMap mFeatureMap=(FastMap)mFeatureTypeMap.get(featureType);
            		Set featureSet = mFeatureMap.keySet();
            		Iterator iterFeature = featureSet.iterator();
            		while (iterFeature.hasNext())
            		{
            			String featureId =(String)iterFeature.next();
            			/*String featureId =StringUtil.removeSpaces(feature).toUpperCase();
            			featureId =StringUtil.replaceString(featureId, "&", "");
            			featureId=featureTypeId+"_"+featureId;
            			if (featureId.length() > 20)
            			{
            				featureId=featureId.substring(0,20);
            			}*/
            			if (!mFeatureIdImageExists.containsKey(featureId))
            			{
            				String dataResourceId = "";
            				List<GenericValue> lProductFeatureDataResource = _delegator.findByAnd("ProductFeatureDataResource", UtilMisc.toMap("productFeatureId", featureId, "featureDataResourceTypeId", featureDataResourceTypeId), UtilMisc.toList("-lastUpdatedStamp"));
            				if(UtilValidate.isNotEmpty(lProductFeatureDataResource))
            				{
            					GenericValue productFeatureDataResource = EntityUtil.getFirst(lProductFeatureDataResource);
            					dataResourceId = productFeatureDataResource.getString("dataResourceId");
            				}
            				else
            				{
            					dataResourceId = _delegator.getNextSeqId("DataResource");	
            				}
                            mFeatureIdImageExists.put(featureId,featureImage);
            				
            	            rowString.setLength(0);
            	            rowString.append("<" + "DataResource" + " ");
            	            rowString.append("dataResourceId" + "=\"" + dataResourceId + "\" ");
            	            rowString.append("dataResourceTypeId" + "=\"" + "SHORT_TEXT" + "\" ");
            	            rowString.append("dataTemplateTypeId" + "=\"" + "FTL" + "\" ");
            	            rowString.append("statusId" + "=\"" + "CTNT_PUBLISHED" + "\" ");
            	            rowString.append("dataResourceName" + "=\"" + featureImage + "\" ");
            	            rowString.append("mimeTypeId" + "=\"" + "text/html" + "\" ");
            	            
            	            if (!UtilValidate.isUrl(featureImage)) 
                     		{
            	            	Map<Object, Object> imageLocationMap = new HashMap<Object, Object>();
                            	for(Map<Object, Object> imageLocationPref : imageLocationPrefList) {
                            		imageLocationMap.put(imageLocationPref.get("key"), imageLocationPref.get("value"));
                            	}
                            	String defaultImageDirectory = (String)imageLocationMap.get("DEFAULT_IMAGE_DIRECTORY");
                            	String defaultSwatchImagePath = (String)imageLocationMap.get(featureDataResourceTypeId);
                            	if(UtilValidate.isNotEmpty(defaultImageDirectory) && UtilValidate.isNotEmpty(defaultSwatchImagePath)) 
                            	{
                            		featureImage = defaultImageDirectory + defaultSwatchImagePath + featureImage;
                            	}
                     		}
            	            
            	            
            	            rowString.append("objectInfo" + "=\"" + featureImage.trim() + "\" ");
            	            rowString.append("isPublic" + "=\"" + "Y" + "\" ");
            	            rowString.append("/>");
            	            bwOutFile.write(rowString.toString());
            	            bwOutFile.newLine();
                		
                			rowString.setLength(0);
       	                    rowString.append("<" + "ProductFeatureDataResource" + " ");
    	                    rowString.append("productFeatureId" + "=\"" + featureId + "\" ");
    	                    rowString.append("dataResourceId" + "=\"" + dataResourceId + "\" ");
    	                    rowString.append("featureDataResourceTypeId" + "=\"" + featureDataResourceTypeId + "\" ");
                            rowString.append("/>");
                            bwOutFile.write(rowString.toString());
                            bwOutFile.newLine();
                            
            				
            			}

            		}
        		}
    	}
   	 catch (Exception e) 
   	  {
		 
	  }
   	 return mFeatureIdImageExists;
    }
    
    private static void buildProductContent(List dataRows,String xmlDataDirPath,String loadImagesDirPath, String imageUrl) {
        File fOutFile =null;
        BufferedWriter bwOutFile=null;
        String masterProductId=null;
        String productId=null;
		try {

	        fOutFile = new File(xmlDataDirPath, "050-ProductContent.xml");
            if (fOutFile.createNewFile()) {
            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));

                writeXmlHeader(bwOutFile);
                
                for (int i=0 ; i < dataRows.size() ; i++) 
                {
                    StringBuilder  rowString = new StringBuilder();
	            	 Map mRow = (Map)dataRows.get(i);
	            	 masterProductId=(String)mRow.get("masterProductId");
	            	 productId=(String)mRow.get("productId");
	            	 if ((UtilValidate.isEmpty(productId)) || (UtilValidate.isNotEmpty(productId) && masterProductId.equals(productId)))
	            	 {
	              		 addProductContent(rowString, mRow, bwOutFile, masterProductId,loadImagesDirPath, imageUrl);
	            	 }
	            }
                bwOutFile.flush();
         	    writeXmlFooter(bwOutFile);
            }
    	}
      	 catch (Exception e) 
      	 {
   	     }
         finally {
             try {
                 if (bwOutFile != null) {
                	 bwOutFile.close();
                 }
             } catch (IOException ioe) {
                 Debug.logError(ioe, module);
             }
         }
    }

    private static void buildProductVariantContent(List dataRows,String xmlDataDirPath,String loadImagesDirPath, String imageUrl) {
        File fOutFile =null;
        BufferedWriter bwOutFile=null;
		try {

	        fOutFile = new File(xmlDataDirPath, "055-ProductVariantContent.xml");
            if (fOutFile.createNewFile()) {
            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));

                writeXmlHeader(bwOutFile);
                
                for (int i=0 ; i < dataRows.size() ; i++) {
                    StringBuilder  rowString = new StringBuilder();
	            	Map mRow = (Map)dataRows.get(i);
	              	 
	              	String masterProductId=(String)mRow.get("masterProductId");
	            	String productId=(String)mRow.get("productId");
	              	if(UtilValidate.isNotEmpty(productId) && !productId.equals(masterProductId)) {
	              		addProductContent(rowString, mRow, bwOutFile, productId,loadImagesDirPath, imageUrl);
	              	}
	            }
                bwOutFile.flush();
         	    writeXmlFooter(bwOutFile);
            }
            
			
    
    	}
      	 catch (Exception e) {
   	         }
         finally {
             try {
                 if (bwOutFile != null) {
                	 bwOutFile.close();
                 }
             } catch (IOException ioe) {
                 Debug.logError(ioe, module);
             }
         }
    }
    private static void addProductContent(StringBuilder rowString,Map mRow,BufferedWriter bwOutFile, String productId,String loadImagesDirPath,String imageUrl) {
    	
    	try 
    	{
			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","SMALL_IMAGE_URL", "smallImage", loadImagesDirPath, imageUrl,"smallImageThruDate");
             addProductContentRow(rowString, mRow, bwOutFile, productId,"image","SMALL_IMAGE_ALT_URL", "smallImageAlt", loadImagesDirPath, imageUrl,"smallImageAltThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","PLP_SWATCH_IMAGE_URL", "plpSwatchImage", loadImagesDirPath, imageUrl,"plpSwatchImageThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","PDP_SWATCH_IMAGE_URL", "pdpSwatchImage", loadImagesDirPath, imageUrl,"pdpSwatchImageThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","THUMBNAIL_IMAGE_URL", "thumbImage", loadImagesDirPath, imageUrl,"thumbImageThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","LARGE_IMAGE_URL", "largeImage", loadImagesDirPath, imageUrl,"largeImageThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","DETAIL_IMAGE_URL", "detailImage", loadImagesDirPath, imageUrl,"detailImageThruDate");

 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","ADDITIONAL_IMAGE_1", "addImage1", loadImagesDirPath, imageUrl,"addImage1ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","XTRA_IMG_1_LARGE", "xtraLargeImage1", loadImagesDirPath, imageUrl,"xtraLargeImage1ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","XTRA_IMG_1_DETAIL", "xtraDetailImage1", loadImagesDirPath, imageUrl,"xtraDetailImage1ThruDate");
 			 
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","ADDITIONAL_IMAGE_2", "addImage2", loadImagesDirPath, imageUrl,"addImage2ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","XTRA_IMG_2_LARGE", "xtraLargeImage2", loadImagesDirPath, imageUrl,"xtraLargeImage2ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","XTRA_IMG_2_DETAIL", "xtraDetailImage2", loadImagesDirPath, imageUrl,"xtraDetailImage2ThruDate");
 			 
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","ADDITIONAL_IMAGE_3", "addImage3", loadImagesDirPath, imageUrl,"addImage3ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","XTRA_IMG_3_LARGE", "xtraLargeImage3", loadImagesDirPath, imageUrl,"xtraLargeImage3ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","XTRA_IMG_3_DETAIL", "xtraDetailImage3", loadImagesDirPath, imageUrl,"xtraDetailImage3ThruDate");
 			 
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","ADDITIONAL_IMAGE_4", "addImage4", loadImagesDirPath, imageUrl,"addImage4ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","XTRA_IMG_4_LARGE", "xtraLargeImage4", loadImagesDirPath, imageUrl,"xtraLargeImage4ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","XTRA_IMG_4_DETAIL", "xtraDetailImage4", loadImagesDirPath, imageUrl,"xtraDetailImage4ThruDate");
 			 
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","ADDITIONAL_IMAGE_5", "addImage5", loadImagesDirPath, imageUrl,"addImage5ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","XTRA_IMG_5_LARGE", "xtraLargeImage5", loadImagesDirPath, imageUrl,"xtraLargeImage5ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","XTRA_IMG_5_DETAIL", "xtraDetailImage5", loadImagesDirPath, imageUrl,"xtraDetailImage5ThruDate");

 			 addProductContentRow(rowString, mRow, bwOutFile, productId, "image","ADDITIONAL_IMAGE_6", "addImage6", loadImagesDirPath, imageUrl,"addImage6ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId, "image","XTRA_IMG_6_LARGE", "xtraLargeImage6", loadImagesDirPath, imageUrl,"xtraLargeImage6ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId, "image","XTRA_IMG_6_DETAIL", "xtraDetailImage6", loadImagesDirPath, imageUrl,"xtraDetailImage6ThruDate");

 			 addProductContentRow(rowString, mRow, bwOutFile, productId, "image","ADDITIONAL_IMAGE_7", "addImage7", loadImagesDirPath, imageUrl,"addImage7ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId, "image","XTRA_IMG_7_LARGE", "xtraLargeImage7", loadImagesDirPath, imageUrl,"xtraLargeImage7ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId, "image","XTRA_IMG_7_DETAIL", "xtraDetailImage7", loadImagesDirPath, imageUrl,"xtraDetailImage7ThruDate");

 			 addProductContentRow(rowString, mRow, bwOutFile, productId, "image","ADDITIONAL_IMAGE_8", "addImage8", loadImagesDirPath, imageUrl,"addImage8ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId, "image","XTRA_IMG_8_LARGE", "xtraLargeImage8", loadImagesDirPath, imageUrl,"xtraLargeImage8ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId, "image","XTRA_IMG_8_DETAIL", "xtraDetailImage8", loadImagesDirPath, imageUrl,"xtraDetailImage8ThruDate");

 			 addProductContentRow(rowString, mRow, bwOutFile, productId, "image","ADDITIONAL_IMAGE_9", "addImage9", loadImagesDirPath, imageUrl,"addImage9ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId, "image","XTRA_IMG_9_LARGE", "xtraLargeImage9", loadImagesDirPath, imageUrl,"xtraLargeImage9ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId, "image","XTRA_IMG_9_DETAIL", "xtraDetailImage9", loadImagesDirPath, imageUrl,"xtraDetailImage9ThruDate");

 			 addProductContentRow(rowString, mRow, bwOutFile, productId, "image","ADDITIONAL_IMAGE_10", "addImage10", loadImagesDirPath, imageUrl,"addImage10ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId, "image","XTRA_IMG_10_LARGE", "xtraLargeImage10", loadImagesDirPath, imageUrl,"xtraLargeImag10ThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId, "image","XTRA_IMG_10_DETAIL", "xtraDetailImage10", loadImagesDirPath, imageUrl,"xtraDetailImage10ThruDate");

 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"text","PRODUCT_NAME", "productName", loadImagesDirPath, imageUrl,"productNameThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"text","SHORT_SALES_PITCH", "salesPitch", loadImagesDirPath, imageUrl,"salesPitchThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"text","LONG_DESCRIPTION", "longDescription", loadImagesDirPath, imageUrl,"longDescriptionThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"text","SPECIALINSTRUCTIONS", "specialInstructions", loadImagesDirPath, imageUrl,"specialInstructionsThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"text","DELIVERY_INFO", "deliveryInfo", loadImagesDirPath, imageUrl,"deliveryInfoThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"text","DIRECTIONS", "directions", loadImagesDirPath, imageUrl,"smallImageAltThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"text","TERMS_AND_CONDS", "termsConditions", loadImagesDirPath, imageUrl,"smallImageAltThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"text","INGREDIENTS", "ingredients", loadImagesDirPath, imageUrl,"termsConditionsThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"text","WARNINGS", "warnings", loadImagesDirPath, imageUrl,"warningsThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"text","PLP_LABEL", "plpLabel", loadImagesDirPath, imageUrl,"plpLabelThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"text","PDP_LABEL", "pdpLabel", loadImagesDirPath, imageUrl,"pdpLabelThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","PDP_VIDEO_URL", "pdpVideoUrl", loadImagesDirPath, imageUrl,"pdpVideoUrlThruDate");
 			 addProductContentRow(rowString, mRow, bwOutFile, productId,"image","PDP_VIDEO_360_URL", "pdpVideo360Url", loadImagesDirPath, imageUrl,"pdpVideo360UrlThruDate");
    		
    	}
    	 catch (Exception e)
    	 {
    		 
    	 }
    }
    private static void addProductContentRow(StringBuilder rowString,Map mRow,BufferedWriter bwOutFile,String productId,String contentType,String productContentTypeId,String colName, String productImagesDirPath, String imageUrl,String colNameThruDate) {

		String contentId=null;
		String dataResourceId=null;
		Timestamp contentTimestamp=null;
    	try {
    		
			String contentValue=(String)mRow.get(colName);
			if (UtilValidate.isEmpty(contentValue) && UtilValidate.isEmpty(contentValue.trim()))
			{
				return;
			}
			String contentValueThruDate=(String)mRow.get(colNameThruDate);
			List<GenericValue> lProductContent = _delegator.findByAnd("ProductContent", UtilMisc.toMap("productId",productId,"productContentTypeId",productContentTypeId),UtilMisc.toList("-fromDate"));
			if (UtilValidate.isNotEmpty(lProductContent))
			{
				GenericValue productContent = EntityUtil.getFirst(lProductContent);
				GenericValue content=productContent.getRelatedOne("Content");
				contentId=content.getString("contentId");
				dataResourceId=content.getString("dataResourceId");
				contentTimestamp =productContent.getTimestamp("fromDate");
			}
			else
			{
				contentId=_delegator.getNextSeqId("Content");
				dataResourceId=_delegator.getNextSeqId("DataResource");
				contentTimestamp =UtilDateTime.nowTimestamp();
			}

			if ("text".equals(contentType))
			{
	            rowString.setLength(0);
	            rowString.append("<" + "DataResource" + " ");
	            rowString.append("dataResourceId" + "=\"" + dataResourceId + "\" ");
	            rowString.append("dataResourceTypeId" + "=\"" + "ELECTRONIC_TEXT" + "\" ");
	            rowString.append("dataTemplateTypeId" + "=\"" + "FTL" + "\" ");
	            rowString.append("statusId" + "=\"" + "CTNT_PUBLISHED" + "\" ");
	            rowString.append("dataResourceName" + "=\"" + colName + "\" ");
	            if(UtilValidate.isNotEmpty(localeString))
	            {
	            	rowString.append("localeString" + "=\"" + localeString + "\" ");
	            }
	            rowString.append("mimeTypeId" + "=\"" + "application/octet-stream" + "\" ");
	            rowString.append("objectInfo" + "=\"" + "" + "\" ");
	            rowString.append("isPublic" + "=\"" + "Y" + "\" ");
	            rowString.append("/>");
	            bwOutFile.write(rowString.toString());
	            bwOutFile.newLine();

	            rowString.setLength(0);
	            rowString.append("<" + "ElectronicText" + " ");
	            rowString.append("dataResourceId" + "=\"" + dataResourceId + "\" ");
	        
	            rowString.append("textData" + "=\"" + contentValue + "\" ");
	            rowString.append("/>");
	            bwOutFile.write(rowString.toString());
	            bwOutFile.newLine();
	            
	            
			}
			else
			{
	            rowString.setLength(0);
	            rowString.append("<" + "DataResource" + " ");
	            rowString.append("dataResourceId" + "=\"" + dataResourceId + "\" ");
	            rowString.append("dataResourceTypeId" + "=\"" + "SHORT_TEXT" + "\" ");
	            rowString.append("dataTemplateTypeId" + "=\"" + "FTL" + "\" ");
	            rowString.append("statusId" + "=\"" + "CTNT_PUBLISHED" + "\" ");
	            rowString.append("dataResourceName" + "=\"" + contentValue + "\" ");
	            if(UtilValidate.isNotEmpty(localeString))
	            {
	            	rowString.append("localeString" + "=\"" + localeString + "\" ");
	            }
	            rowString.append("mimeTypeId" + "=\"" + "text/html" + "\" ");
	            
	            if (!UtilValidate.isUrl(contentValue)) 
         		{
	            	Map<Object, Object> imageLocationMap = new HashMap<Object, Object>();
	            	for(Map<Object, Object> imageLocationPref : imageLocationPrefList) {
	            		imageLocationMap.put(imageLocationPref.get("key"), imageLocationPref.get("value"));
	            	}
	            	
	            	String defaultImageDirectory = (String)imageLocationMap.get("DEFAULT_IMAGE_DIRECTORY");
	            	if(UtilValidate.isNotEmpty(defaultImageDirectory)) 
	            	{
	            		contentValue = defaultImageDirectory + contentValue;	
	            	}
         		}
	           
	            rowString.append("objectInfo" + "=\"" + contentValue.trim() + "\" ");
	            rowString.append("isPublic" + "=\"" + "Y" + "\" ");
	            rowString.append("/>");
	            bwOutFile.write(rowString.toString());
	            bwOutFile.newLine();
			}

            rowString.setLength(0);
            rowString.append("<" + "Content" + " ");
            rowString.append("contentId" + "=\"" + contentId + "\" ");
            rowString.append("contentTypeId" + "=\"" + "DOCUMENT" + "\" ");
            rowString.append("dataResourceId" + "=\"" + dataResourceId + "\" ");
            rowString.append("statusId" + "=\"" + "CTNT_PUBLISHED" + "\" ");
            rowString.append("contentName" + "=\"" + colName + "\" ");
            if(UtilValidate.isNotEmpty(localeString))
            {
            	rowString.append("localeString" + "=\"" + localeString + "\" ");
            }
            rowString.append("/>");
            bwOutFile.write(rowString.toString());
            bwOutFile.newLine();
			
            rowString.setLength(0);
            rowString.append("<" + "ProductContent" + " ");
            rowString.append("productId" + "=\"" + productId + "\" ");
            rowString.append("contentId" + "=\"" + contentId + "\" ");
            rowString.append("productContentTypeId" + "=\"" + productContentTypeId + "\" ");
            rowString.append("fromDate" + "=\"" + _sdf.format(contentTimestamp) + "\" ");
			if (UtilValidate.isNotEmpty(contentValueThruDate))
			{
				java.util.Date formattedThuDate=OsafeAdminUtil.validDate(contentValueThruDate);
           	    contentValueThruDate =_sdf.format(formattedThuDate);
	            rowString.append("thruDate" + "=\"" + contentValueThruDate + "\" ");
			}
			else
			{
	            rowString.append("thruDate" + "=\"" + null + "\" ");
			}
            rowString.append("/>");
            bwOutFile.write(rowString.toString());
            bwOutFile.newLine();
    		
    	}
     	 catch (Exception e) {
	         }

     	 return;
    	
    }
    
    private static void addCategoryContentRow(StringBuilder rowString,Map mRow,BufferedWriter bwOutFile,String contentType,String categoryContentType,String colName) {

		String objectImagePath = OSAFE_PROP.getString("productCategoryImagesPath");
		String contentId=null;
		String productCategoryId=null;
		String dataResourceId=null;
    	try {
    		
			String contentValue=(String)mRow.get(colName);
			if (UtilValidate.isEmpty(contentValue) && UtilValidate.isEmpty(contentValue.trim()))
			{
				return;
			}
			productCategoryId=(String)mRow.get("productCategoryId");
			
	        List<GenericValue> lCategoryContent = _delegator.findByAnd("ProductCategoryContent", UtilMisc.toMap("productCategoryId",productCategoryId,"prodCatContentTypeId",categoryContentType),UtilMisc.toList("-fromDate"));
			if (UtilValidate.isNotEmpty(lCategoryContent))
			{
				GenericValue categoryContent = EntityUtil.getFirst(lCategoryContent);
				GenericValue content=categoryContent.getRelatedOne("Content");
				contentId=content.getString("contentId");
				dataResourceId=content.getString("dataResourceId");
			}
			else
			{
				contentId=_delegator.getNextSeqId("Content");
				dataResourceId=_delegator.getNextSeqId("DataResource");
				
			}
    		

			if ("text".equals(contentType))
			{
	            rowString.setLength(0);
	            rowString.append("<" + "DataResource" + " ");
	            rowString.append("dataResourceId" + "=\"" + dataResourceId + "\" ");
	            rowString.append("dataResourceTypeId" + "=\"" + "ELECTRONIC_TEXT" + "\" ");
	            rowString.append("dataTemplateTypeId" + "=\"" + "FTL" + "\" ");
	            rowString.append("statusId" + "=\"" + "CTNT_PUBLISHED" + "\" ");
	            rowString.append("dataResourceName" + "=\"" + colName + "\" ");
	            if(UtilValidate.isNotEmpty(localeString))
	            {
	            	rowString.append("localeString" + "=\"" + localeString + "\" ");
	            }
	            rowString.append("mimeTypeId" + "=\"" + "application/octet-stream" + "\" ");
	            rowString.append("objectInfo" + "=\"" + "" + "\" ");
	            rowString.append("isPublic" + "=\"" + "Y" + "\" ");
	            rowString.append("/>");
	            bwOutFile.write(rowString.toString());
	            bwOutFile.newLine();

	            rowString.setLength(0);
	            rowString.append("<" + "ElectronicText" + " ");
	            rowString.append("dataResourceId" + "=\"" + dataResourceId + "\"> ");
	            rowString.append("<textData><![CDATA[" + "=\"" +contentValue + "\" ");
	            rowString.append("]]></textData></ElectronicText>");
	            bwOutFile.write(rowString.toString());
	            bwOutFile.newLine();
	            
	            
			}
			else
			{
	            rowString.setLength(0);
	            rowString.append("<" + "DataResource" + " ");
	            rowString.append("dataResourceId" + "=\"" + dataResourceId + "\" ");
	            rowString.append("dataResourceTypeId" + "=\"" + "SHORT_TEXT" + "\" ");
	            rowString.append("dataTemplateTypeId" + "=\"" + "FTL" + "\" ");
	            rowString.append("statusId" + "=\"" + "CTNT_PUBLISHED" + "\" ");
	            rowString.append("dataResourceName" + "=\"" + contentValue + "\" ");
	            if(UtilValidate.isNotEmpty(localeString))
	            {
	            	rowString.append("localeString" + "=\"" + localeString + "\" ");
	            }
	            rowString.append("mimeTypeId" + "=\"" + "text/html" + "\" ");
	            
	            if (!UtilValidate.isUrl(contentValue)) 
         	    {
	            	Map<Object, Object> imageLocationMap = new HashMap<Object, Object>();
	            	for(Map<Object, Object> imageLocationPref : imageLocationPrefList) 
	            	{
	            		imageLocationMap.put(imageLocationPref.get("key"), imageLocationPref.get("value"));
	            	}
	            	
	            	String defaultImageDirectory = (String)imageLocationMap.get("DEFAULT_IMAGE_DIRECTORY");
	            	if(UtilValidate.isNotEmpty(defaultImageDirectory)) 
	            	{
		                contentValue = defaultImageDirectory + contentValue;
	            	}	
         		}
	            
	            rowString.append("objectInfo" + "=\"" + contentValue.trim() + "\" ");
	            rowString.append("isPublic" + "=\"" + "Y" + "\" ");
	            rowString.append("/>");
	            bwOutFile.write(rowString.toString());
	            bwOutFile.newLine();
			}

            rowString.setLength(0);
            rowString.append("<" + "Content" + " ");
            rowString.append("contentId" + "=\"" + contentId + "\" ");
            rowString.append("contentTypeId" + "=\"" + "DOCUMENT" + "\" ");
            rowString.append("dataResourceId" + "=\"" + dataResourceId + "\" ");
            rowString.append("statusId" + "=\"" + "CTNT_PUBLISHED" + "\" ");
            rowString.append("contentName" + "=\"" + colName + "\" ");
            if(UtilValidate.isNotEmpty(localeString))
            {
            	rowString.append("localeString" + "=\"" + localeString + "\" ");
            }
            rowString.append("/>");
            bwOutFile.write(rowString.toString());
            bwOutFile.newLine();
			String sFromDate = (String)mRow.get("fromDate");
			if (UtilValidate.isEmpty(sFromDate))
			{
				sFromDate=_sdf.format(UtilDateTime.nowTimestamp());
			}
            rowString.setLength(0);
            rowString.append("<" + "ProductCategoryContent" + " ");
            rowString.append("productCategoryId" + "=\"" + productCategoryId + "\" ");
            rowString.append("contentId" + "=\"" + contentId + "\" ");
            rowString.append("prodCatContentTypeId" + "=\"" + categoryContentType + "\" ");
            rowString.append("fromDate" + "=\"" + sFromDate + "\" ");
            rowString.append("/>");
            bwOutFile.write(rowString.toString());
            bwOutFile.newLine();
    		
    	}
     	 catch (Exception e) {
	         }

     	 return;
    	
    }
    private static void addProductGoodIdentificationRow(StringBuilder rowString,Map mRow,BufferedWriter bwOutFile,String productId,String colName,String goodIdentificationTypeId) 
    {
    	try {
			String idValue=(String)mRow.get(colName);
			if (UtilValidate.isEmpty(idValue))
			{
				return;
			}
            rowString.setLength(0);
            rowString.append("<" + "GoodIdentification" + " ");
       	    rowString.append("productId" + "=\"" + productId + "\" ");
            rowString.append("goodIdentificationTypeId" + "=\"" + goodIdentificationTypeId + "\" ");
            rowString.append("idValue" + "=\"" + idValue + "\" ");
            rowString.append("/>");
            bwOutFile.write(rowString.toString());
            bwOutFile.newLine();
    		
    	}
    	catch (Exception e)
    	{
    		
    	}
    }
    
    private static void addProductAttributeRow(StringBuilder rowString,Map mRow,BufferedWriter bwOutFile,String productId,String colName,String attrName) 
    {
    	try {
			String attrValue=(String)mRow.get(colName);
			if (UtilValidate.isEmpty(attrValue))
			{
				return;
			}
            rowString.setLength(0);
            rowString.append("<" + "ProductAttribute" + " ");
       	    rowString.append("productId" + "=\"" + productId + "\" ");
            rowString.append("attrName" + "=\"" + attrName + "\" ");
            rowString.append("attrValue" + "=\"" + attrValue + "\" ");
            rowString.append("/>");
            bwOutFile.write(rowString.toString());
            bwOutFile.newLine();
    		
    	}
    	catch (Exception e)
    	{
    		
    	}
    }

    private static String getProductCategoryContent(String productCategoryId ,String productcategoryContentTypeId,List lproductCategoryContent) {
		String contentText=null;

    	try {
    		List<GenericValue> lContent = EntityUtil.filterByCondition(lproductCategoryContent, EntityCondition.makeCondition("prodCatContentTypeId", EntityOperator.EQUALS, productcategoryContentTypeId));
			if (UtilValidate.isNotEmpty(lContent))
			{
                   return getContent(lContent);
            }
    		
    	}
     	 catch (Exception e) {
             Debug.logError(e, module);
	    }

     	 return contentText;
    	
    }
    
    private static String getProductContent(String productId ,String productContentTypeId,List lproductContent) {
		String contentText=null;

    	try {
    		
    		List<GenericValue> lContent = EntityUtil.filterByCondition(lproductContent, EntityCondition.makeCondition("productContentTypeId", EntityOperator.EQUALS, productContentTypeId));
			if (UtilValidate.isNotEmpty(lContent))
			{
                   return getContent(lContent);
            }
    	}
     	 catch (Exception e) {
             Debug.logError(e, module);
	    }

     	 return contentText;
    	
    }
    
    private static String getProductContentThruDate(String productId ,String productContentTypeId,List lproductContent) {
		String contentText=null;

    	try {
    		
    		List<GenericValue> lContent = EntityUtil.filterByCondition(lproductContent, EntityCondition.makeCondition("productContentTypeId", EntityOperator.EQUALS, productContentTypeId));
			if (UtilValidate.isNotEmpty(lContent))
			{
				GenericValue contentContent = EntityUtil.getFirst(lContent);
				
				Timestamp tsstamp = contentContent.getTimestamp("thruDate");
                if (UtilValidate.isNotEmpty(tsstamp))
                {
                	return _sdf.format(new Date(tsstamp.getTime()));
                }
                else
                {
                	return "";
                }
                
            }
    	}
     	 catch (Exception e) {
             Debug.logError(e, module);
	    }

     	 return contentText;
    	
    }

    private static String getPartyContent(String partyId ,String partyContentTypeId,List lpartyContent) {
		String contentText=null;

    	try {
    		
    		List<GenericValue> lContent = EntityUtil.filterByCondition(lpartyContent, EntityCondition.makeCondition("partyContentTypeId", EntityOperator.EQUALS, partyContentTypeId));
			if (UtilValidate.isNotEmpty(lContent))
			{
                   return getContent(lContent);
            }
    		
    	}
     	 catch (Exception e) {
             Debug.logError(e, module);
	    }

     	 return contentText;
    }
    
    private static String getPartyContentThruDate(String partyId ,String partyContentTypeId,List lpartyContent) {
		String contentText=null;

    	try {
    		
    		List<GenericValue> lContent = EntityUtil.filterByCondition(lpartyContent, EntityCondition.makeCondition("partyContentTypeId", EntityOperator.EQUALS, partyContentTypeId));
			if (UtilValidate.isNotEmpty(lContent))
			{
				GenericValue contentContent = EntityUtil.getFirst(lContent);
				
				Timestamp tsstamp = contentContent.getTimestamp("thruDate");
                if (UtilValidate.isNotEmpty(tsstamp))
                {
                	return _sdf.format(new Date(tsstamp.getTime()));
                }
                else
                {
                	return "";
                }
                
            }
    	}
     	 catch (Exception e) {
             Debug.logError(e, module);
	    }

     	 return contentText;
    	
    }

    private static String getContent(List lContent) {
		String contentText=null;

    	try {
    		
				GenericValue contentContent = EntityUtil.getFirst(lContent);
				GenericValue content=contentContent.getRelatedOne("Content");
				GenericValue dataResource=content.getRelatedOne("DataResource");
				String dataResourceTypeId=dataResource.getString("dataResourceTypeId");
				if ("ELECTRONIC_TEXT".equals(dataResourceTypeId))
				{
					GenericValue electronicText=dataResource.getRelatedOne("ElectronicText");
					return electronicText.getString("textData");
					
				}
				else if ("SHORT_TEXT".equals(dataResourceTypeId))
				{
					return dataResource.getString("objectInfo");
				}
    		
    	}
     	 catch (Exception e) {
             Debug.logError(e, module);
	    }

     	 return contentText;
    	
    }

    private static void addPartyContentRow(StringBuilder rowString,Map mRow,BufferedWriter bwOutFile,String contentType,String partyContentType,String colName,String imagesDirPath, String imageUrl, String colNameThruDate) {

		String contentId=null;
		String partyId=null;
		String dataResourceId=null;
		Timestamp contentTimestamp = null;
    	try {
    		
			String contentValue=(String)mRow.get(colName);
			if (UtilValidate.isEmpty(contentValue) && UtilValidate.isEmpty(contentValue.trim()))
			{
				return;
			}
			partyId=(String)mRow.get("partyId");
			String contentValueThruDate=(String)mRow.get(colNameThruDate);
	        List<GenericValue> lPartyContent = _delegator.findByAnd("PartyContent", UtilMisc.toMap("partyId",partyId,"partyContentTypeId",partyContentType),UtilMisc.toList("-fromDate"));
			if (UtilValidate.isNotEmpty(lPartyContent))
			{
				GenericValue partyContent = EntityUtil.getFirst(lPartyContent);
				GenericValue content=partyContent.getRelatedOne("Content");
				contentId=content.getString("contentId");
				dataResourceId=content.getString("dataResourceId");
				contentTimestamp =partyContent.getTimestamp("fromDate");
			}
			else
			{
				contentId=_delegator.getNextSeqId("Content");
				dataResourceId=_delegator.getNextSeqId("DataResource");
				contentTimestamp =UtilDateTime.nowTimestamp();
			}

			if ("text".equals(contentType))
			{
	            rowString.setLength(0);
	            rowString.append("<" + "DataResource" + " ");
	            rowString.append("dataResourceId" + "=\"" + dataResourceId + "\" ");
	            rowString.append("dataResourceTypeId" + "=\"" + "ELECTRONIC_TEXT" + "\" ");
	            rowString.append("dataTemplateTypeId" + "=\"" + "FTL" + "\" ");
	            rowString.append("statusId" + "=\"" + "CTNT_PUBLISHED" + "\" ");
	            rowString.append("dataResourceName" + "=\"" + colName + "\" ");
	            if(UtilValidate.isNotEmpty(localeString))
	            {
	            	rowString.append("localeString" + "=\"" + localeString + "\" ");
	            }
	            rowString.append("mimeTypeId" + "=\"" + "application/octet-stream" + "\" ");
	            rowString.append("objectInfo" + "=\"" + "" + "\" ");
	            rowString.append("isPublic" + "=\"" + "Y" + "\" ");
	            rowString.append("/>");
	            bwOutFile.write(rowString.toString());
	            bwOutFile.newLine();

	            rowString.setLength(0);
	            rowString.append("<" + "ElectronicText" + " ");
	            rowString.append("dataResourceId" + "=\"" + dataResourceId + "\" ");
	            rowString.append("textData" + "=\"" + contentValue + "\" ");
	            rowString.append("/>");
	            bwOutFile.write(rowString.toString());
	            bwOutFile.newLine();
			}
			else
			{
	            rowString.setLength(0);
	            rowString.append("<" + "DataResource" + " ");
	            rowString.append("dataResourceId" + "=\"" + dataResourceId + "\" ");
	            rowString.append("dataResourceTypeId" + "=\"" + "SHORT_TEXT" + "\" ");
	            rowString.append("dataTemplateTypeId" + "=\"" + "FTL" + "\" ");
	            rowString.append("statusId" + "=\"" + "CTNT_PUBLISHED" + "\" ");
	            rowString.append("dataResourceName" + "=\"" + contentValue + "\" ");
	            if(UtilValidate.isNotEmpty(localeString))
	            {
	            	rowString.append("localeString" + "=\"" + localeString + "\" ");
	            }
	            rowString.append("mimeTypeId" + "=\"" + "text/html" + "\" ");
	            
	            if (!UtilValidate.isUrl(contentValue)) 
         	    {
	            	Map<Object, Object> imageLocationMap = new HashMap<Object, Object>();
	            	for(Map<Object, Object> imageLocationPref : imageLocationPrefList) 
	            	{
	            		imageLocationMap.put(imageLocationPref.get("key"), imageLocationPref.get("value"));
	            	}
	            	
	            	String defaultImageDirectory = (String)imageLocationMap.get("DEFAULT_IMAGE_DIRECTORY");
	            	if(UtilValidate.isNotEmpty(defaultImageDirectory)) 
	            	{
		                contentValue = defaultImageDirectory + contentValue;
	            	}	
         	    }
	            
	            
	            rowString.append("objectInfo" + "=\"" + contentValue.trim() + "\" ");
	            rowString.append("isPublic" + "=\"" + "Y" + "\" ");
	            rowString.append("/>");
	            bwOutFile.write(rowString.toString());
	            bwOutFile.newLine();
			}

            rowString.setLength(0);
            rowString.append("<" + "Content" + " ");
            rowString.append("contentId" + "=\"" + contentId + "\" ");
            rowString.append("contentTypeId" + "=\"" + "DOCUMENT" + "\" ");
            rowString.append("dataResourceId" + "=\"" + dataResourceId + "\" ");
            rowString.append("statusId" + "=\"" + "CTNT_PUBLISHED" + "\" ");
            rowString.append("contentName" + "=\"" + colName + "\" ");
            if(UtilValidate.isNotEmpty(localeString))
            {
            	rowString.append("localeString" + "=\"" + localeString + "\" ");
            }
            rowString.append("/>");
            bwOutFile.write(rowString.toString());
            bwOutFile.newLine();
			
            rowString.setLength(0);
            rowString.append("<" + "PartyContent" + " ");
            rowString.append("partyId" + "=\"" + partyId + "\" ");
            rowString.append("contentId" + "=\"" + contentId + "\" ");
            rowString.append("partyContentTypeId" + "=\"" + partyContentType + "\" ");
            rowString.append("fromDate" + "=\"" + _sdf.format(contentTimestamp) + "\" ");
            if (UtilValidate.isNotEmpty(contentValueThruDate))
			{
            	java.util.Date formattedThuDate=OsafeAdminUtil.validDate(contentValueThruDate);
           	    contentValueThruDate =_sdf.format(formattedThuDate);
            	rowString.append("thruDate" + "=\"" + contentValueThruDate + "\" ");
			}
			else
			{
	            rowString.append("thruDate" + "=\"" + null + "\" ");
			}
            rowString.append("/>");
            bwOutFile.write(rowString.toString());
            bwOutFile.newLine();
    		
    	}
     	 catch (Exception e) {
	         }

     	 return;
    	
    }
    
    
    private static void buildProductCategoryFeatures(List dataRows,String xmlDataDirPath, Boolean removeAll) {

        File fOutFile =null;
        BufferedWriter bwOutFile=null;
        Map mFeatureTypeMap = FastMap.newInstance();
		Map mFeatureExists = FastMap.newInstance();
		Map mFeatureTypeExists = FastMap.newInstance();
		Map mFeatureCategoryGroupApplExists = FastMap.newInstance();
		Map mFeatureGroupApplExists = FastMap.newInstance();
        StringBuilder  rowString = new StringBuilder();
        String masterProductId=null;
        String productId = null;
        String productCategoryId =null;
        String[] productCategoryIds =null;
        Map mProductCategoryIds = FastMap.newInstance();
		try {
			
	        fOutFile = new File(xmlDataDirPath, "010-ProductCategoryFeature.xml");
            if (fOutFile.createNewFile()) 
            {
            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));
                writeXmlHeader(bwOutFile);
                
                Map productFeatureSequenceMap = FastMap.newInstance();
                
                for (int i=0 ; i < dataRows.size() ; i++) 
                {
              	    Map mRow = (Map)dataRows.get(i);
              	  
              	    int totSelectableFeatures = 5;
            	    if(UtilValidate.isNotEmpty(mRow.get("totSelectableFeatures"))) 
            	    {
            	    	totSelectableFeatures =  Integer.parseInt((String)mRow.get("totSelectableFeatures"));
				    }
        	    
        	        for(int j = 1; j <= totSelectableFeatures; j++)
        	        {
        	    	    buildFeatureMap(mFeatureTypeMap, (String)mRow.get("selectabeFeature_"+j));
        	        }
              	    int totDescriptiveFeatures = 5;
              	    if(UtilValidate.isNotEmpty(mRow.get("totDescriptiveFeatures"))) 
              	    {
          	    	    totDescriptiveFeatures =  Integer.parseInt((String)mRow.get("totDescriptiveFeatures"));
				    }
          	    
          	        for(int j = 1; j <= totDescriptiveFeatures; j++)
          	        {
          	    	    buildFeatureMap(mFeatureTypeMap, (String)mRow.get("descriptiveFeature_"+j));
          	        }
              	    
	            	masterProductId=(String)mRow.get("masterProductId");
	            	productId = (String)mRow.get("productId");
	            	if ((UtilValidate.isEmpty(productId)) || (UtilValidate.isNotEmpty(productId) && masterProductId.equals(productId)))
	            	{
	                     productCategoryId = (String)mRow.get("productCategoryId");
	                     if(UtilValidate.isNotEmpty(productCategoryId)) 
	                     {
	                    	 productCategoryIds = productCategoryId.split(",");
	                    	 mProductCategoryIds.put(masterProductId, productCategoryIds);
	                     }
	            	}
	            	else
	            	{
	            		if(mProductCategoryIds.containsKey(masterProductId)) {
	            			productCategoryIds = (String[]) mProductCategoryIds.get(masterProductId);	
	            		}
	            	}
	            	if (mFeatureTypeMap.size() > 0)
	            	{
	            		Set featureTypeSet = mFeatureTypeMap.keySet();
	            		Iterator iterFeatureType = featureTypeSet.iterator();
	            		int seqNumber = 0;
	            		while (iterFeatureType.hasNext())
	            		{
	            			
	            			String featureType =(String)iterFeatureType.next();
	            			String featureTypeId = StringUtil.removeSpaces(featureType).toUpperCase();
                			if (featureTypeId.length() > 20)
                			{
                				featureTypeId=featureTypeId.substring(0,20);
                			}
	            			if (!mFeatureTypeExists.containsKey(featureType))
	            			{
	            				mFeatureTypeExists.put(featureType,featureType);
	                            rowString.setLength(0);
	                            rowString.append("<" + "ProductFeatureType" + " ");
	                            rowString.append("productFeatureTypeId" + "=\"" + featureTypeId + "\" ");
	                            rowString.append("parentTypeId" + "=\"" + "" + "\" ");
	                            rowString.append("hasTable" + "=\"" + "N" + "\" ");
	                            if(UtilValidate.isNotEmpty((String) mRow.get(featureType.trim()+"_description")))
	                            {
	                            	rowString.append("description" + "=\"" + mRow.get(featureType.trim()+"_description") + "\" ");
	                            }
	                            else
	                            {
	                            	rowString.append("description" + "=\"" + featureType + "\" ");
	                            }
	                            rowString.append("/>");
	                            bwOutFile.write(rowString.toString());
	                            bwOutFile.newLine();
	                            
	                            rowString.setLength(0);
	                            rowString.append("<" + "ProductFeatureCategory" + " ");
	                            rowString.append("productFeatureCategoryId" + "=\"" + featureTypeId + "\" ");
	                            rowString.append("parentCategoryId" + "=\"" + "" + "\" ");
	                            if(UtilValidate.isNotEmpty((String) mRow.get(featureType.trim()+"_description")))
	                            {
	                            	rowString.append("description" + "=\"" + mRow.get(featureType.trim()+"_description") + "\" ");
	                            }
	                            else
	                            {
	                            	rowString.append("description" + "=\"" + featureType + "\" ");
	                            }
	                            rowString.append("/>");
	                            bwOutFile.write(rowString.toString());
	                            bwOutFile.newLine();

	                            
	                            rowString.setLength(0);
	                            rowString.append("<" + "ProductFeatureGroup" + " ");
	                            rowString.append("productFeatureGroupId" + "=\"" + featureTypeId + "\" ");
	                            if(UtilValidate.isNotEmpty((String) mRow.get(featureType.trim()+"_description")))
	                            {
	                            	rowString.append("description" + "=\"" + mRow.get(featureType.trim()+"_description") + "\" ");
	                            }
	                            else
	                            {
	                            	rowString.append("description" + "=\"" + featureType + "\" ");
	                            }
	                            rowString.append("/>");
	                            bwOutFile.write(rowString.toString());
	                            bwOutFile.newLine();
	                            
	                            sFeatureGroupExists.add(featureTypeId);
	            			}
	            			
	            			if(UtilValidate.isNotEmpty(productCategoryIds)) 
	            			{
	            				for (int j=0;j < productCategoryIds.length;j++)
		                        {
		 	                        String sProductCategoryId= productCategoryIds[j].trim();
			            			if (UtilValidate.isNotEmpty(sProductCategoryId) && !mFeatureCategoryGroupApplExists.containsKey(sProductCategoryId+"_"+featureTypeId))
			            			{
			            				mFeatureCategoryGroupApplExists.put(sProductCategoryId+"_"+featureTypeId,sProductCategoryId+"_"+featureTypeId);
			            				
			            				String productFeatureCatGrpApplFromDate = _sdf.format(UtilDateTime.nowTimestamp());
			            				List<GenericValue> productFeatureCatGrpApplList = _delegator.findByAnd("ProductFeatureCatGrpAppl", UtilMisc.toMap("productCategoryId", sProductCategoryId, "productFeatureGroupId", featureTypeId),UtilMisc.toList("-fromDate"));
			            				productFeatureCatGrpApplList = EntityUtil.filterByDate(productFeatureCatGrpApplList);
			            				if(UtilValidate.isNotEmpty(productFeatureCatGrpApplList))
			            				{
			            					GenericValue productFeatureCatGrpAppl = EntityUtil.getFirst(productFeatureCatGrpApplList);
			            					productFeatureCatGrpApplFromDate = _sdf.format(new Date(productFeatureCatGrpAppl.getTimestamp("fromDate").getTime()));
			            				}
			            				rowString.setLength(0);
			                            rowString.append("<" + "ProductFeatureCatGrpAppl" + " ");
			                            rowString.append("productCategoryId" + "=\"" + sProductCategoryId + "\" ");
			                            rowString.append("productFeatureGroupId" + "=\"" + featureTypeId + "\" ");
			    	                    rowString.append("fromDate" + "=\"" + productFeatureCatGrpApplFromDate + "\" ");
			    	                    rowString.append("sequenceNum" + "=\"" + ((seqNumber +1) *10) + "\" ");
			                            rowString.append("/>");
			                            bwOutFile.write(rowString.toString());
			                            bwOutFile.newLine();
			                            
			                            mProductFeatureCatGrpApplFromDateExists.put(sProductCategoryId + "~" + featureTypeId, productFeatureCatGrpApplFromDate);
			            				
			                            String productFeatureCategoryApplFromDate = _sdf.format(UtilDateTime.nowTimestamp());
			            				List<GenericValue> productFeatureCategoryApplList = _delegator.findByAnd("ProductFeatureCategoryAppl", UtilMisc.toMap("productCategoryId", sProductCategoryId, "productFeatureCategoryId", featureTypeId),UtilMisc.toList("-fromDate"));
			            				productFeatureCategoryApplList = EntityUtil.filterByDate(productFeatureCategoryApplList);
			            				if(UtilValidate.isNotEmpty(productFeatureCategoryApplList))
			            				{
			            					GenericValue productFeatureCategoryAppl = EntityUtil.getFirst(productFeatureCategoryApplList);
			            					productFeatureCategoryApplFromDate = _sdf.format(new Date(productFeatureCategoryAppl.getTimestamp("fromDate").getTime()));
			            				}
			            				
			            				rowString.setLength(0);
			                            rowString.append("<" + "ProductFeatureCategoryAppl" + " ");
			                            rowString.append("productCategoryId" + "=\"" + sProductCategoryId + "\" ");
			                            rowString.append("productFeatureCategoryId" + "=\"" + featureTypeId + "\" ");
			    	                    rowString.append("fromDate" + "=\"" + productFeatureCategoryApplFromDate + "\" ");
			                            rowString.append("/>");
			                            bwOutFile.write(rowString.toString());
			                            bwOutFile.newLine();
			                            
			                            mProductFeatureCategoryApplFromDateExists.put(sProductCategoryId + "~" + featureTypeId, productFeatureCategoryApplFromDate);
			            			}
		                        	
		                        }
	            			}
	                        
	            			
                            FastMap mFeatureMap=(FastMap)mFeatureTypeMap.get(featureType);
	                		Set featureSet = mFeatureMap.keySet();
	                		Iterator iterFeature = featureSet.iterator();
	                		int iSeq=0;
	                		
	                		while (iterFeature.hasNext())
	                		{
	                			String featureId =(String)iterFeature.next();
	                			String featureDescription = (String) mFeatureMap.get(featureId);
	                			/*String featureId =StringUtil.removeSpaces(feature).toUpperCase();
	                			featureId =StringUtil.replaceString(featureId, "&", "");
	                			featureId=featureTypeId+"_"+featureId;
	                			if (featureId.length() > 20)
	                			{
	                				featureId=featureId.substring(0,20);
	                			}*/
		            			if (!mFeatureExists.containsKey(featureId))
		            			{
		            				mFeatureExists.put(featureId,featureId);
	 	                            rowString.setLength(0);
		                            rowString.append("<" + "ProductFeature" + " ");
		                            rowString.append("productFeatureId" + "=\"" + featureId + "\" ");
		                            rowString.append("productFeatureTypeId" + "=\"" + featureTypeId + "\" ");
		                            rowString.append("productFeatureCategoryId" + "=\"" + featureTypeId + "\" ");
		                            rowString.append("description" + "=\"" + featureDescription + "\" ");
		                            rowString.append("/>");
		                           bwOutFile.write(rowString.toString());
		                           bwOutFile.newLine();
		                           
		                           mFeatureValueExists.put(featureTypeId +"~" + featureDescription, featureId);
		            			}

		            			if (!mFeatureGroupApplExists.containsKey(featureId))
		            			{
		            				mFeatureGroupApplExists.put(featureId,featureId);
		            				
		            				String productFeatureGroupApplFromDate = _sdf.format(UtilDateTime.nowTimestamp());
		            				List<GenericValue> productFeatureGroupApplList = _delegator.findByAnd("ProductFeatureGroupAppl", UtilMisc.toMap("productFeatureGroupId", featureTypeId, "productFeatureId", featureId),UtilMisc.toList("-fromDate"));
		            				productFeatureGroupApplList = EntityUtil.filterByDate(productFeatureGroupApplList);
		            				
		            				if(UtilValidate.isNotEmpty(productFeatureGroupApplList))
		            				{
		            					GenericValue productFeatureGroupAppl = EntityUtil.getFirst(productFeatureGroupApplList);
		            					productFeatureGroupApplFromDate = _sdf.format(new Date(productFeatureGroupAppl.getTimestamp("fromDate").getTime()));
		            				}
		            				
		            				Map entityFieldMap = FastMap.newInstance();
		                            rowString.setLength(0);
		                            rowString.append("<" + "ProductFeatureGroupAppl" + " ");
		                            rowString.append("productFeatureGroupId" + "=\"" + featureTypeId + "\" ");
		                            rowString.append("productFeatureId" + "=\"" + featureId + "\" ");
		    	                    rowString.append("fromDate" + "=\"" + productFeatureGroupApplFromDate + "\" ");
		    	                    if(UtilValidate.isNotEmpty((String) mRow.get(featureType.trim()+"_sequenceNum"))) 
	                	            {
	                	            	rowString.append("sequenceNum" + "=\"" + (String) mRow.get(featureType.trim()+"_sequenceNum") + "\" ");
	                	            } 
	                	            else 
	                	            {
		                	            rowString.append("sequenceNum" + "=\"" + ((iSeq +1) *10) + "\" ");
	                	            }
		                            rowString.append("/>");
		                            
		                            if(UtilValidate.isEmpty((String) mRow.get(featureType.trim()+"_sequenceNum"))) 
		                            {
		                            	entityFieldMap.put("productFeatureGroupId", featureTypeId);
			                            entityFieldMap.put("productFeatureId", featureId);
			                            entityFieldMap.put("fromDate", productFeatureGroupApplFromDate);
			                            productFeatureSequenceMap.put(entityFieldMap, featureDescription);
		                            }
		                            bwOutFile.write(rowString.toString());
		                            bwOutFile.newLine();
		                            
		                            mProductFeatureGroupApplFromDateExists.put(featureTypeId + "~" + featureDescription, productFeatureGroupApplFromDate);
		            			}
		            			iSeq++;
	                			
	                		}
	                		seqNumber++;
	            		}
	            	}
	            }
                if(UtilValidate.isNotEmpty(productFeatureSequenceMap))
                {
                	buildFeatureSequence(rowString, bwOutFile, productFeatureSequenceMap, "ProductFeatureGroupAppl");	
                }
                bwOutFile.flush();
         	    writeXmlFooter(bwOutFile);
            }
    	}
      	catch (Exception e) 
      	{
   	    }
        finally 
        {
             try 
             {
                 if (bwOutFile != null) 
                 {
                	 bwOutFile.close();
                 }
             }
             catch (IOException ioe) 
             {
                 Debug.logError(ioe, module);
             }
         }
    }

    private static void buildProductDistinguishingFeatures(List dataRows,String xmlDataDirPath ) 
    {
        File fOutFile =null;
        BufferedWriter bwOutFile=null;
        Map mFeatureTypeMap = FastMap.newInstance();
        StringBuilder  rowString = new StringBuilder();
        String masterProductId=null;
        String variantProductId=null;
        Map mMasterProductId=FastMap.newInstance();
        
		try 
		{
			
	        fOutFile = new File(xmlDataDirPath, "060-ProductDistinguishingFeature.xml");
            if (fOutFile.createNewFile()) 
            {
            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));
                writeXmlHeader(bwOutFile);
                Map productFeatureSequenceMap = FastMap.newInstance();
                for (int i=0 ; i < dataRows.size() ; i++) 
                {
              	    Map mRow = (Map)dataRows.get(i);
	            	masterProductId=(String)mRow.get("masterProductId");
	            	variantProductId = (String)mRow.get("productId");
             		mFeatureTypeMap.clear();
             		int totDescriptiveFeatures = 5;
            	    if(UtilValidate.isNotEmpty(mRow.get("totDescriptiveFeatures"))) 
            	    {
            	    	totDescriptiveFeatures =  Integer.parseInt((String)mRow.get("totDescriptiveFeatures"));
				    }
            	    
            	    for(int j = 1; j <= totDescriptiveFeatures; j++)
            	    {
            	    	buildFeatureMap(mFeatureTypeMap, (String)mRow.get("descriptiveFeature_"+j));
            	    }
              	    
	            	if (mFeatureTypeMap.size() > 0)
	            	{
	            		Set featureTypeSet = mFeatureTypeMap.keySet();
	            		Iterator iterFeatureType = featureTypeSet.iterator();
	            		while (iterFeatureType.hasNext())
	            		{
	            			String featureType =(String)iterFeatureType.next();
	            			String featureTypeId = StringUtil.removeSpaces(featureType).toUpperCase();
	            			
	            			
                			if (featureTypeId.length() > 20)
                			{
                				featureTypeId=featureTypeId.substring(0,20);
                			}
	            			FastMap mFeatureMap=(FastMap)mFeatureTypeMap.get(featureType);
	                		Set featureSet = mFeatureMap.keySet();
	                		Iterator iterFeature = featureSet.iterator();
	                		int iSeq=0;
	                		while (iterFeature.hasNext())
	                		{
	                			String featureId =(String)iterFeature.next();
	                			String featureValue = (String) mFeatureMap.get(featureId);
	                			/*String featureId =StringUtil.removeSpaces(feature).toUpperCase();
	                			featureId =StringUtil.replaceString(featureId, "&", "");
	                			featureId=featureTypeId+"_"+featureId;
	                			if (featureId.length() > 20)
	                			{
	                				featureId=featureId.substring(0,20);
	                			}*/
		       		            	
		       		            String featureFromDate = _sdf.format(UtilDateTime.nowTimestamp());
		       		            if(UtilValidate.isEmpty((String) mRow.get(featureType.trim()+"_fromDate"))) 
		       		            {
		    		                List<GenericValue> productFeatureAppls = _delegator.findByAnd("ProductFeatureAppl", UtilMisc.toMap("productId",masterProductId,"productFeatureId",featureId, "productFeatureApplTypeId","DISTINGUISHING_FEAT"),UtilMisc.toList("-fromDate"));
		    		                if(UtilValidate.isNotEmpty(productFeatureAppls))
		    		                {
		    		                	productFeatureAppls = EntityUtil.filterByDate(productFeatureAppls);
		    		                	if(UtilValidate.isNotEmpty(productFeatureAppls)) 
		    		                	{
		    		                        GenericValue productFeatureAppl = EntityUtil.getFirst(productFeatureAppls);
		    		                    	featureFromDate = _sdf.format(new Date(productFeatureAppl.getTimestamp("fromDate").getTime()));
		    		                    }
		    		                }
		    		            } 
		       		            else 
		       		            {
		    		            	 featureFromDate = (String) mRow.get(featureType.trim()+"_fromDate");
					      			 java.util.Date formattedFromDate=OsafeAdminUtil.validDate(featureFromDate);
					      			 featureFromDate =_sdf.format(formattedFromDate);
		    		            }
		       		            Map entityFieldMap = FastMap.newInstance();	
		                		rowString.setLength(0);
		       	                rowString.append("<" + "ProductFeatureAppl" + " ");
		    	                rowString.append("productId" + "=\"" + masterProductId+ "\" ");
		    	                rowString.append("productFeatureId" + "=\"" + featureId + "\" ");
		    	                rowString.append("productFeatureApplTypeId" + "=\"" + "DISTINGUISHING_FEAT" + "\" ");
		    	                rowString.append("fromDate" + "=\"" + featureFromDate + "\" ");
	                	        if(UtilValidate.isNotEmpty((String) mRow.get(featureType.trim()+"_thruDate"))) 
	                	        {
	                	        	String featureThruDate = (String) mRow.get(featureType.trim()+"_thruDate");
					      			java.util.Date formattedFromDate=OsafeAdminUtil.validDate(featureFromDate);
					      			featureThruDate =_sdf.format(formattedFromDate);
	                	        	rowString.append("thruDate" + "=\"" + featureThruDate + "\" ");
	                	        }
                	            if(UtilValidate.isNotEmpty((String) mRow.get(featureType.trim()+"_sequenceNum"))) 
                	            {
                	            	rowString.append("sequenceNum" + "=\"" + (String) mRow.get(featureType.trim()+"_sequenceNum") + "\" ");
                	            } 
                	            else 
                	            {
	                	            rowString.append("sequenceNum" + "=\"" + ((iSeq +1) *10) + "\" ");
                	            }
		                        rowString.append("/>");
		                        
		                        entityFieldMap.put("productId", masterProductId);
		                        entityFieldMap.put("productFeatureId", featureId);
		                        entityFieldMap.put("fromDate", featureFromDate);
                	            productFeatureSequenceMap.put(entityFieldMap, featureValue);
                	            
		                        bwOutFile.write(rowString.toString());
		                        bwOutFile.newLine();
		       		            
		                  	    if (UtilValidate.isNotEmpty(variantProductId) && !(masterProductId.equals(variantProductId)))
		                  	    {
		                  	    	featureFromDate = _sdf.format(UtilDateTime.nowTimestamp());
		                  	    	
		                  	    	if(UtilValidate.isEmpty((String) mRow.get(featureType.trim()+"_fromDate"))) 
		                  	    	{
		    		                    List<GenericValue> productFeatureAppls = _delegator.findByAnd("ProductFeatureAppl", UtilMisc.toMap("productId",variantProductId,"productFeatureId",featureId, "productFeatureApplTypeId","DISTINGUISHING_FEAT"),UtilMisc.toList("-fromDate"));
		    		                    if(UtilValidate.isNotEmpty(productFeatureAppls))
		    		                    {
		    		                    	productFeatureAppls = EntityUtil.filterByDate(productFeatureAppls);
		    		                    	if(UtilValidate.isNotEmpty(productFeatureAppls)) 
		    		                    	{
		    		                    	    GenericValue productFeatureAppl = EntityUtil.getFirst(productFeatureAppls);
		    		                    	    featureFromDate = _sdf.format(new Date(productFeatureAppl.getTimestamp("fromDate").getTime()));
		    		                    	}
		    		                    }
		    		                } 
		                  	    	else 
		                  	    	{
		    		                	 featureFromDate = (String) mRow.get(featureType.trim()+"_fromDate");
						      			 java.util.Date formattedFromDate=OsafeAdminUtil.validDate(featureFromDate);
						      			 featureFromDate =_sdf.format(formattedFromDate);
		    		                }
		                  	    	
		                  	    	entityFieldMap = FastMap.newInstance();
		                            rowString.setLength(0);
		       	                    rowString.append("<" + "ProductFeatureAppl" + " ");
		    	                    rowString.append("productId" + "=\"" + variantProductId+ "\" ");
		    	                    rowString.append("productFeatureId" + "=\"" + featureId + "\" ");
		    	                    rowString.append("productFeatureApplTypeId" + "=\"" + "DISTINGUISHING_FEAT" + "\" ");
		    	                    rowString.append("fromDate" + "=\"" + featureFromDate + "\" ");
	                	            if(UtilValidate.isNotEmpty((String) mRow.get(featureType.trim()+"_thruDate"))) 
	                	            {
	                	            	String featureThruDate = (String) mRow.get(featureType.trim()+"_thruDate");
						      		    java.util.Date formattedFromDate=OsafeAdminUtil.validDate(featureThruDate);
						      		    featureThruDate =_sdf.format(formattedFromDate);
	                	            	rowString.append("thruDate" + "=\"" + featureThruDate + "\" ");
	                	            }
	                	            if(UtilValidate.isNotEmpty((String) mRow.get(featureType.trim()+"_sequenceNum"))) 
	                	            {
	                	            	rowString.append("sequenceNum" + "=\"" + (String) mRow.get(featureType.trim()+"_sequenceNum") + "\" ");
	                	            } 
	                	            else 
	                	            {
		                	            rowString.append("sequenceNum" + "=\"" + ((iSeq +1) *10) + "\" ");
	                	            }
		                            rowString.append("/>");
		                            
		                            entityFieldMap.put("productId", variantProductId);
		                            entityFieldMap.put("productFeatureId", featureId);
		                            entityFieldMap.put("fromDate", featureFromDate);
	                	            productFeatureSequenceMap.put(entityFieldMap, featureValue);
		                            bwOutFile.write(rowString.toString());
		                            bwOutFile.newLine();
		                            iSeq++;
		                  	    }
	                		}
	            		}
            	    }	            	 
	            }
                if(UtilValidate.isNotEmpty(productFeatureSequenceMap))
                {
                	buildFeatureSequence(rowString, bwOutFile, productFeatureSequenceMap, "ProductFeatureAppl");	
                }
                bwOutFile.flush();
         	    writeXmlFooter(bwOutFile);
            }
    
    	}
      	catch (Exception e) 
      	{
   	    }
        finally 
        {
            try 
            {
                if (bwOutFile != null) 
                {
               	    bwOutFile.close();
                }
            }
            catch (IOException ioe) 
            {
                Debug.logError(ioe, module);
            }
        }
    }
    
    private static void buildProductSelectableFeatures(List dataRows,String xmlDataDirPath ) {

        File fOutFile =null;
        BufferedWriter bwOutFile=null;
        Map mFeatureTypeMap = FastMap.newInstance();
        StringBuilder  rowString = new StringBuilder();
        String masterProductId=null;
        String productId=null;
        
		try 
		{
			
	        fOutFile = new File(xmlDataDirPath, "043-ProductSelectableFeature.xml");
            if (fOutFile.createNewFile()) 
            {
            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));
                writeXmlHeader(bwOutFile);
                
                Map productFeatureSequenceMap = FastMap.newInstance();
                
                for (int i=0 ; i < dataRows.size() ; i++) 
                {
              	    Map mRow = (Map)dataRows.get(i);
              	    masterProductId=(String)mRow.get("masterProductId");
  			        productId=(String)mRow.get("productId");
  			        
              	    mFeatureTypeMap.clear();
              	    
              	    int totSelectableFeatures = 5;
              	    if(UtilValidate.isNotEmpty(mRow.get("totSelectableFeatures"))) 
              	    {
              	    	totSelectableFeatures =  Integer.parseInt((String)mRow.get("totSelectableFeatures"));
				    }
              	    
              	    for(int j = 1; j <= totSelectableFeatures; j++)
          	        {
          	    	    buildFeatureMap(mFeatureTypeMap, (String)mRow.get("selectabeFeature_"+j));
          	        }
              	    
                    int iSeq=0;
                    	
              	        if(mFeatureTypeMap.size() > 0) 
              	        {
              	    	    Set featureTypeSet = mFeatureTypeMap.keySet();
	            		    Iterator iterFeatureType = featureTypeSet.iterator(); 
	            		    while (iterFeatureType.hasNext())
	            		    {
	            			    String featureType =(String)iterFeatureType.next();
	            			    String featureTypeId = StringUtil.removeSpaces(featureType).toUpperCase();
	            			    
                			    if (featureTypeId.length() > 20)
                			    {
                				    featureTypeId=featureTypeId.substring(0,20);
                			    }
                			    FastMap mFeatureMap=(FastMap)mFeatureTypeMap.get(featureType);
	                		    Set featureSet = mFeatureMap.keySet();
	                		    Iterator iterFeature = featureSet.iterator();
	                		    
	                		    while (iterFeature.hasNext())
	                		    {
	                			    String featureId =(String)iterFeature.next();
	                			    String featureValue = (String) mFeatureMap.get(featureId);
	                			    /*String featureId = feature;
	                			    StringUtil.removeSpaces(feature).toUpperCase();
	                			    featureId=featureTypeId+"_"+featureId;
	                			    if (featureId.length() > 20)
	                			    {
	                				    featureId=featureId.substring(0,20);
	                			    }*/
	                			    
	                			    String featureFromDate = _sdf.format(UtilDateTime.nowTimestamp());
	                			    if(UtilValidate.isEmpty((String) mRow.get(featureType.trim()+"_fromDate"))) 
	                			    {
		    		                    List<GenericValue> productFeatureAppls = _delegator.findByAnd("ProductFeatureAppl", UtilMisc.toMap("productId",productId,"productFeatureId",featureId, "productFeatureApplTypeId","STANDARD_FEATURE"),UtilMisc.toList("-fromDate"));
		    		                    if(UtilValidate.isNotEmpty(productFeatureAppls))
		    		                    {
		    		                    	productFeatureAppls = EntityUtil.filterByDate(productFeatureAppls);
		    		                    	if(UtilValidate.isNotEmpty(productFeatureAppls)) 
		    		                    	{
		    		                    	    GenericValue productFeatureAppl = EntityUtil.getFirst(productFeatureAppls);
		    		                    	    featureFromDate = _sdf.format(new Date(productFeatureAppl.getTimestamp("fromDate").getTime()));
		    		                    	}
		    		                    }
		    		                } 
	                			    else 
	                			    {
		    		                	featureFromDate = (String) mRow.get(featureType.trim()+"_fromDate");
		    		                }
	                			    Map entityFieldMap = FastMap.newInstance();
	                			    rowString.setLength(0);
	                	            rowString.append("<" + "ProductFeatureAppl" + " ");
	                	            rowString.append("productId" + "=\"" + productId + "\" ");
	                	            rowString.append("productFeatureId" + "=\"" + featureId + "\" ");
	                	            rowString.append("productFeatureApplTypeId" + "=\"" + "STANDARD_FEATURE" + "\" ");
	                	            rowString.append("fromDate" + "=\"" + featureFromDate + "\" ");
	                	            if(UtilValidate.isNotEmpty((String) mRow.get(featureType.trim()+"_thruDate"))) 
	                	            {
	                	            	String featureThruDate = (String) mRow.get(featureType.trim()+"_thruDate");
						      		    java.util.Date formattedFromDate=OsafeAdminUtil.validDate(featureThruDate);
						      		    featureThruDate =_sdf.format(formattedFromDate);
	                	            	rowString.append("thruDate" + "=\"" + featureThruDate + "\" ");
	                	            }
	                	            if(UtilValidate.isNotEmpty((String) mRow.get(featureType.trim()+"_sequenceNum"))) 
	                	            {
	                	            	rowString.append("sequenceNum" + "=\"" + (String) mRow.get(featureType.trim()+"_sequenceNum") + "\" ");
	                	            } 
	                	            else 
	                	            {
		                	            rowString.append("sequenceNum" + "=\"" + ((iSeq +1) *10) + "\" ");
	                	            }
	                	            rowString.append("/>");
	                	            
	                	            if(UtilValidate.isEmpty((String) mRow.get(featureType.trim()+"_sequenceNum")))
	                	            {
	                	            	entityFieldMap.put("productId", productId);
		                	            entityFieldMap.put("productFeatureId", featureId);
		                	            entityFieldMap.put("fromDate", featureFromDate);
		                	            productFeatureSequenceMap.put(entityFieldMap, featureValue);
	                	            }
	                	            
	                	            bwOutFile.write(rowString.toString());
	                	            bwOutFile.newLine();

	                	           
	                	            if(UtilValidate.isEmpty((String) mRow.get(featureType.trim()+"_fromDate"))) 
	                	            {
		    		                    List<GenericValue> productFeatureAppls = _delegator.findByAnd("ProductFeatureAppl", UtilMisc.toMap("productId",masterProductId,"productFeatureId",featureId, "productFeatureApplTypeId","SELECTABLE_FEATURE"),UtilMisc.toList("-fromDate"));
		    		                    if(UtilValidate.isNotEmpty(productFeatureAppls))
		    		                    {
		    		                    	productFeatureAppls = EntityUtil.filterByDate(productFeatureAppls);
		    		                    	if(UtilValidate.isNotEmpty(productFeatureAppls)) 
		    		                    	{
		    		                    	    GenericValue productFeatureAppl = EntityUtil.getFirst(productFeatureAppls);
		    		                    	    featureFromDate = _sdf.format(new Date(productFeatureAppl.getTimestamp("fromDate").getTime()));
		    		                    	}
		    		                    }
		    		                } 
	                	            else 
	                	            {
		    		                	featureFromDate = (String) mRow.get(featureType.trim()+"_fromDate");
		    		                }
	                	            entityFieldMap = FastMap.newInstance();
	                	            rowString.setLength(0);
	                	            rowString.append("<" + "ProductFeatureAppl" + " ");
	                	            rowString.append("productId" + "=\"" + masterProductId + "\" ");
	                	            rowString.append("productFeatureId" + "=\"" + featureId + "\" ");
	                	            rowString.append("productFeatureApplTypeId" + "=\"" + "SELECTABLE_FEATURE" + "\" ");
	                	            rowString.append("fromDate" + "=\"" + featureFromDate + "\" ");
	                	            if(UtilValidate.isNotEmpty((String) mRow.get(featureType.trim()+"_thruDate"))) 
	                	            {
	                	            	String featureThruDate = (String) mRow.get(featureType.trim()+"_thruDate");
						      		    java.util.Date formattedFromDate=OsafeAdminUtil.validDate(featureThruDate);
						      		    featureThruDate =_sdf.format(formattedFromDate);
	                	            	rowString.append("thruDate" + "=\"" + featureThruDate + "\" ");
	                	            }
	                	            if(UtilValidate.isNotEmpty((String) mRow.get(featureType.trim()+"_sequenceNum"))) 
	                	            {
	                	            	rowString.append("sequenceNum" + "=\"" + (String) mRow.get(featureType.trim()+"_sequenceNum") + "\" ");
	                	            } 
	                	            else 
	                	            {
		                	            rowString.append("sequenceNum" + "=\"" + ((iSeq +1) *10) + "\" ");
	                	            }
	                	            rowString.append("/>");
	                	            if(UtilValidate.isEmpty((String) mRow.get(featureType.trim()+"_sequenceNum")))
	                	            {
	                	            	entityFieldMap.put("productId", masterProductId);
		                	            entityFieldMap.put("productFeatureId", featureId);
		                	            entityFieldMap.put("fromDate", featureFromDate);
		                	            productFeatureSequenceMap.put(entityFieldMap, featureValue);
	                	            }
	                	            
	                	            bwOutFile.write(rowString.toString());
	                	            bwOutFile.newLine();
	                			
	                		    }
	            		    }
              	        }
	            }
                if(UtilValidate.isNotEmpty(productFeatureSequenceMap))
                {
                	buildFeatureSequence(rowString, bwOutFile, productFeatureSequenceMap, "ProductFeatureAppl");	
                }
                
                bwOutFile.flush();
         	    writeXmlFooter(bwOutFile);
            }
    	}
      	catch (Exception e) 
      	{
   	    }
        finally 
        {
             try 
             {
                 if (bwOutFile != null) 
                 {
                	 bwOutFile.close();
                 }
             }
             catch (IOException ioe) 
             {
                 Debug.logError(ioe, module);
             }
        }
      	 
    }
    
    private static void buildFeatureSequence(StringBuilder rowString, BufferedWriter bwOutFile, Map productFeatureSequenceMap, String entityName)
    {
    	List<Map.Entry> productFeatureSequenceMapSort = new ArrayList<Map.Entry>(productFeatureSequenceMap.entrySet());
        Collections.sort(productFeatureSequenceMapSort,
                 new Comparator() {
                     public int compare(Object firstObjToCompare, Object secondObjToCompare) 
                     {
             	    	Map.Entry e1 = (Map.Entry) firstObjToCompare;
             	        Map.Entry e2 = (Map.Entry) secondObjToCompare;
             	        
             		    return OsafeAdminUtil.alphaNumericSort(e1.getValue().toString(), e2.getValue().toString());
                     }
        });
        
        int iSeq = 1;
        for (Map.Entry entry : productFeatureSequenceMapSort) 
        {
        	try 
        	{
        		Map<String, String> entityFieldMap = (Map) entry.getKey();
        		
    		    rowString.setLength(0);
                rowString.append("<" + entityName + " ");
                for (Map.Entry<String, String> entityMap : entityFieldMap.entrySet()) 
                {
                	String entityFieldName = entityMap.getKey();
                	String entityFieldValue = entityFieldMap.get(entityFieldName);
                	rowString.append(entityFieldName + "=\"" + entityFieldValue + "\" ");
                }
                rowString.append("sequenceNum" + "=\"" + (iSeq * 10) + "\" ");
                rowString.append("/>");
                bwOutFile.write(rowString.toString());
                bwOutFile.newLine();
        	}
        	catch (Exception e) 
        	{
        		Debug.logError(e, module);
        	}
        	iSeq++;
        }
    }
    
    private static void buildProductFeatureImage(List dataRows,String xmlDataDirPath,String loadImagesDirPath, String imageUrl ) 
    {

        File fOutFile =null;
        BufferedWriter bwOutFile=null;
        Map mFeatureTypeMap = FastMap.newInstance();
        StringBuilder  rowString = new StringBuilder();
        String productId=null;
		Map mFeatureIdImageExists = FastMap.newInstance();
        
		try 
		{
			
	        fOutFile = new File(xmlDataDirPath, "065-ProductFeatureImage.xml");
            if (fOutFile.createNewFile()) 
            {
            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));
                writeXmlHeader(bwOutFile);
                for (int i=0 ; i < dataRows.size() ; i++) 
                {
                	 Map mRow = (Map)dataRows.get(i);
	            	 String selectFeatureImage=(String)mRow.get("plpSwatchImage");
	            	 if (UtilValidate.isNotEmpty(selectFeatureImage))
	            	 {
	              		mFeatureTypeMap.clear();
	              	    buildFeatureMap(mFeatureTypeMap, (String)mRow.get("featureId"));
	                	if (mFeatureTypeMap.size() > 0)
	                	{
	                		addProductFeatureImageRow(rowString, bwOutFile, mFeatureTypeMap, FastMap.newInstance(),selectFeatureImage,"plpSwatchImage","PLP_SWATCH_IMAGE_URL",loadImagesDirPath, imageUrl);
	                	}
	            	 }
	            	 selectFeatureImage=(String)mRow.get("pdpSwatchImage");
	            	 if (UtilValidate.isNotEmpty(selectFeatureImage))
	            	 {
	              		mFeatureTypeMap.clear();
	              	    buildFeatureMap(mFeatureTypeMap, (String)mRow.get("featureId"));
	                	if (mFeatureTypeMap.size() > 0)
	                	{
	                		mFeatureIdImageExists = addProductFeatureImageRow(rowString, bwOutFile, mFeatureTypeMap, FastMap.newInstance(),selectFeatureImage,"pdpSwatchImage","PDP_SWATCH_IMAGE_URL",loadImagesDirPath, imageUrl);
	                	}
	            	 }
	            }
                bwOutFile.flush();
         	    writeXmlFooter(bwOutFile);
            }
    	}
      	 catch (Exception e) {
   	         }
         finally {
             try {
                 if (bwOutFile != null) 
                 {
                	 bwOutFile.close();
                 }
             } catch (IOException ioe) {
                 Debug.logError(ioe, module);
             }
         }
      	 
       }
    
    
    private static void buildProductFacetGroup(List dataRows,String xmlDataDirPath,String loadImagesDirPath, String imageUrl ) 
    {
    	
        File fOutFile =null;
        BufferedWriter bwOutFile=null;
        Map mFeatureTypeMap = FastMap.newInstance();
        StringBuilder  rowString = new StringBuilder();
        String productId=null;
		Map mFeatureIdImageExists = FastMap.newInstance();
		Map mFacetGroupExists = FastMap.newInstance();
        
		try 
		{
			
	        fOutFile = new File(xmlDataDirPath, "065-ProductFacetGroup.xml");
            if (fOutFile.createNewFile()) 
            {
            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));
                writeXmlHeader(bwOutFile);
                for (int i=0 ; i < dataRows.size() ; i++) 
                {
                	Map mRow = (Map)dataRows.get(i);
                	String productCategoryId=(String)mRow.get("productCategoryId");
                	String sequenceNum=(String)mRow.get("sequenceNum");
                	String fromDate=(String)mRow.get("fromDate");
                	String thruDate=(String)mRow.get("thruDate");
                	String minDisplay=(String)mRow.get("minDisplay");
                	String maxDisplay=(String)mRow.get("maxDisplay");
                	String tooltip=(String)mRow.get("tooltip");
                	String facetGroupId=(String)mRow.get("facetGroupId");
                	if(UtilValidate.isNotEmpty(facetGroupId))
                	{
                		facetGroupId = StringUtil.removeSpaces(facetGroupId).toUpperCase();
                		if (facetGroupId.length() > 20)
            			{
            				facetGroupId=facetGroupId.substring(0,20);
            			}
                	}
                	
                	String description=(String)mRow.get("description");
                	
            		if (!sFeatureGroupExists.contains(facetGroupId))
        			{
        				sFeatureGroupExists.add(facetGroupId);
        				
        				rowString.setLength(0);
                        rowString.append("<" + "ProductFeatureType" + " ");
                        rowString.append("productFeatureTypeId" + "=\"" + facetGroupId + "\" ");
                        rowString.append("parentTypeId" + "=\"" + "" + "\" ");
                        rowString.append("hasTable" + "=\"" + "N" + "\" ");
                        rowString.append("description" + "=\"" + description + "\" ");
                        rowString.append("/>");
                        bwOutFile.write(rowString.toString());
                        bwOutFile.newLine();
                        
                        rowString.setLength(0);
                        rowString.append("<" + "ProductFeatureCategory" + " ");
                        rowString.append("productFeatureCategoryId" + "=\"" + facetGroupId + "\" ");
                        rowString.append("parentCategoryId" + "=\"" + "" + "\" ");
                        rowString.append("description" + "=\"" + description + "\" ");
                        rowString.append("/>");
                        bwOutFile.write(rowString.toString());
                        bwOutFile.newLine();
        				
        				rowString.setLength(0);
                        rowString.append("<" + "ProductFeatureGroup" + " ");
                        rowString.append("productFeatureGroupId" + "=\"" + facetGroupId + "\" ");
                        rowString.append("description" + "=\"" + description + "\" ");
                        rowString.append("/>");
                        bwOutFile.write(rowString.toString());
                        bwOutFile.newLine();
        			}

                	String fromDateKey = productCategoryId + "~" + facetGroupId;
                	String productFeatureCatGrpApplFromDate = _sdf.format(UtilDateTime.nowTimestamp());
                	if(mProductFeatureCatGrpApplFromDateExists.containsKey(fromDateKey))
                	{
                		productFeatureCatGrpApplFromDate = (String)mProductFeatureCatGrpApplFromDateExists.get(fromDateKey);
                	}
                	else
                	{
                		mProductFeatureCatGrpApplFromDateExists.put(fromDateKey, productFeatureCatGrpApplFromDate);
                	}
                	rowString.setLength(0);
                    rowString.append("<" + "ProductFeatureCatGrpAppl" + " ");
                    rowString.append("productCategoryId" + "=\"" + productCategoryId + "\" ");
                    rowString.append("productFeatureGroupId" + "=\"" + facetGroupId + "\" ");
	                rowString.append("fromDate" + "=\"" + productFeatureCatGrpApplFromDate + "\" ");
	                rowString.append("sequenceNum" + "=\"" + sequenceNum + "\" ");
	                rowString.append("facetValueMin" + "=\"" + minDisplay + "\" ");
	                rowString.append("facetValueMax" + "=\"" + maxDisplay + "\" ");
	                rowString.append("facetTooltip" + "=\"" + tooltip + "\" ");
                    rowString.append("/>");
                    bwOutFile.write(rowString.toString());
                    bwOutFile.newLine();

                    String productFeatureCategoryApplFromDate = _sdf.format(UtilDateTime.nowTimestamp());
                	if(mProductFeatureCategoryApplFromDateExists.containsKey(fromDateKey))
                	{
                		productFeatureCategoryApplFromDate = (String)mProductFeatureCategoryApplFromDateExists.get(fromDateKey);
                	}
                	else
                	{
                		mProductFeatureCategoryApplFromDateExists.put(fromDateKey, productFeatureCategoryApplFromDate);
                	}
                	rowString.setLength(0);
                    rowString.append("<" + "ProductFeatureCategoryAppl" + " ");
                    rowString.append("productCategoryId" + "=\"" + productCategoryId + "\" ");
                    rowString.append("productFeatureCategoryId" + "=\"" + facetGroupId + "\" ");
                    rowString.append("fromDate" + "=\"" + productFeatureCategoryApplFromDate + "\" ");
                    rowString.append("/>");
                    bwOutFile.write(rowString.toString());
                    bwOutFile.newLine();
	            }
                bwOutFile.flush();
         	    writeXmlFooter(bwOutFile);
            }
    	}
      	 catch (Exception e) {
   	         }
         finally {
             try {
                 if (bwOutFile != null) 
                 {
                	 bwOutFile.close();
                 }
             } catch (IOException ioe) {
                 Debug.logError(ioe, module);
             }
         }
       }
    
    private static void buildProductFacetValue(List dataRows,String xmlDataDirPath,String loadImagesDirPath, String imageUrl ) 
    {
    	
        File fOutFile =null;
        BufferedWriter bwOutFile=null;
        Map mFeatureTypeMap = FastMap.newInstance();
        StringBuilder  rowString = new StringBuilder();
        String productId=null;
		Map mFeatureIdImageExists = FastMap.newInstance();
        
		try 
		{
	        fOutFile = new File(xmlDataDirPath, "067-ProductFacetValue.xml");
            if (fOutFile.createNewFile()) 
            {
            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));
                writeXmlHeader(bwOutFile);
                
                for (int i=0 ; i < dataRows.size() ; i++) 
                {
	            	 Map mRow = (Map)dataRows.get(i);
	            	 String productFeatureGroupId=(String)mRow.get("facetGroupId");
	            	 if(UtilValidate.isNotEmpty(productFeatureGroupId))
                	 {
            		 	productFeatureGroupId = StringUtil.removeSpaces(productFeatureGroupId).toUpperCase();
                		if (productFeatureGroupId.length() > 20)
            			{
                			productFeatureGroupId=productFeatureGroupId.substring(0,20);
            			}
                	 }
	            	 String productFeatureId=(String)mRow.get("facetValueId");
	            	 String description=(String)mRow.get("description");
	            	 if (UtilValidate.isNotEmpty(description))
	            	 {
	            		 description = description.trim();
	            	 }
	            	 String sequenceNum=(String)mRow.get("sequenceNum");
	            	 
	            	 if (!mFeatureValueExists.containsKey(productFeatureGroupId + "~" + description))
        			 {
	            		 productFeatureId = _delegator.getNextSeqId("ProductFeature");
                         rowString.setLength(0);
                         rowString.append("<" + "ProductFeature" + " ");
                         rowString.append("productFeatureId" + "=\"" + productFeatureId + "\" ");
                         rowString.append("productFeatureTypeId" + "=\"" + productFeatureGroupId + "\" ");
                         rowString.append("productFeatureCategoryId" + "=\"" + productFeatureGroupId + "\" ");
                         rowString.append("description" + "=\"" + description + "\" ");
                         rowString.append("/>");
                         bwOutFile.write(rowString.toString());
                         bwOutFile.newLine();
                         
                         //featureTypeIdMap is used in buildFeatureMap function below used for creating swatches
                         featureTypeIdMap.put(productFeatureGroupId.toUpperCase() + "~" + description, productFeatureId);
                         
	            		 mFeatureValueExists.put(productFeatureGroupId + "~" + description, productFeatureId);
        			 }
	            	 else
	            	 {
	            		 productFeatureId = (String)mFeatureValueExists.get(productFeatureGroupId + "~" + description);
	            	 }
	            	 
	            	 String fromDateKey = productFeatureGroupId + "~" + description;
	            	 String productFeatureGroupApplFromDate = _sdf.format(UtilDateTime.nowTimestamp());
	             	 if(mProductFeatureGroupApplFromDateExists.containsKey(fromDateKey))
	             	 {
	             		productFeatureGroupApplFromDate = (String)mProductFeatureGroupApplFromDateExists.get(fromDateKey);
	             	 }
                 	 else
                	 {
                 		mProductFeatureGroupApplFromDateExists.put(fromDateKey, productFeatureGroupApplFromDate);
                	 }
                 	 
                    rowString.setLength(0);
                    rowString.append("<" + "ProductFeatureGroupAppl" + " ");
                    rowString.append("productFeatureGroupId" + "=\"" + productFeatureGroupId + "\" ");
                    rowString.append("productFeatureId" + "=\"" + productFeatureId + "\" ");
                    rowString.append("fromDate" + "=\"" + productFeatureGroupApplFromDate + "\" ");
                    rowString.append("sequenceNum" + "=\"" + sequenceNum + "\" ");
                    rowString.append("/>");
                    bwOutFile.write(rowString.toString());
                    bwOutFile.newLine();
                    
                    String selectFeatureImage=(String)mRow.get("plpSwatchUrl");
   	            	if (UtilValidate.isNotEmpty(selectFeatureImage))
   	            	{
   	              		mFeatureTypeMap.clear();
   	              	    buildFeatureMap(mFeatureTypeMap, productFeatureGroupId + ":" + description);
   	              	
   	                	if (mFeatureTypeMap.size() > 0)
   	                	{
   	                		addProductFeatureImageRow(rowString, bwOutFile, mFeatureTypeMap, FastMap.newInstance(),selectFeatureImage,"plpSwatchImage","PLP_SWATCH_IMAGE_URL",loadImagesDirPath, imageUrl);
   	                	}
   	            	}
   	            	selectFeatureImage=(String)mRow.get("pdpSwatchUrl");
   	            	if (UtilValidate.isNotEmpty(selectFeatureImage))
   	            	{
   	              		mFeatureTypeMap.clear();
   	              	    buildFeatureMap(mFeatureTypeMap, productFeatureGroupId + ":" + description);
   	                	if (mFeatureTypeMap.size() > 0)
   	                	{
   	                		mFeatureIdImageExists = addProductFeatureImageRow(rowString, bwOutFile, mFeatureTypeMap, FastMap.newInstance(),selectFeatureImage,"pdpSwatchImage","PDP_SWATCH_IMAGE_URL",loadImagesDirPath, imageUrl);
   	                	}
   	            	}
	            }
                bwOutFile.flush();
         	    writeXmlFooter(bwOutFile);
            }
    	}
      	 catch (Exception e) {
   	         }
         finally {
             try {
                 if (bwOutFile != null) 
                 {
                	 bwOutFile.close();
                 }
             } catch (IOException ioe) {
                 Debug.logError(ioe, module);
             }
         }
         
       }
    
    private static void buildProductAssoc(List dataRows,String xmlDataDirPath) 
    {
        File fOutFile =null;
        BufferedWriter bwOutFile=null;
		try 
		{
	        fOutFile = new File(xmlDataDirPath, "070-ProductAssoc.xml");
            if (fOutFile.createNewFile()) 
            {
            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));

                writeXmlHeader(bwOutFile);
                int compSeqNum = 0;
                int accessSeqNum = 0;
                int seqNum = 0;
                for (int i=0 ; i < dataRows.size() ; i++) 
                {
                    StringBuilder  rowString = new StringBuilder();
	            	Map mRow = (Map)dataRows.get(i);
	                rowString.append("<" + "ProductAssoc" + " ");
	                rowString.append("productId" + "=\"" + mRow.get("productId")+ "\" ");
	                rowString.append("productIdTo" + "=\"" + mRow.get("productIdTo") + "\" ");
	                String productAssocTypeId = "PRODUCT_COMPLEMENT";
	                if(((String)mRow.get("productAssocType")).equalsIgnoreCase("ACCESSORY"))
	                {
	                    productAssocTypeId = "PRODUCT_ACCESSORY";
	                    accessSeqNum = accessSeqNum + 10;
	                    seqNum = accessSeqNum;
	                }
	                else
	                {
	                	compSeqNum = compSeqNum + 10;
	                	seqNum = compSeqNum;
	                }
	                rowString.append("productAssocTypeId" + "=\"" + productAssocTypeId + "\" ");
	                    
	                String productAssocFromDate = _sdf.format(UtilDateTime.nowTimestamp());;
	                if(UtilValidate.isEmpty(mRow.get("fromDate"))) 
	             	{
	                    List<GenericValue> productAssocs = _delegator.findByAnd("ProductAssoc", UtilMisc.toMap("productId",mRow.get("productId"),"productIdTo",mRow.get("productIdTo"),"productAssocTypeId",productAssocTypeId),UtilMisc.toList("-fromDate"));
	                    if(UtilValidate.isNotEmpty(productAssocs)) 
	                    {
	                    	productAssocs = EntityUtil.filterByDate(productAssocs);
	                    	if(UtilValidate.isNotEmpty(productAssocs)) 
	                        {
	                            GenericValue productAssoc = EntityUtil.getFirst(productAssocs);
	                        	productAssocFromDate = _sdf.format(new Date(productAssoc.getTimestamp("fromDate").getTime()));
	                        }
	                    }
	                } 
	             	else 
	                {
	             	    String fromDate=(String)mRow.get("fromDate");
	                    java.util.Date formattedFromDate=OsafeAdminUtil.validDate(fromDate);
	                    productAssocFromDate =_sdf.format(formattedFromDate);
	                }
	                rowString.append("fromDate" + "=\"" + productAssocFromDate + "\" ");
	                    
	                if (UtilValidate.isNotEmpty(mRow.get("thruDate")))
	                {
	                	String thruDate=(String)mRow.get("thruDate");
	                	java.util.Date formattedThuDate=OsafeAdminUtil.validDate(thruDate);
	                	String sThruDate =_sdf.format(formattedThuDate);
	                	if (UtilValidate.isNotEmpty(sThruDate))
	                    {
	                    	rowString.append("thruDate" + "=\"" + sThruDate + "\" ");
	                    }
	                 }
	               
	                rowString.append("sequenceNum" + "=\"" + seqNum + "\" ");
                    rowString.append("/>");
                    bwOutFile.write(rowString.toString());
                    bwOutFile.newLine();
	            }
                bwOutFile.flush();
         	    writeXmlFooter(bwOutFile);
            }
    	}
      	catch (Exception e) 
      	{
   	    }
        finally 
        {
            try 
            {
                if (bwOutFile != null) 
                {
                    bwOutFile.close();
                }
            } 
            catch (IOException ioe) 
            {
                Debug.logError(ioe, module);
            }
        }
    }
    
    static Map featureTypeIdMap = FastMap.newInstance();
    private static Map buildFeatureMap(Map featureTypeMap,String parseFeatureType) 
    {
    	if (UtilValidate.isNotEmpty(parseFeatureType))
    	{
        	int iFeatIdx = parseFeatureType.indexOf(':');
        	if (iFeatIdx > -1)
        	{
            	String featureType = parseFeatureType.substring(0,iFeatIdx).trim();
            	String sFeatures = parseFeatureType.substring(iFeatIdx +1);
                String[] featureTokens = sFeatures.split(",");
            	Map mFeatureMap = FastMap.newInstance();
                for (int f=0;f < featureTokens.length;f++)
                {
                	String featureId = ""; 
                	try 
                	{
                		String featureTypeKey = StringUtil.removeSpaces(featureType).toUpperCase()+"~"+featureTokens[f].trim();
                		if(featureTypeIdMap.containsKey(featureTypeKey))
                		{
                			featureId = (String) featureTypeIdMap.get(featureTypeKey); 
                		}
                		else
                		{
                			List productFeatureList = _delegator.findByAnd("ProductFeature", UtilMisc.toMap("productFeatureTypeId", StringUtil.removeSpaces(featureType).toUpperCase(), "productFeatureCategoryId", StringUtil.removeSpaces(featureType).toUpperCase(), "description", featureTokens[f].trim()));
                			if(UtilValidate.isNotEmpty(productFeatureList))
                			{
                				GenericValue productFeature = EntityUtil.getFirst(productFeatureList);
        						featureId = productFeature.getString("productFeatureId");
                			}
                			else
                			{
                				featureId = _delegator.getNextSeqId("ProductFeature");
                			}
                		}
                		featureTypeIdMap.put(featureTypeKey, featureId);
					} catch (GenericEntityException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
                	mFeatureMap.put(""+featureId,""+featureTokens[f].trim());
                }
        		featureTypeMap.put(featureType, mFeatureMap);
        	}
    		
    	}
    	return featureTypeMap;
    	    	
    }

    private static Map<String, String> formatProductXLSData(Map<String, String> dataMap) {
    	Map<String, String> formattedDataMap = new HashMap<String, String>();
    	for (Map.Entry<String, String> entry : dataMap.entrySet()) {
    		String value = entry.getValue();
    		if(UtilValidate.isNotEmpty(value)) {
    			value = StringUtil.replaceString(value, "&", "&amp");
    			value = StringUtil.replaceString(value, ";", "&#59;");
    	    	value = StringUtil.replaceString(value, "&amp", "&amp;");
    	    	value = StringUtil.replaceString(value, "\"", "&quot;");
    		}
    		formattedDataMap.put(entry.getKey(), value);
    	}
    	return formattedDataMap;
    }
    
    public static Map<String, Object> importEbayProductXls(DispatchContext ctx, Map<String, ?> context) {
        LocalDispatcher dispatcher = ctx.getDispatcher();
        _delegator = ctx.getDelegator();
        List<String> messages = FastList.newInstance();

        String ebayXlsFileName = (String)context.get("uploadFileName");
        String ebayXlsFilePath = (String)context.get("uploadFilePath");
        String productStoreId = (String) context.get("productStoreId");
        String browseRootProductCategoryId = (String) context.get("browseRootProductCategoryId");
        String fileName="clientEbayProductImport.xls";

        
        File inputWorkbook = null;
        File baseDataDir = null;
        BufferedWriter fOutProduct=null;
        
        String importDataPath = FlexibleStringExpander.expandString(OSAFE_ADMIN_PROP.getString("ecommerce-import-data-path"),context);
        
        if (UtilValidate.isNotEmpty(ebayXlsFileName)&& UtilValidate.isNotEmpty(ebayXlsFilePath) && ebayXlsFileName.toUpperCase().endsWith("XLS")) 
        {
            try {
                inputWorkbook = new File(ebayXlsFilePath + ebayXlsFileName);
            } catch (Exception exc) {
                Debug.logError(exc, module);
            }
        }
        else {
            messages.add("No path specified for Excel sheet file, doing nothing.");
        }
        
        if (inputWorkbook != null) 
        {
            WritableWorkbook workbook = null;
            
            try 
            {

                WorkbookSettings ws = new WorkbookSettings();
                ws.setLocale(new Locale("en", "EN"));
                Workbook wb = Workbook.getWorkbook(inputWorkbook,ws);
                Sheet ebaySheet = wb.getSheet(0);
                BufferedWriter bw = null; 

                
                File file = new File(importDataPath, "temp" + fileName);
                WorkbookSettings wbSettings = new WorkbookSettings();
                wbSettings.setLocale(new Locale("en", "EN"));
                workbook = Workbook.createWorkbook(file, wbSettings);
                int iRows=0;
                Map mWorkBookHeadCaptions = createWorkBookHeaderCaptions();

                WritableSheet excelSheetModHistory = createWorkBookSheet(workbook,"Mod History", 0);
            	createWorkBookHeaderRow(excelSheetModHistory, buildModHistoryHeader(),mWorkBookHeadCaptions);
            	createWorkBookRow(excelSheetModHistory, _sdf.format(UtilDateTime.nowDate()), 0, 1);
            	createWorkBookRow(excelSheetModHistory, "system", 1, 1);
            	createWorkBookRow(excelSheetModHistory, "Auto Generated Product Import Template From Ebay Product", 2, 1);
                
            	WritableSheet excelSheetCategory = createWorkBookSheet(workbook,"Category", 1);
            	createWorkBookHeaderRow(excelSheetCategory, buildCategoryHeader(),mWorkBookHeadCaptions);
            	
                WritableSheet excelSheetProduct = createWorkBookSheet(workbook,"Product", 2);
            	createWorkBookHeaderRow(excelSheetProduct, buildProductHeader(),mWorkBookHeadCaptions);

                WritableSheet excelSheetProductAssoc = createWorkBookSheet(workbook,"Product Association", 3);
            	createWorkBookHeaderRow(excelSheetProductAssoc, buildProductAssocHeader(),mWorkBookHeadCaptions);
            	
                WritableSheet excelSheetManufacturer = createWorkBookSheet(workbook,"Manufacturer", 4);
            	createWorkBookHeaderRow(excelSheetManufacturer, buildManufacturerHeader(),mWorkBookHeadCaptions);

            	List dataRows = buildDataRows(buildEbayProductHeader(),ebaySheet);
            	iRows = createProductCategoryWorkSheetFromEbay(excelSheetCategory, browseRootProductCategoryId,dataRows);
            	createWorkBookRow(excelSheetModHistory, _sdf.format(UtilDateTime.nowDate()), 0, 2);
            	createWorkBookRow(excelSheetModHistory, "system", 1, 2);
            	createWorkBookRow(excelSheetModHistory,"(" +  iRows + ") Product Categories Generated", 2, 2);

            	iRows = createProductWorkSheetFromEbay(excelSheetProduct, browseRootProductCategoryId,dataRows);
            	createWorkBookRow(excelSheetModHistory, _sdf.format(UtilDateTime.nowDate()), 0, 2);
            	createWorkBookRow(excelSheetModHistory, "system", 1, 2);
            	createWorkBookRow(excelSheetModHistory,"(" +  iRows + ") Products Generated", 2, 2);
            	
            	workbook.write();
                workbook.close();
                
                new File(importDataPath, fileName).delete();
                File renameFile =new File(importDataPath, fileName);
                RandomAccessFile out = new RandomAccessFile(renameFile, "rw");
		        InputStream inputStr = new FileInputStream(file);
		        byte[] bytes = new byte[102400];
		        int bytesRead;
		        while ((bytesRead = inputStr.read(bytes)) != -1)
		        {
		            out.write(bytes, 0, bytesRead);
		        }
		        out.close();
		        inputStr.close();
            	

            } catch (BiffException be) {
                Debug.logError(be, module);
            } catch (Exception exc) {
                Debug.logError(exc, module);
            }
            finally {
                new File(importDataPath, ebayXlsFileName).delete();
                if (workbook != null) 
                {
                    try {
                        workbook.close();
                    } catch (Exception exc) {
                        //Debug.warning();
                    }
                }
            }
        }
        Map<String, Object> resp = UtilMisc.toMap("messages", (Object) messages);
        return resp;
    }
        
    
    public static Map<String, Object> importRemoveEntityData(DispatchContext ctx, Map<String, ?> context)
    {

        Delegator delegator = ctx.getDelegator();
        LocalDispatcher dispatcher = ctx.getDispatcher();
        List<String> messages = FastList.newInstance();

        SQLProcessor sqlP = null;
        String[] removeEntities = Constants.IMPORT_REMOVE_ENTITIES;
        // #############################################################
        // Removing entity names which are store in Constants java file.
        // #############################################################

        if (removeEntities != null) 
        {
            for (String entity: removeEntities)
            {
                String sql = null;
                try
                {
                    GenericHelperInfo helperInfo = delegator.getGroupHelperInfo(delegator.getEntityGroupName(entity));
                    sqlP = new SQLProcessor(helperInfo);
                    DatasourceInfo datasourceInfo = EntityConfigUtil.getDatasourceInfo(helperInfo.getHelperBaseName());

                    int deleteRowCount =0; 
                    String tableName = delegator.getModelEntity(entity).getTableName(datasourceInfo);
                    if (entity.equalsIgnoreCase("ProductCategory"))
                    {
                        String nowDateTime = _sdf.format(UtilDateTime.nowTimestamp());
                        sql = "UPDATE " + tableName;
                        sql += " SET PRIMARY_PARENT_CATEGORY_ID = NULL";
                        sql += ", LAST_UPDATED_STAMP = '"+nowDateTime+"'";
                        sql += " WHERE PRIMARY_PARENT_CATEGORY_ID IS NOT NULL ";
                        sqlP.prepareStatement(sql);
                        sqlP.executeUpdate();

                        sql = "DELETE FROM " + tableName;
                        sql += " WHERE LAST_UPDATED_STAMP = '"+nowDateTime+"'";
                        sqlP.prepareStatement(sql);
                        deleteRowCount = sqlP.executeUpdate();
                    }
                    else if (entity.equalsIgnoreCase("Product")) 
                    {   
                    	
                        String nowDateTime = _sdf.format(UtilDateTime.nowTimestamp());
                        sql = "UPDATE " + tableName;
                        sql += " SET PRIMARY_PRODUCT_CATEGORY_ID = NULL";
                        sqlP.prepareStatement(sql);
                        sqlP.executeUpdate();

                        sql = "DELETE FROM " + tableName;
                        sqlP.prepareStatement(sql);
                        deleteRowCount = sqlP.executeUpdate();
                    	 
                    }
                    else if (entity.equalsIgnoreCase("InvoiceItem")) 
                    {   
                    	
                        String nowDateTime = _sdf.format(UtilDateTime.nowTimestamp());
                        sql = "UPDATE " + tableName;
                        sql += " SET PARENT_INVOICE_ID = NULL";
                        sql += " , PARENT_INVOICE_ITEM_SEQ_ID=NULL";
                        sqlP.prepareStatement(sql);
                        sqlP.executeUpdate();

                        sql = "DELETE FROM " + tableName;
                        sqlP.prepareStatement(sql);
                        deleteRowCount = sqlP.executeUpdate();
                    	 
                    }
                    else
                    {
                        sql = "DELETE FROM " + tableName;
                        sqlP.prepareStatement(sql);
                        deleteRowCount = sqlP.executeUpdate();
                    }

                }
                catch (GenericEntityException e)
                {
                    Debug.logInfo("An error occurred executing query"+e, module);
                    return ServiceUtil.returnError("An error "+e.getMessage()+" occurred while executing query "+sql);
                }
                catch (Exception e)
                {
                    Debug.logInfo("An error occurred executing query"+e, module);
                    return ServiceUtil.returnError("An error "+e.getMessage()+" occurred while executing query "+sql);
                }
                finally
                {
                    try
                    {
                        sqlP.close();
                    }
                    catch (GenericDataSourceException e)
                    {
                        Debug.logInfo("An error occurred in closing SQLProcessor"+e, module);
                    }
                    catch (Exception e)
                    {
                    	Debug.logInfo("An error occurred in closing SQLProcessor"+e, module);
                    } 
                }
            }
        }
        else 
        {
            messages.add("No value for remove entities, doing nothing.");
        }
        // send the notification
        Map<String, Object> resp = UtilMisc.toMap("messages", (Object) messages);
        return resp;
    }
    
    private static String makeOfbizId(String idValue)
    {
    	String id=idValue;
    	try {
        	id=StringUtil.removeSpaces(idValue);
        	id=StringUtil.replaceString(id, "_", "");
        	if (id.length() > 20)
        	{
        		id=id.substring(0,20);
        	}
    		
    	}
    	catch(Exception e)
    	{
    		Debug.logError(e,module);
    	}
    	return id;
    }
    
    private static String getOsafeImagePath(String imageType) {
    	
    	if(UtilValidate.isEmpty(imageType))
    	{
    		return "";
    	}
    	String XmlFilePath = FlexibleStringExpander.expandString(UtilProperties.getPropertyValue("osafeAdmin.properties", "image-location-preference-file"), context);
    	
    	Map<Object, Object> imageLocationMap = OsafeManageXml.findByKeyFromXmlFile(XmlFilePath, "key", imageType);
    	if(UtilValidate.isNotEmpty(imageLocationMap.get("value")))
    	{
    	    return (String)imageLocationMap.get("value");
    	} 
    	else 
    	{
    		return "";
        }
    }
    

    public static Map<String, Object> exportOrderXML(DispatchContext ctx, Map<String, ?> context) 
    {
    	_delegator = ctx.getDelegator();
        LocalDispatcher dispatcher = ctx.getDispatcher();
        List<String> messages = FastList.newInstance();
        List<String> orderIdList = (List)context.get("orderList");
        String productStoreId = (String)context.get("productStoreId");
        _locale = (Locale) context.get("locale");
        
        ObjectFactory factory = new ObjectFactory();
        BigFishOrderFeedType bfOrderFeedType = factory.createBigFishOrderFeedType();
        
        String downloadTempDir = FeedsUtil.getFeedDirectory("order");
        String orderFileName = "Order";
        if(orderIdList.size() == 1) 
        {
        	orderFileName = orderFileName + orderIdList.get(0);
        }
        orderFileName = orderFileName + "_" + (OsafeAdminUtil.convertDateTimeFormat(UtilDateTime.nowTimestamp(), "yyyy-MM-dd-HHmm"));
        
        orderFileName = UtilValidate.stripWhitespace(orderFileName) + ".xml";
        
        if (!new File(downloadTempDir).exists()) 
        {
        	new File(downloadTempDir).mkdirs();
	    }
        
        File file = new File(downloadTempDir + orderFileName);
  	  
        Map result = ServiceUtil.returnSuccess();
        
        List<String> exportedOrderIdList = FastList.newInstance();
        
        List<String> exportMessageList = FastList.newInstance();
        
        List orderList = bfOrderFeedType.getOrder();
        
        OrderType order = null;
        int i = 0;
  	    for(String orderId : orderIdList) 
  	    {
  	    	boolean bWriteRecord = true;
  	    	exportMessageList.add("IN PROGRESS: [Processing row #"+(i+1)+" Order ID: "+orderId+"]");
  	    	String errorLogText = UtilProperties.getMessage(resource, "ErrorLogTextLabel", UtilMisc.toMap("rowNo", Integer.toString(i+1)), _locale);
  	    	try 
  	    	{
	  	    	order = factory.createOrderType();
	  	    	
	  	    	OrderReadHelper orderReadHelper = null;
	  	    	String orderStatusId = "";
	  	    	GenericValue orderHeader = _delegator.findByPrimaryKey("OrderHeader", UtilMisc.toMap("orderId", orderId));
	  	    	if(UtilValidate.isNotEmpty(orderHeader)) 
	  	    	{
	  	    	    orderStatusId = (String)orderHeader.get("statusId");
	  	    	}
	  	    	
	  	    	if(UtilValidate.isNotEmpty(orderHeader)) 
	  	    	{
	  	    	    orderReadHelper = new OrderReadHelper(orderHeader);
	  	    	    
	  	    	    // Set Order Customer Detail
			        CustomerType customer = setOrderCustomerFeed(factory, orderHeader, orderReadHelper, exportMessageList, errorLogText);
			        if(UtilValidate.isNotEmpty(customer))
			        {
			        	order.setCustomer(customer);
			        }
			        else
			        {
			        	bWriteRecord = false;
			        }
			        
			        // Set Order Header Detail
			        if(bWriteRecord)
			        {
			        	OrderHeaderType oh = setOrderHeaderFeed(factory, orderHeader, orderReadHelper, exportMessageList, errorLogText);
				        if(UtilValidate.isNotEmpty(oh))
				        {
				        	order.setOrderHeader(oh);
				        }
				        else
				        {
				        	bWriteRecord = false;
				        }
			        }
			        
		  	    	//Set Order Shipment
			        if(bWriteRecord)
			        {
			        	OrderShipmentType orderShipmentType = setOrderShipmentFeed(factory, orderHeader, orderReadHelper, exportMessageList, errorLogText);
				        if(UtilValidate.isNotEmpty(orderShipmentType))
				        {
				        	order.setOrderShipment(orderShipmentType);
				        }
				        else
				        {
				        	bWriteRecord = false;
				        }
			        }
			        
			        //Set Order Line Items
			        if(bWriteRecord)
			        {
			        	OrderLineItemsType orderLineItems = setOrderLineItemsFeed(factory, orderHeader, orderReadHelper, exportMessageList, errorLogText);
				        if(UtilValidate.isNotEmpty(orderLineItems))
				        {
				        	order.setOrderLineItems(orderLineItems);
				        }
				        else
				        {
				        	bWriteRecord = false;
				        }
			        }
			        
		    	    // Set Order Payment Detail
			        if(bWriteRecord)
			        {
			        	OrderPaymentType orderPaymentType = setOrderPaymentFeed(factory, orderHeader, orderReadHelper, exportMessageList, errorLogText, productStoreId);
				        if(UtilValidate.isNotEmpty(orderPaymentType))
				        {
				        	order.setOrderPayment(orderPaymentType);
				        }
				        else
				        {
				        	bWriteRecord = false;
				        }
			        }
			        
			        //Set Order Attribute Detail
			        if(bWriteRecord)
			        {
			        	OrderAttributeType orderAttributeType = setOrderAttributeFeed(factory, orderHeader, orderReadHelper, exportMessageList, errorLogText);
				        if(UtilValidate.isNotEmpty(orderAttributeType))
				        {
				        	order.setOrderAttribute(orderAttributeType);
				        }
				        else
				        {
				        	bWriteRecord = false;
				        }
			        }
			        
		  	    	//Set Order Adjustment Detail
			        if(bWriteRecord)
			        {
			        	OrderAdjustmentType orderAdjustmentType = setOrderAdjustmentFeed(factory, orderHeader, orderReadHelper, exportMessageList, errorLogText);
				        if(UtilValidate.isNotEmpty(orderAdjustmentType))
				        {
				        	order.setOrderAdjustment(orderAdjustmentType);
				        }
				        else
				        {
				        	bWriteRecord = false;
				        }
			        }
	  	    	}
	  	    	if(bWriteRecord)
	  	    	{
	  	    		orderList.add(order);
		  	    	exportedOrderIdList.add(orderId);
	  	    	}
	  	    	else
	  	    	{
	  	    		exportMessageList.add("Error in Order Export for Order ID ["+ orderId +"]");
	  	    		Debug.logInfo("Error in Order Export for Order ID ["+ orderId +"]", module);
	  	    	}
  	    	} 
  	    	catch (Exception e) 
  	    	{
  	    		exportMessageList.add("Error in Order Export "+e);
  	    		Debug.logInfo("Error in Order Export "+e, module);
  	    	}
  	    	exportMessageList.add("ROW COMPLETE: [Processed row #"+(i+1)+" Order ID: "+orderId+"]");
  	    	i++;
  	    }
  	    bfOrderFeedType.setCount(String.valueOf(exportedOrderIdList.size()));
  	    
        FeedsUtil.marshalObject(new JAXBElement<BigFishOrderFeedType>(new QName("", "BigFishOrderFeed"), BigFishOrderFeedType.class, null, bfOrderFeedType), file);
  	    result.put("feedsDirectoryPath", downloadTempDir);
        result.put("feedsFileName", orderFileName);
        result.put("feedsExportedIdList", exportedOrderIdList);
        result.put("exportMessageList", exportMessageList);
        return result;
    }
    
    
    public static OrderHeaderType setOrderHeaderFeed(ObjectFactory factory, GenericValue orderHeader, OrderReadHelper orderReadHelper, List exportMessageList, String errorLogText)
    {
    	String orderId = "";
    	try
    	{
    		List<GenericValue> orderItems = orderReadHelper.getOrderItems();
        	List<GenericValue> orderAdjustments = orderReadHelper.getAdjustments();
        	orderId = orderHeader.getString("orderId");
        	OrderHeaderType oh = factory.createOrderHeaderType();
        	
        	String orderProductStoreId = "";
    	    String orderStatusId = "";
    	    if(UtilValidate.isNotEmpty(orderHeader)) 
    	    {
    	    	oh.setProductStoreId(getString(orderHeader.getString("productStoreId")));
    	    	oh.setStatusId(getString(orderHeader.getString("statusId")));
    	    	oh.setOrderId(orderHeader.getString("orderId"));
    	    	oh.setOrderDate(formatDate(orderHeader.getTimestamp("orderDate")));
    	    	oh.setEntryDate(formatDate(orderHeader.getTimestamp("entryDate")));
    	    	oh.setCreatedBy(getString(orderHeader.getString("createdBy")));
    	    }
    	    BigDecimal adjustmentTotal = BigDecimal.ZERO;
            for(GenericValue orderAdjustment : orderAdjustments)
            {
            	if(UtilValidate.isNotEmpty(orderAdjustment.getBigDecimal("amount")))
            	{
            	    adjustmentTotal = adjustmentTotal.add(orderAdjustment.getBigDecimal("amount")).setScale(scale, rounding);
            	}
            }
            BigDecimal shippingTotal = orderReadHelper.getShippingTotal();
            BigDecimal taxAmount = orderReadHelper.getTaxTotal();
            
            BigDecimal grandTotal = orderReadHelper.getOrderGrandTotal();
            
            BigDecimal orderItemSubTotal = orderReadHelper.getOrderItemsSubTotal();
            
            adjustmentTotal = adjustmentTotal.subtract(shippingTotal.add(taxAmount));
        	
            oh.setOrderSubTotal(orderItemSubTotal.toString());
        	oh.setOrderTotalItem(Integer.toString(orderItems.size()));
        	oh.setCurrency(orderReadHelper.getCurrency());
        	oh.setOrderShippingCharge(shippingTotal.toString());
        	oh.setOrderTax(taxAmount.toString());
        	oh.setOrderTotalAmount(grandTotal.toString());
        	oh.setOrderTotalAdjustment(adjustmentTotal.toString());
        	return oh;
    	}
    	catch(Exception e)
    	{
    		Debug.logInfo("Error in export Order Header for Order ID ["+ orderId +"]"+e, module);
    		exportMessageList.add(errorLogText+e);
    		return null;
    	}
    }
    
    public static OrderShipmentType setOrderShipmentFeed(ObjectFactory factory, GenericValue orderHeader, OrderReadHelper orderReadHelper, List exportMessageList, String errorLogText)
    {
    	String orderId = "";
    	try
    	{
    		OrderShipmentType orderShipmentType = factory.createOrderShipmentType();
        	orderId = orderHeader.getString("orderId");
    	    List shipmentList = orderShipmentType.getShipment();
    	    String productStoreId = orderReadHelper.getProductStoreId();
	    	List<GenericValue> orderItemShipGroups = orderReadHelper.getOrderItemShipGroups();
	    	if(UtilValidate.isNotEmpty(orderItemShipGroups))
	    	{
	    		for(GenericValue orderItemShipGroup : orderItemShipGroups)
		    	{
		    		ShipmentType shipment = factory.createShipmentType();
		    		String shipGroupSeqId = orderItemShipGroup.getString("shipGroupSeqId");
		    		shipment.setShipGroupSequenceId(getString(orderItemShipGroup.getString("shipGroupSeqId")));
		    		shipment.setShippingMethod(getString(orderItemShipGroup.getString("shipmentMethodTypeId")));
		    		shipment.setCarrier(getString(orderItemShipGroup.getString("carrierPartyId")));
		    		shipment.setShippingInstructions(getString(orderItemShipGroup.getString("shippingInstructions")));	
		    		shipment.setTrackingNumber(getString(orderItemShipGroup.getString("trackingNumber")));	
		    		
		    		ShippingAddressType shippingAddress = factory.createShippingAddressType();
		    	    GenericValue postalAddress = orderItemShipGroup.getRelatedOne("PostalAddress");
		    	    if(UtilValidate.isNotEmpty(postalAddress))
		    	    {
		    	    	shippingAddress.setToName(getString(postalAddress.getString("toName")));
			    	    shippingAddress.setAddress1(getString(postalAddress.getString("address1")));
			    	    shippingAddress.setAddress2(getString(postalAddress.getString("address2")));
			    	    shippingAddress.setAddress3(getString(postalAddress.getString("address3")));
			    	    if(UtilValidate.isNotEmpty(postalAddress.getString("countryGeoId")))
			    	    {
				    	    if (displayCountryFieldAsLong(productStoreId))
				    	    {
				    	    	shippingAddress.setCountry(getGeoName(postalAddress.getString("countryGeoId")));
				    	    }
				    	    else
				    	    {
				    	    	shippingAddress.setCountry(postalAddress.getString("countryGeoId"));
				    	    }
			    	    }
			    	    else
			    	    {
			    	    	shippingAddress.setCountry("");
			    	    }
			    	    shippingAddress.setCityTown(getString(postalAddress.getString("city")));
			    	    if(UtilValidate.isNotEmpty(postalAddress.getString("stateProvinceGeoId")))
			    	    {
				    	    if (displayStateFieldAsLong(productStoreId))
				    	    {
				    	    	shippingAddress.setStateProvince(getGeoName(postalAddress.getString("stateProvinceGeoId")));
				    	    }
				    	    else
				    	    {
				    	    	shippingAddress.setStateProvince(postalAddress.getString("stateProvinceGeoId"));
				    	    }
			    	    }
			    	    else
			    	    {
			    	    	shippingAddress.setStateProvince("");
			    	    }
			    	    if(UtilValidate.isNotEmpty(postalAddress.getString("postalCode")))
			    	    {
				    	    if (displayZipFieldAsLong(productStoreId))
				    	    {
				    	        String postalCode = postalAddress.getString("postalCode");
				    	        if (UtilValidate.isNotEmpty(postalAddress.getString("postalCodeExt")))
				    	        {
				    	            postalCode = postalCode+"-"+postalAddress.getString("postalCodeExt");
				    	        }
				    	        shippingAddress.setZipPostCode(postalCode);
				    	    }
				    	    else
				    	    {
				    	    	shippingAddress.setZipPostCode(postalAddress.getString("postalCode"));
				    	    }
			    	    }
			    	    else
			    	    {
			    	    	shippingAddress.setZipPostCode("");
			    	    }
			    	    shipment.setShippingAddress(shippingAddress);
		    	    }
		    		
		    	    // Set Order Line Item detail 
		  	    	List<GenericValue> orderItemShipGroupAssocList = orderItemShipGroup.getRelated("OrderItemShipGroupAssoc");
		  	    	
		  	    	List shipGrouporderLineItemList = shipment.getShipGroupLineItem();
		  	    	if(UtilValidate.isNotEmpty(orderItemShipGroupAssocList))
		  	    	{
		  	    		for(GenericValue orderItemShipGroupAssocGV : orderItemShipGroupAssocList) 
			  	    	{
		  	    			ShipGroupLineItemType shipGroupLineItem = factory.createShipGroupLineItemType();
		  	    			GenericValue orderItem = orderItemShipGroupAssocGV.getRelatedOne("OrderItem");
		  	    			shipGroupLineItem.setProductId(getString(orderItem.getString("productId")));
		  	    			shipGroupLineItem.setSequenceId(getString(orderItemShipGroupAssocGV.getString("orderItemSeqId")));
		  	    			if(UtilValidate.isNotEmpty(orderItemShipGroupAssocGV.get("quantity")))
		  	    			{
		  	    				shipGroupLineItem.setQuantity(UtilMisc.toInteger(orderItemShipGroupAssocGV.get("quantity")));
		  	    			}
		  	    			else
		  	    			{
		  	    				shipGroupLineItem.setQuantity(0);
		  	    			}
		  	    			
		  	    		    // Set Order Line Item Attributes
			  	  	        OrderLineAttributeType orderLineAttributeType = factory.createOrderLineAttributeType();
			  	  	        List<GenericValue> orderItemAttributes = orderItem.getRelated("OrderItemAttribute");
			  	  	        
			  	  	        List itemAttributeList = orderLineAttributeType.getAttribute();	
			  	  	        if(UtilValidate.isNotEmpty(orderItemAttributes))
			  	  	        {
				  	  	        for(GenericValue orderItemAttribute : orderItemAttributes)
			  	  	        	{
			  	  	        	    AttributeType attribute = factory.createAttributeType();
			  	  	        	    String attrName = orderItemAttribute.getString("attrName");
			  	  	        	    if(attrName.startsWith("GIFT_MSG_FROM_") || attrName.startsWith("GIFT_MSG_TO_") || attrName.startsWith("GIFT_MSG_TEXT_"))
			  	  	        	    {
			  	  	        	        int iShipId = attrName.lastIndexOf('_');
			  	  	        	        if(iShipId > -1 && attrName.substring(iShipId+1).equals(shipGroupSeqId))
			  	  	        	        {
				  	  	        	        attribute.setName(orderItemAttribute.getString("attrName"));
					  	    		        attribute.setValue(getString(orderItemAttribute.getString("attrValue")));
					  	    		        itemAttributeList.add(attribute);
			  	  	        	        }
			  	  	        	    }
			  	  	        	}
			  	  	        }
			  	  	        shipGroupLineItem.setOrderLineAttribute(orderLineAttributeType);
		  	    			
		  	    			shipGrouporderLineItemList.add(shipGroupLineItem);
			  	    	}
		  	    	}
		  	        shipmentList.add(shipment);
		    	}
	    	}
	    	return orderShipmentType;
    	}
    	catch(Exception e)
    	{
    		Debug.logInfo("Error in export Order Shipment for Order ID ["+ orderId +"]"+e, module);
    		exportMessageList.add(errorLogText+e);
    		return null;
    	}
    }
    
    public static OrderLineItemsType setOrderLineItemsFeed(ObjectFactory factory, GenericValue orderHeader, OrderReadHelper orderReadHelper, List exportMessageList, String errorLogText)
    {
    	String orderId = "";
    	try
    	{
    		OrderLineItemsType orderLineItems = factory.createOrderLineItemsType();
        	orderId = orderHeader.getString("orderId");
  	    	List<GenericValue> orderItems = orderReadHelper.getOrderItems();
  	    	List orderLineItemsList = orderLineItems.getOrderLine();
  	    	if(UtilValidate.isNotEmpty(orderItems))
  	    	{
  	    		for(GenericValue orderItem : orderItems) 
	  	    	{
	  	    		//GenericValue orderItem = _delegator.findByPrimaryKey("OrderItem", UtilMisc.toMap("orderId", orderId, "orderItemSeqId", orderItemShipGroupAssocGV.getString("orderItemSeqId")));
	  	    		BigDecimal lineTotalGross = BigDecimal.ZERO;
	  	    		BigDecimal orderItemAdjustmentTotal = orderReadHelper.getOrderItemAdjustmentsTotal(orderItem);
	  	    		BigDecimal offerPrice = null;
	  	    		if(orderItemAdjustmentTotal.compareTo(BigDecimal.ZERO) == -1) 
	  	    		{
	  	    			try 
	  	    			{
	  	    			    offerPrice = ((BigDecimal) orderItem.get("unitPrice")).add((orderItemAdjustmentTotal.divide((BigDecimal) orderItem.get("quantity")))).setScale(scale, rounding);
	  	    			} 
	  	    			catch (ArithmeticException ae) 
	  	    			{
							Debug.logInfo("Error in Calculating Offer Price"+ae, module);
						}
	  	    		}
	  	    		
  	  	       
  	  	            lineTotalGross = orderItem.getBigDecimal("unitPrice").multiply(orderItem.getBigDecimal("quantity")).setScale(scale,rounding);
	  	    		OrderLineType orderLine = factory.createOrderLineType();
	  	    		
	  	    		orderLine.setStatusId(getString(orderItem.getString("statusId")));	
	  	    		orderLine.setProductId(getString(orderItem.getString("productId")));	
	  	    		orderLine.setSequenceId(getString(orderItem.getString("orderItemSeqId")));	
	  	    		if(UtilValidate.isNotEmpty(orderItem.get("quantity")))
	  	    		{
	  	    			orderLine.setQuantity(UtilMisc.toInteger(orderItem.get("quantity")));	
	  	    		}
	  	    		else
	  	    		{
	  	    			orderLine.setQuantity(0);
	  	    		}
	  	    		orderLine.setPrice(getString(orderItem.get("unitPrice")));	
	  	    		orderLine.setListPrice(getString(orderItem.get("unitListPrice")));	
	  	    		orderLine.setIsPromo(getString(orderItem.getString("isPromo")));	
	  	    		orderLine.setIsModifiedPrice(getString(orderItem.get("isModifiedPrice")));	
	  	    		orderLine.setOfferPrice(formatBigDecimal(offerPrice));
	  	    		orderLine.setLineTotalAmount(lineTotalGross.toString());
	  	    		
	  	    		//Set Order Line Tax
	  	    		List orderLineSalexTaxList = orderLine.getOrderLineSalesTax();
	  	    		List<GenericValue> orderItemAdjustments = orderReadHelper.getOrderItemAdjustments(orderItem);
	  	    		List<GenericValue> orderItemSalesTaxAdjustments = FastList.newInstance();
	  	    		if(UtilValidate.isNotEmpty(orderItemAdjustments))
	  	    		{
	  	    			orderItemSalesTaxAdjustments = EntityUtil.filterByAnd(orderItemAdjustments, UtilMisc.toMap("orderAdjustmentTypeId", "SALES_TAX"));
	  	    		}
	  	    		if(UtilValidate.isNotEmpty(orderItemSalesTaxAdjustments))
	  	    		{
	  	    			for (GenericValue orderItemSalesTaxAdjustment : orderItemSalesTaxAdjustments) 
	  	  	    	    {
	  	    				OrderLineSalesTaxType orderLineSalesTax = factory.createOrderLineSalesTaxType();
	  	    				orderLineSalesTax.setShipGroupSequenceId(getString(orderItemSalesTaxAdjustment.getString("shipGroupSeqId")));
	  	    				if(UtilValidate.isNotEmpty(orderItemSalesTaxAdjustment.get("sourcePercentage")))
	  	    				{
	  	    					orderLineSalesTax.setTaxPercent((orderItemSalesTaxAdjustment.getBigDecimal("sourcePercentage").setScale(3, rounding)).toString());
	  	    				}
	  	    				else
	  	    				{
	  	    					orderLineSalesTax.setTaxPercent("");
	  	    				}
	  	    				orderLineSalesTax.setTaxAuthorityGeo(getString(orderItemSalesTaxAdjustment.getString("taxAuthGeoId")));
	  	    				orderLineSalesTax.setTaxAuthorityParty(getString(orderItemSalesTaxAdjustment.getString("taxAuthPartyId")));
	  	    				if(UtilValidate.isNotEmpty(orderItemSalesTaxAdjustment.get("amount")))
	  	    				{
	  	    					orderLineSalesTax.setSalesTax((orderItemSalesTaxAdjustment.getBigDecimal("amount").setScale(3, rounding)).toString());
	  	    				}
	  	    				else
	  	    				{
	  	    					orderLineSalesTax.setSalesTax("");
	  	    				}
	  	    				orderLineSalexTaxList.add(orderLineSalesTax);
	  	  	    	    }
	  	    		}
	  	    		else
	  	  	    	{
	  	    			OrderLineSalesTaxType orderLineSalesTax = factory.createOrderLineSalesTaxType();
	  	    			orderLineSalexTaxList.add(orderLineSalesTax);
	  	  	    	}
	  	    		
	  	    	    //Set Order Line Shipping Charge
	  	    		List orderLineShippingList = orderLine.getOrderLineShippingCharge();
	  	    		List<GenericValue> orderItemShippingAdjustments = FastList.newInstance();
	  	    		if(UtilValidate.isNotEmpty(orderItemAdjustments))
	  	    		{
	  	    			orderItemShippingAdjustments = EntityUtil.filterByAnd(orderItemAdjustments, UtilMisc.toMap("orderAdjustmentTypeId", "SHIPPING_CHARGES"));
	  	    		}
	  	    		if(UtilValidate.isNotEmpty(orderItemShippingAdjustments))
	  	    		{
	  	    			for (GenericValue orderItemShippingAdjustment : orderItemShippingAdjustments) 
	  	  	    	    {
	  	    				OrderLineShippingChargeType orderLineShippingCharge = factory.createOrderLineShippingChargeType();
	  	    				
	  	    				orderLineShippingCharge.setShipGroupSequenceId(getString(orderItemShippingAdjustment.getString("shipGroupSeqId")));
	  	    				
	  	    				if(UtilValidate.isNotEmpty(orderItemShippingAdjustment.get("amount")))
	  	    				{
	  	    					orderLineShippingCharge.setShippingCharge((orderItemShippingAdjustment.getBigDecimal("amount").setScale(3, rounding)).toString());
	  	    				}
	  	    				else
	  	    				{
	  	    					orderLineShippingCharge.setShippingCharge("");
	  	    				}
	  	    				orderLineShippingList.add(orderLineShippingCharge);
	  	  	    	    }
	  	    		}
	  	    		else
	  	  	    	{
	  	    			OrderLineShippingChargeType orderLineShippingCharge = factory.createOrderLineShippingChargeType();
	  	  	    	    orderLineShippingList.add(orderLineShippingCharge);
	  	  	    	}
	  	    	    // Set Order Line Promotion Detail 
	  	    		List<GenericValue> orderItemPromotionAdjustments = FastList.newInstance();
	  	    		if(UtilValidate.isNotEmpty(orderItemAdjustments))
	  	    		{
	  	    			orderItemPromotionAdjustments = EntityUtil.filterByAnd(orderItemAdjustments, UtilMisc.toMap("orderAdjustmentTypeId", "PROMOTION_ADJUSTMENT"));
	  	    		}
	  	  	    	List orderLinePromotionList = orderLine.getOrderLinePromotion();
	  	  	    	if(UtilValidate.isNotEmpty(orderItemPromotionAdjustments)) 
	  	  	    	{
	  	  	    	    for (GenericValue orderItemPromoAdjustment : orderItemPromotionAdjustments) 
	  	  	    	    {
	  	  	    		    OrderLinePromotionType orderLinePromotion = factory.createOrderLinePromotionType();
	  	  	    		    GenericValue adjustmentType = orderItemPromoAdjustment.getRelatedOne("OrderAdjustmentType");
	  	  	    		    GenericValue productPromo = orderItemPromoAdjustment.getRelatedOne("ProductPromo");
	  	  	    		    String promoCodeText = "";
	  	  	    		    if(UtilValidate.isNotEmpty(productPromo)) 
	  	  	    		    {
	  	  	    			    List<GenericValue> productPromoCode = productPromo.getRelated("ProductPromoCode");
	  	  	    			    Set<String> promoCodesEntered = orderReadHelper.getProductPromoCodesEntered();
	  	  	    			    if(UtilValidate.isNotEmpty(promoCodesEntered)) 
	  	  	    			    {
	  	  	    				    for(String promoCodeEntered : promoCodesEntered) 
	  	  	    				    {
	  	  	    					    if(UtilValidate.isNotEmpty(productPromoCode)) 
	  	  	    					    {
	  	  	    						    for(GenericValue promoCode : productPromoCode) 
	  	  	    						    {
	  	  	    							    String promoCodeEnteredId = promoCodeEntered;
	  	  	    							    String promoCodeId = (String) promoCode.get("productPromoCodeId");
	  	  	    							    if(UtilValidate.isNotEmpty(promoCodeEnteredId)) 
	  	  	    							    {
	  	  	    								    if(promoCodeId.equals(promoCodeEnteredId)) 
	  	  	    								    {
	  	  	    									    promoCodeText = (String)promoCode.get("productPromoCodeId");
	  	  	    								    }
	  	  	    							    }
	  	  	    						    }
	  	  	    					    }
	  	  	    				    }
	  	  	    			    }
	  	  	    			    else
	  	  	    			    {
	  	  	    			        promoCodeText = (String)productPromo.get("promoName");
	  	  	    			    }
	  	  	    		    }
		  	  	    		orderLinePromotion.setShipGroupSequenceId(getString(orderItemPromoAdjustment.getString("shipGroupSeqId")));
	  	  	    	        orderLinePromotion.setPromotionCode(promoCodeText);
	  	  	                BigDecimal promotionAmount = orderReadHelper.calcItemAdjustment(orderItemPromoAdjustment, orderItem);
	  	  	    	        orderLinePromotion.setPromotionAmount(promotionAmount.toString());
	  	  	                orderLinePromotionList.add(orderLinePromotion);
	  	  	            }
	  	  	    	}
	  	  	    	else
	  	  	    	{
	  	  	    	    OrderLinePromotionType orderLinePromotion = factory.createOrderLinePromotionType();
	  	  	    		orderLinePromotionList.add(orderLinePromotion);
	  	  	    	}
	  	  	    	
	  	  	    	// Set Order Line Item Attributes
	  	  	        OrderLineAttributeType orderLineAttributeType = factory.createOrderLineAttributeType();
	  	  	        List<GenericValue> orderItemAttributes = orderItem.getRelated("OrderItemAttribute");
	  	  	        
	  	  	        List itemAttributeList = orderLineAttributeType.getAttribute();	
	  	  	        if(UtilValidate.isNotEmpty(orderItemAttributes))
	  	  	        {
		  	  	        for(GenericValue orderItemAttribute : orderItemAttributes)
	  	  	        	{
	  	  	        	    AttributeType attribute = factory.createAttributeType();
	  	    		        attribute.setName(orderItemAttribute.getString("attrName"));
	  	    		        attribute.setValue(getString(orderItemAttribute.getString("attrValue")));
	  	    		        itemAttributeList.add(attribute);	
	  	  	        	}
	  	  	        }
	  	  	        orderLine.setOrderLineAttribute(orderLineAttributeType);
	  	  	        orderLineItemsList.add(orderLine);
	  	    	}
  	    	}
    	    return orderLineItems;
    	}
    	catch(Exception e)
    	{
    		Debug.logInfo("Error in export Order Line Items for Order ID ["+ orderId +"]"+e, module);
    		exportMessageList.add(errorLogText+e);
    		return null;
    	}
    }
    
    public static OrderPaymentType setOrderPaymentFeed(ObjectFactory factory, GenericValue orderHeader, OrderReadHelper orderReadHelper, List exportMessageList, String errorLogText, String productStoreId)
    {
    	String orderId = "";
	    try
	    {
	    	OrderPaymentType orderPaymentType = factory.createOrderPaymentType();
		    List paymentList = orderPaymentType.getPayment();
		    orderId = orderHeader.getString("orderId");	
		    List<GenericValue> orderPayments = orderReadHelper.getPaymentPreferences();
		    
		    String feedsMaskAccountInfo = OsafeAdminUtil.getProductStoreParm(_delegator, productStoreId, "FEEDS_MASK_ACCOUNT_INFO");
		    
		    //According to System Param Desc:-
		    //Mask (e.g. *1234) to Account information (Credit Card, Bank Account, Routing Numbers) on all outgoing FEEDS. TRUE or FALSE. Default is TRUE.
		    if(UtilValidate.isEmpty(feedsMaskAccountInfo))
		    {
		    	feedsMaskAccountInfo = "TRUE";
		    }
		    
	    	if(UtilValidate.isNotEmpty(orderPayments)) 
	    	{
	    		for(GenericValue orderPaymentPreference : orderPayments) 
	    		{
	    			GenericValue paymentMethod = null;
	    	    	GenericValue creditCard = null;
	    	    	String transactionId = "";
	    	    	String paymentId = "";
	    	    	String merchantRefNo = "";
	    	    	String paymentMethodTypeId = "";
	    	    	String paymentMethodId = "";
	    	    	String statusId = "";
	    	    	BigDecimal amount = BigDecimal.ZERO;
	    	    	String giftCardNumber = "";
	    	    	String cardExpireDate = "";
	    	    	
	    	    	List<GenericValue> gatewayResponses = null;
	    			
	    			PaymentType orderPayment = factory.createPaymentType();
	    			paymentMethod = orderPaymentPreference.getRelatedOne("PaymentMethod");
	    			gatewayResponses = orderPaymentPreference.getRelated("PaymentGatewayResponse");
	    			
	    			paymentMethodTypeId = orderPaymentPreference.getString("paymentMethodTypeId");
	    			statusId = orderPaymentPreference.getString("statusId");
	    			amount = orderPaymentPreference.getBigDecimal("maxAmount");
	    			paymentMethodId = orderPaymentPreference.getString("paymentMethodId");
	    			orderPayment.setPaymentMethod(paymentMethodTypeId);
	    			orderPayment.setAmount(amount.toString());
	    			orderPayment.setStatusId(statusId);
	    			
	    			orderPayment.setPaymentMethodId(getString(paymentMethodId));
    			
	    			if(UtilValidate.isNotEmpty(paymentMethod))
	    			{
	    				
	    				if((paymentMethod.getString("paymentMethodTypeId").equals("CREDIT_CARD"))) 
	    				{
	  	    				creditCard = paymentMethod.getRelatedOne("CreditCard");
	  	    				if(UtilValidate.isNotEmpty(creditCard))
	  	    				{
	  	    					orderPayment.setCardType(getString(creditCard.getString("cardType")));
	  	    					if(UtilValidate.isNotEmpty(creditCard.getString("cardNumber")))
		  	    				{
	  	    						String cardNumber = creditCard.getString("cardNumber");
	  	    						if(OsafeAdminUtil.isProductStoreParmTrue(feedsMaskAccountInfo))
	  	    					    {
	  	    							cardNumber = cardNumber.substring(cardNumber.length() - 4);
	  	    							cardNumber = "*"+cardNumber;
	  	    					    }
	  	    						
	  	    						orderPayment.setCardNumber(cardNumber);
		  	    				}
	  	    					else
	  	    					{
	  	    						orderPayment.setCardNumber("");
	  	    					}
	  	    					orderPayment.setExpiryDate(getString(creditCard.getString("expireDate")));
	  	    				}
	  	    			} 
	    				
	  	    			if(paymentMethod.getString("paymentMethodTypeId").equals("SAGEPAY_TOKEN")) 
	  	    			{
	  	    				GenericValue sagePayTokenGV = _delegator.findOne("SagePayTokenPaymentMethod", UtilMisc.toMap("paymentMethodId", (String)paymentMethod.get("paymentMethodId")), false);
	  	    				if(UtilValidate.isNotEmpty(sagePayTokenGV)) 
	  	    				{
	  	    					orderPayment.setSagePayPaymentToken(getString(sagePayTokenGV.getString("sagePayToken")));
	  	    				    creditCard = _delegator.findOne("CreditCard", UtilMisc.toMap("paymentMethodId", (String)paymentMethod.get("paymentMethodId")), false);
	  	    				    if(UtilValidate.isNotEmpty(creditCard))
	  	    				    {
	  	    				    	orderPayment.setCardType(getString(creditCard.getString("cardType")));
	  	    				    }
	  	    				}
	  	    			}
	  	    			
	  	    		    if(paymentMethod.getString("paymentMethodTypeId").equals("EXT_PAYPAL")) 
	  	    		    {
	  	    				GenericValue payPalMethod = _delegator.findOne("PayPalPaymentMethod", UtilMisc.toMap("paymentMethodId", (String)paymentMethod.get("paymentMethodId")), false);
	  	    				if(UtilValidate.isNotEmpty(payPalMethod)) 
	  	    				{
	  	    					orderPayment.setPayPalPayerId(getString(payPalMethod.getString("paypalPayerId")));
	  	    					orderPayment.setPayPalTransactionId(getString(payPalMethod.getString("transactionId")));
	  	    					orderPayment.setPayPalPayerStatus(getString(payPalMethod.getString("payerStatus")));
	  	    					orderPayment.setPayPalPaymentToken(getString(payPalMethod.getString("expressCheckoutToken")));
	  	    				}
  	    			    }
	  	    			
	  	    		    if(paymentMethod.getString("paymentMethodTypeId").equals("EXT_EBS")) 
	  	    		    {
	  	    				GenericValue ebsMethod = _delegator.findOne("EbsPaymentMethod", UtilMisc.toMap("paymentMethodId", (String)paymentMethod.get("paymentMethodId")), false);
	  	    				if(UtilValidate.isNotEmpty(ebsMethod)) 
	  	    				{
	  	    					merchantRefNo = (String)ebsMethod.get("merchantReferenceNum");
	  	    					orderPayment.setEbsTransactionId(getString(ebsMethod.getString("transactionId")));
	  	    					orderPayment.setEbsPaymentId(getString(ebsMethod.getString("paymentId")));
	  	    					orderPayment.setMerchantReferenceNumber(getString(ebsMethod.getString("merchantReferenceNum")));
	  	    				}
  	    			    }
	  	    		    if(paymentMethod.getString("paymentMethodTypeId").equals("EXT_PAYNETZ")) 
  	    		        {
	  	    		        GenericValue payNetzMethod = _delegator.findOne("PayNetzPaymentMethod", UtilMisc.toMap("paymentMethodId", (String)paymentMethod.get("paymentMethodId")), false);
	  	    		        if(UtilValidate.isNotEmpty(payNetzMethod))
	  	    		        {
	  	    		        	orderPayment.setMerchantTransactionId(getString(payNetzMethod.getString("merchantTransactionId")));
	  	    		        }
  	    		        }
	  	  	    		if(paymentMethod.getString("paymentMethodTypeId").equals("GIFT_CARD")) 
	  	    		    {
	  	  	    			orderPayment.setCardType(getString(paymentMethod.getString("description")));
	  	  	    		    GenericValue giftCard = orderPaymentPreference.getRelatedOne("GiftCard");
		  	  	    		if(UtilValidate.isNotEmpty(giftCard))
	  	  	    			{
		  	  	    		    giftCardNumber = giftCard.getString("cardNumber");
	  	    	    		    cardExpireDate = giftCard.getString("expireDate");
	  	  	    			}
		  	  	    		orderPayment.setCardNumber(getString(giftCardNumber));
			  	  	    	orderPayment.setExpiryDate(getString(cardExpireDate));
	  	    		    }
	    			} 
	    	    	
	    	    	if(UtilValidate.isNotEmpty(gatewayResponses)) 
	    	    	{
	    	    		PaymentGatewayResponseType paymentGatewayResponseType = factory.createPaymentGatewayResponseType();
	    	    		List gatewayResponseList = paymentGatewayResponseType.getGatewayResponse();
	    	    		if(UtilValidate.isNotEmpty(gatewayResponses))
	    	    		{
	    	    			for(GenericValue gatewayResponse : gatewayResponses) 
		    	    		{
		    	    			GatewayResponseType gatewayResponseType = factory.createGatewayResponseType();
		    	    			gatewayResponseType.setTransCodeEnumId(getString(gatewayResponse.getString("transCodeEnumId")));
		    	    			if(UtilValidate.isNotEmpty(gatewayResponse.get("amount")))
		    	    			{
		    	    				gatewayResponseType.setAmount((gatewayResponse.getBigDecimal("amount").setScale(scale, rounding)).toString());
		    	    			}
		    	    			else
		    	    			{
		    	    				gatewayResponseType.setAmount("");
		    	    			}
		    	    			gatewayResponseType.setReferenceNumber(getString(gatewayResponse.getString("referenceNum")));
		    	    			gatewayResponseType.setAltReferenceNumber(getString(gatewayResponse.getString("altReference")));
		    	    			gatewayResponseType.setTransactionDate(getString(gatewayResponse.get("transactionDate").toString()));
		    	    			gatewayResponseType.setGatewayCode(getString(gatewayResponse.getString("gatewayCode")));
		    	    			gatewayResponseType.setGatewayFlag(getString(gatewayResponse.getString("gatewayFlag")));
		    	    			gatewayResponseType.setGatewayMessage(getString(gatewayResponse.getString("gatewayMessage")));
		    	    			gatewayResponseList.add(gatewayResponseType);
		    	    		}
	    	    		}
	    	    		orderPayment.setPaymentGatewayResponse(paymentGatewayResponseType);
	    	    	}
	    	    	paymentList.add(orderPayment);
	    		}
	    	}
	    	return orderPaymentType;
	    }
	    catch(Exception e)
	    {
	    	Debug.logInfo("Error in export Order Payment for Order ID ["+ orderId +"]"+e, module);
	    	exportMessageList.add(errorLogText+e);
	    	return null;
	    }
    }
    
    public static OrderAttributeType setOrderAttributeFeed(ObjectFactory factory, GenericValue orderHeader, OrderReadHelper orderReadHelper, List exportMessageList, String errorLogText)
    {
    	String orderId = "";
	    try
	    {
	    	OrderAttributeType orderAttributeType = factory.createOrderAttributeType();
	    	orderId = orderHeader.getString("orderId"); 
  	    	List attributeList = orderAttributeType.getAttribute();
  	    	List<GenericValue> orderAttributeList = _delegator.findByAnd("OrderAttribute", UtilMisc.toMap("orderId", orderId));
  	    	if(UtilValidate.isNotEmpty(orderAttributeList)) 
  	    	{
  	    	    for(GenericValue orderAttribute : orderAttributeList) 
  	    	    {
  	    		    AttributeType attribute = factory.createAttributeType();
  	    		    attribute.setName(orderAttribute.getString("attrName"));
  	    		    attribute.setValue(getString(orderAttribute.getString("attrValue")));
  	    		    attributeList.add(attribute);
  	    	    }
  	    	}
  	    	return orderAttributeType;
	    }
	    catch(Exception e)
	    {
	    	Debug.logInfo("Error in export Order Attributes for Order ID ["+ orderId +"]"+e, module);
	    	exportMessageList.add(errorLogText+e);
	    	return null;
	    }
    }
    
    public static CustomerType setOrderCustomerFeed(ObjectFactory factory, GenericValue orderHeader, OrderReadHelper orderReadHelper, List exportMessageList, String errorLogText)
    {
    	String orderId = "";
    	String productStoreId = orderReadHelper.getProductStoreId();
	    try
	    {
	    	CustomerType customer = factory.createCustomerType();
	    	orderId = orderHeader.getString("orderId");
	    	String orderType = orderHeader.getString("orderTypeId");
	    	
	    	GenericValue displayParty = null;
  	        String displayPartyId = "";
  	        if ("PURCHASE_ORDER".equals(orderType)) 
  	        {
  	            displayParty = orderReadHelper.getSupplierAgent();
  	        }
  	        else 
  	        {
  	            displayParty = orderReadHelper.getPlacingParty();
  	        }
  	        if(UtilValidate.isNotEmpty(displayParty)) 
  	        {
  	        	displayPartyId = (String)displayParty.get("partyId");
  	        	customer.setCustomerId(displayPartyId);
  	        	
  	        	List<GenericValue> partyEmailDetails = (List<GenericValue>) ContactHelper.getContactMech(displayParty, "PRIMARY_EMAIL", "EMAIL_ADDRESS", false);
            	if(UtilValidate.isNotEmpty(partyEmailDetails))
            	{
            		GenericValue partyEmailDetail = EntityUtil.getFirst(partyEmailDetails);
            		customer.setEmailAddress(getString(partyEmailDetail.getString("infoString")));
            	}
                
                GenericValue person = _delegator.findByPrimaryKey("Person", UtilMisc.toMap("partyId", displayPartyId));
                
                if(UtilValidate.isNotEmpty(person))
                {
                	customer.setFirstName(getString(person.getString("firstName")));
        	        customer.setLastName(getString(person.getString("lastName")));
                }
    	        
    	        String homePhone = FeedsUtil.getPartyPhoneNumber(displayPartyId, "PHONE_HOME", _delegator);
    	        customer.setHomePhone(homePhone);
    	        String cellPhone = FeedsUtil.getPartyPhoneNumber(displayPartyId, "PHONE_MOBILE", _delegator);
    	        customer.setCellPhone(cellPhone);
    	        String workPhone = FeedsUtil.getPartyPhoneNumber(displayPartyId, "PHONE_WORK", _delegator);
    	        customer.setWorkPhone(workPhone);
    	        String workPhoneExt = FeedsUtil.getPartyPhoneExt(displayPartyId, "PHONE_WORK", _delegator);
    	        customer.setWorkPhoneExt(workPhoneExt);
  	        }
  	          
	        List<Map<String, GenericValue>> contactMechValueMaps = ContactMechWorker.getOrderContactMechValueMaps(_delegator, orderId);
	        List billingAddressList = FastList.newInstance();
	        List shippingAddressList = FastList.newInstance();
	        
	        if(UtilValidate.isNotEmpty(contactMechValueMaps))
	        {
	        	for(Map<String, GenericValue> contactMechValueMap : contactMechValueMaps) 
		        {
					GenericValue contactMechPurpose = (GenericValue)contactMechValueMap.get("contactMechPurposeType");
					if(contactMechPurpose.getString("contactMechPurposeTypeId").equals("BILLING_LOCATION"))
					{
						BillingAddressType billingAddress = factory.createBillingAddressType();
						billingAddressList = customer.getBillingAddress();
			    	    GenericValue postalAddress = (GenericValue)contactMechValueMap.get("postalAddress");
			    	    
			    	    billingAddress.setToName(getString(postalAddress.getString("toName")));
			    	    billingAddress.setAddress1(getString(postalAddress.getString("address1")));
			    	    billingAddress.setAddress2(getString(postalAddress.getString("address2")));
			    	    billingAddress.setAddress3(getString(postalAddress.getString("address3")));
			    	    if(UtilValidate.isNotEmpty(postalAddress.getString("countryGeoId")))
			    	    {
				    	    if (displayCountryFieldAsLong(productStoreId))
				    	    {
				    	    	billingAddress.setCountry(getGeoName(postalAddress.getString("countryGeoId")));
				    	    }
				    	    else
				    	    {
				    	    	billingAddress.setCountry(postalAddress.getString("countryGeoId"));
				    	    }
			    	    }
			    	    else
			    	    {
			    	    	billingAddress.setCountry("");
			    	    }
			    	    billingAddress.setCityTown(getString(postalAddress.getString("city")));
			    	    
			    	    if(UtilValidate.isNotEmpty(postalAddress.getString("stateProvinceGeoId")))
			    	    {
				    	    if (displayStateFieldAsLong(productStoreId))
				    	    {
				    	    	billingAddress.setStateProvince(getGeoName(postalAddress.getString("stateProvinceGeoId")));
				    	    }
				    	    else
				    	    {
				    	    	billingAddress.setStateProvince(postalAddress.getString("stateProvinceGeoId"));
				    	    }
			    	    }
			    	    else
			    	    {
			    	    	billingAddress.setStateProvince("");
			    	    }
			    	    if(UtilValidate.isNotEmpty(postalAddress.getString("postalCode")))
			    	    {
				    	    if (displayZipFieldAsLong(productStoreId))
				    	    {
				    	        String postalCode = postalAddress.getString("postalCode");
				    	        if (UtilValidate.isNotEmpty(postalAddress.getString("postalCodeExt")))
				    	        {
				    	            postalCode = postalCode+"-"+postalAddress.getString("postalCodeExt");
				    	        }
					    	    billingAddress.setZipPostCode(postalCode);
				    	    }
				    	    else
				    	    {
					    	    billingAddress.setZipPostCode(postalAddress.getString("postalCode"));
				    	    }
			    	    }
			    	    else
			    	    {
			    	    	billingAddress.setZipPostCode("");
			    	    }
			            billingAddressList.add(billingAddress);
					}
					if(contactMechPurpose.getString("contactMechPurposeTypeId").equals("SHIPPING_LOCATION"))
					{
						ShippingAddressType shippingAddress = factory.createShippingAddressType();
						shippingAddressList = customer.getShippingAddress();
			    	    GenericValue postalAddress = (GenericValue)contactMechValueMap.get("postalAddress");
			    	    shippingAddress.setToName(getString(postalAddress.getString("toName")));
			    	    shippingAddress.setAddress1(getString(postalAddress.getString("address1")));
			    	    shippingAddress.setAddress2(getString(postalAddress.getString("address2")));
			    	    shippingAddress.setAddress3(getString(postalAddress.getString("address3")));
			    	    if(UtilValidate.isNotEmpty(postalAddress.getString("countryGeoId")))
			    	    {
				    	    if (displayCountryFieldAsLong(productStoreId))
				    	    {
				    	    	shippingAddress.setCountry(getGeoName(postalAddress.getString("countryGeoId")));
				    	    }
				    	    else
				    	    {
				    	    	shippingAddress.setCountry(postalAddress.getString("countryGeoId"));
				    	    }
			    	    }
			    	    else
			    	    {
			    	    	shippingAddress.setCountry("");
			    	    }
			    	    shippingAddress.setCityTown(getString(postalAddress.getString("city")));
			    	    
			    	    if(UtilValidate.isNotEmpty(postalAddress.getString("stateProvinceGeoId")))
			    	    {
				    	    if (displayStateFieldAsLong(productStoreId))
				    	    {
				    	    	shippingAddress.setStateProvince(getGeoName(postalAddress.getString("stateProvinceGeoId")));
				    	    }
				    	    else
				    	    {
				    	    	shippingAddress.setStateProvince(postalAddress.getString("stateProvinceGeoId"));
				    	    }
			    	    }
			    	    else
			    	    {
			    	    	shippingAddress.setStateProvince("");
			    	    }
			    	    if(UtilValidate.isNotEmpty(postalAddress.getString("postalCode")))
			    	    {
				    	    if (displayZipFieldAsLong(productStoreId))
				    	    {
				    	        String postalCode = postalAddress.getString("postalCode");
				    	        if (UtilValidate.isNotEmpty(postalAddress.getString("postalCodeExt")))
				    	        {
				    	            postalCode = postalCode+"-"+postalAddress.getString("postalCodeExt");
				    	        }
				    	        shippingAddress.setZipPostCode(postalCode);
				    	    }
				    	    else
				    	    {
				    	    	shippingAddress.setZipPostCode(postalAddress.getString("postalCode"));
				    	    }
			    	    }
			    	    else
			    	    {
			    	    	shippingAddress.setZipPostCode("");
			    	    }
			    	    shippingAddressList.add(shippingAddress);
					}
		        }
	        }
	        return customer;
	    }
	    catch(Exception e)
	    {
	    	Debug.logInfo("Error in export Order Customer for Order ID ["+ orderId +"]"+e, module);
	    	exportMessageList.add(errorLogText+e);
	    	return null;
	    }
    }
    
    public static OrderAdjustmentType setOrderAdjustmentFeed(ObjectFactory factory, GenericValue orderHeader, OrderReadHelper orderReadHelper, List exportMessageList, String errorLogText)
    {
    	String orderId = "";
	    try
	    {
	    	orderId = orderHeader.getString("orderId");
	    	List<GenericValue> orderHeaderAdjustments = orderReadHelper.getOrderHeaderAdjustments();
		    OrderAdjustmentType orderAdjustmentType = factory.createOrderAdjustmentType();
		    List adjustmentList = orderAdjustmentType.getAdjustment();
		    
	    	if(UtilValidate.isNotEmpty(orderHeaderAdjustments))
	    	{
	    		for(GenericValue orderHeaderAdjustment : orderHeaderAdjustments)
		    	{
		    		AdjustmentType adjustment = factory.createAdjustmentType();
		    		BigDecimal adjAmount = BigDecimal.ZERO;
		    		adjAmount = orderHeaderAdjustment.getBigDecimal("amount").setScale(scale, rounding);
		    		
		    		adjustment.setShipGroupSequenceId(getString(orderHeaderAdjustment.getString("shipGroupSeqId")));
		    		
		    		if(orderHeaderAdjustment.getString("orderAdjustmentTypeId").equals("LOYALTY_POINTS"))
		    		{
		    			List<GenericValue> orderAdjustmentAttributes = orderHeaderAdjustment.getRelated("OrderAdjustmentAttribute");
		    			for(GenericValue orderAdjustmentAttribute : orderAdjustmentAttributes)
		    			{
		    				String adjustmentAttrValue = "";
		    				if(UtilValidate.isNotEmpty(orderAdjustmentAttribute.getString("attrValue")))
		    				{
		    					adjustmentAttrValue = orderAdjustmentAttribute.getString("attrValue");
		    				}
		    				
		    				if(orderAdjustmentAttribute.getString("attrName").equals("ADJUST_METHOD"))
		    				{
		    					adjustment.setAdjustMethod(adjustmentAttrValue);
		    				}
		    				else if(orderAdjustmentAttribute.getString("attrName").equals("ADJUST_POINTS"))
		    				{
		    					adjustment.setAdjustPoints(adjustmentAttrValue);
		    				}
		    				else if(orderAdjustmentAttribute.getString("attrName").equals("CONVERSION_FACTOR"))
		    				{
		    					adjustment.setAdjustConversion(adjustmentAttrValue);
		    				}
		    				else if(orderAdjustmentAttribute.getString("attrName").equals("MEMBER_ID"))
		    				{
		    					adjustment.setAdjustMemberId(adjustmentAttrValue);
		    				}
		    			}
		    		}
		    		else if(orderHeaderAdjustment.getString("orderAdjustmentTypeId").equals("SALES_TAX"))
		    		{
		    			if(UtilValidate.isNotEmpty(orderHeaderAdjustment.get("sourcePercentage")))
	    				{
		    				adjustment.setTaxPercent((orderHeaderAdjustment.getBigDecimal("sourcePercentage").setScale(3, rounding)).toString());
	    				}
	    				else
	    				{
	    					adjustment.setTaxPercent("");
	    				}
	    				adjustment.setTaxAuthorityGeo(getString(orderHeaderAdjustment.getString("taxAuthGeoId")));
	    				adjustment.setTaxAuthorityParty(getString(orderHeaderAdjustment.getString("taxAuthPartyId")));
		    		}
		    		else if(orderHeaderAdjustment.getString("orderAdjustmentTypeId").equals("PROMOTION_ADJUSTMENT"))
		    		{
		    			GenericValue productPromo = orderHeaderAdjustment.getRelatedOne("ProductPromo");
		  	    		String promoCodeText = "";
		  	    		if(UtilValidate.isNotEmpty(productPromo)) 
		  	    		{
		  	    			List<GenericValue> productPromoCode = productPromo.getRelated("ProductPromoCode");
		  	    			Set<String> promoCodesEntered = orderReadHelper.getProductPromoCodesEntered();
		  	    			if(UtilValidate.isNotEmpty(promoCodesEntered)) 
		  	    			{
		  	    				for(String promoCodeEntered : promoCodesEntered) 
		  	    				{
		  	    					if(UtilValidate.isNotEmpty(productPromoCode)) 
		  	    					{
		  	    						for(GenericValue promoCode : productPromoCode) 
		  	    						{
		  	    							String promoCodeEnteredId = promoCodeEntered;
		  	    							String promoCodeId = (String) promoCode.get("productPromoCodeId");
		  	    							if(UtilValidate.isNotEmpty(promoCodeEnteredId)) 
		  	    							{
		  	    								if(promoCodeId.equals(promoCodeEnteredId)) 
		  	    								{
		  	    									promoCodeText = (String)promoCode.get("productPromoCodeId");
		  	    								}
		  	    							}
		  	    						}
		  	    					}
		  	    				}
		  	    			}
		  	    			else
		  	    			{
		  	    				promoCodeText = productPromo.getString("promoName");
		  	    			}
		  	    		}
		  	    	    adjustment.setPromotionCode(promoCodeText);
		    		}
		    		adjustment.setAdjustmentType(orderHeaderAdjustment.getString("orderAdjustmentTypeId"));
		    		adjustment.setAmount(adjAmount.toString());
		    		adjustmentList.add(adjustment);
		    	}
	    	}
	    	return orderAdjustmentType;
	    }
	    catch(Exception e)
	    {
	    	Debug.logInfo("Error in export Order Adjustments for Order ID ["+ orderId +"]"+e, module);
	    	exportMessageList.add(errorLogText+e);
	    	return null;
	    }
    }
    
    public static Map<String, Object> exportCustRequestContactUsXML(DispatchContext ctx, Map<String, ?> context) {

        LocalDispatcher dispatcher = ctx.getDispatcher();
        List<String> messages = FastList.newInstance();
        Delegator delegator = ctx.getDelegator();
        _locale = (Locale) context.get("locale");
        List<String> custRequestIdList = (List)context.get("custRequestIdList");
        
        String downloadTempDir = FeedsUtil.getFeedDirectory("custrequest");
        
        Map result = ServiceUtil.returnSuccess();
        
        String custRequestFileName = "ContactUs";
        if(custRequestIdList.size() == 1) 
        {
        	custRequestFileName = custRequestFileName + custRequestIdList.get(0);
        }
        custRequestFileName = custRequestFileName + "_" + (OsafeAdminUtil.convertDateTimeFormat(UtilDateTime.nowTimestamp(), "yyyy-MM-dd-HHmm"));
        custRequestFileName = UtilValidate.stripWhitespace(custRequestFileName) + ".xml";
        
        if (!new File(downloadTempDir).exists()) 
        {
        	new File(downloadTempDir).mkdirs();
	    }
        
        File file = new File(downloadTempDir + custRequestFileName);
  	  
        ObjectFactory factory = new ObjectFactory();
        
        BigFishContactUsFeedType bfContactUsFeedType = factory.createBigFishContactUsFeedType();
  	 
        List contactUsList = bfContactUsFeedType.getContactUs();
  	  
        List<String> exportedCustRequestIdList = FastList.newInstance();
        
        List<String> exportMessageList = FastList.newInstance();
        
        ContactUsType contactUs = null;
        
        int i = 0;
  	    for(String custRequestId : custRequestIdList) 
  	    {
  	    	exportMessageList.add("IN PROGRESS: [Processing row #"+(i+1)+" Contact Us ID: "+custRequestId+"]");
  	    	
  	    	String errorLogText = UtilProperties.getMessage(resource, "ErrorLogTextLabel", UtilMisc.toMap("rowNo", Integer.toString(i+1)), _locale);
  	    	
  	    	contactUs = factory.createContactUsType();
  	    	
  	    	try
  	    	{
  	    		GenericValue custRequest = delegator.findOne("CustRequest",UtilMisc.toMap("custRequestId", custRequestId), false);
  	    		GenericValue productStore= custRequest.getRelatedOne("ProductStore");
  	    		if (UtilValidate.isNotEmpty(productStore))
  	    		{
  	  	    		contactUs.setProductStoreId(productStore.getString("productStoreId"));
  	  	    		contactUs.setProductStoreName(productStore.getString("storeName"));
  	    		}
  	    		contactUs.setContactUsId(custRequestId);
  	    		String firstName = "";
  	    		String lastName = "";
  	    		String emailAddress = "";
  	    		String orderId = "";
  	    		String contactPhone = "";
  	    		String comment = "";

  	    		List<GenericValue> custReqAttributeList = custRequest.getRelated("CustRequestAttribute");
  	    		for(GenericValue custReqAttribute : custReqAttributeList) 
  	    		{
  	    			if(custReqAttribute.get("attrName").equals("FIRST_NAME")) 
  	    			{
  	    				firstName = (String) custReqAttribute.get("attrValue"); 
  	    			}
                    if(custReqAttribute.get("attrName").equals("LAST_NAME")) 
                    {
                    	lastName = (String) custReqAttribute.get("attrValue");
  	    			}
                    if(custReqAttribute.get("attrName").equals("ORDER_NUMBER")) 
                    {
                    	orderId = (String) custReqAttribute.get("attrValue");
  	    			}
                    if(custReqAttribute.get("attrName").equals("EMAIL_ADDRESS")) 
                    {
                    	emailAddress = (String) custReqAttribute.get("attrValue");
  	    			}
                    if(custReqAttribute.get("attrName").equals("CONTACT_PHONE")) 
                    {
                    	contactPhone = (String) custReqAttribute.get("attrValue");
  	    			}
                    if(custReqAttribute.get("attrName").equals("COMMENT")) 
                    {
                    	comment = (String) custReqAttribute.get("attrValue");
  	    			}
  	    		}
  	    		if(contactPhone.length()> 6) 
  	    		{
  	    			contactPhone = contactPhone.substring(0,3)+"-"+contactPhone.substring(3,6)+"-"+contactPhone.substring(6);
  	    		}
  	    		contactUs.setFirstName(firstName);
  	    		contactUs.setLastName(lastName);
  	    		contactUs.setOrderId(orderId);
  	    		contactUs.setContactPhone(contactPhone);
  	    		contactUs.setComment(StringUtil.wrapString(comment).toString());
  	    		contactUs.setEmailAddress(emailAddress);
  	    		
  	    		contactUsList.add(contactUs);
  	    		exportedCustRequestIdList.add(custRequestId);
  	    	}
  	    	catch (Exception e)
  	    	{
  	    		e.printStackTrace();
  	    		messages.add("Error in Customer Contact Us Export.");
  	    		exportMessageList.add(errorLogText + e);
  	    	}
  	    	exportMessageList.add("ROW COMPLETE: [Processed row #"+(i+1)+" Contact Us ID: "+custRequestId+"]");
  	    	i++;
  	    }
  	  
  	  bfContactUsFeedType.setCount(String.valueOf(exportedCustRequestIdList.size()));
  	  
      FeedsUtil.marshalObject(new JAXBElement<BigFishContactUsFeedType>(new QName("", "BigFishContactUsFeed"), BigFishContactUsFeedType.class, null, bfContactUsFeedType), file);
      result.put("feedsDirectoryPath", downloadTempDir);
      result.put("feedsFileName", custRequestFileName);
      result.put("feedsExportedIdList", exportedCustRequestIdList);
      result.put("exportMessageList", exportMessageList);
      return result;
    }
    
    public static Map<String, Object> exportCustRequestCatalogXML(DispatchContext ctx, Map<String, ?> context) {

        LocalDispatcher dispatcher = ctx.getDispatcher();
        List<String> messages = FastList.newInstance();
        Delegator delegator = ctx.getDelegator();
        _locale = (Locale) context.get("locale");
        List<String> custRequestIdList = (List)context.get("custRequestIdList");
        
        String downloadTempDir = FeedsUtil.getFeedDirectory("custrequest");
        
        Map result = ServiceUtil.returnSuccess();
        
        String custRequestFileName = "RequestCatalog";
        if(custRequestIdList.size() == 1)
        {
        	custRequestFileName = custRequestFileName + custRequestIdList.get(0);
        }
        custRequestFileName = custRequestFileName + "_" + (OsafeAdminUtil.convertDateTimeFormat(UtilDateTime.nowTimestamp(), "yyyy-MM-dd-HHmm"));
        custRequestFileName = UtilValidate.stripWhitespace(custRequestFileName) + ".xml";
        
        if (!new File(downloadTempDir).exists()) 
        {
        	new File(downloadTempDir).mkdirs();
	    }
        
        File file = new File(downloadTempDir + custRequestFileName);
  	  
        ObjectFactory factory = new ObjectFactory();
        
        BigFishRequestCatalogFeedType bfRequestCatalogFeedType = factory.createBigFishRequestCatalogFeedType();
  	 
        List requestCatalogList = bfRequestCatalogFeedType.getRequestCatalog();
  	  
        List<String> exportedCustRequestIdList = FastList.newInstance();
        
        List<String> exportMessageList = FastList.newInstance();
        
        RequestCatalogType customerRequest = null;

        int i = 0;
        
  	    for(String custRequestId : custRequestIdList)
  	    {
            exportMessageList.add("IN PROGRESS: [Processing row #"+(i+1)+" Request Catalog ID: "+custRequestId+"]");
  	    	
  	    	String errorLogText = UtilProperties.getMessage(resource, "ErrorLogTextLabel", UtilMisc.toMap("rowNo", Integer.toString(i+1)), _locale);
  	    	customerRequest = factory.createRequestCatalogType();
  	    	
  	    	try
  	    	{
  	    		GenericValue custRequest = delegator.findOne("CustRequest",UtilMisc.toMap("custRequestId", custRequestId), false);
  	    		GenericValue productStore= custRequest.getRelatedOne("ProductStore");
  	    		if (UtilValidate.isNotEmpty(productStore))
  	    		{
  	    			customerRequest.setProductStoreId(productStore.getString("productStoreId"));
  	    			customerRequest.setProductStoreName(productStore.getString("storeName"));
  	    		}
  	    		customerRequest.setRequestCatalogId(custRequestId);
  	    		String firstName = "";
  	    		String lastName = "";
  	    		String country = "";
  	    		String address1 = "";
  	    		String address2 = "";
  	    		String address3 = "";
  	    		String city = "";
  	    		String state = "";
  	    		String zip = "";
  	    		String emailAddress = "";
  	    		String contactPhone = "";
  	    		String comment = "";
  	    		
  	    		List<GenericValue> custReqAttributeList = custRequest.getRelated("CustRequestAttribute");
  	    		for(GenericValue custReqAttribute : custReqAttributeList)
  	    		{
  	    			if(custReqAttribute.get("attrName").equals("FIRST_NAME"))
  	    			{
  	    				firstName = (String) custReqAttribute.get("attrValue"); 
  	    			}
                    if(custReqAttribute.get("attrName").equals("LAST_NAME"))
                    {
                    	lastName = (String) custReqAttribute.get("attrValue");
  	    			}
                    if(custReqAttribute.get("attrName").equals("COUNTRY"))
                    {
                    	country = (String) custReqAttribute.get("attrValue");
  	    			}
                    if(custReqAttribute.get("attrName").equals("ADDRESS1"))
                    {
                    	address1 = (String) custReqAttribute.get("attrValue");
  	    			}
                    if(custReqAttribute.get("attrName").equals("ADDRESS2"))
                    {
                    	address2 = (String) custReqAttribute.get("attrValue");
  	    			}
                    if(custReqAttribute.get("attrName").equals("ADDRESS3"))
                    {
                    	address3 = (String) custReqAttribute.get("attrValue");
  	    			}
                    if(custReqAttribute.get("attrName").equals("CITY"))
                    {
                    	city = (String) custReqAttribute.get("attrValue");
  	    			}
                    if(custReqAttribute.get("attrName").equals("STATE_PROVINCE"))
                    {
                    	state = (String) custReqAttribute.get("attrValue");
  	    			}
                    if(custReqAttribute.get("attrName").equals("ZIP_POSTAL_CODE"))
                    {
                    	zip = (String) custReqAttribute.get("attrValue");
  	    			}
                    if(custReqAttribute.get("attrName").equals("EMAIL_ADDRESS"))
                    {
                    	emailAddress = (String) custReqAttribute.get("attrValue");
  	    			}
                    if(custReqAttribute.get("attrName").equals("CONTACT_PHONE"))
                    {
                    	contactPhone = (String) custReqAttribute.get("attrValue");
  	    			}
                    if(custReqAttribute.get("attrName").equals("COMMENT"))
                    {
                    	comment = (String) custReqAttribute.get("attrValue");
  	    			}
  	    		}
  	    		if(contactPhone.length()> 6) 
  	    		{
  	    			contactPhone = contactPhone.substring(0,3)+"-"+contactPhone.substring(3,6)+"-"+contactPhone.substring(6);
  	    		}
  	    		customerRequest.setFirstName(firstName);
  	    		customerRequest.setLastName(lastName);
  	    		customerRequest.setCountry(country);
  	    		customerRequest.setAddress1(address1);
  	    		customerRequest.setAddress2(address2);
  	    		customerRequest.setAddress3(address3);
  	    		customerRequest.setCityTown(city);
  	    		customerRequest.setStateProvince(state);
  	    		customerRequest.setZipPostCode(zip);
  	    		customerRequest.setContactPhone(contactPhone);
  	    		customerRequest.setComment(StringUtil.wrapString(comment).toString());
  	    		customerRequest.setEmailAddress(emailAddress);
  	    		
  	    		requestCatalogList.add(customerRequest);
  	    		
  	    		exportedCustRequestIdList.add(custRequestId);
  	    	}
  	    	catch (Exception e)
  	    	{
  	    		e.printStackTrace();
  	    		messages.add("Error in Customer Export.");
  	    		exportMessageList.add(errorLogText + e);
  	    	}
  	    	exportMessageList.add("ROW COMPLETE: [Processed row #"+(i+1)+" Request Catalog ID: "+custRequestId+"]");
  	    	i++;
  	    }
  	  
  	    bfRequestCatalogFeedType.setCount(String.valueOf(exportedCustRequestIdList.size()));
  	  
        FeedsUtil.marshalObject(new JAXBElement<BigFishRequestCatalogFeedType>(new QName("", "BigFishRequestCatalogFeed"), BigFishRequestCatalogFeedType.class, null, bfRequestCatalogFeedType), file);
      
        result.put("feedsDirectoryPath", downloadTempDir);
        result.put("feedsFileName", custRequestFileName);
        result.put("feedsExportedIdList", exportedCustRequestIdList);
        result.put("exportMessageList", exportMessageList);
        return result;
    }
    
    
    
    public static Map<String, Object> exportCustomerXML(DispatchContext ctx, Map<String, ?> context) {

        LocalDispatcher dispatcher = ctx.getDispatcher();
        List<String> messages = FastList.newInstance();
        Delegator delegator = ctx.getDelegator();
        _delegator =  ctx.getDelegator();
        _locale = (Locale) context.get("locale");
        String productStoreId = (String)context.get("productStoreId");
        ObjectFactory factory = new ObjectFactory();
        
        BigFishCustomerFeedType bfCustomerFeedType = factory.createBigFishCustomerFeedType();
        
        List<String> customerIdList = (List)context.get("customerList");
        
        String downloadTempDir = FeedsUtil.getFeedDirectory("customer");
        
        Map result = ServiceUtil.returnSuccess();
        
        String customerFileName = "Customer";
        if(customerIdList.size() == 1)
        {
        	customerFileName = customerFileName + customerIdList.get(0);
        }
        customerFileName = customerFileName + "_" + (OsafeAdminUtil.convertDateTimeFormat(UtilDateTime.nowTimestamp(), "yyyy-MM-dd-HHmm"));
        customerFileName = UtilValidate.stripWhitespace(customerFileName) + ".xml";
        
        if (!new File(downloadTempDir).exists()) 
        {
        	new File(downloadTempDir).mkdirs();
	    }
        
        File file = new File(downloadTempDir + customerFileName);
        
        CustomerType customerType = null;
        
        List customerList = bfCustomerFeedType.getCustomer();
        
        List<String> exportedCustomerIdList = FastList.newInstance();
  	  
        List<String> exportMessageList = FastList.newInstance();
        
        CustomerType customer = null;
        int i = 0;
  	    for(String customerId : customerIdList)
  	    {
  	    	exportMessageList.add("IN PROGRESS: [Processing row #"+(i+1)+" Customer ID: "+customerId+"]");
  	    	
  	    	String errorLogText = UtilProperties.getMessage(resource, "ErrorLogTextLabel", UtilMisc.toMap("rowNo", Integer.toString(i+1)), _locale);
  	    	
  	    	GenericValue party = null;
  	    	GenericValue person = null;
  	    	
  	    	try
  	    	{
  	    	    party = delegator.findByPrimaryKey("Party", UtilMisc.toMap("partyId", customerId));
  	    	    person = delegator.findByPrimaryKey("Person", UtilMisc.toMap("partyId", customerId));
  	    	
  	    	    String partyId = (String)party.get("partyId");
  	    	    
  	    	    customer = factory.createCustomerType();
  	    	    
  	    	    GenericValue partyEmailFormatAttr = delegator.findByPrimaryKey("PartyAttribute", UtilMisc.toMap("partyId", customerId,"attrName","PARTY_EMAIL_PREFERENCE"));
  	    	    
  	    	    List<GenericValue> partyContactDetails = delegator.findByAnd("PartyContactDetailByPurpose", UtilMisc.toMap("partyId", customerId));
                partyContactDetails = EntityUtil.filterByDate(partyContactDetails);
                partyContactDetails = EntityUtil.filterByDate(partyContactDetails, UtilDateTime.nowTimestamp(), "purposeFromDate", "purposeThruDate", true);
  	    	
                List<GenericValue> partyEmailDetails = EntityUtil.filterByAnd(partyContactDetails, UtilMisc.toMap("contactMechPurposeTypeId","PRIMARY_EMAIL"));
                GenericValue partyEmailDetail = EntityUtil.getFirst(partyEmailDetails);
                
                
                List<GenericValue> partyContactMechPurpose = party.getRelated("PartyContactMechPurpose");
                List<GenericValue> billingContactMechList = FastList.newInstance();
                List<GenericValue> shippingContactMechList = FastList.newInstance();
                
            	if (UtilValidate.isNotEmpty(partyContactMechPurpose))
            	{
        	        partyContactMechPurpose = EntityUtil.filterByDate(partyContactMechPurpose,true);
        	
        	        List<GenericValue> partyBillingLocations = EntityUtil.filterByAnd(partyContactMechPurpose, UtilMisc.toMap("contactMechPurposeTypeId", "BILLING_LOCATION"));
        	        partyBillingLocations = EntityUtil.getRelated("PartyContactMech", partyBillingLocations);
        	        partyBillingLocations = EntityUtil.filterByDate(partyBillingLocations,true);
        	        partyBillingLocations = EntityUtil.orderBy(partyBillingLocations, UtilMisc.toList("fromDate DESC"));
        	        if (UtilValidate.isNotEmpty(partyBillingLocations)) 
        	        {
        	        	GenericValue partyBillingLocation = EntityUtil.getFirst(partyBillingLocations);
        	            billingContactMechList = EntityUtil.getRelated("ContactMech",partyBillingLocations);
        	            //context.billingContactMechList = billingContactMechList;
        	        }
        	
        	        
        	        List<GenericValue> partyShippingLocations = EntityUtil.filterByAnd(partyContactMechPurpose, UtilMisc.toMap("contactMechPurposeTypeId", "SHIPPING_LOCATION"));
        	        partyShippingLocations = EntityUtil.getRelated("PartyContactMech", partyShippingLocations);
        	        partyShippingLocations = EntityUtil.filterByDate(partyShippingLocations,true);
        	        partyShippingLocations = EntityUtil.orderBy(partyShippingLocations, UtilMisc.toList("fromDate DESC"));
        	        if (UtilValidate.isNotEmpty(partyShippingLocations)) 
        	        {
        	        	GenericValue partyShippingLocation = EntityUtil.getFirst(partyShippingLocations);
        	            shippingContactMechList = EntityUtil.getRelated("ContactMech",partyShippingLocations);
        	        }
            	}
                List billingAddressList = null;
            	for(GenericValue billingContactMech : billingContactMechList) 
    	        {
    				GenericValue postalAddress = billingContactMech.getRelatedOne("PostalAddress");
    				BillingAddressType billingAddress = factory.createBillingAddressType();
    				billingAddressList = customer.getBillingAddress();
    		    	    
		    	    String address1 = (String)postalAddress.get("address1");
		    	    String address2 = (String)postalAddress.get("address2");
		    	    String address3 = (String)postalAddress.get("address3");
		    	    String city = (String)postalAddress.get("city");
		    	    billingAddress.setAddress1(address1);
		    	    billingAddress.setAddress2(address2);
		    	    billingAddress.setAddress3(address3);
		    	    billingAddress.setCityTown(city);
		    	    if (displayCountryFieldAsLong(productStoreId))
		    	    {
			    	    billingAddress.setCountry(getGeoName(postalAddress.getString("countryGeoId")));
		    	    }
		    	    else
		    	    {
			    	    billingAddress.setCountry(postalAddress.getString("countryGeoId"));
		    	    }
		    	    if (displayStateFieldAsLong(productStoreId))
		    	    {
			    	    billingAddress.setStateProvince(getGeoName(postalAddress.getString("stateProvinceGeoId")));
		    	    }
		    	    else
		    	    {
			    	    billingAddress.setStateProvince(postalAddress.getString("stateProvinceGeoId"));
		    	    }
		    	    if (displayZipFieldAsLong(productStoreId))
		    	    {
		    	        String postalCode = postalAddress.getString("postalCode");
		    	        if (UtilValidate.isNotEmpty(postalAddress.getString("postalCodeExt")))
		    	        {
		    	            postalCode = postalCode+"-"+postalAddress.getString("postalCodeExt");
		    	        }
			    	    billingAddress.setZipPostCode(postalCode);
		    	    }
		    	    else
		    	    {
			    	    billingAddress.setZipPostCode(postalAddress.getString("postalCode"));
		    	    }
		            billingAddressList.add(billingAddress);
    				
    	        }
                
            	List shippingAddressList = null;
            	for(GenericValue shippingContactMech : shippingContactMechList) 
    	        {
    				GenericValue postalAddress = shippingContactMech.getRelatedOne("PostalAddress");
    				ShippingAddressType shippingAddress = factory.createShippingAddressType();
    				shippingAddressList = customer.getShippingAddress();
    		    	    
		    	    String address1 = (String)postalAddress.get("address1");
		    	    String address2 = (String)postalAddress.get("address2");
		    	    String address3 = (String)postalAddress.get("address3");
		    	    String city = (String)postalAddress.get("city");
		    	    shippingAddress.setAddress1(address1);
		    	    shippingAddress.setAddress2(address2);
		    	    shippingAddress.setAddress3(address3);
		    	    shippingAddress.setCityTown(city);
		    	    if (displayCountryFieldAsLong(productStoreId))
		    	    {
		    	    	shippingAddress.setCountry(getGeoName(postalAddress.getString("countryGeoId")));
		    	    }
		    	    else
		    	    {
		    	    	shippingAddress.setCountry(postalAddress.getString("countryGeoId"));
		    	    }
		    	    if (displayStateFieldAsLong(productStoreId))
		    	    {
		    	    	shippingAddress.setStateProvince(getGeoName(postalAddress.getString("stateProvinceGeoId")));
		    	    }
		    	    else
		    	    {
		    	    	shippingAddress.setStateProvince(postalAddress.getString("stateProvinceGeoId"));
		    	    }
		    	    if (displayZipFieldAsLong(productStoreId))
		    	    {
		    	        String postalCode = postalAddress.getString("postalCode");
		    	        if (UtilValidate.isNotEmpty(postalAddress.getString("postalCodeExt")))
		    	        {
		    	            postalCode = postalCode+"-"+postalAddress.getString("postalCodeExt");
		    	        }
		    	        shippingAddress.setZipPostCode(postalCode);
		    	    }
		    	    else
		    	    {
		    	    	shippingAddress.setZipPostCode(postalAddress.getString("postalCode"));
		    	    }
		    	    shippingAddressList.add(shippingAddress);
    				
    	        }
    	        
                GenericValue partyGenderAttr = delegator.findByPrimaryKey("PartyAttribute", UtilMisc.toMap("partyId", customerId,"attrName","GENDER"));
    	        String gender = "";
    	        if(UtilValidate.isNotEmpty(partyGenderAttr))
    	        {
    	        	if("M".equals(partyGenderAttr.getString("attrValue")))
    	        	{
        	        	gender = "MALE";
        	        }
    	        	else if ("F".equals(partyGenderAttr.getString("attrValue")))
        	        {
        	        	gender = "FEMALE";
        	        }
    	        }
    	        
    	        String allowSolicitation = (String)partyEmailDetail.get("allowSolicitation");
    	        if(UtilValidate.isEmpty(allowSolicitation))
    	        {
    	        	allowSolicitation = "";
    	        }
    	        if(allowSolicitation.equalsIgnoreCase("Y"))
    	        {
    	        	allowSolicitation = "TRUE";
    	        } else if (allowSolicitation.equalsIgnoreCase("N"))
    	        {
    	        	allowSolicitation = "FALSE";
    	        }
    	        
    	        GenericValue partyTitleAttr = delegator.findByPrimaryKey("PartyAttribute", UtilMisc.toMap("partyId", customerId,"attrName","TITLE"));
    	        String title = "";
    	        if(UtilValidate.isNotEmpty(partyTitleAttr))
    	        {
    	        	title = partyTitleAttr.getString("attrValue");
    	        }
    	        
    	        GenericValue partyIsDownloadedAttr = delegator.findByPrimaryKey("PartyAttribute", UtilMisc.toMap("partyId", customerId,"attrName","IS_DOWNLOADED"));
    	        String isDownloaded = "";
    	        if(UtilValidate.isNotEmpty(partyIsDownloadedAttr))
    	        {
    	        	isDownloaded = partyIsDownloadedAttr.getString("attrValue");
    	        }
    	        
    	        GenericValue partyDobDdMmYyyyAttr = delegator.findByPrimaryKey("PartyAttribute", UtilMisc.toMap("partyId", customerId,"attrName","DOB_DDMMYYYY"));
    	        String dobDdMmYyyy = "";
    	        if(UtilValidate.isNotEmpty(partyDobDdMmYyyyAttr))
    	        {
    	        	dobDdMmYyyy = partyDobDdMmYyyyAttr.getString("attrValue");
    	        }
    	        
    	        GenericValue partyDobDdMmAttr = delegator.findByPrimaryKey("PartyAttribute", UtilMisc.toMap("partyId", customerId,"attrName","DOB_DDMM"));
    	        String dobDdMm = "";
    	        if(UtilValidate.isNotEmpty(partyDobDdMmAttr))
    	        {
    	        	dobDdMm = partyDobDdMmAttr.getString("attrValue");
    	        }
    	        
    	        GenericValue partyDobMmDdYyyyAttr = delegator.findByPrimaryKey("PartyAttribute", UtilMisc.toMap("partyId", customerId,"attrName","DOB_MMDDYYYY"));
    	        String dobMmDdYyyy = "";
    	        if(UtilValidate.isNotEmpty(partyDobMmDdYyyyAttr))
    	        {
    	        	dobMmDdYyyy = partyDobMmDdYyyyAttr.getString("attrValue");
    	        }
    	        
    	        GenericValue partyDobMmDdAttr = delegator.findByPrimaryKey("PartyAttribute", UtilMisc.toMap("partyId", customerId,"attrName","DOB_MMDD"));
    	        String dobMmDd = "";
    	        if(UtilValidate.isNotEmpty(partyDobMmDdAttr))
    	        {
    	        	dobMmDd = partyDobMmDdAttr.getString("attrValue");
    	        }
    	        
    	        //Set Customer Personal Information
    	        customer.setProductStoreId(productStoreId);
    	        customer.setCustomerId(partyId);
    	        customer.setFirstName((String)person.get("firstName"));
    	        customer.setLastName((String)person.get("lastName"));

    	        if(UtilValidate.isNotEmpty(party.get("createdStamp")))
    	        {
        	        customer.setDateRegistered(party.get("createdStamp").toString());
    	        }
    	        else 
    	        {
        	        customer.setDateRegistered("");
    	        }
    	        customer.setEmailAddress((String)partyEmailDetail.get("infoString"));
    	        customer.setEmailOptIn(allowSolicitation);
    	        String homePhone = FeedsUtil.getPartyPhoneNumber(partyId, "PHONE_HOME", delegator);
    	        customer.setHomePhone(homePhone);
    	        String cellPhone = FeedsUtil.getPartyPhoneNumber(partyId, "PHONE_MOBILE", delegator);
    	        customer.setCellPhone(cellPhone);
    	        String workPhone = FeedsUtil.getPartyPhoneNumber(partyId, "PHONE_WORK", delegator);
    	        customer.setWorkPhone(workPhone);
    	        String workPhoneExt = FeedsUtil.getPartyPhoneExt(partyId, "PHONE_WORK", delegator);
    	        customer.setWorkPhoneExt(workPhoneExt);
    	        
    	        List<GenericValue> userLogins = party.getRelated("UserLogin");
    	        if(UtilValidate.isNotEmpty(userLogins))
    	        {
    	        	GenericValue userLogin = EntityUtil.getFirst(userLogins);
    	        	UserLoginType userLoginType = factory.createUserLoginType();
    	        	userLoginType.setUserName((String)userLogin.get("userLoginId"));
    	        	userLoginType.setPassword("");
    	        	String userEnabled = "";
    	        	if(UtilValidate.isNotEmpty(userLogin.get("enabled")))
    	        	{
    	        		userEnabled = (String)userLogin.get("enabled");
    	        	}
    	        	userLoginType.setUserEnabled(userEnabled);
    	        	
    	        	String userIsSystem = "";
    	        	if(UtilValidate.isNotEmpty(userLogin.get("isSystem")))
    	        	{
    	        		userIsSystem = (String)userLogin.get("isSystem"); 
    	        	}
    	        	userLoginType.setUserIsSystem(userIsSystem);
    	        	
    	        	customer.setUserLogin(userLoginType);
    	        	
    	        }
    	        
    	        //Set Customer Attribute Detail
	  	    	CustomerAttributeType customerAttributeType = factory.createCustomerAttributeType();
	  	    	List attributeList = customerAttributeType.getAttribute();
	  	    	List<GenericValue> customerAttributeList = delegator.findByAnd("PartyAttribute", UtilMisc.toMap("partyId", customerId));
	  	    	if(UtilValidate.isNotEmpty(customerAttributeList)) 
	  	    	{
	  	    	    for(GenericValue customerAttribute : customerAttributeList)
	  	    	    {
	  	    		    AttributeType attribute = factory.createAttributeType();
	  	    		    attribute.setName(customerAttribute.getString("attrName"));
	  	    		    attribute.setValue(customerAttribute.getString("attrValue"));
	  	    		    attributeList.add(attribute);
	  	    	    }
	  	    	}
	  	    	customer.setCustomerAttribute(customerAttributeType);
	  	    	
    	        customerList.add(customer);
    	        exportedCustomerIdList.add(customerId);
    	        Debug.logInfo("Exporting Customer "+customerId, module);
  	    	}
  	    	catch (Exception e)
  	    	{
  	    		e.printStackTrace();
  	    		messages.add("Error in Customer Export.");
  	    		exportMessageList.add(errorLogText + e);
  	    	}
  	    	exportMessageList.add("ROW COMPLETE: [Processed row #"+(i+1)+" Customer ID: "+customerId+"]");
  	    	i++;
  	    }
        
  	    bfCustomerFeedType.setCount(String.valueOf(exportedCustomerIdList.size()));
  	    
        FeedsUtil.marshalObject(new JAXBElement<BigFishCustomerFeedType>(new QName("", "BigFishCustomerFeed"), BigFishCustomerFeedType.class, null, bfCustomerFeedType), file);  
        result.put("feedsDirectoryPath", downloadTempDir);
        result.put("feedsFileName", customerFileName);
        result.put("exportMessageList", exportMessageList);
        result.put("feedsExportedIdList", exportedCustomerIdList);
        return result;

    }
    
    
    public static Map<String, Object> importProductXML(DispatchContext ctx, Map<String, ?> context) {
        LocalDispatcher dispatcher = ctx.getDispatcher();
        _delegator = ctx.getDelegator();
        List<String> messages = FastList.newInstance();
        List<String> errorMessages = FastList.newInstance();

        String xmlDataFilePath = (String)context.get("xmlDataFile");
        String xmlDataDirPath = (String)context.get("xmlDataDir");
        String loadImagesDirPath=(String)context.get("productLoadImagesDir");
        String imageUrl = (String)context.get("imageUrl");
        Boolean removeAll = (Boolean) context.get("removeAll");
        Boolean autoLoad = (Boolean) context.get("autoLoad");
        String productStoreId = (String)context.get("productStoreId");

        if (removeAll == null) removeAll = Boolean.FALSE;
        if (autoLoad == null) autoLoad = Boolean.FALSE;

        File inputWorkbook = null;
        String tempDataFile = null;
        File baseDataDir = null;
        File baseFilePath = null;
        BufferedWriter fOutProduct=null;
        if (UtilValidate.isNotEmpty(xmlDataFilePath) && UtilValidate.isNotEmpty(xmlDataDirPath)) 
        {
        	baseFilePath = new File(xmlDataFilePath);
            try 
            {
                URL xlsDataFileUrl = UtilURL.fromFilename(xmlDataFilePath);
                InputStream ins = xlsDataFileUrl.openStream();

                if (ins != null && (xmlDataFilePath.toUpperCase().endsWith("XML"))) 
                {
                    baseDataDir = new File(xmlDataDirPath);
                    if (baseDataDir.isDirectory() && baseDataDir.canWrite()) {

                        // ############################################
                        // move the existing xml files in dump directory
                        // ############################################
                        File dumpXmlDir = null;
                        File[] fileArray = baseDataDir.listFiles();
                        for (File file: fileArray) 
                        {
                            try 
                            {
                                if (file.getName().toUpperCase().endsWith("XML")) 
                                {
                                    if (dumpXmlDir == null) 
                                    {
                                        dumpXmlDir = new File(baseDataDir, "dumpxml_"+UtilDateTime.nowDateString());
                                    }
                                    FileUtils.copyFileToDirectory(file, dumpXmlDir);
                                    file.delete();
                                }
                            } 
                            catch (IOException ioe) 
                            {
                                Debug.logError(ioe, module);
                            } 
                            catch (Exception exc) 
                            {
                                Debug.logError(exc, module);
                            }
                        }
                        // ######################################
                        //save the temp xml data file on server 
                        // ######################################
                        try 
                        {
                        	tempDataFile = UtilDateTime.nowAsString()+"."+FilenameUtils.getExtension(xmlDataFilePath);
                            inputWorkbook = new File(baseDataDir,  tempDataFile);
                            if (inputWorkbook.createNewFile()) 
                            {
                                Streams.copy(ins, new FileOutputStream(inputWorkbook), true, new byte[1]); 
                            }
                        } 
                        catch (IOException ioe) 
                        {
                                Debug.logError(ioe, module);
                        } 
                        catch (Exception exc) 
                        {
                                Debug.logError(exc, module);
                        }
                    }
                    else {
                        messages.add("xml data dir path not found or can't be write");
                    }
                }
                else 
                {
                    messages.add(" path specified for XML file is wrong , doing nothing.");
                }

            } 
            catch (IOException ioe) 
            {
                Debug.logError(ioe, module);
            } 
            catch (Exception exc) 
            {
                Debug.logError(exc, module);
            }
        }
        else 
        {
            messages.add("No path specified for XML file or xml data direcotry, doing nothing.");
        }

        // ######################################
        //read the temp xls file and generate xml 
        // ######################################
        try 
        {
        if (inputWorkbook != null && baseDataDir  != null) 
        {
        	try 
        	{
        		JAXBContext jaxbContext = JAXBContext.newInstance("com.osafe.feeds.osafefeeds");
            	Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            	JAXBElement<BigFishProductFeedType> bfProductFeedType = (JAXBElement<BigFishProductFeedType>)unmarshaller.unmarshal(inputWorkbook);
            	
            	if(UtilValidate.isNotEmpty(productStoreId))
            	{
            		try
            		{
            			GenericValue productStore = _delegator.findByPrimaryKey("ProductStore", UtilMisc.toMap("productStoreId", productStoreId));
                		if(UtilValidate.isNotEmpty(productStore))
                		{
                			localeString = productStore.getString("defaultLocaleString");
                		}
            		}
            		catch(GenericEntityException gee)
            		{
            			Debug.log("No Product Store Found For ProductStoreId "+productStoreId, gee.toString());
            		}
            	}
            	
            	List<ProductType> products = FastList.newInstance();
            	List<CategoryType> productCategories = FastList.newInstance();
            	List<AssociationType> productAssociations = FastList.newInstance();
            	List<FacetCatGroupType> productFacetGroup = FastList.newInstance();
            	List<FacetValueType> productFacetValue = FastList.newInstance();
            	List<ManufacturerType> productManufacturers = FastList.newInstance();
            	
            	ProductsType productsType = bfProductFeedType.getValue().getProducts();
            	if(UtilValidate.isNotEmpty(productsType))
            	{
            	    products = productsType.getProduct();
            	}
            	
            	ProductCategoryType productCategoryType = bfProductFeedType.getValue().getProductCategory();
            	if(UtilValidate.isNotEmpty(productCategoryType))
            	{
            	    productCategories = productCategoryType.getCategory();
            	}
            	
            	ProductAssociationType productAssociationType = bfProductFeedType.getValue().getProductAssociation();
            	if(UtilValidate.isNotEmpty(productAssociationType))
            	{
            	    productAssociations = productAssociationType.getAssociation();
            	}
            	
            	ProductFacetCatGroupType productFacetCatGroupType = bfProductFeedType.getValue().getProductFacetGroup();
            	if(UtilValidate.isNotEmpty(productFacetCatGroupType))
            	{
            	    productFacetGroup = productFacetCatGroupType.getFacetCatGroup();
            	}
            	
            	ProductFacetValueType productFacetValueType = bfProductFeedType.getValue().getProductFacetValue();
            	if(UtilValidate.isNotEmpty(productFacetValueType))
            	{
            	    productFacetValue = productFacetValueType.getFacetValue();
            	}
            	
            	ProductManufacturerType productManufacturerType = bfProductFeedType.getValue().getProductManufacturer();
            	if(UtilValidate.isNotEmpty(productManufacturerType))
            	{
            	    productManufacturers = productManufacturerType.getManufacturer();
            	}

            	if(productCategories.size() > 0) 
            	{
            		List dataRows = buildProductCategoryXMLDataRows(productCategories);
            		buildProductCategory(dataRows, xmlDataDirPath,loadImagesDirPath, imageUrl);
            	}
            	if(products.size() > 0)
            	{
            	    List dataRows = buildProductXMLDataRows(products);
            	    buildProduct(dataRows, xmlDataDirPath);
                	buildProductVariant(dataRows, xmlDataDirPath,loadImagesDirPath,imageUrl, removeAll);
                	buildProductSelectableFeatures(dataRows, xmlDataDirPath);
                	buildProductGoodIdentification(dataRows, xmlDataDirPath);
                	buildProductCategoryFeatures(dataRows, xmlDataDirPath, removeAll);
                    buildProductDistinguishingFeatures(dataRows, xmlDataDirPath);
                    buildProductContent(dataRows, xmlDataDirPath,loadImagesDirPath,imageUrl);
                    buildProductVariantContent(dataRows, xmlDataDirPath,loadImagesDirPath,imageUrl);
                    buildProductAttribute(dataRows, xmlDataDirPath);
            	}
            	if(productAssociations.size() > 0)
            	{
            		List dataRows = buildProductAssociationXMLDataRows(productAssociations);
            		buildProductAssoc(dataRows, xmlDataDirPath);
            	}
            	if(productFacetGroup.size() > 0)
            	{
            		List dataRows = buildProductFacetGroupXMLDataRows(productFacetGroup);
            		buildProductFacetGroup(dataRows, xmlDataDirPath,loadImagesDirPath,imageUrl);
            	}
            	if(productFacetValue.size() > 0)
            	{
            		List dataRows = buildProductFacetValueXMLDataRows(productFacetValue);
            		buildProductFacetValue(dataRows, xmlDataDirPath,loadImagesDirPath,imageUrl);
            	}
            	if(productManufacturers.size() > 0)
            	{
            		List dataRows = buildProductManufacturerXMLDataRows(productManufacturers);
            		buildManufacturer(dataRows,xmlDataDirPath,loadImagesDirPath,imageUrl,productStoreId);
            	}
            	
        	} 
        	catch (Exception e) 
        	{
        		Debug.logError(e, module);
			}
        	finally 
        	{
                try {
                    if (fOutProduct != null)
                    {
                    	fOutProduct.close();
                    }
                } catch (IOException ioe)
                {
                    Debug.logError(ioe, module);
                }
            }
        }
     // ############################################
        // clear static fields for next run
        // ############################################
        featureTypeIdMap.clear();
        sFeatureGroupExists.clear();
        mFeatureValueExists.clear();
        mProductFeatureCatGrpApplFromDateExists.clear();
        mProductFeatureCategoryApplFromDateExists.clear();
        mProductFeatureGroupApplFromDateExists.clear();
     // ############################################
        // call the service for remove entity data 
        // if removeAll and autoLoad parameter are true 
        // ############################################
        if (removeAll)
        {
            Map importRemoveEntityDataParams = UtilMisc.toMap();
            try {
            
                Map result = dispatcher.runSync("importRemoveEntityData", importRemoveEntityDataParams);
            
                List<String> serviceMsg = (List)result.get("messages");
                for (String msg: serviceMsg)
                {
                    messages.add(msg);
                }
            } 
            catch (Exception exc) 
            {
                Debug.logError(exc, module);
                autoLoad = Boolean.FALSE;
            }
        }

        // ##############################################
        // move the generated xml files in done directory
        // ##############################################
        File doneXmlDir = new File(baseDataDir, Constants.DONE_XML_DIRECTORY_PREFIX+UtilDateTime.nowDateString());
        File[] fileArray = baseDataDir.listFiles();
        for (File file: fileArray)
        {
            try 
            {
                if (FilenameUtils.getExtension(file.getName()).equalsIgnoreCase("XML"))
                {
                	if(!(file.getName().equals(tempDataFile)) && (!file.getName().equals(baseFilePath.getName()))){
                		FileUtils.copyFileToDirectory(file, doneXmlDir);
                        file.delete();
                	}
                }
            } 
            catch (IOException ioe) 
            {
                Debug.logError(ioe, module);
            } 
            catch (Exception exc) 
            {
                Debug.logError(exc, module);
            }
        }

        // ######################################################################
        // call service for insert row in database  from generated xml data files 
        // by calling service entityImportDir if autoLoad parameter is true
        // ######################################################################
        
	        if (autoLoad)
	        {
	            Map entityImportDirParams = UtilMisc.toMap("path", doneXmlDir.getPath(), 
	                                                     "userLogin", context.get("userLogin"));
	             Map result = dispatcher.runSync("entityImportDir", entityImportDirParams);
	             if(UtilValidate.isNotEmpty(result.get("responseMessage")) && result.get("responseMessage").equals("error"))
	             {
	                 return ServiceUtil.returnError(result.get("errorMessage").toString());
	             }
	             else
	             {
		             List<String> serviceMsg = (List)result.get("messages");
		             for (String msg: serviceMsg)
		             {
		                 messages.add(msg);
		             }
	             }
	        }
        } 
        catch (Exception exc) 
        {
            Debug.logError(exc, module);
        }
        finally
        {
        	inputWorkbook.delete();
        }
        	
        Map<String, Object> resp = UtilMisc.toMap("messages", (Object) messages);
        return resp;  

    }
      
    public static List buildProductCategoryXMLDataRows(List<CategoryType> productCategories) 
    {
		List dataRows = FastList.newInstance();

		try {
			
            for (int rowCount = 0 ; rowCount < productCategories.size() ; rowCount++) {
            	CategoryType productCategory = (CategoryType) productCategories.get(rowCount);
            
            	Map mRows = FastMap.newInstance();
                
                mRows.put("productCategoryId",productCategory.getCategoryId());
                mRows.put("parentCategoryId",productCategory.getParentCategoryId());
                mRows.put("categoryName",productCategory.getCategoryName());
                mRows.put("description",productCategory.getDescription());
                mRows.put("longDescription",productCategory.getLongDescription());
                mRows.put("plpText",productCategory.getAdditionalPlpText());
                mRows.put("pdpText",productCategory.getAdditionalPdpText());
                mRows.put("fromDate",productCategory.getFromDate());
                mRows.put("thruDate",productCategory.getThruDate());
                
                PlpImageType plpImage = productCategory.getPlpImage();
                if(UtilValidate.isNotEmpty(plpImage))
                {
                    mRows.put("plpImageName",plpImage.getUrl());
                }
                
                mRows = formatProductXLSData(mRows);
                dataRows.add(mRows);
             }
    	}
      	catch (Exception e) {
      		e.printStackTrace();
   	    }
      	return dataRows;
   }
    
    public static List buildProductAssociationXMLDataRows(List<AssociationType> productAssociations) {
		List dataRows = FastList.newInstance();

		try {
			
            for (int rowCount = 0 ; rowCount < productAssociations.size() ; rowCount++) {
            	AssociationType productAssociation = (AssociationType)productAssociations.get(rowCount);
            	Map mRows = FastMap.newInstance();
                
                mRows.put("productId",productAssociation.getMasterProductId());
                mRows.put("productIdTo",productAssociation.getMasterProductIdTo());
                mRows.put("productAssocType",productAssociation.getProductAssocType());
                mRows.put("fromDate",productAssociation.getFromDate());
                mRows.put("thruDate",productAssociation.getThruDate());

                mRows = formatProductXLSData(mRows);
                dataRows.add(mRows);
             }
    	}
      	catch (Exception e) {
      		e.printStackTrace();
   	    }
      	return dataRows;
   }
    
    public static List buildProductFacetGroupXMLDataRows(List<FacetCatGroupType> productFacetGroups) {
		List dataRows = FastList.newInstance();
		Map mFacetToolTipMap = FastMap.newInstance();
		
		try {
			
            for (int rowCount = 0 ; rowCount < productFacetGroups.size() ; rowCount++) 
            {
            	FacetCatGroupType productFacetGroup = (FacetCatGroupType)productFacetGroups.get(rowCount);
            	Map mRows = FastMap.newInstance();
            	
            	String productCategoryId = productFacetGroup.getProductCategoryId();
            	String tooltip = productFacetGroup.getTooltip();
            	FacetGroupType facetGroup = productFacetGroup.getFacetGroup();
            	String facetGroupId = "";
            	String facetGroupDescription = "";
            	if(UtilValidate.isNotEmpty(facetGroup))
            	{
            		facetGroupId = facetGroup.getFacetGroupId();
            		facetGroupDescription = facetGroup.getDescription();
            	}
            	if(UtilValidate.isNotEmpty(productCategoryId))
            	{
            		String sequenceNum = productFacetGroup.getSequenceNum();
                	String fromDate = productFacetGroup.getFromDate();
                	String thruDate = productFacetGroup.getThruDate();
                	String minDisplay = productFacetGroup.getMinDisplay();
                	String maxDisplay = productFacetGroup.getMaxDisplay();
                	if(UtilValidate.isNotEmpty(tooltip))
    				{
    					//use the tooltip that is set
    				}
    				else if(UtilValidate.isNotEmpty(mFacetToolTipMap.get(facetGroupId)))
    				{
    					tooltip = (String)mFacetToolTipMap.get(facetGroupId);
    				}
                	
                	mRows.put("productCategoryId",productCategoryId);
                	mRows.put("sequenceNum",sequenceNum);
                	mRows.put("fromDate",fromDate);
                	mRows.put("thruDate",thruDate);
                	mRows.put("minDisplay",minDisplay);
                	mRows.put("maxDisplay",maxDisplay);
                	mRows.put("tooltip",tooltip);
                	mRows.put("facetGroupId",facetGroupId);
                	mRows.put("description",facetGroupDescription);
                	
                    mRows = formatProductXLSData(mRows);
                    dataRows.add(mRows);
            	}
            	else
    			{
    				//if productCategoryId is empty, then apply tooltip to all categories will this facetgroup
    				mFacetToolTipMap.put(facetGroupId, tooltip);
    			}
             }
    	}
      	catch (Exception e) 
      	{
      		e.printStackTrace();
   	    }
   	    
      	return dataRows;
   }
    
    public static List buildProductFacetValueXMLDataRows(List<FacetValueType> productFacetValues) {
		List dataRows = FastList.newInstance();

		try {
			
            for (int rowCount = 0 ; rowCount < productFacetValues.size() ; rowCount++) 
            {
            	FacetValueType productFacetValue = (FacetValueType)productFacetValues.get(rowCount);
            	Map mRows = FastMap.newInstance();
            	
            	String productFeatureGroupId = productFacetValue.getProductFeatureGroupId();
            	String productFeatureId = productFacetValue.getProductFeatureId();
            	String description = productFacetValue.getDescription();
            	String fromDate = productFacetValue.getFromDate();
            	String thruDate = productFacetValue.getThruDate();
            	String sequenceNum = productFacetValue.getSequenceNum();
            	PlpSwatchType plpSwatch = productFacetValue.getPlpSwatch();
            	String plpSwatchImage = "";
            	if(UtilValidate.isNotEmpty(plpSwatch))
            	{
            		plpSwatchImage = plpSwatch.getUrl();
            	}
            	PdpSwatchType pdpSwatch = productFacetValue.getPdpSwatch();
            	String pdpSwatchImage = "";
            	if(UtilValidate.isNotEmpty(pdpSwatch))
            	{
            		pdpSwatchImage = pdpSwatch.getUrl();
            	}
            	
            	mRows.put("facetGroupId",productFeatureGroupId);
            	mRows.put("facetValueId",productFeatureId);
            	mRows.put("description",description);
            	mRows.put("fromDate",fromDate);
            	mRows.put("thruDate",thruDate);
            	mRows.put("sequenceNum",sequenceNum);
            	mRows.put("plpSwatchUrl",plpSwatchImage);
            	mRows.put("pdpSwatchUrl",pdpSwatchImage);
            	
                mRows = formatProductXLSData(mRows);
                dataRows.add(mRows);
                 
             }
    	}
      	catch (Exception e) 
      	{
      		e.printStackTrace();
   	    }

      	return dataRows;
   }
    
    public static List buildProductManufacturerXMLDataRows(List<ManufacturerType> productManufacturers) {
		List dataRows = FastList.newInstance();

		try {
			
            for (int rowCount = 0 ; rowCount < productManufacturers.size() ; rowCount++) {
            	ManufacturerType productManufacturer = (ManufacturerType)productManufacturers.get(rowCount);
            	Map mRows = FastMap.newInstance();
                
                mRows.put("partyId",productManufacturer.getManufacturerId());
                mRows.put("manufacturerName",productManufacturer.getManufacturerName());
                mRows.put("shortDescription",productManufacturer.getDescription());
                mRows.put("longDescription",productManufacturer.getLongDescription());
                
                ManufacturerAddressType manufacturerAddress = productManufacturer.getAddress();
                if(UtilValidate.isNotEmpty(manufacturerAddress)) 
                {
                	mRows.put("address1",manufacturerAddress.getAddress1());
                    mRows.put("city",manufacturerAddress.getCityTown());
                    mRows.put("state",manufacturerAddress.getStateProvince());
                    mRows.put("zip",manufacturerAddress.getZipPostCode());
                    mRows.put("country",manufacturerAddress.getCountry());
                }
                
                ManufacturerImageType manufacturerImage = productManufacturer.getManufacturerImage();
                if(UtilValidate.isNotEmpty(manufacturerImage)) 
                {
                	mRows.put("manufacturerImage",manufacturerImage.getUrl());
                	mRows.put("manufacturerImageThruDate",manufacturerImage.getThruDate());
                }
                                
                mRows = formatProductXLSData(mRows);
                dataRows.add(mRows);
             }
    	}
      	catch (Exception e) {
      		e.printStackTrace();
   	    }
      	return dataRows;
   }
    
    public static List buildProductXMLDataRows(List<ProductType> products) 
    {
		List dataRows = FastList.newInstance();

		try 
		{

			for (int rowCount = 0 ; rowCount < products.size() ; rowCount++) 
			{
            	ProductType product = (ProductType) products.get(rowCount);
            
            	Map mRows = FastMap.newInstance();
                
                mRows.put("masterProductId",product.getMasterProductId());
                mRows.put("productId",product.getProductId());
                mRows.put("internalName",product.getInternalName());
                mRows.put("productName",product.getProductName());
                mRows.put("salesPitch",product.getSalesPitch());
                mRows.put("longDescription",product.getLongDescription());
                mRows.put("specialInstructions",product.getSpecialInstructions());
                mRows.put("deliveryInfo",product.getDeliveryInfo());
                mRows.put("directions",product.getDirections());
                mRows.put("termsConditions",product.getTermsAndConds());
                mRows.put("ingredients",product.getIngredients());
                mRows.put("warnings",product.getWarnings());
                mRows.put("plpLabel",product.getPlpLabel());
                mRows.put("pdpLabel",product.getPdpLabel());
                mRows.put("productHeight",product.getProductHeight());
                mRows.put("productWidth",product.getProductWidth());
                mRows.put("productDepth",product.getProductDepth());
                mRows.put("returnable",product.getReturnable());
                mRows.put("taxable",product.getTaxable());
                mRows.put("chargeShipping",product.getChargeShipping());
                mRows.put("introDate",product.getIntroDate());
                mRows.put("discoDate",product.getDiscoDate());
                mRows.put("manufacturerId",product.getManufacturerId());
                
                ProductPriceType productPrice = product.getProductPrice();
                if(UtilValidate.isNotEmpty(productPrice)) 
                {
                	ListPriceType listPrice = productPrice.getListPrice();
                	if(UtilValidate.isNotEmpty(listPrice)) 
                	{
                        mRows.put("listPrice",listPrice.getPrice());
                        mRows.put("listPriceCurrency",listPrice.getCurrency());
                        mRows.put("listPriceFromDate",listPrice.getFromDate());
                        mRows.put("listPriceThruDate",listPrice.getThruDate());
                	}
                    
                    SalesPriceType salesPrice = productPrice.getSalesPrice();
                    if(UtilValidate.isNotEmpty(salesPrice)) 
                    {
                        mRows.put("defaultPrice",salesPrice.getPrice());
                        mRows.put("defaultPriceCurrency",salesPrice.getCurrency());
                        mRows.put("defaultPriceFromDate",salesPrice.getFromDate());
                        mRows.put("defaultPriceThruDate",salesPrice.getThruDate());
                    }
                }
                
                
                ProductCategoryMemberType productCategory = product.getProductCategoryMember();
                if(UtilValidate.isNotEmpty(productCategory)) 
                {
                	List<CategoryMemberType> categoryList = productCategory.getCategory();
                    
                    StringBuffer categoryId = new StringBuffer("");
                    if(UtilValidate.isNotEmpty(categoryList)) 
                    {
                    	
                    	for(int i = 0; i < categoryList.size(); i++) 
                    	{
                    		CategoryMemberType category = (CategoryMemberType)categoryList.get(i);
                    		if(!category.getCategoryId().equals("")) 
                    		{
                    		    categoryId.append(category.getCategoryId() + ",");
                    		    mRows.put(category.getCategoryId() + "_sequenceNum",category.getSequenceNum());
                    		    mRows.put(category.getCategoryId() + "_fromDate",category.getFromDate());
                    		    mRows.put(category.getCategoryId() + "_thruDate",category.getThruDate());
                    		}
                    	}
                    	if(categoryId.length() > 1) 
                    	{
                    	    categoryId.setLength(categoryId.length()-1);
                    	}
                    }
                    mRows.put("productCategoryId",categoryId.toString());
                    mRows.put("manufacturerId",product.getManufacturerId());
                }
                
                
                ProductSelectableFeatureType selectableFeature = product.getProductSelectableFeature();
                if(UtilValidate.isNotEmpty(selectableFeature)) 
                {
                	List<FeatureType> selectableFeatureList = selectableFeature.getFeature();
                    if(UtilValidate.isNotEmpty(selectableFeatureList)) 
                    {
                    	for(int i = 0; i < selectableFeatureList.size(); i++) 
                    	{
                    		String featureId = new String("");
                    		FeatureType feature = (FeatureType)selectableFeatureList.get(i);
                    		if(UtilValidate.isNotEmpty(feature.getFeatureId())) 
                    		{
                    		    StringBuffer featureValue = new StringBuffer("");
                    		    List featureValues = feature.getValue();
                    		    if(UtilValidate.isNotEmpty(featureValues)) 
                    		    {
                            	
                            	    for(int value = 0; value < featureValues.size(); value++) 
                            	    {
                            		    if(!featureValues.get(value).equals("")) 
                            		    {
                            		        featureValue.append(featureValues.get(value) + ",");
                            		    }
                            	    }
                            	    if(featureValue.length() > 1) 
                            	    {
                            	        featureValue.setLength(featureValue.length()-1);
                            	    }
                                }
                    		    if(featureValue.length() > 0) 
                    		    {
                    		        featureId = feature.getFeatureId() + ":" + featureValue.toString();
                    		        mRows.put(feature.getFeatureId() + "_sequenceNum",feature.getSequenceNum());
                    		        mRows.put(feature.getFeatureId() + "_fromDate",feature.getFromDate());
                        		    mRows.put(feature.getFeatureId() + "_thruDate",feature.getThruDate());
                        		    mRows.put(feature.getFeatureId() + "_description",feature.getDescription());
                    		    }
                    		}
                    		mRows.put("selectabeFeature_"+(i+1),featureId);
                    	}
                    	mRows.put("totSelectableFeatures",new Integer(selectableFeatureList.size()).toString());
                    }
                }
                else
                {
                	mRows.put("totSelectableFeatures",new Integer(0).toString());
                }
                
                
                ProductDescriptiveFeatureType descriptiveFeature = product.getProductDescriptiveFeature();
                if(UtilValidate.isNotEmpty(descriptiveFeature)) 
                {
                	List<FeatureType> descriptiveFeatureList = descriptiveFeature.getFeature();
                    if(UtilValidate.isNotEmpty(descriptiveFeatureList)) 
                    {
                    	for(int i = 0; i < descriptiveFeatureList.size(); i++) 
                    	{
                    		String featureId = new String("");
                    		FeatureType feature = (FeatureType)descriptiveFeatureList.get(i);
                    		if(UtilValidate.isNotEmpty(feature.getFeatureId())) 
                    		{
                    		    StringBuffer featureValue = new StringBuffer("");
                    		    List featureValues = feature.getValue();
                    		    if(UtilValidate.isNotEmpty(featureValues)) 
                    		    {
                            	
                            	    for(int value = 0; value < featureValues.size(); value++) 
                            	    {
                            		    if(!featureValues.get(value).equals("")) 
                            		    {
                            		        featureValue.append(featureValues.get(value) + ",");
                            		    }
                            	    }
                            	    if(featureValue.length() > 1) 
                            	    {
                            	        featureValue.setLength(featureValue.length()-1);
                            	    }
                                }
                    		    if(featureValue.length() > 0) 
                    		    {
                    		        featureId = feature.getFeatureId() + ":" + featureValue.toString();
                    		        mRows.put(feature.getFeatureId() + "_sequenceNum",feature.getSequenceNum());
                    		        mRows.put(feature.getFeatureId() + "_fromDate",feature.getFromDate());
                        		    mRows.put(feature.getFeatureId() + "_thruDate",feature.getThruDate());
                        		    mRows.put(feature.getFeatureId() + "_description",feature.getDescription());
                    		    }
                    		}
                    		mRows.put("descriptiveFeature_"+(i+1),featureId);
                    	}
                    	mRows.put("totDescriptiveFeatures",new Integer(descriptiveFeatureList.size()).toString());
                    }
                }
                else
                {
                	mRows.put("totDescriptiveFeatures",new Integer(0).toString());
                }
                
                ProductImageType productImage = product.getProductImage();
                if(UtilValidate.isNotEmpty(productImage)) 
                {
                	PlpSwatchType plpSwatch = productImage.getPlpSwatch();
                	if(UtilValidate.isNotEmpty(plpSwatch)) 
                	{
                		mRows.put("plpSwatchImage",plpSwatch.getUrl());
                		mRows.put("plpSwatchImageThruDate",plpSwatch.getThruDate());
                	}
                    
                    PdpSwatchType pdpSwatch = productImage.getPdpSwatch();
                    if(UtilValidate.isNotEmpty(pdpSwatch)) 
                    {
                        mRows.put("pdpSwatchImage",pdpSwatch.getUrl());
                        mRows.put("pdpSwatchImageThruDate",pdpSwatch.getThruDate());
                    }
                    
                    PlpSmallImageType plpSmallImage = productImage.getPlpSmallImage();
                    if(UtilValidate.isNotEmpty(plpSmallImage)) 
                    {
                    	mRows.put("smallImage",plpSmallImage.getUrl());
                    	mRows.put("smallImageThruDate",plpSmallImage.getThruDate());
                    }
                    
                    PlpSmallAltImageType plpSmallAltImage = productImage.getPlpSmallAltImage();
                    if(UtilValidate.isNotEmpty(plpSmallAltImage)) 
                    {
                    	mRows.put("smallImageAlt",plpSmallAltImage.getUrl());
                    	mRows.put("smallImageAltThruDate",plpSmallAltImage.getThruDate());
                    }
                    
                    PdpThumbnailImageType pdpThumbnailImage = productImage.getPdpThumbnailImage();
                    if(UtilValidate.isNotEmpty(pdpThumbnailImage)) 
                    {
                    	mRows.put("thumbImage",pdpThumbnailImage.getUrl());
                    	mRows.put("thumbImageThruDate",pdpThumbnailImage.getThruDate());
                    }
                    
                    PdpLargeImageType plpLargeImage = productImage.getPdpLargeImage();
                    if(UtilValidate.isNotEmpty(plpLargeImage)) 
                    {
                    	mRows.put("largeImage",plpLargeImage.getUrl());
                    	mRows.put("largeImageThruDate",plpLargeImage.getThruDate());
                    }
                    
                    PdpDetailImageType pdpDetailImage = productImage.getPdpDetailImage();
                    if(UtilValidate.isNotEmpty(pdpDetailImage)) 
                    {
                    	mRows.put("detailImage",pdpDetailImage.getUrl());
                    	mRows.put("detailImageThruDate",pdpDetailImage.getThruDate());
                    }
                    
                    PdpVideoType pdpVideo = productImage.getPdpVideoImage();
                    if(UtilValidate.isNotEmpty(pdpVideo)) 
                    {
                    	mRows.put("pdpVideoUrl",pdpVideo.getUrl());
                    	mRows.put("pdpVideoUrlThruDate",pdpVideo.getThruDate());
                    }
                    
                    PdpVideo360Type pdpVideo360 = productImage.getPdpVideo360Image();
                    if(UtilValidate.isNotEmpty(pdpVideo360)) 
                    {
                    	mRows.put("pdpVideo360Url",pdpVideo360.getUrl());
                    	mRows.put("pdpVideo360UrlThruDate",pdpVideo360.getThruDate());
                    }
                    
                    PdpAlternateImageType pdpAlternateImage = productImage.getPdpAlternateImage();
                    if(UtilValidate.isNotEmpty(pdpAlternateImage)) 
                    {
                    	List pdpAdditionalImages = pdpAlternateImage.getPdpAdditionalImage();
                        if(UtilValidate.isNotEmpty(pdpAdditionalImages)) 
                        { 
                        	int totPdpAdditionalThumbImage = 0;
                        	int totPdpAdditionalLargeImage = 0;
                        	int totPdpAdditionalDetailImage = 0;
                        	for(int i = 0; i < pdpAdditionalImages.size(); i++) 
                        	{
                        		PdpAdditionalImageType pdpAdditionalImage = (PdpAdditionalImageType) pdpAdditionalImages.get(i);
                        	    
                        		PdpAdditionalThumbImageType pdpAdditionalThumbImage = pdpAdditionalImage.getPdpAdditionalThumbImage();
                        		if(UtilValidate.isNotEmpty(pdpAdditionalThumbImage)) 
                        		{
                        			mRows.put("addImage"+(i+1),pdpAdditionalThumbImage.getUrl());
                        			mRows.put("addImage"+(i+1)+"ThruDate",pdpAdditionalThumbImage.getThruDate());
                        			totPdpAdditionalThumbImage = totPdpAdditionalThumbImage + 1;
                        		}
                        	    
                        	    PdpAdditionalLargeImageType pdpAdditionalLargeImage = pdpAdditionalImage.getPdpAdditionalLargeImage();
                        	    if(UtilValidate.isNotEmpty(pdpAdditionalLargeImage)) 
                        	    {
                        	    	mRows.put("xtraLargeImage"+(i+1),pdpAdditionalLargeImage.getUrl());
                        	    	mRows.put("xtraLargeImage"+(i+1)+"ThruDate",pdpAdditionalLargeImage.getThruDate());
                        	    	totPdpAdditionalLargeImage = totPdpAdditionalLargeImage + 1;
                        	    }
                        	    
                        	    PdpAdditionalDetailImageType pdpAdditionalDetailImage = pdpAdditionalImage.getPdpAdditionalDetailImage();
                        	    if(UtilValidate.isNotEmpty(pdpAdditionalDetailImage)) 
                        	    {
                        	    	mRows.put("xtraDetailImage"+(i+1),pdpAdditionalDetailImage.getUrl());
                        	    	mRows.put("xtraDetailImage"+(i+1)+"ThruDate",pdpAdditionalDetailImage.getThruDate());
                        	    	totPdpAdditionalDetailImage = totPdpAdditionalDetailImage + 1;
                        	    }
                        	}
                        	mRows.put("totPdpAdditionalThumbImage",new Integer(totPdpAdditionalThumbImage).toString());
                        	mRows.put("totPdpAdditionalLargeImage",new Integer(totPdpAdditionalLargeImage).toString());
                        	mRows.put("totPdpAdditionalDetailImage",new Integer(totPdpAdditionalDetailImage).toString());
                        }
                    }
                    
                }
                
                
                GoodIdentificationType goodIdentification = product.getProductGoodIdentification();
                if(UtilValidate.isNotEmpty(goodIdentification)) 
                {
                	mRows.put("goodIdentificationSkuId",goodIdentification.getSku());
                    mRows.put("goodIdentificationGoogleId",goodIdentification.getGoogleId());
                    mRows.put("goodIdentificationIsbnId",goodIdentification.getIsbn());
                    mRows.put("goodIdentificationManufacturerId",goodIdentification.getManuId());
                }
                
                
                ProductInventoryType productInventory = product.getProductInventory();
                if(UtilValidate.isNotEmpty(productInventory)) 
                {
                	mRows.put("bfInventoryTot",productInventory.getBigfishInventoryTotal());
                    mRows.put("bfInventoryWhs",productInventory.getBigfishInventoryWarehouse());
                }
                
                ProductAttributeType productAttribute = product.getProductAttribute();
                if(UtilValidate.isNotEmpty(productAttribute)) 
                {
                	mRows.put("multiVariant",productAttribute.getPdpSelectMultiVariant());
                	mRows.put("giftMessage",productAttribute.getPdpCheckoutGiftMessage());
                	mRows.put("pdpQtyMin",productAttribute.getPdpQtyMin());
                	mRows.put("pdpQtyMax",productAttribute.getPdpQtyMax());
                	mRows.put("pdpQtyDefault",productAttribute.getPdpQtyDefault());
                	mRows.put("pdpInStoreOnly",productAttribute.getPdpInStoreOnly());
                }
                
                mRows.put("weight",product.getProductWeight());
                mRows = formatProductXLSData(mRows);
                dataRows.add(mRows);
             }
    	}
      	catch (Exception e) 
      	{
      		e.printStackTrace();
   	    }
      	return dataRows;
   }
    
    
    public static List buildOrderStatusXMLDataRows(List<OrderStatusType> orderList) 
    {
		List dataRows = FastList.newInstance();
		try 
		{
            for (int rowCount = 0 ; rowCount < orderList.size() ; rowCount++) 
            {
            	OrderStatusType order = (OrderStatusType) orderList.get(rowCount);
            
            	Map mRows = FastMap.newInstance();
                
                mRows.put("orderId",order.getOrderId());
                mRows.put("productStoreId",order.getProductStoreId());
                mRows.put("orderStatus",order.getOrderStatus());
                mRows.put("orderShipDate",order.getOrderShipDate());
                mRows.put("orderShipCarrier",order.getOrderShipCarrier());
                mRows.put("orderShipMethod",order.getOrderShipMethod());
                mRows.put("orderTrackingNumber",order.getOrderTrackingNumber());
                mRows.put("orderNote",order.getOrderNote());
                
                List orderItems = order.getOrderItem();
                if(UtilValidate.isNotEmpty(orderItems)) 
                {
                	for(int i = 0; i < orderItems.size(); i++)
                	{
                		OrderItemType orderItem = (OrderItemType) orderItems.get(i);
                		mRows.put("productId_" + (i + 1),orderItem.getProductId());
                		mRows.put("shipGroupSeqId_" + (i + 1),orderItem.getShipGroupSequenceId());
                		mRows.put("orderItemSequenceId_" + (i + 1),orderItem.getSequenceId());
                        mRows.put("orderItemStatus_" + (i + 1),orderItem.getOrderItemStatus());
                        mRows.put("orderItemShipDate_" + (i + 1),orderItem.getOrderItemShipDate());
                        mRows.put("orderItemCarrier_" + (i + 1),orderItem.getOrderItemCarrier());
                        mRows.put("orderItemShipMethod_" + (i + 1),orderItem.getOrderItemShipMethod());
                        mRows.put("orderItemTrackingNumber_" + (i + 1),orderItem.getOrderItemTrackingNumber());
                	}
                }
                mRows.put("totalOrderItems",new Integer(orderItems.size()).toString());
                mRows = formatProductXLSData(mRows);
                dataRows.add(mRows);
             }
    	}
      	catch (Exception e) 
      	{
      		e.printStackTrace();
   	    }
      	return dataRows;
   }
    

    private static void buildOrderItemShipment(BufferedWriter bwOutFile, String orderId)
    {
         StringBuilder  rowString = new StringBuilder();
		 List<GenericValue> orderItems = FastList.newInstance();
 		 try
 		 {
		     orderItems = _delegator.findByAnd("OrderItem", UtilMisc.toMap("orderId", orderId));
 		 }
 		 catch (GenericEntityException e)
 		 {
		     e.printStackTrace();
	     }
 		 if(UtilValidate.isNotEmpty(orderItems)) 
 		 {
 			 for(GenericValue orderItem : orderItems) 
 			 {
 				 try
 				 {
			         List<GenericValue> orderItemShipGroupAssocList = _delegator.findByAnd("OrderItemShipGroupAssoc", UtilMisc.toMap("orderId", orderId, "orderItemSeqId", (String)orderItem.get("orderItemSeqId")), UtilMisc.toList("+orderItemSeqId"));
					 if(UtilValidate.isNotEmpty(orderItemShipGroupAssocList)) 
					 {
			            for(GenericValue orderItemShipGroupAssoc : orderItemShipGroupAssocList) 
			            {
			                //buildOrderItemShipment(bwOutFile, orderItem, orderItemShipGroupAssoc.getString("shipGroupSeqId"));
			            }
					 }
	 	    	 }
	 	      	 catch (Exception e) 
	 	      	 {
	 	      		e.printStackTrace();
	 	   	     }
 			 }
 		 }
    }

    private static void buildOrderItemShipment(BufferedWriter bwOutFile, GenericValue orderItemShip, String shipGroupSeqid, String orderId)
    {
         StringBuilder  rowString = new StringBuilder();
 		 try
			 {
				 //Create Shipment
				 //Create Shipment Package
				 
				 //Create Shipment Item
				 //Create Order Shipment
				 //Create Shipment Package Content
				 //Create Item Issuance
				 List<GenericValue> orderItemShipGroupAssocList = FastList.newInstance();
				 if(UtilValidate.isEmpty(orderItemShip))
				 {
					 orderItemShipGroupAssocList = _delegator.findByAnd("OrderItemShipGroupAssoc", UtilMisc.toMap("orderId", orderId), UtilMisc.toList("+orderItemSeqId"));
					 shipGroupSeqid = EntityUtil.getFirst(orderItemShipGroupAssocList).getString("shipGroupSeqId");
				 }
				 else
				 {
					 orderItemShipGroupAssocList = _delegator.findByAnd("OrderItemShipGroupAssoc", UtilMisc.toMap("orderId", orderId, "orderItemSeqId", (String)orderItemShip.get("orderItemSeqId")), UtilMisc.toList("+orderItemSeqId"));
				 }
				 
				 List<GenericValue> shipments = _delegator.findByAnd("Shipment", UtilMisc.toMap("primaryOrderId", orderId, "primaryShipGroupSeqId", shipGroupSeqid));
				 String shipmentId = "";
				 if(UtilValidate.isNotEmpty(shipments))
				 {
					 shipmentId = EntityUtil.getFirst(shipments).getString("shipmentId");
				 }
				 else
				 {
					 shipmentId = _delegator.getNextSeqId("Shipment"); 
				 }
				 
                 rowString.setLength(0);
       		     rowString.append("<" + "Shipment" + " ");
	             rowString.append("shipmentId" + "=\"" + shipmentId + "\" ");
	             rowString.append("shipmentTypeId" + "=\"" + "SALES_SHIPMENT" + "\" ");
	             rowString.append("statusId" + "=\"" + "SHIPMENT_SHIPPED" + "\" ");
	             rowString.append("primaryOrderId" + "=\"" + orderId + "\" ");
	             rowString.append("primaryShipGroupSeqId" + "=\"" + shipGroupSeqid + "\" ");
	             rowString.append("/>");
	             bwOutFile.write(rowString.toString());
                 bwOutFile.newLine();
                 
                 List<GenericValue> shipmentPackages = _delegator.findByAnd("ShipmentPackage", UtilMisc.toMap("shipmentId", shipmentId));
				 
                 String shipmentPackageSeqId = "";
				 if(UtilValidate.isNotEmpty(shipmentPackages))
				 {
					 shipmentPackageSeqId = EntityUtil.getFirst(shipmentPackages).getString("shipmentPackageSeqId");
				 }
				 else
				 {
					 shipmentPackageSeqId = _delegator.getNextSeqId("ShipmentPackage"); 
				 }
				 
				 rowString.setLength(0);
       		     rowString.append("<" + "ShipmentPackage" + " ");
	             rowString.append("shipmentId" + "=\"" + shipmentId + "\" ");
	             rowString.append("shipmentPackageSeqId" + "=\"" + shipmentPackageSeqId + "\" ");
	             rowString.append("dateCreated" + "=\"" + _sdf.format(UtilDateTime.nowTimestamp()) + "\" ");
	             rowString.append("/>");
	             bwOutFile.write(rowString.toString());
                 bwOutFile.newLine();
				 
				 if(UtilValidate.isNotEmpty(orderItemShipGroupAssocList)) 
				 {
					 for(GenericValue orderItemShipGroupAssoc : orderItemShipGroupAssocList)
					 {
						 GenericValue orderItem = orderItemShipGroupAssoc.getRelatedOne("OrderItem");
						 
						 rowString.setLength(0);
		       		     rowString.append("<" + "ShipmentItem" + " ");
			             rowString.append("shipmentId" + "=\"" + shipmentId + "\" ");
			             rowString.append("shipmentItemSeqId" + "=\"" + orderItem.getString("orderItemSeqId") + "\" ");
			             rowString.append("productId" + "=\"" + orderItem.getString("productId") + "\" ");
			             rowString.append("quantity" + "=\"" + orderItem.getString("quantity") + "\" ");
			             rowString.append("/>");
			             bwOutFile.write(rowString.toString());
		                 bwOutFile.newLine();
						 
		                 String itemIssuanceId=_delegator.getNextSeqId("ItemIssuance");
		                 rowString.setLength(0);
		       		     rowString.append("<" + "ItemIssuance" + " ");
			             rowString.append("itemIssuanceId" + "=\"" + itemIssuanceId + "\" ");
			             rowString.append("orderId" + "=\"" + orderItem.getString("orderId") + "\" ");
			             rowString.append("orderItemSeqId" + "=\"" + orderItem.getString("orderItemSeqId") + "\" ");
			             rowString.append("shipGroupSeqId" + "=\"" + shipGroupSeqid + "\" ");
			             rowString.append("shipmentId" + "=\"" + shipmentId + "\" ");
			             rowString.append("shipmentItemSeqId" + "=\"" + orderItem.getString("orderItemSeqId") + "\" ");
			             rowString.append("quantity" + "=\"" + orderItem.getString("quantity") + "\" ");
			             rowString.append("/>");
			             bwOutFile.write(rowString.toString());
		                 bwOutFile.newLine();
		                 
						 List<GenericValue> orderShipmentList = _delegator.findByAnd("OrderShipment", UtilMisc.toMap("orderId", orderId, "orderItemSeqId", orderItemShipGroupAssoc.getString("orderItemSeqId"), "shipGroupSeqId", orderItemShipGroupAssoc.getString("shipGroupSeqId"), "shipmentId", shipmentId), UtilMisc.toList("+orderItemSeqId")); 
		       	         if(UtilValidate.isEmpty(orderShipmentList)) 
		     		     {
			                 rowString.setLength(0);
			       		     rowString.append("<" + "OrderShipment" + " ");
				             rowString.append("orderId" + "=\"" + orderItem.getString("orderId") + "\" ");
				             rowString.append("orderItemSeqId" + "=\"" + orderItem.getString("orderItemSeqId") + "\" ");
				             rowString.append("shipGroupSeqId" + "=\"" + shipGroupSeqid + "\" ");
				             rowString.append("shipmentId" + "=\"" + shipmentId + "\" ");
				             rowString.append("shipmentItemSeqId" + "=\"" + orderItem.getString("orderItemSeqId") + "\" ");
				             rowString.append("quantity" + "=\"" + orderItem.getString("quantity") + "\" ");
				             rowString.append("/>");
				             bwOutFile.write(rowString.toString());
			                 bwOutFile.newLine();
		     		     }
		       	         
		       	         rowString.setLength(0);
		       		     rowString.append("<" + "ShipmentPackageContent" + " ");
			             rowString.append("shipmentId" + "=\"" + shipmentId + "\" ");
			             rowString.append("shipmentPackageSeqId" + "=\"" + shipmentPackageSeqId + "\" ");
			             rowString.append("shipmentItemSeqId" + "=\"" + orderItem.getString("orderItemSeqId") + "\" ");
			             rowString.append("quantity" + "=\"" + orderItem.getString("quantity") + "\" ");
			             rowString.append("subProductId" + "=\"" + orderItem.getString("productId") + "\" ");
			             rowString.append("/>");
			             bwOutFile.write(rowString.toString());
		                 bwOutFile.newLine();
					 }
				 }
 	    	 }
 	      	 catch (Exception e)
 	      	 {
 	      		e.printStackTrace();
 	   	     }
 		 
    }

    
    private static String getOrderStatus(String OrderId, Map mRow) 
    {
    	Map xmlOrderItems = FastMap.newInstance();
    	for(int orderItemNo = 0; orderItemNo < Integer.parseInt((String)mRow.get("totalOrderItems")); orderItemNo++)
    	{
    		if(UtilValidate.isEmpty(mRow.get("orderItemSequenceId_" + (orderItemNo + 1)))) 
    		{
    		    try 
    		    {
					List<GenericValue> orderItems = _delegator.findByAnd("OrderItem", UtilMisc.toMap("orderId", OrderId, "productId", mRow.get("productId_" + (orderItemNo + 1))));
					if(UtilValidate.isNotEmpty(orderItems)) 
					{
						for(GenericValue orderItem : orderItems) 
						{
							xmlOrderItems.put((String)orderItem.getString("orderItemSeqId"), "ITEM_"+(String)mRow.get("orderItemStatus_" + (orderItemNo + 1)));
						}
					}
				} 
    		    catch (GenericEntityException e) 
    		    {
					e.printStackTrace();
				}	
    		} 
    		else 
    		{
    			xmlOrderItems.put((String)mRow.get("orderItemSequenceId_" + (orderItemNo + 1)), "ITEM_"+(String)mRow.get("orderItemStatus_" + (orderItemNo + 1)));
    		}
    	}
    	List<GenericValue> orderItems = null;
		try 
		{
			orderItems = _delegator.findByAnd("OrderItem", UtilMisc.toMap("orderId", (String)mRow.get("orderId")));
		} 
		catch (GenericEntityException e) 
		{
			e.printStackTrace();
		}
		List totalOrderItemStatus = FastList.newInstance();
   	    if(UtilValidate.isNotEmpty(orderItems)) 
   	    {
   		     for(GenericValue orderItem : orderItems) 
   		     {
   			     if(UtilValidate.isNotEmpty(xmlOrderItems.get(orderItem.getString("orderItemSeqId"))))
   			     {
   			    	totalOrderItemStatus.add(xmlOrderItems.get(orderItem.getString("orderItemSeqId")));
   			     }
   			     else
   			     {
   			    	totalOrderItemStatus.add(orderItem.getString("statusId"));
   			     }
   		     }  
   	    }
   	    if(totalOrderItemStatus.contains("ITEM_APPROVED")) 
   	    {
   	    	return "ORDER_APPROVED";
   	    }
   	    else if(totalOrderItemStatus.contains("ITEM_COMPLETED")) 
   	    {
   	    	return "ORDER_COMPLETED";
   	    }
   	    else if(new HashSet(totalOrderItemStatus).size() == 1) 
   	    {
   	    	if(totalOrderItemStatus.get(0).equals("ITEM_APPROVED")) 
   	    	{
   	    		return "ORDER_APPROVED";
   	    	}
   	    	if(totalOrderItemStatus.get(0).equals("ITEM_COMPLETED")) 
   	    	{
   	    		return "ORDER_COMPLETED";
   	    	}
   	    	if(totalOrderItemStatus.get(0).equals("ITEM_CANCELLED")) 
   	    	{
   	    		return "ORDER_CANCELLED";
   	    	}
   	    }
    	return "";
    }
    
    public static Map<String, Object> importStoreXML(DispatchContext ctx, Map<String, ?> context) 
    {
        LocalDispatcher dispatcher = ctx.getDispatcher();
        _delegator = ctx.getDelegator();
        List<String> messages = FastList.newInstance();

        String xmlDataFilePath = (String)context.get("xmlDataFile");
        String xmlDataDirPath = (String)context.get("xmlDataDir");
        Boolean autoLoad = (Boolean) context.get("autoLoad");
        List<String> processedStoreCodeList = (List)context.get("processedStoreCodeList");

        if (autoLoad == null) autoLoad = Boolean.FALSE;

        File inputWorkbook = null;
        File baseDataDir = null;
        String tempDataFile = null;
        File baseFilePath = null;
        
        BufferedWriter fOutProduct=null;
        if (UtilValidate.isNotEmpty(xmlDataFilePath) && UtilValidate.isNotEmpty(xmlDataDirPath)) 
        {
            try 
            {
            	baseFilePath = new File(xmlDataFilePath);
                URL xlsDataFileUrl = UtilURL.fromFilename(xmlDataFilePath);
                InputStream ins = xlsDataFileUrl.openStream();

                if (ins != null && (xmlDataFilePath.toUpperCase().endsWith("XML"))) 
                {
                    baseDataDir = new File(xmlDataDirPath);
                    if (baseDataDir.isDirectory() && baseDataDir.canWrite()) 
                    {

                        // ############################################
                        // move the existing xml files in dump directory
                        // ############################################
                        File dumpXmlDir = null;
                        File[] fileArray = baseDataDir.listFiles();
                        for (File file: fileArray) 
                        {
                            try 
                            {
                                if (file.getName().toUpperCase().endsWith("XML")) 
                                {
                                    if (dumpXmlDir == null) 
                                    {
                                        dumpXmlDir = new File(baseDataDir, "dumpxml_"+UtilDateTime.nowDateString());
                                    }
                                    FileUtils.copyFileToDirectory(file, dumpXmlDir);
                                    file.delete();
                                }
                            } 
                            catch (IOException ioe) 
                            {
                                Debug.logError(ioe, module);
                            } 
                            catch (Exception exc) 
                            {
                                Debug.logError(exc, module);
                            }
                        }
                        // ######################################
                        //save the temp xls data file on server 
                        // ######################################
                        try 
                        {
                        	tempDataFile = UtilDateTime.nowAsString()+"."+FilenameUtils.getExtension(xmlDataFilePath);
                            inputWorkbook = new File(baseDataDir,  tempDataFile);
                            if (inputWorkbook.createNewFile()) 
                            {
                                Streams.copy(ins, new FileOutputStream(inputWorkbook), true, new byte[1]); 
                            }
                        } 
                        catch (IOException ioe) 
                        {
                                Debug.logError(ioe, module);
                        } 
                        catch (Exception exc) 
                        {
                                Debug.logError(exc, module);
                        }
                    }
                    else 
                    {
                        messages.add("xml data dir path not found or can't be write");
                    }
                }
                else 
                {
                    messages.add(" path specified for Excel sheet file is wrong , doing nothing.");
                }

            } 
            catch (IOException ioe) 
            {
                Debug.logError(ioe, module);
            } 
            catch (Exception exc) 
            {
                Debug.logError(exc, module);
            }
        }
        else 
        {
            messages.add("No path specified for Excel sheet file or xml data direcotry, doing nothing.");
        }

        // ######################################
        //read the temp xls file and generate xml 
        // ######################################
        try 
        {
        if (inputWorkbook != null && baseDataDir  != null) 
        {
        	try 
        	{
        		JAXBContext jaxbContext = JAXBContext.newInstance("com.osafe.feeds.osafefeeds");
            	Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            	JAXBElement<BigFishStoreFeedType> bfStoreFeedType = (JAXBElement<BigFishStoreFeedType>)unmarshaller.unmarshal(inputWorkbook);
            	if(UtilValidate.isNotEmpty(bfStoreFeedType)) 
            	{
            		List<StoreType> storeList = bfStoreFeedType.getValue().getStore();
            		List dataRows = buildStoreXMLDataRows(storeList);
                	buildStore(dataRows, xmlDataDirPath, processedStoreCodeList);
            	}
            	
        	} 
        	catch (Exception e) 
        	{
        		Debug.logError(e, module);
			}
        	finally 
        	{
                try 
                {
                    if (fOutProduct != null) 
                    {
                    	fOutProduct.close();
                    }
                } 
                catch (IOException ioe) 
                {
                    Debug.logError(ioe, module);
                }
            }
        }
        
     

        // ##############################################
        // move the generated xml files in done directory
        // ##############################################
        File doneXmlDir = new File(baseDataDir, Constants.DONE_XML_DIRECTORY_PREFIX+UtilDateTime.nowDateString());
        File[] fileArray = baseDataDir.listFiles();
        for (File file: fileArray) 
        {
            try 
            {
                if (FilenameUtils.getExtension(file.getName()).equalsIgnoreCase("XML")) 
                {
                	if(!(file.getName().equals(tempDataFile)) && (!file.getName().equals(baseFilePath.getName())))
                	{
                		FileUtils.copyFileToDirectory(file, doneXmlDir);
                        file.delete();
                	}
                }
            } 
            catch (IOException ioe) 
            {
                Debug.logError(ioe, module);
            } 
            catch (Exception exc) 
            {
                Debug.logError(exc, module);
            }
        }
        
        // ######################################################################
        // call service for insert row in database  from generated xml data files 
        // by calling service entityImportDir if autoLoad parameter is true
        // ######################################################################
        if (autoLoad) 
        {
            Map entityImportDirParams = UtilMisc.toMap("path", doneXmlDir.getPath(), 
                                                     "userLogin", context.get("userLogin"));
             try 
             {
                 Map result = dispatcher.runSync("entityImportDir", entityImportDirParams);
             
                 List<String> serviceMsg = (List)result.get("messages");
                 for (String msg: serviceMsg) 
                 {
                     messages.add(msg);
                 }
             } 
             catch (Exception exc) 
             {
                 Debug.logError(exc, module);
             }
        }
    } 
    catch (Exception exc) 
    {
        Debug.logError(exc, module);
    }
    finally 
    {
        inputWorkbook.delete();
    } 
        	
    Map<String, Object> resp = UtilMisc.toMap("messages", (Object) messages);
    return resp;  

    }
    
    public static List buildStoreXMLDataRows(List<StoreType> storeList) 
    {
		List dataRows = FastList.newInstance();

		try 
		{
			
            for (int rowCount = 0 ; rowCount < storeList.size() ; rowCount++) 
            {
            	StoreType store = storeList.get(rowCount);
            
            	Map mRows = FastMap.newInstance();
                
                mRows.put("productStoreId",store.getProductStoreId());
                mRows.put("storeId",store.getStoreId());
                mRows.put("storeCode",store.getStoreCode());
                mRows.put("storeName",store.getStoreName());
                
                StoreAddressType storesAddress = store.getStoreAddress();
                if(UtilValidate.isNotEmpty(storesAddress)) {
                	mRows.put("country",storesAddress.getCountry());
                    mRows.put("address1",storesAddress.getAddress1());
                    mRows.put("address2",storesAddress.getAddress2());
                    mRows.put("address3",storesAddress.getAddress3());
                    mRows.put("city",storesAddress.getCityTown());
                    mRows.put("state",storesAddress.getStateProvince());
                    mRows.put("zip",storesAddress.getZipPostCode());
                    mRows.put("phone",storesAddress.getStorePhone());
                }
                
                mRows.put("openingHours",store.getOpeningHours());
                mRows.put("storeNotice",store.getStoreNotice());
                mRows.put("storeContentSpot",store.getStoreContentSpot());
                mRows.put("status",store.getStatus());
                mRows.put("geoCodeLat",store.getGeoCodeLat());
                mRows.put("geoCodeLong",store.getGeoCodeLong());
                
                mRows = formatProductXLSData(mRows);
                dataRows.add(mRows);
             }
    	}
      	catch (Exception e) {
      		e.printStackTrace();
   	    }
      	return dataRows;
   }
    
    private static void buildStore(List dataRows,String xmlDataDirPath, List processedStoreCodeList) 
    {
        File fOutFile =null;
        BufferedWriter bwOutFile=null;
        
		try 
		{
	        fOutFile = new File(xmlDataDirPath, "000-StoreLocation.xml");
            if (fOutFile.createNewFile()) 
            {
            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));

                writeXmlHeader(bwOutFile);
                
                for (int i=0 ; i < dataRows.size() ; i++) 
                {
                     StringBuilder  rowString = new StringBuilder();
	            	 Map mRow = (Map)dataRows.get(i);
	            	 String storeCode = (String)mRow.get("storeCode");
	            	 if(processedStoreCodeList.contains(storeCode))
	            	 {
		            	 String partyId = null;
		            	 if(UtilValidate.isNotEmpty(mRow.get("storeId"))) 
		            	 {
		            		 partyId = (String)mRow.get("storeId");
		            	 } 
		            	 else 
		            	 {
		            		 partyId = _delegator.getNextSeqId("Party");
		            	 }
	                     rowString.append("<" + "Party" + " ");
	                     rowString.append("partyId" + "=\"" + partyId + "\" ");
	                     rowString.append("partyTypeId" + "=\"" + "PARTY_GROUP" + "\" ");
	                     if(((String)mRow.get("status")).equalsIgnoreCase("open")) 
	                     {
	                         rowString.append("statusId" + "=\"" + "PARTY_ENABLED" + "\" ");
	                     }
	                     else if(((String)mRow.get("status")).equalsIgnoreCase("closed")) 
	                     {
	                    	 rowString.append("statusId" + "=\"" + "PARTY_DISABLED" + "\" ");
	                     }
	                     rowString.append("/>");
	                     bwOutFile.write(rowString.toString());
	                     bwOutFile.newLine();
	                     
	                     rowString.setLength(0);
	                     rowString.append("<" + "PartyRole" + " ");
	                     rowString.append("partyId" + "=\"" + partyId + "\" ");
	                     rowString.append("roleTypeId" + "=\"" + "STORE_LOCATION" + "\" ");
	                     rowString.append("/>");
	                     bwOutFile.write(rowString.toString());
	                     bwOutFile.newLine();

	                     List<GenericValue> productStoreRoles = _delegator.findByAnd("ProductStoreRole", UtilMisc.toMap("partyId",partyId,"roleTypeId","STORE_LOCATION","productStoreId",mRow.get("productStoreId")),UtilMisc.toList("-fromDate"));
	 	                 if(UtilValidate.isNotEmpty(productStoreRoles)) 
	 	                 {
	 	                	productStoreRoles = EntityUtil.filterByDate(productStoreRoles);
	 	                 }
	 	                 if(UtilValidate.isEmpty(productStoreRoles))
		                 {
		                     rowString.setLength(0);
		                     rowString.append("<" + "ProductStoreRole" + " ");
		                     rowString.append("partyId" + "=\"" + partyId + "\" ");
		                     rowString.append("roleTypeId" + "=\"" + "STORE_LOCATION" + "\" ");
		                     rowString.append("productStoreId" + "=\"" + mRow.get("productStoreId") + "\" ");
		                     rowString.append("fromDate" + "=\"" + _sdf.format(UtilDateTime.nowTimestamp()) + "\" ");
		                     rowString.append("/>");
		                     bwOutFile.write(rowString.toString());
		                     bwOutFile.newLine();
		                 }
	                     
	                     rowString.setLength(0);
	                     rowString.append("<" + "PartyGroup" + " ");
	                     rowString.append("partyId" + "=\"" + partyId + "\" ");
	                     rowString.append("groupName" + "=\"" + (String)mRow.get("storeName") + "\" ");
	                     rowString.append("groupNameLocal" + "=\"" + (String)mRow.get("storeCode") + "\" ");
	                     rowString.append("/>");
	                     bwOutFile.write(rowString.toString());
	                     bwOutFile.newLine();
	                     
	         			 String contactMechId=_delegator.getNextSeqId("ContactMech");
	                     rowString.setLength(0);
	                     rowString.append("<" + "ContactMech" + " ");
	                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
	                     rowString.append("contactMechTypeId" + "=\"" + "POSTAL_ADDRESS" + "\" ");
	                     rowString.append("/>");
	                     bwOutFile.write(rowString.toString());
	                     bwOutFile.newLine();

	                     rowString.setLength(0);
	                     rowString.append("<" + "PartyContactMech" + " ");
	                     rowString.append("partyId" + "=\"" + partyId + "\" ");
	                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
	                     rowString.append("fromDate" + "=\"" +  _sdf.format(UtilDateTime.nowTimestamp()) + "\" ");
	                     rowString.append("/>");
	                     bwOutFile.write(rowString.toString());
	                     bwOutFile.newLine();
	                     
	                     rowString.setLength(0);
	                     rowString.append("<" + "PartyContactMechPurpose" + " ");
	                     rowString.append("partyId" + "=\"" + partyId + "\" ");
	                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
	                     rowString.append("contactMechPurposeTypeId" + "=\"" + "GENERAL_LOCATION" + "\" ");
	                     rowString.append("fromDate" + "=\"" +  _sdf.format(UtilDateTime.nowTimestamp()) + "\" ");
	                     rowString.append("/>");
	                     bwOutFile.write(rowString.toString());
	                     bwOutFile.newLine();
	                     
	                     rowString.setLength(0);
	                     rowString.append("<" + "PostalAddress" + " ");
	                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
	                     if(mRow.get("address1") != null) 
	                     {
	                         rowString.append("address1" + "=\"" +  (String)mRow.get("address1") + "\" ");
	                     }
	                     if(mRow.get("address2") != null) 
	                     {
	                    	 rowString.append("address2" + "=\"" +  (String)mRow.get("address2") + "\" "); 
	                     }
	                     if(mRow.get("address3") != null) 
	                     {
	                    	 rowString.append("address3" + "=\"" +  (String)mRow.get("address3") + "\" ");
	                     }
	                     if(mRow.get("city") != null) 
	                     {
	                    	 rowString.append("city" + "=\"" +  (String)mRow.get("city") + "\" ");
	                     }
	                     if(mRow.get("state") != null) 
	                     {
	                    	 rowString.append("stateProvinceGeoId" + "=\"" +  mRow.get("state") + "\" ");
	                     }
	                     if(mRow.get("country") != null) 
	                     {
	                    	 rowString.append("countryGeoId" + "=\"" +  mRow.get("country") + "\" ");
	                     }
	                     if(mRow.get("zip") != null) 
	                     {
	                    	 rowString.append("postalCode" + "=\"" +  mRow.get("zip") + "\" ");
	                     }
	                     
	                     rowString.append("/>");
	                     bwOutFile.write(rowString.toString());
	                     bwOutFile.newLine();
	                     
	                     contactMechId=_delegator.getNextSeqId("ContactMech");
	                     rowString.setLength(0);
	                     rowString.append("<" + "ContactMech" + " ");
	                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
	                     rowString.append("contactMechTypeId" + "=\"" + "TELECOM_NUMBER" + "\" ");
	                     rowString.append("/>");
	                     bwOutFile.write(rowString.toString());
	                     bwOutFile.newLine();

	                     rowString.setLength(0);
	                     rowString.append("<" + "PartyContactMech" + " ");
	                     rowString.append("partyId" + "=\"" + partyId + "\" ");
	                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
	                     rowString.append("fromDate" + "=\"" +  _sdf.format(UtilDateTime.nowTimestamp()) + "\" ");
	                     rowString.append("/>");
	                     bwOutFile.write(rowString.toString());
	                     bwOutFile.newLine();
	                     
	                     rowString.setLength(0);
	                     rowString.append("<" + "PartyContactMechPurpose" + " ");
	                     rowString.append("partyId" + "=\"" + partyId + "\" ");
	                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
	                     rowString.append("contactMechPurposeTypeId" + "=\"" + "PRIMARY_PHONE" + "\" ");
	                     rowString.append("fromDate" + "=\"" +  _sdf.format(UtilDateTime.nowTimestamp()) + "\" ");
	                     rowString.append("/>");
	                     bwOutFile.write(rowString.toString());
	                     bwOutFile.newLine();
	                     
	                     rowString.setLength(0);
	                     rowString.append("<" + "TelecomNumber" + " ");
	                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
	                     rowString.append("contactNumber" + "=\"" +  (String)mRow.get("phone") + "\" ");
	                     rowString.append("/>");
	                     bwOutFile.write(rowString.toString());
	                     bwOutFile.newLine();
	                     
	                     addPartyStoreContentRow(rowString, mRow, bwOutFile, partyId, "text", "STORE_HOURS", "openingHours");
	                     addPartyStoreContentRow(rowString, mRow, bwOutFile, partyId, "text", "STORE_NOTICE", "storeNotice");
	                     addPartyStoreContentRow(rowString, mRow, bwOutFile, partyId, "text", "STORE_CONTENT_SPOT", "storeContentSpot");
	 	            	
	                     if(mRow.get("geoCodeLat") != "" || mRow.get("geoCodeLong") != "") 
	                     {
	                         String geoPointId = _delegator.getNextSeqId("GeoPoint");
	                         rowString.setLength(0);
	                         rowString.append("<" + "GeoPoint" + " ");
	                         rowString.append("geoPointId" + "=\"" + geoPointId + "\" ");
	                         rowString.append("dataSourceId" + "=\"" + "GEOPT_GOOGLE" + "\" ");
	                         rowString.append("latitude" + "=\"" + (String)mRow.get("geoCodeLat") + "\" ");
	                         rowString.append("longitude" + "=\"" + (String)mRow.get("geoCodeLong") + "\" ");
	                         rowString.append("/>");
	                         bwOutFile.write(rowString.toString());
	                         bwOutFile.newLine();
	                     
	                         rowString.setLength(0);
	                         rowString.append("<" + "PartyGeoPoint" + " ");
	                         rowString.append("partyId" + "=\"" + partyId + "\" ");
	                         rowString.append("geoPointId" + "=\"" + geoPointId + "\" ");
	                         rowString.append("fromDate" + "=\"" +  _sdf.format(UtilDateTime.nowTimestamp()) + "\" ");
	                         rowString.append("/>");
	                         bwOutFile.write(rowString.toString());
	                         bwOutFile.newLine();
	                     }
	            	 }
	            }
                bwOutFile.flush();
         	    writeXmlFooter(bwOutFile);
            }
    	}
      	catch (Exception e) 
      	{
   	    }
        finally 
        {
            try 
            {
                if (bwOutFile != null) 
                {
               	 bwOutFile.close();
                }
            } 
            catch (IOException ioe) 
            {
                Debug.logError(ioe, module);
            }
         }
    }
    
    private static void addPartyStoreContentRow(StringBuilder rowString,Map mRow,BufferedWriter bwOutFile, String partyId, String contentType,String partyContentType,String colName) {

		String contentId=null;
		String dataResourceId=null;
    	try {
    		
			String contentValue=(String)mRow.get(colName);
			if (UtilValidate.isEmpty(contentValue) && UtilValidate.isEmpty(contentValue.trim()))
			{
				return;
			}
	        List<GenericValue> lPartyContent = _delegator.findByAnd("PartyContent", UtilMisc.toMap("partyId",partyId,"partyContentTypeId",partyContentType),UtilMisc.toList("-fromDate"));
			if (UtilValidate.isNotEmpty(lPartyContent))
			{
				GenericValue partyContent = EntityUtil.getFirst(lPartyContent);
				GenericValue content=partyContent.getRelatedOne("Content");
				contentId=content.getString("contentId");
				dataResourceId=content.getString("dataResourceId");
			}
			else
			{
				contentId=_delegator.getNextSeqId("Content");
				dataResourceId=_delegator.getNextSeqId("DataResource");
				
			}
			contentId=_delegator.getNextSeqId("Content");
			dataResourceId=_delegator.getNextSeqId("DataResource");
    		

			if ("text".equals(contentType))
			{
	            rowString.setLength(0);
	            rowString.append("<" + "DataResource" + " ");
	            rowString.append("dataResourceId" + "=\"" + dataResourceId + "\" ");
	            rowString.append("dataResourceTypeId" + "=\"" + "ELECTRONIC_TEXT" + "\" ");
	            rowString.append("dataTemplateTypeId" + "=\"" + "FTL" + "\" ");
	            rowString.append("statusId" + "=\"" + "CTNT_PUBLISHED" + "\" ");
	            rowString.append("dataResourceName" + "=\"" + colName + "\" ");
	            if(UtilValidate.isNotEmpty(localeString))
	            {
	            	rowString.append("localeString" + "=\"" + localeString + "\" ");
	            }
	            rowString.append("mimeTypeId" + "=\"" + "application/octet-stream" + "\" ");
	            rowString.append("objectInfo" + "=\"" + "" + "\" ");
	            rowString.append("isPublic" + "=\"" + "Y" + "\" ");
	            rowString.append("/>");
	            bwOutFile.write(rowString.toString());
	            bwOutFile.newLine();

	            rowString.setLength(0);
	            rowString.append("<" + "ElectronicText" + " ");
	            rowString.append("dataResourceId" + "=\"" + dataResourceId + "\" ");
	            rowString.append("textData" + "=\"" + contentValue + "\" ");
	            rowString.append("/>");
	            bwOutFile.write(rowString.toString());
	            bwOutFile.newLine();
	            
	            
			}

			rowString.setLength(0);
            rowString.append("<" + "Content" + " ");
            rowString.append("contentId" + "=\"" + contentId + "\" ");
            rowString.append("contentTypeId" + "=\"" + "DOCUMENT" + "\" ");
            rowString.append("dataResourceId" + "=\"" + dataResourceId + "\" ");
            rowString.append("statusId" + "=\"" + "CTNT_PUBLISHED" + "\" ");
            rowString.append("contentName" + "=\"" + colName + "\" ");
            if(UtilValidate.isNotEmpty(localeString))
            {
            	rowString.append("localeString" + "=\"" + localeString + "\" ");
            }
            rowString.append("/>");
            bwOutFile.write(rowString.toString());
            bwOutFile.newLine();
            String fromDate=(String)mRow.get("fromDate");
            String sFromDate = "";
            if(UtilValidate.isNotEmpty(fromDate))
            {
            	java.util.Date formattedFromDate=OsafeAdminUtil.validDate(fromDate);
                sFromDate = _sdf.format(formattedFromDate);
            }
            else
            {
            	sFromDate = _sdf.format(UtilDateTime.nowTimestamp());
            }
			
            rowString.setLength(0);
            rowString.append("<" + "PartyContent" + " ");
            rowString.append("partyId" + "=\"" + partyId + "\" ");
            rowString.append("contentId" + "=\"" + contentId + "\" ");
            rowString.append("partyContentTypeId" + "=\"" + partyContentType + "\" ");
            rowString.append("fromDate" + "=\"" + sFromDate + "\" ");
            rowString.append("/>");
            bwOutFile.write(rowString.toString());
            bwOutFile.newLine();
    		
    	}
     	 catch (Exception e) {
	     }

     	 return;
    }
    
    
    public static Map<String, Object> importCustomerXML(DispatchContext ctx, Map<String, ?> context) {LocalDispatcher dispatcher = ctx.getDispatcher();
    _delegator = ctx.getDelegator();
    List<String> messages = FastList.newInstance();

    String xmlDataFilePath = (String)context.get("xmlDataFile");
    String xmlDataDirPath = (String)context.get("xmlDataDir");
    Boolean autoLoad = (Boolean) context.get("autoLoad");
    GenericValue userLogin = (GenericValue) context.get("userLogin");
    if (autoLoad == null) autoLoad = Boolean.FALSE;

    File inputWorkbook = null;
    String tempDataFile = null;
    File baseDataDir = null;
    File baseFilePath = null;
    BufferedWriter fOutProduct=null;
    if (UtilValidate.isNotEmpty(xmlDataFilePath) && UtilValidate.isNotEmpty(xmlDataDirPath)) {
    	baseFilePath = new File(xmlDataFilePath);
        try {
            URL xlsDataFileUrl = UtilURL.fromFilename(xmlDataFilePath);
            InputStream ins = xlsDataFileUrl.openStream();

            if (ins != null && (xmlDataFilePath.toUpperCase().endsWith("XML"))) {
                baseDataDir = new File(xmlDataDirPath);
                if (baseDataDir.isDirectory() && baseDataDir.canWrite()) {

                    // ############################################
                    // move the existing xml files in dump directory
                    // ############################################
                    File dumpXmlDir = null;
                    File[] fileArray = baseDataDir.listFiles();
                    for (File file: fileArray) {
                        try {
                            if (file.getName().toUpperCase().endsWith("XML")) {
                                if (dumpXmlDir == null) {
                                    dumpXmlDir = new File(baseDataDir, "dumpxml_"+UtilDateTime.nowDateString());
                                }
                                FileUtils.copyFileToDirectory(file, dumpXmlDir);
                                file.delete();
                            }
                        } catch (IOException ioe) {
                            Debug.logError(ioe, module);
                        } catch (Exception exc) {
                            Debug.logError(exc, module);
                        }
                    }
                    // ######################################
                    //save the temp xls data file on server 
                    // ######################################
                    try {
                    	tempDataFile = UtilDateTime.nowAsString()+"."+FilenameUtils.getExtension(xmlDataFilePath);
                        inputWorkbook = new File(baseDataDir,  tempDataFile);
                        if (inputWorkbook.createNewFile()) {
                            Streams.copy(ins, new FileOutputStream(inputWorkbook), true, new byte[1]); 
                        }
                        } catch (IOException ioe) {
                            Debug.logError(ioe, module);
                        } catch (Exception exc) {
                            Debug.logError(exc, module);
                        }
                }
                else {
                    messages.add("xml data dir path not found or can't be write");
                }
            }
            else {
                messages.add(" path specified for Excel sheet file is wrong , doing nothing.");
            }

        } catch (IOException ioe) {
            Debug.logError(ioe, module);
        } catch (Exception exc) {
            Debug.logError(exc, module);
        }
    }
    else {
        messages.add("No path specified for Excel sheet file or xml data direcotry, doing nothing.");
    }

    // ######################################
    //read the temp xls file and generate xml 
    // ######################################
    List dataRows = FastList.newInstance();
    try 
    {
    if (inputWorkbook != null && baseDataDir  != null) 
    {
    	try 
    	{
    		JAXBContext jaxbContext = JAXBContext.newInstance("com.osafe.feeds.osafefeeds");
        	Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        	JAXBElement<BigFishCustomerFeedType> bfCustomerFeedType = (JAXBElement<BigFishCustomerFeedType>)unmarshaller.unmarshal(inputWorkbook);
        	
        	List<CustomerType> customerList = bfCustomerFeedType.getValue().getCustomer();
        	
        	if(customerList.size() > 0) 
        	{
        		dataRows = buildCustomerXMLDataRows(customerList);
        		buildCustomer(dataRows, xmlDataDirPath, messages);
        	}
        	
        	
    	} 
    	catch (Exception e) 
    	{
    		Debug.logError(e, module);
		}
    	finally 
    	{
            try 
            {
                if (fOutProduct != null) 
                {
                	fOutProduct.close();
                }
            } 
            catch (IOException ioe) 
            {
                Debug.logError(ioe, module);
            }
        }
    }
    
    // ##############################################
    // move the generated xml files in done directory
    // ##############################################
    File doneXmlDir = new File(baseDataDir, Constants.DONE_XML_DIRECTORY_PREFIX+UtilDateTime.nowDateString());
    File[] fileArray = baseDataDir.listFiles();
    for (File file: fileArray) 
    {
        try 
        {
            if (FilenameUtils.getExtension(file.getName()).equalsIgnoreCase("XML")) 
            {
            	if(!(file.getName().equals(tempDataFile)) && (!file.getName().equals(baseFilePath.getName())))
            	{
            		FileUtils.copyFileToDirectory(file, doneXmlDir);
                    file.delete();
            	}
            }
        } 
        catch (IOException ioe) 
        {
            Debug.logError(ioe, module);
        }
        catch (Exception exc) 
        {
            Debug.logError(exc, module);
        }
    }

    // ######################################################################
    // call service for insert row in database  from generated xml data files 
    // by calling service entityImportDir if autoLoad parameter is true
    // ######################################################################
    if (autoLoad) 
    {
        //Debug.logInfo("=====657========="+doneXmlDir.getPath()+"=========================", module);
        Map entityImportDirParams = UtilMisc.toMap("path", doneXmlDir.getPath(), 
                                                 "userLogin", context.get("userLogin"));
         try 
         {
             Map result = dispatcher.runSync("entityImportDir", entityImportDirParams);
             if(UtilValidate.isNotEmpty(result.get("responseMessage")) && result.get("responseMessage").equals("error"))
             {
                 return ServiceUtil.returnError(result.get("errorMessage").toString());
             }
             List<String> serviceMsg = (List)result.get("messages");
             for (String msg: serviceMsg) 
             {
                 messages.add(msg);
             }
             
         } 
         catch (Exception exc) 
         {
             Debug.logError(exc, module);
         }
    }
    } 
    catch (Exception exc) 
    {
        Debug.logError(exc, module);
    }
    finally 
    {
        inputWorkbook.delete();
    } 
            
    Map<String, Object> resp = UtilMisc.toMap("messages", (Object) messages);
    return resp;  
  }
    
    
    public static List buildCustomerXMLDataRows(List<CustomerType> customers) {
		List dataRows = FastList.newInstance();

		try 
		{
            for (int rowCount = 0 ; rowCount < customers.size() ; rowCount++) 
            {
            	CustomerType customer = (CustomerType)customers.get(rowCount);
            	Map mRows = FastMap.newInstance();
                
                mRows.put("productStoreId",customer.getProductStoreId());
                mRows.put("customerId",customer.getCustomerId());
                mRows.put("firstName",customer.getFirstName());
                mRows.put("lastName",customer.getLastName());
                mRows.put("dateRegistered",customer.getDateRegistered());
                mRows.put("emailAddress",customer.getEmailAddress());
                mRows.put("emailOptIn",customer.getEmailOptIn());
                mRows.put("homePhone",customer.getHomePhone());
                mRows.put("cellPhone",customer.getCellPhone());
                mRows.put("workPhone",customer.getWorkPhone());
                mRows.put("workPhoneExt",customer.getWorkPhoneExt());
                
                List<BillingAddressType> billingAddressList = customer.getBillingAddress();
                
                if(UtilValidate.isNotEmpty(billingAddressList)) 
                {
                	for(int i = 0; i < billingAddressList.size(); i++) 
                	{
                		BillingAddressType billingAddress = (BillingAddressType)billingAddressList.get(i);
                		
                		mRows.put("billingCountry_"+(i+1),billingAddress.getCountry());
                		mRows.put("billingAddress1_"+(i+1),billingAddress.getAddress1());
                		mRows.put("billingAddress2_"+(i+1),billingAddress.getAddress2());
                		mRows.put("billingAddress3_"+(i+1),billingAddress.getAddress3());
                		mRows.put("billingCity_"+(i+1),billingAddress.getCityTown());
                		mRows.put("billingState_"+(i+1),billingAddress.getStateProvince());
                		mRows.put("billingZip_"+(i+1),billingAddress.getZipPostCode());
                	}
                	mRows.put("totBillingAddress",new Integer(billingAddressList.size()).toString());
                }
                
                List<ShippingAddressType> shippingAddressList = customer.getShippingAddress();
                
                if(UtilValidate.isNotEmpty(shippingAddressList)) 
                {
                	for(int i = 0; i < shippingAddressList.size(); i++) 
                	{
                		ShippingAddressType shippingAddress = (ShippingAddressType)shippingAddressList.get(i);
                		
                		mRows.put("shippingCountry_"+(i+1), shippingAddress.getCountry());
                		mRows.put("shippingAddress1_"+(i+1),shippingAddress.getAddress1());
                		mRows.put("shippingAddress2_"+(i+1),shippingAddress.getAddress2());
                		mRows.put("shippingAddress3_"+(i+1),shippingAddress.getAddress3());
                		mRows.put("shippingCity_"+(i+1),shippingAddress.getCityTown());
                		mRows.put("shippingState_"+(i+1),shippingAddress.getStateProvince());
                		mRows.put("shippingZip_"+(i+1),shippingAddress.getZipPostCode());
                	}
                	mRows.put("totShippingAddress",new Integer(shippingAddressList.size()).toString());
                }
                
                UserLoginType userLogin = customer.getUserLogin();
                
                if(UtilValidate.isNotEmpty(userLogin)) 
                {
                	mRows.put("userName",userLogin.getUserName());
                    mRows.put("password",userLogin.getPassword());
                    mRows.put("userEnabled",userLogin.getUserEnabled());
                    mRows.put("userIsSystem",userLogin.getUserIsSystem());
                }
                
                CustomerAttributeType customerAttribute = customer.getCustomerAttribute();
                
                if(UtilValidate.isNotEmpty(customerAttribute))
                {
                	List<AttributeType> attributeList = customerAttribute.getAttribute();
                	for(int i = 0; i < attributeList.size(); i++) 
                	{
                		AttributeType attribute = (AttributeType)attributeList.get(i);
                		
                		mRows.put("attrName_"+(i+1), attribute.getName());
                		mRows.put("attrValue_"+(i+1),attribute.getValue());
                	}
                	mRows.put("totAttributes",new Integer(attributeList.size()).toString());
                }
                
                mRows = formatProductXLSData(mRows);
                dataRows.add(mRows);
             }
    	}
      	catch (Exception e) {
      		e.printStackTrace();
   	    }
      	return dataRows;
   }
    
    
    
    private static void buildCustomer(List dataRows,String xmlDataDirPath, List messages)
    {

        File fOutFile =null;
        BufferedWriter bwOutFile=null;
        boolean useEncryption = "true".equals(UtilProperties.getPropertyValue("security.properties", "password.encrypt"));
   	    String partyId = null;
        
		try 
		{
	        fOutFile = new File(xmlDataDirPath, "000-Customer.xml");
            if (fOutFile.createNewFile()) 
            {
            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));

                writeXmlHeader(bwOutFile);
                
                for (int i=0 ; i < dataRows.size() ; i++) 
                {
                     StringBuilder  rowString = new StringBuilder();
	            	 Map mRow = (Map)dataRows.get(i);
	            	 if(UtilValidate.isNotEmpty(mRow.get("customerId"))) 
	            	 {
	            		 partyId = (String)mRow.get("customerId");
	            	 } 
	            	 else 
	            	 {
	            		 partyId = _delegator.getNextSeqId("Party");
	            	 }
                     rowString.append("<" + "Party" + " ");
                     rowString.append("partyId" + "=\"" + partyId + "\" ");
                     rowString.append("partyTypeId" + "=\"" + "PERSON" + "\" ");
                     rowString.append("statusId" + "=\"" + "PARTY_ENABLED" + "\" ");
                     if(UtilValidate.isNotEmpty(mRow.get("dateRegistered"))) 
                     {
                    	 rowString.append("createdDate" + "=\"" +  mRow.get("dateRegistered") + "\" ");
                     }
                     else
                     {
                    	 rowString.append("createdDate" + "=\"" +  _sdf.format(UtilDateTime.nowTimestamp()) + "\" ");	 
                     }
                     rowString.append("lastModifiedDate" + "=\"" +  _sdf.format(UtilDateTime.nowTimestamp()) + "\" ");
                     rowString.append("/>");
                     bwOutFile.write(rowString.toString());
                     bwOutFile.newLine();
                     
                     rowString.setLength(0);
                     rowString.append("<" + "PartyRole" + " ");
                     rowString.append("partyId" + "=\"" + partyId + "\" ");
                     rowString.append("roleTypeId" + "=\"" + "CUSTOMER" + "\" ");
                     rowString.append("/>");
                     bwOutFile.write(rowString.toString());
                     bwOutFile.newLine();
                     
                     List<GenericValue> productStoreRoles = _delegator.findByAnd("ProductStoreRole", UtilMisc.toMap("partyId",partyId,"roleTypeId","CUSTOMER","productStoreId",mRow.get("productStoreId")),UtilMisc.toList("-fromDate"));
 	                 if(UtilValidate.isNotEmpty(productStoreRoles)) 
 	                 {
 	                	productStoreRoles = EntityUtil.filterByDate(productStoreRoles);
 	                 }
 	                 if(UtilValidate.isEmpty(productStoreRoles))
	                 {
	                     rowString.setLength(0);
	                     rowString.append("<" + "ProductStoreRole" + " ");
	                     rowString.append("partyId" + "=\"" + partyId + "\" ");
	                     rowString.append("roleTypeId" + "=\"" + "CUSTOMER" + "\" ");
	                     rowString.append("productStoreId" + "=\"" + mRow.get("productStoreId") + "\" ");
	                     rowString.append("fromDate" + "=\"" + _sdf.format(UtilDateTime.nowTimestamp()) + "\" ");
	                     rowString.append("/>");
	                     bwOutFile.write(rowString.toString());
	                     bwOutFile.newLine();
	                 }
 	                 
                     String userName = (String)mRow.get("userName");
                     String password = (String)mRow.get("password");
                     if(UtilValidate.isNotEmpty(password)) 
                     {
                    	 password = useEncryption ? HashCrypt.getDigestHash(password, LoginServices.getHashType()) : password;
                     }
                     
                     rowString.setLength(0);
                     rowString.append("<" + "UserLogin" + " ");
                     rowString.append("userLoginId" + "=\"" + userName + "\" ");
                     rowString.append("currentPassword" + "=\"" + password + "\" ");
                     rowString.append("partyId" + "=\"" + partyId + "\" ");
                     if(UtilValidate.isNotEmpty((String)mRow.get("userEnabled"))) 
                     {
                    	 rowString.append("enabled" + "=\"" + (String)mRow.get("userEnabled") + "\" ");
                     }
                     if(UtilValidate.isNotEmpty((String)mRow.get("userIsSystem"))) 
                     {
                    	 rowString.append("isSystem" + "=\"" + (String)mRow.get("userIsSystem") + "\" "); 
                     }
                     
                     rowString.append("/>");
                     bwOutFile.write(rowString.toString());
                     bwOutFile.newLine();
                     
                     rowString.setLength(0);
                     rowString.append("<" + "Person" + " ");
                     rowString.append("partyId" + "=\"" + partyId + "\" ");
                     rowString.append("firstName" + "=\"" + (String)mRow.get("firstName") + "\" ");
                     rowString.append("lastName" + "=\"" + (String)mRow.get("lastName") + "\" ");
                     rowString.append("/>");
                     bwOutFile.write(rowString.toString());
                     bwOutFile.newLine();
                     String contactMechId = null;
                     if(UtilValidate.isNotEmpty((String)mRow.get("homePhone"))) 
                     {
                    	 List<GenericValue> partyContactDetailByPurposeHomePhoneList = _delegator.findByAnd("PartyContactDetailByPurpose", UtilMisc.toMap("partyId", partyId, "contactMechPurposeTypeId", "PHONE_HOME", "contactMechTypeId", "TELECOM_NUMBER", "contactNumber", (String)mRow.get("homePhone")), UtilMisc.toList("-fromDate"));
                    	 partyContactDetailByPurposeHomePhoneList = EntityUtil.filterByDate(partyContactDetailByPurposeHomePhoneList);
                         if(UtilValidate.isNotEmpty(partyContactDetailByPurposeHomePhoneList))
                         {
                        	 GenericValue partyContactDetailByPurposeHomePhone = EntityUtil.getFirst(partyContactDetailByPurposeHomePhoneList);
                             contactMechId = partyContactDetailByPurposeHomePhone.getString("contactMechId");
                         }
                         else
                         {
                        	 contactMechId = _delegator.getNextSeqId("ContactMech");
                         }
                         rowString.setLength(0);
                         rowString.append("<" + "ContactMech" + " ");
                         rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
                         rowString.append("contactMechTypeId" + "=\"" + "TELECOM_NUMBER" + "\" ");
                         rowString.append("/>");
                         bwOutFile.write(rowString.toString());
                         bwOutFile.newLine();

                         String partyContactMechFromDate = _sdf.format(UtilDateTime.nowTimestamp());
                         List partyContectMechHomePhoneList = _delegator.findByAnd("PartyContactMech", UtilMisc.toMap("partyId", partyId, "contactMechId", contactMechId), UtilMisc.toList("-fromDate"));
                         partyContectMechHomePhoneList = EntityUtil.filterByDate(partyContectMechHomePhoneList);
                         if(UtilValidate.isNotEmpty(partyContectMechHomePhoneList))
                         {
                        	 GenericValue partyContectMechHomePhone = EntityUtil.getFirst(partyContectMechHomePhoneList);
                        	 if(UtilValidate.isNotEmpty(partyContectMechHomePhone))
                        	 {
                        		 partyContactMechFromDate =_sdf.format(new Date(partyContectMechHomePhone.getTimestamp("fromDate").getTime())); 
                        	 }
                         }
                         rowString.setLength(0);
                         rowString.append("<" + "PartyContactMech" + " ");
                         rowString.append("partyId" + "=\"" + partyId + "\" ");
                         rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
                         rowString.append("fromDate" + "=\"" +  partyContactMechFromDate + "\" ");
                         rowString.append("/>");
                         bwOutFile.write(rowString.toString());
                         bwOutFile.newLine();
                         
                         String partyContactMechPurposeFromDate = _sdf.format(UtilDateTime.nowTimestamp());
                         List partyContectMechPurposeHomePhoneList = _delegator.findByAnd("PartyContactMechPurpose", UtilMisc.toMap("partyId", partyId, "contactMechId", contactMechId, "contactMechPurposeTypeId", "PHONE_HOME"), UtilMisc.toList("-fromDate"));
                         partyContectMechPurposeHomePhoneList = EntityUtil.filterByDate(partyContectMechPurposeHomePhoneList);
                         if(UtilValidate.isNotEmpty(partyContectMechPurposeHomePhoneList))
                         {
                        	 GenericValue partyContectMechPurposeHomePhone = EntityUtil.getFirst(partyContectMechPurposeHomePhoneList);
                        	 if(UtilValidate.isNotEmpty(partyContectMechPurposeHomePhone))
                        	 {
                        		 partyContactMechPurposeFromDate =_sdf.format(new Date(partyContectMechPurposeHomePhone.getTimestamp("fromDate").getTime())); 
                        	 }
                         }
                         rowString.setLength(0);
                         rowString.append("<" + "PartyContactMechPurpose" + " ");
                         rowString.append("partyId" + "=\"" + partyId + "\" ");
                         rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
                         rowString.append("contactMechPurposeTypeId" + "=\"" + "PHONE_HOME" + "\" ");
                         rowString.append("fromDate" + "=\"" +  partyContactMechPurposeFromDate + "\" ");
                         rowString.append("/>");
                         bwOutFile.write(rowString.toString());
                         bwOutFile.newLine();
                         
                         rowString.setLength(0);
                         rowString.append("<" + "TelecomNumber" + " ");
                         rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
                         rowString.append("contactNumber" + "=\"" +  (String)mRow.get("homePhone") + "\" ");
                         rowString.append("/>");
                         bwOutFile.write(rowString.toString());
                         bwOutFile.newLine();
                     }
                     
                     if(UtilValidate.isNotEmpty((String)mRow.get("cellPhone"))) 
                     {
                    	 
                    	 List<GenericValue> partyContactDetailByPurposeCellPhoneList = _delegator.findByAnd("PartyContactDetailByPurpose", UtilMisc.toMap("partyId", partyId, "contactMechPurposeTypeId", "PHONE_MOBILE", "contactMechTypeId", "TELECOM_NUMBER", "contactNumber", (String)mRow.get("cellPhone")), UtilMisc.toList("-fromDate"));
                    	 partyContactDetailByPurposeCellPhoneList = EntityUtil.filterByDate(partyContactDetailByPurposeCellPhoneList);
                         if(UtilValidate.isNotEmpty(partyContactDetailByPurposeCellPhoneList))
                         {
                        	 GenericValue partyContactDetailByPurposeCellPhone = EntityUtil.getFirst(partyContactDetailByPurposeCellPhoneList);
                             contactMechId = partyContactDetailByPurposeCellPhone.getString("contactMechId");
                         }
                         else
                         {
                        	 contactMechId = _delegator.getNextSeqId("ContactMech");
                         }
                    	 
                         rowString.setLength(0);
                         rowString.append("<" + "ContactMech" + " ");
                         rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
                         rowString.append("contactMechTypeId" + "=\"" + "TELECOM_NUMBER" + "\" ");
                         rowString.append("/>");
                         bwOutFile.write(rowString.toString());
                         bwOutFile.newLine();

                         String partyContactMechFromDate = _sdf.format(UtilDateTime.nowTimestamp());
                         List partyContectMechCellPhoneList = _delegator.findByAnd("PartyContactMech", UtilMisc.toMap("partyId", partyId, "contactMechId", contactMechId), UtilMisc.toList("-fromDate"));
                         partyContectMechCellPhoneList = EntityUtil.filterByDate(partyContectMechCellPhoneList);
                         if(UtilValidate.isNotEmpty(partyContectMechCellPhoneList))
                         {
                        	 GenericValue partyContectMechCellPhone = EntityUtil.getFirst(partyContectMechCellPhoneList);
                        	 if(UtilValidate.isNotEmpty(partyContectMechCellPhone))
                        	 {
                        		 partyContactMechFromDate =_sdf.format(new Date(partyContectMechCellPhone.getTimestamp("fromDate").getTime())); 
                        	 }
                         }
                         rowString.setLength(0);
                         rowString.append("<" + "PartyContactMech" + " ");
                         rowString.append("partyId" + "=\"" + partyId + "\" ");
                         rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
                         rowString.append("fromDate" + "=\"" +  partyContactMechFromDate + "\" ");
                         rowString.append("/>");
                         bwOutFile.write(rowString.toString());
                         bwOutFile.newLine();
                         
                         String partyContactMechPurposeFromDate = _sdf.format(UtilDateTime.nowTimestamp());
                         List partyContectMechPurposeCellPhoneList = _delegator.findByAnd("PartyContactMechPurpose", UtilMisc.toMap("partyId", partyId, "contactMechId", contactMechId, "contactMechPurposeTypeId", "PHONE_MOBILE"), UtilMisc.toList("-fromDate"));
                         partyContectMechPurposeCellPhoneList = EntityUtil.filterByDate(partyContectMechPurposeCellPhoneList);
                         if(UtilValidate.isNotEmpty(partyContectMechPurposeCellPhoneList))
                         {
                        	 GenericValue partyContectMechPurposeCellPhone = EntityUtil.getFirst(partyContectMechPurposeCellPhoneList);
                        	 if(UtilValidate.isNotEmpty(partyContectMechPurposeCellPhone))
                        	 {
                        		 partyContactMechPurposeFromDate =_sdf.format(new Date(partyContectMechPurposeCellPhone.getTimestamp("fromDate").getTime())); 
                        	 }
                         }
                         rowString.setLength(0);
                         rowString.append("<" + "PartyContactMechPurpose" + " ");
                         rowString.append("partyId" + "=\"" + partyId + "\" ");
                         rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
                         rowString.append("contactMechPurposeTypeId" + "=\"" + "PHONE_MOBILE" + "\" ");
                         rowString.append("fromDate" + "=\"" + partyContactMechPurposeFromDate + "\" ");
                         rowString.append("/>");
                         bwOutFile.write(rowString.toString());
                         bwOutFile.newLine();
                         
                         rowString.setLength(0);
                         rowString.append("<" + "TelecomNumber" + " ");
                         rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
                         rowString.append("contactNumber" + "=\"" +  (String)mRow.get("cellPhone") + "\" ");
                         rowString.append("/>");
                         bwOutFile.write(rowString.toString());
                         bwOutFile.newLine();
                     }
                     
                     if(UtilValidate.isNotEmpty((String)mRow.get("workPhone"))) 
                     {
                    	 List<GenericValue> partyContactDetailByPurposeWorkPhoneList = _delegator.findByAnd("PartyContactDetailByPurpose", UtilMisc.toMap("partyId", partyId, "contactMechPurposeTypeId", "PHONE_WORK", "contactMechTypeId", "TELECOM_NUMBER", "contactNumber", (String)mRow.get("workPhone")), UtilMisc.toList("-fromDate"));
                    	 partyContactDetailByPurposeWorkPhoneList = EntityUtil.filterByDate(partyContactDetailByPurposeWorkPhoneList);
                         if(UtilValidate.isNotEmpty(partyContactDetailByPurposeWorkPhoneList))
                         {
                        	 GenericValue partyContactDetailByPurposeWorkPhone = EntityUtil.getFirst(partyContactDetailByPurposeWorkPhoneList);
                             contactMechId = partyContactDetailByPurposeWorkPhone.getString("contactMechId");
                         }
                         else
                         {
                        	 contactMechId = _delegator.getNextSeqId("ContactMech");
                         }
                    	 
                         rowString.setLength(0);
                         rowString.append("<" + "ContactMech" + " ");
                         rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
                         rowString.append("contactMechTypeId" + "=\"" + "TELECOM_NUMBER" + "\" ");
                         rowString.append("/>");
                         bwOutFile.write(rowString.toString());
                         bwOutFile.newLine();

                         String partyContactMechFromDate = _sdf.format(UtilDateTime.nowTimestamp());
                         List partyContectMechWorkPhoneList = _delegator.findByAnd("PartyContactMech", UtilMisc.toMap("partyId", partyId, "contactMechId", contactMechId), UtilMisc.toList("-fromDate"));
                         partyContectMechWorkPhoneList = EntityUtil.filterByDate(partyContectMechWorkPhoneList);
                         if(UtilValidate.isNotEmpty(partyContectMechWorkPhoneList))
                         {
                        	 GenericValue partyContectMechWorkPhone = EntityUtil.getFirst(partyContectMechWorkPhoneList);
                        	 if(UtilValidate.isNotEmpty(partyContectMechWorkPhone))
                        	 {
                        		 partyContactMechFromDate =_sdf.format(new Date(partyContectMechWorkPhone.getTimestamp("fromDate").getTime())); 
                        	 }
                         }
                         rowString.setLength(0);
                         rowString.append("<" + "PartyContactMech" + " ");
                         rowString.append("partyId" + "=\"" + partyId + "\" ");
                         rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
                         rowString.append("fromDate" + "=\"" +  partyContactMechFromDate + "\" ");
                         if(UtilValidate.isNotEmpty((String)mRow.get("workPhoneExt"))) 
                         {
                        	 rowString.append("extension" + "=\"" +  (String)mRow.get("workPhoneExt") + "\" ");
                         }
                         rowString.append("/>");
                         bwOutFile.write(rowString.toString());
                         bwOutFile.newLine();
                         
                         String partyContactMechPurposeFromDate = _sdf.format(UtilDateTime.nowTimestamp());
                         List partyContectMechPurposeWorkPhoneList = _delegator.findByAnd("PartyContactMechPurpose", UtilMisc.toMap("partyId", partyId, "contactMechId", contactMechId, "contactMechPurposeTypeId", "PHONE_WORK"), UtilMisc.toList("-fromDate"));
                         partyContectMechPurposeWorkPhoneList = EntityUtil.filterByDate(partyContectMechPurposeWorkPhoneList);
                         if(UtilValidate.isNotEmpty(partyContectMechPurposeWorkPhoneList))
                         {
                        	 GenericValue partyContectMechPurposeWorkPhone = EntityUtil.getFirst(partyContectMechPurposeWorkPhoneList);
                        	 if(UtilValidate.isNotEmpty(partyContectMechPurposeWorkPhone))
                        	 {
                        		 partyContactMechPurposeFromDate =_sdf.format(new Date(partyContectMechPurposeWorkPhone.getTimestamp("fromDate").getTime())); 
                        	 }
                         }
                         rowString.setLength(0);
                         rowString.append("<" + "PartyContactMechPurpose" + " ");
                         rowString.append("partyId" + "=\"" + partyId + "\" ");
                         rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
                         rowString.append("contactMechPurposeTypeId" + "=\"" + "PHONE_WORK" + "\" ");
                         rowString.append("fromDate" + "=\"" +  partyContactMechPurposeFromDate + "\" ");
                         rowString.append("/>");
                         bwOutFile.write(rowString.toString());
                         bwOutFile.newLine();
                         
                         rowString.setLength(0);
                         rowString.append("<" + "TelecomNumber" + " ");
                         rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
                         rowString.append("contactNumber" + "=\"" +  (String)mRow.get("workPhone") + "\" ");
                         rowString.append("/>");
                         bwOutFile.write(rowString.toString());
                         bwOutFile.newLine();
                     }
                     
                     if(UtilValidate.isNotEmpty((String)mRow.get("emailAddress")))
                     {
                    	 List<GenericValue> partyContactDetailByPurposeEmailList = _delegator.findByAnd("PartyContactDetailByPurpose", UtilMisc.toMap("partyId", partyId, "contactMechPurposeTypeId", "PRIMARY_EMAIL", "contactMechTypeId", "EMAIL_ADDRESS", "infoString", (String)mRow.get("emailAddress")), UtilMisc.toList("-fromDate"));
                    	 partyContactDetailByPurposeEmailList = EntityUtil.filterByDate(partyContactDetailByPurposeEmailList);
                         if(UtilValidate.isNotEmpty(partyContactDetailByPurposeEmailList))
                         {
                        	 GenericValue partyContactDetailByPurposeEmail = EntityUtil.getFirst(partyContactDetailByPurposeEmailList);
                             contactMechId = partyContactDetailByPurposeEmail.getString("contactMechId");
                         }
                         else
                         {
                        	 contactMechId = _delegator.getNextSeqId("ContactMech");
                         }
                         rowString.setLength(0);
                         rowString.append("<" + "ContactMech" + " ");
                         rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
                         rowString.append("contactMechTypeId" + "=\"" + "EMAIL_ADDRESS" + "\" ");
                         rowString.append("infoString" + "=\"" + (String)mRow.get("emailAddress") + "\" ");
                         rowString.append("/>");
                         bwOutFile.write(rowString.toString());
                         bwOutFile.newLine();
                         
                         String partyContactMechFromDate = _sdf.format(UtilDateTime.nowTimestamp());
                         List partyContectMechEmailList = _delegator.findByAnd("PartyContactMech", UtilMisc.toMap("partyId", partyId, "contactMechId", contactMechId), UtilMisc.toList("-fromDate"));
                         partyContectMechEmailList = EntityUtil.filterByDate(partyContectMechEmailList);
                         if(UtilValidate.isNotEmpty(partyContectMechEmailList))
                         {
                        	 GenericValue partyContectMechEmail = EntityUtil.getFirst(partyContectMechEmailList);
                        	 if(UtilValidate.isNotEmpty(partyContectMechEmail))
                        	 {
                        		 partyContactMechFromDate =_sdf.format(new Date(partyContectMechEmail.getTimestamp("fromDate").getTime())); 
                        	 }
                         }
                         rowString.setLength(0);
                         rowString.append("<" + "PartyContactMech" + " ");
                         rowString.append("partyId" + "=\"" + partyId + "\" ");
                         rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
                         rowString.append("fromDate" + "=\"" +  partyContactMechFromDate + "\" ");
                         if (UtilValidate.isNotEmpty(mRow.get("emailOptIn")))
                         {
                             if(((String)mRow.get("emailOptIn")).equalsIgnoreCase("TRUE")) 
                             {
                            	 rowString.append("allowSolicitation" + "=\"" + "Y" + "\" ");	 
                             }
                             else if(((String)mRow.get("emailOptIn")).equalsIgnoreCase("FALSE")) 
                             {
                            	 rowString.append("allowSolicitation" + "=\"" + "N" + "\" ");	 
                             }
                             else
                             {
                            	 rowString.append("allowSolicitation" + "=\"" + "N" + "\" ");	 
                             }
                        	 
                         }
                         else
                         {
                        	 rowString.append("allowSolicitation" + "=\"" + "N" + "\" ");	 
                        	 
                         }
                        		 
                         rowString.append("/>");
                         bwOutFile.write(rowString.toString());
                         bwOutFile.newLine();
                         
                         String partyContactMechPurposeFromDate = _sdf.format(UtilDateTime.nowTimestamp());
                         List partyContectMechPurposeEmailList = _delegator.findByAnd("PartyContactMechPurpose", UtilMisc.toMap("partyId", partyId, "contactMechId", contactMechId, "contactMechPurposeTypeId", "PRIMARY_EMAIL"), UtilMisc.toList("-fromDate"));
                         partyContectMechPurposeEmailList = EntityUtil.filterByDate(partyContectMechPurposeEmailList);
                         if(UtilValidate.isNotEmpty(partyContectMechPurposeEmailList))
                         {
                        	 GenericValue partyContectMechPurposeEmail = EntityUtil.getFirst(partyContectMechPurposeEmailList);
                        	 if(UtilValidate.isNotEmpty(partyContectMechPurposeEmail))
                        	 {
                        		 partyContactMechPurposeFromDate =_sdf.format(new Date(partyContectMechPurposeEmail.getTimestamp("fromDate").getTime())); 
                        	 }
                         }
                         rowString.setLength(0);
                         rowString.append("<" + "PartyContactMechPurpose" + " ");
                         rowString.append("partyId" + "=\"" + partyId + "\" ");
                         rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
                         rowString.append("contactMechPurposeTypeId" + "=\"" + "PRIMARY_EMAIL" + "\" ");
                         rowString.append("fromDate" + "=\"" + partyContactMechPurposeFromDate + "\" ");
                         rowString.append("/>");
                         bwOutFile.write(rowString.toString());
                         bwOutFile.newLine();
                     }
                     
                     
                     if(UtilValidate.isNotEmpty(mRow.get("totBillingAddress")) && Integer.parseInt((String)mRow.get("totBillingAddress")) > 0) 
                     {
		            	 for(int billingAddressNo = 0; billingAddressNo < Integer.parseInt((String)mRow.get("totBillingAddress")); billingAddressNo++) 
		            	 {
		            		 StringBuilder billingAddressFromFile = new StringBuilder("");
		            		 
		            		 if(mRow.get("billingAddress1_"+(billingAddressNo+1)) != null) 
		                     {
		            			 billingAddressFromFile.append((String)mRow.get("billingAddress1_"+(billingAddressNo+1))); 
		                     }
		                     if(mRow.get("billingAddress2_"+(billingAddressNo+1)) != null) 
		                     {
		                    	 billingAddressFromFile.append((String)mRow.get("billingAddress2_"+(billingAddressNo+1))); 
		                     }
		                     if(mRow.get("billingAddress3_"+(billingAddressNo+1)) != null) 
		                     {
		                    	 billingAddressFromFile.append((String)mRow.get("billingAddress3_"+(billingAddressNo+1))); 
		                     }
		                     if(mRow.get("billingCity_"+(billingAddressNo+1)) != null) 
		                     {
		                    	 billingAddressFromFile.append((String)mRow.get("billingCity_"+(billingAddressNo+1)));
		                     }
		                     if(mRow.get("billingState_"+(billingAddressNo+1)) != null) 
		                     {
		                    	 billingAddressFromFile.append(mRow.get("billingState_"+(billingAddressNo+1)));
		                     }
		                     if(mRow.get("billingZip_"+(billingAddressNo+1)) != null) 
		                     {
		                    	 billingAddressFromFile.append(mRow.get("billingZip_"+(billingAddressNo+1)));
		                     }
		                     if(mRow.get("billingCountry_"+(billingAddressNo+1)) != null) 
		                     {
		                    	 billingAddressFromFile.append(mRow.get("billingCountry_"+(billingAddressNo+1)));
		                     }
		                     contactMechId = _delegator.getNextSeqId("ContactMech");
		            		 List<GenericValue> partyContactDetailByPurposeBillingAddressList = _delegator.findByAnd("PartyContactDetailByPurpose", UtilMisc.toMap("partyId", partyId, "contactMechPurposeTypeId", "BILLING_LOCATION", "contactMechTypeId", "POSTAL_ADDRESS"), UtilMisc.toList("-fromDate"));
	                    	 partyContactDetailByPurposeBillingAddressList = EntityUtil.filterByDate(partyContactDetailByPurposeBillingAddressList);
	                    	 for(GenericValue partyContactDetailByPurposeBillingAddress : partyContactDetailByPurposeBillingAddressList)
	                    	 {
	                    		 StringBuilder billingAddress = new StringBuilder("");
	                    		 if(UtilValidate.isNotEmpty(partyContactDetailByPurposeBillingAddress.getString("address1")))
	                    		 {
	                    			 billingAddress.append(partyContactDetailByPurposeBillingAddress.getString("address1"));
	                    		 }
	                    		 if(UtilValidate.isNotEmpty(partyContactDetailByPurposeBillingAddress.getString("address2")))
	                    		 {
	                    			 billingAddress.append(partyContactDetailByPurposeBillingAddress.getString("address2"));
	                    		 }
	                    		 if(UtilValidate.isNotEmpty(partyContactDetailByPurposeBillingAddress.getString("address3")))
	                    		 {
	                    			 billingAddress.append(partyContactDetailByPurposeBillingAddress.getString("address3"));
	                    		 }
	                    		 if(UtilValidate.isNotEmpty(partyContactDetailByPurposeBillingAddress.getString("city")))
	                    		 {
	                    			 billingAddress.append(partyContactDetailByPurposeBillingAddress.getString("city"));
	                    		 }
	                    		 if(UtilValidate.isNotEmpty(partyContactDetailByPurposeBillingAddress.getString("stateProvinceGeoId")))
	                    		 {
	                    			 billingAddress.append(partyContactDetailByPurposeBillingAddress.getString("stateProvinceGeoId"));
	                    		 }
	                    		 if(UtilValidate.isNotEmpty(partyContactDetailByPurposeBillingAddress.getString("postalCode")))
	                    		 {
	                    			 billingAddress.append(partyContactDetailByPurposeBillingAddress.getString("postalCode"));
	                    		 }
	                    		 if(UtilValidate.isNotEmpty(partyContactDetailByPurposeBillingAddress.getString("countryGeoId")))
	                    		 {
	                    			 billingAddress.append(partyContactDetailByPurposeBillingAddress.getString("countryGeoId"));
	                    		 }
	                    		 if(billingAddress.toString().equals(billingAddressFromFile.toString()))
	                    		 {
	                    			 contactMechId =  partyContactDetailByPurposeBillingAddress.getString("contactMechId");
	                    			 break;
	                    		 }
	                    	 }
		            		 
		                     rowString.setLength(0);
		                     rowString.append("<" + "ContactMech" + " ");
		                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
		                     rowString.append("contactMechTypeId" + "=\"" + "POSTAL_ADDRESS" + "\" ");
		                     rowString.append("/>");
		                     bwOutFile.write(rowString.toString());
		                     bwOutFile.newLine();

		                     String partyContactMechFromDate = _sdf.format(UtilDateTime.nowTimestamp());
	                         List partyContectMechBillingAddressList = _delegator.findByAnd("PartyContactMech", UtilMisc.toMap("partyId", partyId, "contactMechId", contactMechId), UtilMisc.toList("-fromDate"));
	                         partyContectMechBillingAddressList = EntityUtil.filterByDate(partyContectMechBillingAddressList);
	                         if(UtilValidate.isNotEmpty(partyContectMechBillingAddressList))
	                         {
	                        	 GenericValue partyContectMechBillingAddress = EntityUtil.getFirst(partyContectMechBillingAddressList);
	                        	 if(UtilValidate.isNotEmpty(partyContectMechBillingAddress))
	                        	 {
	                        		 partyContactMechFromDate =_sdf.format(new Date(partyContectMechBillingAddress.getTimestamp("fromDate").getTime())); 
	                        	 }
	                         }
		                     
		                     rowString.setLength(0);
		                     rowString.append("<" + "PartyContactMech" + " ");
		                     rowString.append("partyId" + "=\"" + partyId + "\" ");
		                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
		                     rowString.append("fromDate" + "=\"" +  partyContactMechFromDate + "\" ");
		                     rowString.append("/>");
		                     bwOutFile.write(rowString.toString());
		                     bwOutFile.newLine();
		                     
		                     String partyContactMechPurposeFromDate = _sdf.format(UtilDateTime.nowTimestamp());
	                         List partyContectMechPurposeBillingAddressList = _delegator.findByAnd("PartyContactMechPurpose", UtilMisc.toMap("partyId", partyId, "contactMechId", contactMechId, "contactMechPurposeTypeId", "BILLING_LOCATION"), UtilMisc.toList("-fromDate"));
	                         partyContectMechPurposeBillingAddressList = EntityUtil.filterByDate(partyContectMechPurposeBillingAddressList);
	                         if(UtilValidate.isNotEmpty(partyContectMechPurposeBillingAddressList))
	                         {
	                        	 GenericValue partyContectMechPurposeBillingAddress = EntityUtil.getFirst(partyContectMechPurposeBillingAddressList);
	                        	 if(UtilValidate.isNotEmpty(partyContectMechPurposeBillingAddress))
	                        	 {
	                        		 partyContactMechPurposeFromDate =_sdf.format(new Date(partyContectMechPurposeBillingAddress.getTimestamp("fromDate").getTime())); 
	                        	 }
	                         }
		                     rowString.setLength(0);
		                     rowString.append("<" + "PartyContactMechPurpose" + " ");
		                     rowString.append("partyId" + "=\"" + partyId + "\" ");
		                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
		                     rowString.append("contactMechPurposeTypeId" + "=\"" + "BILLING_LOCATION" + "\" ");
		                     rowString.append("fromDate" + "=\"" + partyContactMechPurposeFromDate + "\" ");
		                     rowString.append("/>");
		                     bwOutFile.write(rowString.toString());
		                     bwOutFile.newLine();
		                     
		                     List<GenericValue> partyContectMechPurposeGeneralAddressList = EntityUtil.filterByDate(_delegator.findByAnd("PartyContactMechPurpose", UtilMisc.toMap("partyId",partyId, "contactMechPurposeTypeId", "GENERAL_LOCATION")));
		                     partyContectMechPurposeGeneralAddressList = EntityUtil.filterByDate(partyContectMechPurposeGeneralAddressList);
		                     
		                     if(UtilValidate.isEmpty(partyContectMechPurposeGeneralAddressList)) 
		                     {
		                    	 partyContectMechPurposeGeneralAddressList = EntityUtil.filterByAnd(partyContectMechPurposeGeneralAddressList, UtilMisc.toMap("contactMechId", contactMechId));
		                    	 partyContactMechPurposeFromDate = _sdf.format(UtilDateTime.nowTimestamp());
		                         if(UtilValidate.isNotEmpty(partyContectMechPurposeGeneralAddressList))
		                         {
		                        	 GenericValue partyContectMechPurposeGeneralAddress = EntityUtil.getFirst(partyContectMechPurposeGeneralAddressList);
		                        	 if(UtilValidate.isNotEmpty(partyContectMechPurposeGeneralAddress))
		                        	 {
		                        		 partyContactMechPurposeFromDate =_sdf.format(new Date(partyContectMechPurposeGeneralAddress.getTimestamp("fromDate").getTime())); 
		                        	 }
		                         }
			                     rowString.setLength(0);
			                     rowString.append("<" + "PartyContactMechPurpose" + " ");
			                     rowString.append("partyId" + "=\"" + partyId + "\" ");
			                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
			                     rowString.append("contactMechPurposeTypeId" + "=\"" + "GENERAL_LOCATION" + "\" ");
			                     rowString.append("fromDate" + "=\"" +  partyContactMechPurposeFromDate + "\" ");
			                     rowString.append("/>");
			                     bwOutFile.write(rowString.toString());
			                     bwOutFile.newLine();
		                     }
		                     
		                     rowString.setLength(0);
		                     rowString.append("<" + "PostalAddress" + " ");
		                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
		                     rowString.append("toName" + "=\"" + (String)mRow.get("firstName") + " " + (String)mRow.get("lastName") + "\" ");
		                     if(mRow.get("billingAddress1_"+(billingAddressNo+1)) != null) 
		                     {
		                    	 rowString.append("address1" + "=\"" +  (String)mRow.get("billingAddress1_"+(billingAddressNo+1)) + "\" "); 
		                     }
		                     if(mRow.get("billingAddress2_"+(billingAddressNo+1)) != null) 
		                     {
		                    	 rowString.append("address2" + "=\"" +  (String)mRow.get("billingAddress2_"+(billingAddressNo+1)) + "\" "); 
		                     }
		                     if(mRow.get("billingAddress3_"+(billingAddressNo+1)) != null) 
		                     {
		                    	 rowString.append("address3" + "=\"" +  (String)mRow.get("billingAddress3_"+(billingAddressNo+1)) + "\" "); 
		                     }
		                     if(mRow.get("billingCity_"+(billingAddressNo+1)) != null) 
		                     {
		                    	 rowString.append("city" + "=\"" +  (String)mRow.get("billingCity_"+(billingAddressNo+1)) + "\" ");
		                     }
		                     if(mRow.get("billingState_"+(billingAddressNo+1)) != null) 
		                     {
		                    	 rowString.append("stateProvinceGeoId" + "=\"" +  mRow.get("billingState_"+(billingAddressNo+1)) + "\" ");
		                     }
		                     if(mRow.get("billingZip_"+(billingAddressNo+1)) != null) 
		                     {
		                    	 rowString.append("postalCode" + "=\"" +  mRow.get("billingZip_"+(billingAddressNo+1)) + "\" ");
		                     }
		                     if(mRow.get("billingCountry_"+(billingAddressNo+1)) != null) 
		                     {
		                    	 rowString.append("countryGeoId" + "=\"" +  mRow.get("billingCountry_"+(billingAddressNo+1)) + "\" ");
		                     }
		                     
		                     rowString.append("/>");
		                     bwOutFile.write(rowString.toString());
		                     bwOutFile.newLine();
		            	 }
                     }
                     
                     if(UtilValidate.isNotEmpty(mRow.get("totShippingAddress")) && Integer.parseInt((String)mRow.get("totShippingAddress")) > 0) 
                     {
		            	 for(int shippingAddressNo = 0; shippingAddressNo < Integer.parseInt((String)mRow.get("totShippingAddress")); shippingAddressNo++) 
		            	 {
                             StringBuilder shippingAddressFromFile = new StringBuilder("");
		            		 
		            		 if(mRow.get("shippingAddress1_"+(shippingAddressNo+1)) != null) 
		                     {
		            			 shippingAddressFromFile.append((String)mRow.get("shippingAddress1_"+(shippingAddressNo+1))); 
		                     }
		                     if(mRow.get("shippingAddress2_"+(shippingAddressNo+1)) != null) 
		                     {
		                    	 shippingAddressFromFile.append((String)mRow.get("shippingAddress2_"+(shippingAddressNo+1))); 
		                     }
		                     if(mRow.get("shippingAddress3_"+(shippingAddressNo+1)) != null) 
		                     {
		                    	 shippingAddressFromFile.append((String)mRow.get("shippingAddress3_"+(shippingAddressNo+1))); 
		                     }
		                     if(mRow.get("shippingCity_"+(shippingAddressNo+1)) != null) 
		                     {
		                    	 shippingAddressFromFile.append((String)mRow.get("shippingCity_"+(shippingAddressNo+1)));
		                     }
		                     if(mRow.get("shippingState_"+(shippingAddressNo+1)) != null) 
		                     {
		                    	 shippingAddressFromFile.append(mRow.get("shippingState_"+(shippingAddressNo+1)));
		                     }
		                     if(mRow.get("shippingZip_"+(shippingAddressNo+1)) != null) 
		                     {
		                    	 shippingAddressFromFile.append(mRow.get("shippingZip_"+(shippingAddressNo+1)));
		                     }
		                     if(mRow.get("shippingCountry_"+(shippingAddressNo+1)) != null) 
		                     {
		                    	 shippingAddressFromFile.append(mRow.get("shippingCountry_"+(shippingAddressNo+1)));
		                     }
		                     contactMechId = _delegator.getNextSeqId("ContactMech");
		            		 List<GenericValue> partyContactDetailByPurposeShippingAddressList = _delegator.findByAnd("PartyContactDetailByPurpose", UtilMisc.toMap("partyId", partyId, "contactMechPurposeTypeId", "SHIPPING_LOCATION", "contactMechTypeId", "POSTAL_ADDRESS"), UtilMisc.toList("-fromDate"));
	                    	 partyContactDetailByPurposeShippingAddressList = EntityUtil.filterByDate(partyContactDetailByPurposeShippingAddressList);
	                    	 for(GenericValue partyContactDetailByPurposeShippingAddress : partyContactDetailByPurposeShippingAddressList)
	                    	 {
	                    		 StringBuilder shippingAddress = new StringBuilder("");
	                    		 if(UtilValidate.isNotEmpty(partyContactDetailByPurposeShippingAddress.getString("address1")))
	                    		 {
	                    			 shippingAddress.append(partyContactDetailByPurposeShippingAddress.getString("address1"));
	                    		 }
	                    		 if(UtilValidate.isNotEmpty(partyContactDetailByPurposeShippingAddress.getString("address2")))
	                    		 {
	                    			 shippingAddress.append(partyContactDetailByPurposeShippingAddress.getString("address2"));
	                    		 }
	                    		 if(UtilValidate.isNotEmpty(partyContactDetailByPurposeShippingAddress.getString("address3")))
	                    		 {
	                    			 shippingAddress.append(partyContactDetailByPurposeShippingAddress.getString("address3"));
	                    		 }
	                    		 if(UtilValidate.isNotEmpty(partyContactDetailByPurposeShippingAddress.getString("city")))
	                    		 {
	                    			 shippingAddress.append(partyContactDetailByPurposeShippingAddress.getString("city"));
	                    		 }
	                    		 if(UtilValidate.isNotEmpty(partyContactDetailByPurposeShippingAddress.getString("stateProvinceGeoId")))
	                    		 {
	                    			 shippingAddress.append(partyContactDetailByPurposeShippingAddress.getString("stateProvinceGeoId"));
	                    		 }
	                    		 if(UtilValidate.isNotEmpty(partyContactDetailByPurposeShippingAddress.getString("postalCode")))
	                    		 {
	                    			 shippingAddress.append(partyContactDetailByPurposeShippingAddress.getString("postalCode"));
	                    		 }
	                    		 if(UtilValidate.isNotEmpty(partyContactDetailByPurposeShippingAddress.getString("countryGeoId")))
	                    		 {
	                    			 shippingAddress.append(partyContactDetailByPurposeShippingAddress.getString("countryGeoId"));
	                    		 }
	                    		 if(shippingAddress.toString().equals(shippingAddressFromFile.toString()))
	                    		 {
	                    			 contactMechId =  partyContactDetailByPurposeShippingAddress.getString("contactMechId");
	                    			 break;
	                    		 }
	                    	 }
		            		 
		                     rowString.setLength(0);
		                     rowString.append("<" + "ContactMech" + " ");
		                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
		                     rowString.append("contactMechTypeId" + "=\"" + "POSTAL_ADDRESS" + "\" ");
		                     rowString.append("/>");
		                     bwOutFile.write(rowString.toString());
		                     bwOutFile.newLine();

		                     String partyContactMechFromDate = _sdf.format(UtilDateTime.nowTimestamp());
	                         List partyContectMechShippingAddressList = _delegator.findByAnd("PartyContactMech", UtilMisc.toMap("partyId", partyId, "contactMechId", contactMechId), UtilMisc.toList("-fromDate"));
	                         partyContectMechShippingAddressList = EntityUtil.filterByDate(partyContectMechShippingAddressList);
	                         if(UtilValidate.isNotEmpty(partyContectMechShippingAddressList))
	                         {
	                        	 GenericValue partyContectMechShippingAddress = EntityUtil.getFirst(partyContectMechShippingAddressList);
	                        	 if(UtilValidate.isNotEmpty(partyContectMechShippingAddress))
	                        	 {
	                        		 partyContactMechFromDate =_sdf.format(new Date(partyContectMechShippingAddress.getTimestamp("fromDate").getTime())); 
	                        	 }
	                         }
		                     rowString.setLength(0);
		                     rowString.append("<" + "PartyContactMech" + " ");
		                     rowString.append("partyId" + "=\"" + partyId + "\" ");
		                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
		                     rowString.append("fromDate" + "=\"" + partyContactMechFromDate + "\" ");
		                     rowString.append("/>");
		                     bwOutFile.write(rowString.toString());
		                     bwOutFile.newLine();
		                     
		                     String partyContactMechPurposeFromDate = _sdf.format(UtilDateTime.nowTimestamp());
	                         List partyContectMechPurposeShippingAddressList = _delegator.findByAnd("PartyContactMechPurpose", UtilMisc.toMap("partyId", partyId, "contactMechId", contactMechId, "contactMechPurposeTypeId", "SHIPPING_LOCATION"), UtilMisc.toList("-fromDate"));
	                         partyContectMechPurposeShippingAddressList = EntityUtil.filterByDate(partyContectMechPurposeShippingAddressList);
	                         if(UtilValidate.isNotEmpty(partyContectMechPurposeShippingAddressList))
	                         {
	                        	 GenericValue partyContectMechPurposeShippingAddress = EntityUtil.getFirst(partyContectMechPurposeShippingAddressList);
	                        	 if(UtilValidate.isNotEmpty(partyContectMechPurposeShippingAddress))
	                        	 {
	                        		 partyContactMechPurposeFromDate =_sdf.format(new Date(partyContectMechPurposeShippingAddress.getTimestamp("fromDate").getTime())); 
	                        	 }
	                         }
		                     rowString.setLength(0);
		                     rowString.append("<" + "PartyContactMechPurpose" + " ");
		                     rowString.append("partyId" + "=\"" + partyId + "\" ");
		                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
		                     rowString.append("contactMechPurposeTypeId" + "=\"" + "SHIPPING_LOCATION" + "\" ");
		                     rowString.append("fromDate" + "=\"" +  partyContactMechPurposeFromDate + "\" ");
		                     rowString.append("/>");
		                     bwOutFile.write(rowString.toString());
		                     bwOutFile.newLine();
		                     
		                     rowString.setLength(0);
		                     rowString.append("<" + "PostalAddress" + " ");
		                     rowString.append("contactMechId" + "=\"" + contactMechId + "\" ");
		                     rowString.append("toName" + "=\"" + (String)mRow.get("firstName") + " " + (String)mRow.get("lastName") + "\" ");
		                     if(mRow.get("shippingAddress1_"+(shippingAddressNo+1)) != null) 
		                     {
		                    	 rowString.append("address1" + "=\"" +  (String)mRow.get("shippingAddress1_"+(shippingAddressNo+1)) + "\" "); 
		                     }
		                     if(mRow.get("shippingAddress2_"+(shippingAddressNo+1)) != null) 
		                     {
		                    	 rowString.append("address2" + "=\"" +  (String)mRow.get("shippingAddress2_"+(shippingAddressNo+1)) + "\" "); 
		                     }
		                     if(mRow.get("shippingAddress3_"+(shippingAddressNo+1)) != null) 
		                     {
		                    	 rowString.append("address3" + "=\"" +  (String)mRow.get("shippingAddress3_"+(shippingAddressNo+1)) + "\" "); 
		                     }
		                     if(mRow.get("shippingCity_"+(shippingAddressNo+1)) != null) 
		                     {
		                    	 rowString.append("city" + "=\"" +  (String)mRow.get("shippingCity_"+(shippingAddressNo+1)) + "\" ");
		                     }
		                     if(mRow.get("shippingState_"+(shippingAddressNo+1)) != null) 
		                     {
		                    	 rowString.append("stateProvinceGeoId" + "=\"" +  mRow.get("shippingState_"+(shippingAddressNo+1)) + "\" ");
		                     }
		                     if(mRow.get("shippingZip_"+(shippingAddressNo+1)) != null) 
		                     {
		                    	 rowString.append("postalCode" + "=\"" +  mRow.get("shippingZip_"+(shippingAddressNo+1)) + "\" ");
		                     }
		                     if(mRow.get("shippingCountry_"+(shippingAddressNo+1)) != null) 
		                     {
		                    	 rowString.append("countryGeoId" + "=\"" +  mRow.get("shippingCountry_"+(shippingAddressNo+1)) + "\" ");
		                     }
		                     
		                     rowString.append("/>");
		                     bwOutFile.write(rowString.toString());
		                     bwOutFile.newLine();
		            	 }
		            	 
                     }
 	            	 
                     buildCustomerAttribute(rowString, bwOutFile, mRow, partyId);
	            }
                bwOutFile.flush();
         	    writeXmlFooter(bwOutFile);
            }
            
    	}
      	catch (Exception e) 
      	{
            Debug.logError(e.getMessage(), module + ".buildCustomer");
            messages.add("Error: processing party Id[" + partyId + "].  In module:" + module + ".buildCustomer");
   	    }
        finally 
        {
            try 
            {
                if (bwOutFile != null) 
                {
               	 bwOutFile.close();
                }
            } 
            catch (IOException ioe) 
            {
                Debug.logError(ioe.getMessage(), module + ".buildCustomer");
            }
        }
    }
    
    private static void buildCustomerAttribute(StringBuilder rowString, BufferedWriter bwOutFile, Map mRow, String partyId) 
    {
		try 
		{
			if(UtilValidate.isNotEmpty(mRow.get("totAttributes")) && Integer.parseInt((String)mRow.get("totAttributes")) > 0) 
            {
				
           	    for(int attributeNo = 0; attributeNo < Integer.parseInt((String)mRow.get("totAttributes")); attributeNo++) 
           	    {
           		    if(UtilValidate.isNotEmpty(mRow.get("attrName_"+attributeNo)) && UtilValidate.isNotEmpty(mRow.get("attrValue_"+attributeNo)))
           		    {
           			    rowString.setLength(0);
	                    rowString.append("<" + "PartyAttribute" + " ");
	                    rowString.append("partyId" + "=\"" + partyId + "\" ");
	                    rowString.append("attrName" + "=\"" + (String)mRow.get("attrName_"+attributeNo) + "\" ");
	                    rowString.append("attrValue" + "=\"" + (String)mRow.get("attrValue_"+attributeNo) + "\" ");	 
	                    rowString.append("/>");
	                    bwOutFile.write(rowString.toString());
	                    bwOutFile.newLine();
           		    }
           	    }
            }
    	}
      	catch (Exception e) 
      	{
            Debug.logError(e.getMessage(), module + ".buildCustomerAttribute");
   	    }
    }
    
    public static Map<String, Object> exportProductXML(DispatchContext ctx, Map<String, ?> context) 
    {
        _delegator = ctx.getDelegator();
        _dispatcher = ctx.getDispatcher();
        _locale = (Locale) context.get("locale");
        List<String> messages = FastList.newInstance();
        try {
        String productStoreId = (String) context.get("productStoreId");
        String browseRootProductCategoryId = (String) context.get("browseRootProductCategoryId");
        String isSampleFile = (String) context.get("sampleFile");
        String fileName="clientProductImport.xml";
        
        ObjectFactory factory = new ObjectFactory();
        
        BigFishProductFeedType bfProductFeedType = factory.createBigFishProductFeedType();
        
        if (UtilValidate.isNotEmpty(isSampleFile) && isSampleFile.equals("Y"))
        {
        	fileName="sampleClientProductImport.xml";
        }
        String importDataPath = FlexibleStringExpander.expandString(OSAFE_ADMIN_PROP.getString("ecommerce-import-data-path"),context);
        File file = new File(importDataPath, "temp" + fileName);
        if (UtilValidate.isNotEmpty(isSampleFile) && isSampleFile.equals("Y")) {
        	
        	//Product Category
	        ProductCategoryType productCategoryType = factory.createProductCategoryType();
	        List productCategoryList =  productCategoryType.getCategory();
	        createProductCategoryXmlSample(factory, productCategoryList);
	  	    bfProductFeedType.setProductCategory(productCategoryType);
	  	    
	  	    //Products
	  	    ProductsType productsType = factory.createProductsType();
	  	    List productList = productsType.getProduct();
	  	    createProductXmlSample(factory, productList);
	  	    bfProductFeedType.setProducts(productsType);
	  	    
	  	    //Product Assoc
	  	    ProductAssociationType productAssociationType = factory.createProductAssociationType();
	  	    List productAssocList = productAssociationType.getAssociation();
	  	    createProductAssocXmlSample(factory, productAssocList);
	  	    bfProductFeedType.setProductAssociation(productAssociationType);
	  	    
	  	    //Product Facet Groups
	  	    ProductFacetCatGroupType productFacetCatGroupType = factory.createProductFacetCatGroupType();
	  	    List facetGroupList = productFacetCatGroupType.getFacetCatGroup();
	  	    createProductFacetGroupSample(factory, facetGroupList);
	  	    bfProductFeedType.setProductFacetGroup(productFacetCatGroupType);
	  	    
	  	    //Product Facet Values
	  	    ProductFacetValueType productFacetValueType = factory.createProductFacetValueType();
	  	    List facetValueList = productFacetValueType.getFacetValue();
	  	    createProductFacetValueSample(factory, facetValueList);
	  	    bfProductFeedType.setProductFacetValue(productFacetValueType);
	  	    
	  	    //Product Manufactuter
	  	    ProductManufacturerType productManufacturerType = factory.createProductManufacturerType();
	  	    List manufacturerList = productManufacturerType.getManufacturer();
	  	    createProductManufacturerSample(factory, manufacturerList);
	  	    bfProductFeedType.setProductManufacturer(productManufacturerType);
	        
        } else {
        	//Product Category
	        ProductCategoryType productCategoryType = factory.createProductCategoryType();
	        List productCategoryList =  productCategoryType.getCategory();
            List<Map<String, Object>> dataRows = buildProductCategoryDataRows(context);
            generateProductCategoryXML(factory, productCategoryList,  dataRows);
	  	    bfProductFeedType.setProductCategory(productCategoryType);
	  	    
	  	    //Products
	  	    ProductsType productsType = factory.createProductsType();
	  	    List productList = productsType.getProduct();
            dataRows = buildProductDataRows(context);
            generateProductXML(factory, productList, dataRows);
	  	    bfProductFeedType.setProducts(productsType);
	  	    
	  	    //Product Assoc
	  	    ProductAssociationType productAssociationType = factory.createProductAssociationType();
	  	    List productAssocList = productAssociationType.getAssociation();
            dataRows = buildProductAssocDataRows(context);
            generateProductAssocXML(factory, productAssocList, dataRows);
	  	    bfProductFeedType.setProductAssociation(productAssociationType);

	  	    //Product Facet Groups
	  	    ProductFacetCatGroupType productFacetCatGroupType = factory.createProductFacetCatGroupType();
	  	    List facetGroupList = productFacetCatGroupType.getFacetCatGroup();
            dataRows = buildFacetGroupDataRows(context);
            generateFacetGroupXML(factory, facetGroupList, dataRows);
	  	    bfProductFeedType.setProductFacetGroup(productFacetCatGroupType);
	  	    
	  	    //Product Facet Values
	  	    ProductFacetValueType productFacetValueType = factory.createProductFacetValueType();
	  	    List facetValueList = productFacetValueType.getFacetValue();
            dataRows = buildFacetValueDataRows(context);
            generateFacetValueXML(factory, facetValueList, dataRows);
	  	    bfProductFeedType.setProductFacetValue(productFacetValueType);
	  	    
	  	    //Product Manufactuter
	  	    ProductManufacturerType productManufacturerType = factory.createProductManufacturerType();
	  	    List manufacturerList = productManufacturerType.getManufacturer();
            dataRows = buildManufacturerDataRows(context);
            generateManufacturerXML(factory, manufacturerList, dataRows);
	  	    bfProductFeedType.setProductManufacturer(productManufacturerType);
        }
  	    FeedsUtil.marshalObject(new JAXBElement<BigFishProductFeedType>(new QName("", "BigFishProductFeed"), BigFishProductFeedType.class, null, bfProductFeedType), file);
  	    
  	    new File(importDataPath, fileName).delete();
        File renameFile =new File(importDataPath, fileName);
        RandomAccessFile out = new RandomAccessFile(renameFile, "rw");
        InputStream inputStr = new FileInputStream(file);
        byte[] bytes = new byte[102400];
        int bytesRead;
        while ((bytesRead = inputStr.read(bytes)) != -1)
        {
            out.write(bytes, 0, bytesRead);
        }
        out.close();
      inputStr.close();
        } catch (Exception e) {
        	Debug.logError(e, module);
		}
        Map<String, Object> resp = UtilMisc.toMap("messages", (Object) messages);
        return resp;
        
    }

    public static void createProductCategoryXmlSample(ObjectFactory factory, List productCategoryList) {
    	try {
            CategoryType category = factory.createCategoryType();
        	category.setCategoryId("");
            category.setParentCategoryId("");
            category.setCategoryName("");
            category.setDescription("");
            category.setLongDescription("");
                    
            PlpImageType plpImage = factory.createPlpImageType();
            plpImage.setUrl("");
                    
            category.setPlpImage(plpImage);
                    
            category.setAdditionalPlpText("");
            category.setAdditionalPdpText("");
                    
            category.setFromDate("");
            category.setThruDate("");
            productCategoryList.add(category);
    	} catch (Exception e) {
    		Debug.logError(e, module);
    	}
    	
    }
    public static void createProductXmlSample(ObjectFactory factory, List productList) {
    	try {

    		ProductType productType = factory.createProductType();
    		productType.setMasterProductId("");
    		productType.setProductId("");
    		productType.setInternalName("");
    		productType.setProductName("");
    		productType.setSalesPitch("");
    		productType.setLongDescription("");
    		productType.setSpecialInstructions("");
    		productType.setDeliveryInfo("");
    		productType.setDirections("");
    		productType.setTermsAndConds("");
    		productType.setIngredients("");
    		productType.setWarnings("");
    		productType.setPlpLabel("");
    		productType.setPdpLabel("");
    		productType.setProductHeight("");
    		productType.setProductWidth("");
    		productType.setProductDepth("");
    		productType.setProductWeight("");
    		productType.setReturnable("");
    		productType.setTaxable("");
    		productType.setChargeShipping("");
    		productType.setIntroDate("");
    		productType.setDiscoDate("");
    		productType.setManufacturerId("");
    		
    		ProductPriceType productPrice = factory.createProductPriceType();
            ListPriceType listPrice = factory.createListPriceType();
            listPrice.setPrice("");
            listPrice.setCurrency("");
            listPrice.setFromDate("");
            listPrice.setThruDate("");
            productPrice.setListPrice(listPrice);
            
            SalesPriceType salesPrice = factory.createSalesPriceType();
            salesPrice.setPrice("");
            salesPrice.setCurrency("");
            salesPrice.setFromDate("");
            salesPrice.setThruDate("");
            productPrice.setSalesPrice(salesPrice);
            productType.setProductPrice(productPrice);
            
            ProductCategoryMemberType productCategory = factory.createProductCategoryMemberType();
            List<CategoryMemberType> categoryList = productCategory.getCategory();
            
            CategoryMemberType categoryMember = factory.createCategoryMemberType();
            categoryMember.setCategoryId("");
            categoryMember.setSequenceNum("");
            categoryMember.setFromDate("");
            categoryMember.setThruDate("");
            categoryList.add(categoryMember);
            
            productType.setProductCategoryMember(productCategory);

            ProductSelectableFeatureType selectableFeature = factory.createProductSelectableFeatureType();
            
            List<FeatureType> selectableFeatureList = selectableFeature.getFeature();
            FeatureType selFeature = (FeatureType)factory.createFeatureType();
            selFeature.setFeatureId("");
            List valueSelList = selFeature.getValue();
            valueSelList.add("");
            selFeature.setDescription("");
            selFeature.setFromDate("");
            selFeature.setThruDate("");
            selFeature.setDescription("");
            selFeature.setSequenceNum("");
            selectableFeatureList.add(selFeature);
            productType.setProductSelectableFeature(selectableFeature);
            
            ProductDescriptiveFeatureType descriptiveFeature = factory.createProductDescriptiveFeatureType();
            
            List<FeatureType> descriptiveFeatureList = descriptiveFeature.getFeature();
            FeatureType delFeature = (FeatureType)factory.createFeatureType();
            delFeature.setFeatureId("");
            List valueDesList = delFeature.getValue();
            valueDesList.add("");
            delFeature.setDescription("");
            delFeature.setFromDate("");
            delFeature.setThruDate("");
            delFeature.setDescription("");
            delFeature.setSequenceNum("");
            descriptiveFeatureList.add(delFeature);
            productType.setProductDescriptiveFeature(descriptiveFeature);
            
            ProductImageType productImage = factory.createProductImageType();
            
            PlpSwatchType plpSwatch = factory.createPlpSwatchType();
            plpSwatch.setUrl("");
            plpSwatch.setThruDate("");
            productImage.setPlpSwatch(plpSwatch);
            
            PdpSwatchType pdpSwatch = factory.createPdpSwatchType();
            pdpSwatch.setUrl("");
            pdpSwatch.setThruDate("");
            productImage.setPdpSwatch(pdpSwatch);
            
            PlpSmallImageType plpSmallImage = factory.createPlpSmallImageType();
            plpSmallImage.setUrl("");
            plpSmallImage.setThruDate("");
            productImage.setPlpSmallImage(plpSmallImage);
            
            PlpSmallAltImageType plpSmallAltImage = factory.createPlpSmallAltImageType();
            plpSmallAltImage.setUrl("");
            plpSmallAltImage.setThruDate("");
            productImage.setPlpSmallAltImage(plpSmallAltImage);
            
            PdpThumbnailImageType pdpThumbnailImage = factory.createPdpThumbnailImageType();
            pdpThumbnailImage.setUrl("");
            pdpThumbnailImage.setThruDate("");
            productImage.setPdpThumbnailImage(pdpThumbnailImage);
            
            PdpLargeImageType pdpLargeImage = factory.createPdpLargeImageType();
            pdpLargeImage.setUrl("");
            pdpLargeImage.setThruDate("");
            productImage.setPdpLargeImage(pdpLargeImage);
            
            PdpDetailImageType pdpDetailImage = factory.createPdpDetailImageType();
            pdpDetailImage.setUrl("");
            pdpDetailImage.setThruDate("");
            productImage.setPdpDetailImage(pdpDetailImage);
            
            PdpVideoType pdpVideo = factory.createPdpVideoType();
            pdpVideo.setUrl("");
            pdpVideo.setThruDate("");
            productImage.setPdpVideoImage(pdpVideo);
            
            PdpVideo360Type pdpVideo360 = factory.createPdpVideo360Type();
            pdpVideo360.setUrl("");
            pdpVideo360.setThruDate("");
            productImage.setPdpVideo360Image(pdpVideo360);
            
            PdpAlternateImageType pdpAlternateImage = factory.createPdpAlternateImageType();
            List pdpAdditionalImages = pdpAlternateImage.getPdpAdditionalImage();
            PdpAdditionalImageType pdpAdditionalImage = factory.createPdpAdditionalImageType();
            	   
            PdpAdditionalThumbImageType pdpAdditionalThumbImage = factory.createPdpAdditionalThumbImageType();
            pdpAdditionalThumbImage.setUrl("");
            pdpAdditionalThumbImage.setThruDate("");
            pdpAdditionalImage.setPdpAdditionalThumbImage(pdpAdditionalThumbImage);
            		
            PdpAdditionalLargeImageType pdpAdditionalLargeImage = factory.createPdpAdditionalLargeImageType();
            pdpAdditionalLargeImage.setUrl("");
            pdpAdditionalLargeImage.setThruDate("");
            pdpAdditionalImage.setPdpAdditionalLargeImage(pdpAdditionalLargeImage);
            	    
            PdpAdditionalDetailImageType pdpAdditionalDetailImage = factory.createPdpAdditionalDetailImageType();
            pdpAdditionalDetailImage.setUrl("");
            pdpAdditionalDetailImage.setThruDate("");
            pdpAdditionalImage.setPdpAdditionalDetailImage(pdpAdditionalDetailImage);
            pdpAdditionalImages.add(pdpAdditionalImage);
            productImage.setPdpAlternateImage(pdpAlternateImage);
            productType.setProductImage(productImage);
            
            GoodIdentificationType goodIdentification = factory.createGoodIdentificationType();
            goodIdentification.setSku("");
            goodIdentification.setIsbn("");
            goodIdentification.setGoogleId("");
            goodIdentification.setManuId("");
            productType.setProductGoodIdentification(goodIdentification);
            
            ProductInventoryType productInventory = factory.createProductInventoryType();
            productInventory.setBigfishInventoryTotal("");
            productInventory.setBigfishInventoryWarehouse("");
            productType.setProductInventory(productInventory);
            
            ProductAttributeType productAttribute = factory.createProductAttributeType();
            productAttribute.setPdpSelectMultiVariant("");
            productAttribute.setPdpCheckoutGiftMessage("");
            productAttribute.setPdpQtyMin("");
            productAttribute.setPdpQtyMax("");
            productAttribute.setPdpQtyDefault("");
            productAttribute.setPdpInStoreOnly("");
            productType.setProductAttribute(productAttribute);            
            
            productList.add(productType);
    	} catch (Exception e) {
    		Debug.logError(e, module);
    	}
    	
    }
    
    public static void createProductAssocXmlSample(ObjectFactory factory, List productAssocList) {
    	try {
    		AssociationType productAssoc = factory.createAssociationType();
            productAssoc.setMasterProductId("");
            productAssoc.setMasterProductIdTo("");
            productAssoc.setProductAssocType("");
            productAssoc.setFromDate("");
            productAssoc.setThruDate("");
            productAssocList.add(productAssoc);
    	} catch (Exception e) {
    		Debug.logError(e, module);
    	}
    }

    public static void createProductFacetGroupSample(ObjectFactory factory, List facetGroupList) {
    	try {
    		FacetCatGroupType facetCatGroup = factory.createFacetCatGroupType();
    		facetCatGroup.setProductCategoryId("");
    		facetCatGroup.setSequenceNum("");
    		facetCatGroup.setFromDate("");
    		facetCatGroup.setThruDate("");
    		facetCatGroup.setMinDisplay("");
    		facetCatGroup.setMaxDisplay("");
    		facetCatGroup.setTooltip("");
    		FacetGroupType facetGroup = factory.createFacetGroupType();
    		facetGroup.setFacetGroupId("");
    		facetGroup.setDescription("");
    		facetCatGroup.setFacetGroup(facetGroup);
            facetGroupList.add(facetCatGroup);
    	} catch (Exception e) {
    		Debug.logError(e, module);
    	}
    }
    
    public static void createProductFacetValueSample(ObjectFactory factory, List facetValueList) {
    	try {
    		FacetValueType facetValue = factory.createFacetValueType();
    		facetValue.setProductFeatureGroupId("");
    		facetValue.setProductFeatureId("");
    		facetValue.setDescription("");
    		facetValue.setFromDate("");
    		facetValue.setThruDate("");
    		facetValue.setSequenceNum("");
    		PlpSwatchType plpSwatch = factory.createPlpSwatchType();
            plpSwatch.setUrl("");
            facetValue.setPlpSwatch(plpSwatch);
            PdpSwatchType pdpSwatch = factory.createPdpSwatchType();
            pdpSwatch.setUrl("");
            facetValue.setPdpSwatch(pdpSwatch);
            facetValueList.add(facetValue);
    	} catch (Exception e) {
    		Debug.logError(e, module);
    	}
    }
    
    public static void createProductManufacturerSample(ObjectFactory factory, List manufacturerList) {
    	try {
            ManufacturerType manufacturer= factory.createManufacturerType();
            manufacturer.setManufacturerId("");
            manufacturer.setManufacturerName("");
            manufacturer.setDescription("");
            manufacturer.setLongDescription("");
            ManufacturerImageType manufacturerImage = factory.createManufacturerImageType();
            manufacturerImage.setUrl("");
            manufacturerImage.setThruDate("");
            manufacturer.setManufacturerImage(manufacturerImage);
            
            ManufacturerAddressType manufacturerAddress = factory.createManufacturerAddressType();
            manufacturerAddress.setAddress1("");
            manufacturerAddress.setCityTown("");
            manufacturerAddress.setCountry("");
            manufacturerAddress.setStateProvince("");
            manufacturerAddress.setZipPostCode("");
            manufacturer.setAddress(manufacturerAddress);
            
            manufacturerList.add(manufacturer);
            
    	} catch (Exception e) {
    		Debug.logError(e, module);
    	}
    }

    public static Map<String, Object> importProductRatingXML(DispatchContext ctx, Map<String, ?> context) {
        LocalDispatcher dispatcher = ctx.getDispatcher();
        _delegator = ctx.getDelegator();
        List<String> messages = FastList.newInstance();

        String xmlDataFilePath = (String)context.get("xmlDataFile");
        String xmlDataDirPath = (String)context.get("xmlDataDir");
        String loadImagesDirPath=(String)context.get("productLoadImagesDir");
        List processedProductIdList = (List)context.get("processedProductIdList");
        String imageUrl = (String)context.get("imageUrl");
        Boolean removeAll = (Boolean) context.get("removeAll");
        Boolean autoLoad = (Boolean) context.get("autoLoad");

        if (removeAll == null) removeAll = Boolean.FALSE;
        if (autoLoad == null) autoLoad = Boolean.FALSE;

        File inputWorkbook = null;
        String tempDataFile = null;
        File baseDataDir = null;
        File baseFilePath = null;
        BufferedWriter fOutProduct=null;
        if (UtilValidate.isNotEmpty(xmlDataFilePath) && UtilValidate.isNotEmpty(xmlDataDirPath)) 
        {
        	baseFilePath = new File(xmlDataFilePath);
            try 
            {
                URL xlsDataFileUrl = UtilURL.fromFilename(xmlDataFilePath);
                InputStream ins = xlsDataFileUrl.openStream();

                if (ins != null && (xmlDataFilePath.toUpperCase().endsWith("XML"))) 
                {
                    baseDataDir = new File(xmlDataDirPath);
                    if (baseDataDir.isDirectory() && baseDataDir.canWrite()) 
                    {

                        // ############################################
                        // move the existing xml files in dump directory
                        // ############################################
                        File dumpXmlDir = null;
                        File[] fileArray = baseDataDir.listFiles();
                        for (File file: fileArray) 
                        {
                            try 
                            {
                                if (file.getName().toUpperCase().endsWith("XML")) 
                                {
                                    if (dumpXmlDir == null) 
                                    {
                                        dumpXmlDir = new File(baseDataDir, "dumpxml_"+UtilDateTime.nowDateString());
                                    }
                                    FileUtils.copyFileToDirectory(file, dumpXmlDir);
                                    file.delete();
                                }
                            } 
                            catch (IOException ioe) 
                            {
                                Debug.logError(ioe, module);
                            } 
                            catch (Exception exc) 
                            {
                                Debug.logError(exc, module);
                            }
                        }
                        // ######################################
                        //save the temp xls data file on server 
                        // ######################################
                        try 
                        {
                        	tempDataFile = UtilDateTime.nowAsString()+"."+FilenameUtils.getExtension(xmlDataFilePath);
                            inputWorkbook = new File(baseDataDir,  tempDataFile);
                            if (inputWorkbook.createNewFile()) 
                            {
                                Streams.copy(ins, new FileOutputStream(inputWorkbook), true, new byte[1]); 
                            }
                        } 
                        catch (IOException ioe) 
                        {
                            Debug.logError(ioe, module);
                        } 
                        catch (Exception exc) 
                        {
                            Debug.logError(exc, module);
                        }
                    }
                    else 
                    {
                        messages.add("xml data dir path not found or can't be write");
                    }
                }
                else 
                {
                    messages.add(" path specified for Excel sheet file is wrong , doing nothing.");
                }

            } 
            catch (IOException ioe) 
            {
                Debug.logError(ioe, module);
            } 
            catch (Exception exc) 
            {
                Debug.logError(exc, module);
            }
        }
        else 
        {
            messages.add("No path specified for Excel sheet file or xml data direcotry, doing nothing.");
        }

        // ######################################
        //read the temp xls file and generate xml 
        // ######################################
        try 
        {
        if (inputWorkbook != null && baseDataDir  != null) 
        {
        	try 
        	{
        		JAXBContext jaxbContext = JAXBContext.newInstance("com.osafe.feeds.osafefeeds");
            	Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            	JAXBElement<BigFishProductRatingFeedType> bfProductRatingFeedType = (JAXBElement<BigFishProductRatingFeedType>)unmarshaller.unmarshal(inputWorkbook);
            	
            	List<ProductRatingType> productRatingList = bfProductRatingFeedType.getValue().getProductRating();
            	
            	if(productRatingList.size() > 0) 
            	{
            		List dataRows = buildProductRatingXMLDataRows(productRatingList);
            		buildProductRating(dataRows, xmlDataDirPath,messages, processedProductIdList);
            	}
        	} 
        	catch (Exception e) 
        	{
        		Debug.logError(e, module);
			}
        	finally 
        	{
                try 
                {
                    if (fOutProduct != null) 
                    {
                    	fOutProduct.close();
                    }
                } 
                catch (IOException ioe) 
                {
                    Debug.logError(ioe, module);
                }
            }
        }
        
        // ##############################################
        // move the generated xml files in done directory
        // ##############################################
        File doneXmlDir = new File(baseDataDir, Constants.DONE_XML_DIRECTORY_PREFIX+UtilDateTime.nowDateString());
        File[] fileArray = baseDataDir.listFiles();
        for (File file: fileArray) 
        {
            try 
            {
                if (FilenameUtils.getExtension(file.getName()).equalsIgnoreCase("XML")) 
                {
                	if(!(file.getName().equals(tempDataFile)) && (!file.getName().equals(baseFilePath.getName())))
                	{
                		FileUtils.copyFileToDirectory(file, doneXmlDir);
                        file.delete();
                	}
                }
            } 
            catch (IOException ioe) 
            {
                Debug.logError(ioe, module);
            } 
            catch (Exception exc) 
            {
                Debug.logError(exc, module);
            }
        }

        // ######################################################################
        // call service for insert row in database  from generated xml data files 
        // by calling service entityImportDir if autoLoad parameter is true
        // ######################################################################
        if (autoLoad) 
        {
            Map entityImportDirParams = UtilMisc.toMap("path", doneXmlDir.getPath(), 
                                                     "userLogin", context.get("userLogin"));
             try 
             {
                 Map result = dispatcher.runSync("entityImportDir", entityImportDirParams);
                 if(UtilValidate.isNotEmpty(result.get("responseMessage")) && result.get("responseMessage").equals("error"))
	             {
	                 return ServiceUtil.returnError(result.get("errorMessage").toString());
	             }
                 List<String> serviceMsg = (List)result.get("messages");
                 for (String msg: serviceMsg) 
                 {
                     messages.add(msg);
                 }
             } 
             catch (Exception exc) 
             {
                 Debug.logError(exc, module);
             }
        }
    } 
    catch (Exception exc) 
    {
            Debug.logError(exc, module);
    }
    finally 
    {
            inputWorkbook.delete();
    } 
                
    Map<String, Object> resp = UtilMisc.toMap("messages", (Object) messages);
    return resp;  

    }
    
    public static List buildProductRatingXMLDataRows(List<ProductRatingType> productRatingList) {
		List dataRows = FastList.newInstance();

		try {
			
            for (int rowCount = 0 ; rowCount < productRatingList.size() ; rowCount++) {
            	ProductRatingType productRating = (ProductRatingType) productRatingList.get(rowCount);
            
            	Map mRows = FastMap.newInstance();
            	mRows.put("productStoreId",productRating.getProductStoreId());
                mRows.put("productId",productRating.getProductId());
                mRows.put("sku",productRating.getSku());
                mRows.put("productRatingScore",productRating.getProductRatingScore());
                mRows = formatProductXLSData(mRows);
                dataRows.add(mRows);
             }
    	}
      	catch (Exception e) {
      		e.printStackTrace();
   	    }
      	return dataRows;
   }
    
    private static void buildProductRating(List dataRows,String xmlDataDirPath, List messages, List processedProductIdList) 
    {

        File fOutFile =null;
        BufferedWriter bwOutFile=null;
        String categoryImageName=null;
    	String productId = null;
		try 
		{
	        fOutFile = new File(xmlDataDirPath, "000-ProductRating.xml");
            if (fOutFile.createNewFile()) 
            {
            	bwOutFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fOutFile), "UTF-8"));

                writeXmlHeader(bwOutFile);
                
                for (int i=0 ; i < dataRows.size() ; i++) 
                {
                    StringBuilder  rowString = new StringBuilder();
	            	Map mRow = (Map)dataRows.get(i);
	            	productId = (String)mRow.get("productId");
	            	String sku = (String)mRow.get("sku");
	            	String productIdToCheck = "";
	            	if(UtilValidate.isNotEmpty(productId))
	            	{
	            		productIdToCheck = productId;
	            	}
	            	else
	            	{
	            		if(UtilValidate.isNotEmpty(sku))
		            	{
		            		productIdToCheck = sku;
		            	}
	            	}
	            	if(processedProductIdList.contains(productIdToCheck))
	            	{
	            		if(UtilValidate.isEmpty(productId) && UtilValidate.isNotEmpty(sku)) 
		            	{
		            		List<GenericValue> goodIdentificationList = _delegator.findByAnd("GoodIdentification", UtilMisc.toMap("goodIdentificationTypeId", "SKU", "idValue", sku));
		            		if(UtilValidate.isNotEmpty(goodIdentificationList)) 
		            		{
		            			productId = EntityUtil.getFirst(goodIdentificationList).getString("productId");
		            		}
		            	}
		            	if(UtilValidate.isNotEmpty(productId)) 
		            	{
		            		rowString.append("<" + "ProductCalculatedInfo" + " ");
	                        rowString.append("productId" + "=\"" + productId + "\" ");
	                        
	                        if(mRow.get("productRatingScore") != null) 
	                        {
	                        	String productRatingScore = (String)mRow.get("productRatingScore");
	                        	if(productRatingScore.equals(""))
	                            {
	                            	productRatingScore = null;
	                            }
	                        	rowString.append("averageCustomerRating" + "=\"" + productRatingScore + "\" ");
	                        }
	                        rowString.append("/>");
	                        bwOutFile.write(rowString.toString());
		            	}
	                    bwOutFile.newLine();
	            	}
	            }
                bwOutFile.flush();
         	    writeXmlFooter(bwOutFile);
            }
    	}
      	 catch (Exception e) 
      	 {
             Debug.logError(e.getMessage(), module + ".buildProductRating");
             messages.add("Error: prcessing product Id[" + productId + "]. In Module:" + module + ".buildProductRating");
   	     }
         finally 
         {
             try {
                 if (bwOutFile != null) {
                	 bwOutFile.close();
                 }
             } catch (IOException ioe) {
                 Debug.logError(ioe.getMessage(), module + ".buildProductRating");
             }
         }
      	 
       }
    public static Map<String, Object> importReevooCsvToFeed(DispatchContext dctx, Map<String, ?> context) {

        Map<String, Object> result = ServiceUtil.returnSuccess();
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        String reevooCsvFileLoc = (String)context.get("reevooCsvFileLoc");
        
        if (UtilValidate.isNotEmpty(reevooCsvFileLoc)) {
            try {
                // ######################################
                // make the input stram for csv data file
                // ######################################
                URL reevooCsvFileUrl = UtilURL.fromFilename(reevooCsvFileLoc);
                InputStream ins = reevooCsvFileUrl.openStream();
                if (ins != null && (reevooCsvFileLoc.toUpperCase().endsWith("CSV"))) {

                    ObjectFactory factory = new ObjectFactory();
                    BigFishProductRatingFeedType bfProductRatingFeedType = factory.createBigFishProductRatingFeedType();
                    String downloadTempDir = FeedsUtil.getFeedDirectory("ProductRating");

                    String productRatingFileName = "ProductRating";
                    productRatingFileName = productRatingFileName + "_" + (OsafeAdminUtil.convertDateTimeFormat(UtilDateTime.nowTimestamp(), "yyyy-MM-dd-HHmm"));
                    productRatingFileName = UtilValidate.stripWhitespace(productRatingFileName) + ".xml";

                    if (!new File(downloadTempDir).exists()) {
                        new File(downloadTempDir).mkdirs();
                    }
                    File file = new File(downloadTempDir, productRatingFileName);
                    List productRatingList = bfProductRatingFeedType.getProductRating();

                    // #######################
                    // Read csv file as String
                    // #######################
                    String csvFile  = UtilIO.readString(ins);
                    csvFile = csvFile.replaceAll("\\r", "");
                    String[] records = csvFile.split("\\n");
                    // ########################################
                    // Start row from index 1 for remove header
                    // ########################################
                    for (int i = 1; i < records.length; i++) {
                        try {
                            if (records[i] != null) {
                                String str = records[i].trim();
                                String[] map = str.split(",");
                                if (map.length == 2) {
                                    ProductRatingType productRating = factory.createProductRatingType();
                                    productRating.setSku(map[0]);
                                    productRating.setProductRatingScore(map[1]);
                                    productRatingList.add(productRating);
                                }
                            }
                        } catch(Exception e) {}
                    }
                    FeedsUtil.marshalObject(new JAXBElement<BigFishProductRatingFeedType>(new QName("", "BigFishProductRatingFeed"), BigFishProductRatingFeedType.class, null, bfProductRatingFeedType), file);
                    result.put("feedFile", file);
                    result.put("feedFileAsString", FileUtil.readTextFile(file, Boolean.TRUE).toString());
                }
                
            } catch (Exception exc) {
                ServiceUtil.returnError("Error occured in creating product rating feed xml from reevoo csv");
            }
        }
        return result;
    }

    public static void createProductRatingXmlFromXls(ObjectFactory factory, List productRatingList, List dataRows, String productStoreId) 
    {
    	try 
    	{
    		ProductRatingType productRating = null;
    		for (int i=0 ; i < dataRows.size() ; i++) 
    		{
    			Map mRow = (Map)dataRows.get(i);
    			productRating = factory.createProductRatingType();
    			productRating.setProductId((String)mRow.get("productId"));
    			productRating.setProductRatingScore((String)mRow.get("ratingScore"));
    			if(UtilValidate.isNotEmpty(productStoreId))
    			{
    				productRating.setProductStoreId(productStoreId);
    			}
    			else
    			{
    				productRating.setProductStoreId("");
    			}
    			productRatingList.add(productRating);
    		}
    	
    	} catch (Exception e) 
    	{
    		Debug.logError(e, module);
    	}
    }
    
    
    public static void createStoreXmlFromXls(ObjectFactory factory, List storeList, List dataRows, String productStoreId) 
    {
    	try 
    	{
    		
    		StoreType store = null;
    		for (int i=0 ; i < dataRows.size() ; i++) 
    		{
    			Map mRow = (Map)dataRows.get(i);
    			store = factory.createStoreType();
    			
    			store.setStoreId((String)mRow.get("storeId"));
    			store.setStoreCode((String)mRow.get("storeCode"));
    			store.setStoreName((String)mRow.get("storeName"));
    			    			
    			StoreAddressType storeAddress = factory.createStoreAddressType();
    			
    			storeAddress.setCountry((String)mRow.get("country"));
    			storeAddress.setAddress1((String)mRow.get("address1"));
    			storeAddress.setAddress2((String)mRow.get("address2"));
    			storeAddress.setAddress3((String)mRow.get("address3"));
    			storeAddress.setCityTown((String)mRow.get("cityOrTown"));
    			storeAddress.setStateProvince((String)mRow.get("stateOrProvince"));
    			storeAddress.setZipPostCode((String)mRow.get("zipOrPostcode"));
    			storeAddress.setStorePhone((String)mRow.get("telephoneNumber"));
    			store.setStoreAddress(storeAddress);
    			
    			store.setStatus((String)mRow.get("status"));
    			store.setOpeningHours((String)mRow.get("openingHours"));
    			store.setStoreNotice((String)mRow.get("storeNotice"));
    			store.setStoreContentSpot((String)mRow.get("contentSpot"));
    			store.setGeoCodeLong((String)mRow.get("geoCodeLong"));
    			store.setGeoCodeLat((String)mRow.get("geoCodeLat"));
    			
    			if(UtilValidate.isNotEmpty(productStoreId))
    			{
    				store.setProductStoreId(productStoreId);
    			}
    			else
    			{
    				store.setProductStoreId("");
    			}
    			storeList.add(store);
    		}
    	
    	} catch (Exception e) 
    	{
    		Debug.logError(e, module);
    	}
    }
    
    public static void createOrderStatusUpdateXmlFromXls(ObjectFactory factory, List orderStatusUpdateList, List dataRows, String productStoreId) 
    {
    	try 
    	{
    		OrderStatusType orderStatusType = null;
    		for (int i=0 ; i < dataRows.size() ; i++) 
    		{
    			Map mRow = (Map)dataRows.get(i);
    			orderStatusType = factory.createOrderStatusType();
    			orderStatusType.setOrderId((String)mRow.get("orderId"));
    			orderStatusType.setOrderStatus((String)mRow.get("orderStatus"));
    			orderStatusType.setOrderShipDate((String)mRow.get("orderShipDate"));
    			orderStatusType.setOrderShipCarrier((String)mRow.get("orderShipCarrier"));
    			orderStatusType.setOrderShipMethod((String)mRow.get("orderShipMethod"));
    			orderStatusType.setOrderTrackingNumber((String)mRow.get("orderTrackingNumber"));
    			orderStatusType.setOrderNote((String)mRow.get("orderNote"));
    			if(UtilValidate.isNotEmpty(productStoreId))
    			{
    				orderStatusType.setProductStoreId(productStoreId);
    			}
    			else
    			{
    				orderStatusType.setProductStoreId("");
    			}
    			orderStatusUpdateList.add(orderStatusType);
    		}
    	
    	} catch (Exception e) 
    	{
    		Debug.logError(e, module);
    	}
    }

    public static List createFeatureVariantProductId(List selectableFeatureList, String selectableFeature)
    {
    	if (UtilValidate.isNotEmpty(selectableFeature))
    	{
    		List tempSelectableFeatureList = FastList.newInstance();
        	int iFeatIdx = selectableFeature.indexOf(':');
        	if (iFeatIdx > -1)
        	{
            	String featureType = selectableFeature.substring(0,iFeatIdx).trim();
            	String sFeatures = selectableFeature.substring(iFeatIdx +1);
                String[] featureTokens = sFeatures.split(",");
            	HashMap mFeatureMap = new HashMap();
            	
            	if(selectableFeatureList.size() > 0)
            	{
            		for (int i=0; i < selectableFeatureList.size();i++)
                    {
            			for (int f=0; f < featureTokens.length; f++)
                        {
            				
            				ArrayList featureList = new ArrayList();
            				ArrayList tempList =  (ArrayList)selectableFeatureList.get(i);
            				featureList.addAll(tempList);
            				featureList.add(featureType+"~"+featureTokens[f].trim());
            				tempSelectableFeatureList.add(featureList);
                        }
                    }
            	}
            	else
            	{
            		for (int f=0; f < featureTokens.length; f++)
                    {
            			ArrayList featureList = new ArrayList();
            			featureList.add(featureType+"~"+featureTokens[f].trim());
            			selectableFeatureList.add(featureList);
                    }	
            	}
        	}
        	if(tempSelectableFeatureList.size() > 0)
        	{
        		selectableFeatureList = tempSelectableFeatureList;
        	}
    	}
    	
    	return selectableFeatureList;
    }
    
    
    public static Map<String, Object> validateProductData(DispatchContext ctx, Map<String, ?> context) 
    {
    	
        LocalDispatcher dispatcher = ctx.getDispatcher();
        _delegator = ctx.getDelegator();
        Locale locale = (Locale) context.get("locale");
        
        List<Map> productCatDataList = (List) context.get("productCatDataList");
        List<Map> productDataList = (List) context.get("productDataList");
        List<Map> productAssocDataList = (List) context.get("productAssocDataList");
        List<Map> productFacetGroupDataList = (List) context.get("productFacetGroupDataList");
        List<Map> productFacetValueDataList = (List) context.get("productFacetValueDataList");
        List<Map> manufacturerDataList = (List) context.get("manufacturerDataList");
        
        List<String> prodCatErrorList = FastList.newInstance();
        List<String> prodCatWarningList = FastList.newInstance();
        List<String> serviceLogProdCatMessageList = FastList.newInstance();
        
        List<String> productErrorList = FastList.newInstance();
        List<String> productWarningList = FastList.newInstance();
        List<String> serviceLogProductMessageList = FastList.newInstance();

        List<String> productAssocErrorList = FastList.newInstance();
        List<String> productAssocWarningList = FastList.newInstance();
        List<String> serviceLogProductAssocMessageList = FastList.newInstance();
        
        List<String> productFacetGroupErrorList = FastList.newInstance();
        List<String> productFacetGroupWarningList = FastList.newInstance();
        List<String> serviceLogProductFacetGroupMessageList = FastList.newInstance();
        
        List<String> productFacetValueErrorList = FastList.newInstance();
        List<String> productFacetValueWarningList = FastList.newInstance();
        List<String> serviceLogProductFacetValueMessageList = FastList.newInstance();

        List<String> productManufacturerErrorList = FastList.newInstance();
        List<String> productManufacturerWarningList = FastList.newInstance();
        List<String> serviceLogProductManufacturerMessageList = FastList.newInstance();

        List<String> errorMessageList = FastList.newInstance();
        
        Set prevProdCatList = FastSet.newInstance();
        List existingProdCatIdList = FastList.newInstance();

        Map result = ServiceUtil.returnSuccess();
        try
        {
        	List existingProdCatList = _delegator.findList("ProductCategory", null, null, null, null, false);
            Map<String, List> itenNoMap = FastMap.newInstance();
            Map<String, List> prodNoMap = FastMap.newInstance();
            if(UtilValidate.isNotEmpty(existingProdCatList))
            {
                existingProdCatIdList = EntityUtil.getFieldListFromEntityList(existingProdCatList, "productCategoryId", true);
            }

            Set productFeatureSet = FastSet.newInstance();
            Set productFeatureGroupSet = FastSet.newInstance();
            Map mFeatureTypeMap = FastMap.newInstance();
            int totalSelectableFeature = 5;
            int totalDescriptiveFeature = 5;
            List productFeatures = _delegator.findList("ProductFeature", null, null, null, null, false);
            List productFeatureIds = FastList.newInstance();
            if(UtilValidate.isNotEmpty(productFeatures))
            {
            	productFeatureIds = EntityUtil.getFieldListFromEntityList(productFeatures,"productFeatureId", true);
            }

            String osafeThemeServerPath = FlexibleStringExpander.expandString(OSAFE_PROP.getString("osafeThemeServer"), context);
            String osafeThemeImagePath = osafeThemeServerPath; 
            //Get the DEFAULT_IMAGE_DIRECTORY path from OsafeImagePath.xml

            String XmlFilePath = FlexibleStringExpander.expandString(UtilProperties.getPropertyValue("osafeAdmin.properties", "image-location-preference-file"), context);
            	
            List<Map<Object, Object>> imageLocationPrefList = OsafeManageXml.getListMapsFromXmlFile(XmlFilePath);

            Map<Object, Object> imageLocationMap = new HashMap<Object, Object>();

            for(Map<Object, Object> imageLocationPref : imageLocationPrefList) 
            {
                imageLocationMap.put(imageLocationPref.get("key"), imageLocationPref.get("value"));
            }

            String defaultImageDirectory = (String)imageLocationMap.get("DEFAULT_IMAGE_DIRECTORY");
            if(UtilValidate.isNotEmpty(defaultImageDirectory)) 
            {
                osafeThemeImagePath = osafeThemeImagePath + defaultImageDirectory;
            }

            //Validation

            List newProdCatIdList = FastList.newInstance();
            List itemNoList = FastList.newInstance();

            //Validation for Product Category
            Integer rowNo = new Integer(1);
            for(Map productCategory : productCatDataList) 
            {
            	String errorLogText = UtilProperties.getMessage(resource, "ErrorLogTextLabel", UtilMisc.toMap("rowNo", rowNo.toString()), locale);
				String warningLogText =  UtilProperties.getMessage(resource, "WarningLogTextLabel", UtilMisc.toMap("rowNo", rowNo.toString()), locale);
				
                String parentCategoryId = (String)productCategory.get("parentCategoryId");
                String productCategoryId = (String)productCategory.get("productCategoryId");
                String categoryName = (String)productCategory.get("categoryName");
                String description = (String)productCategory.get("description");
                String longDescription = (String)productCategory.get("longDescription");
                String plpImageName = (String)productCategory.get("plpImageName");
                String thruDate = (String)productCategory.get("thruDate");
                String fromDate = (String)productCategory.get("fromDate");
                
                serviceLogProdCatMessageList.add("IN PROGRESS: [Processing row #"+rowNo.toString()+" Parent Category ID: "+parentCategoryId+" Product Category ID: "+ productCategoryId+"]");
                
                if(UtilValidate.isNotEmpty(productCategoryId))
                {
                    if(!OsafeAdminUtil.isValidId(productCategoryId))
                    {
                        prodCatErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InValidIdError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "CategoryId", "idData", productCategoryId), locale));
                        serviceLogProdCatMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InValidIdError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "CategoryId", "idData", productCategoryId), locale));
                    }
                    if(productCategoryId.length() > 20)
                    {
                    	prodCatErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "IdLengthExceedError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "Category ID", "fieldData", productCategoryId), locale));
                    	serviceLogProdCatMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "IdLengthExceedError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "Category ID", "fieldData", productCategoryId), locale));
                    }
                    prevProdCatList.add(productCategoryId);
                }

                if(UtilValidate.isNotEmpty(parentCategoryId))
                {
                	boolean parentCategoryIdMatch = false;
                    if(prevProdCatList.contains(parentCategoryId.trim()))
                    {
                    	parentCategoryIdMatch = true;
                    }
                    else
                    {
                    	if(existingProdCatIdList.contains(parentCategoryId.trim()))
                        {
                    		parentCategoryIdMatch = true;
                        }
                    }
                
                    if(!parentCategoryIdMatch)
                    {
                    	prodCatErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ParentCategoryIdMatchingError", UtilMisc.toMap("rowNo", rowNo.toString(), "parentCategoryId", parentCategoryId), locale));
                    	serviceLogProdCatMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ParentCategoryIdMatchingError", UtilMisc.toMap("rowNo", rowNo.toString(), "parentCategoryId", parentCategoryId), locale));
                    	
                    }
                    
                    if(UtilValidate.isEmpty(productCategoryId))
                    {
                        prodCatErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ParentCategoryIdAssociationError", UtilMisc.toMap("rowNo", rowNo.toString(), "parentCategoryId", parentCategoryId), locale));
                        serviceLogProdCatMessageList.add(errorLogText +UtilProperties.getMessage("OSafeAdminUiLabels", "ParentCategoryIdAssociationError", UtilMisc.toMap("rowNo", rowNo.toString(), "parentCategoryId", parentCategoryId), locale));
                                                 
                    }
                    else 
                    {
                        newProdCatIdList.add(productCategoryId);
                    }
                }
                else
                {
                	prodCatErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "BlankParentCategoryIdError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                	serviceLogProdCatMessageList.add(errorLogText +UtilProperties.getMessage("OSafeAdminUiLabels", "BlankParentCategoryIdError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                	
                }
                if(UtilValidate.isEmpty(categoryName))
                {
                    prodCatErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "BlankCategoryNameError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                    serviceLogProdCatMessageList.add(errorLogText +UtilProperties.getMessage("OSafeAdminUiLabels", "BlankCategoryNameError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                    
                }
                else
                {
                  if(categoryName.length() > 100)
                    {
                    	prodCatErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "CatNameLengthExceedError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "Category Name", "fieldData", categoryName), locale));
                    	serviceLogProdCatMessageList.add(errorLogText +UtilProperties.getMessage("OSafeAdminUiLabels", "CatNameLengthExceedError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "Category Name", "fieldData", categoryName), locale));
                    	
                    }
                } 
                
                if(UtilValidate.isEmpty(description))
                {
                	prodCatWarningList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "BlankCategoryDescWarning", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                	serviceLogProdCatMessageList.add(warningLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "BlankCategoryDescWarning", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                }
                else
                {
                    if(description.length() > 255)
                    {
                    	prodCatErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "DescLengthExceedError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "Category Description", "fieldData", description), locale));
                    	serviceLogProdCatMessageList.add(errorLogText +UtilProperties.getMessage("OSafeAdminUiLabels", "DescLengthExceedError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "Category Description", "fieldData", description), locale));
                    	
                    }
                }  
                if(UtilValidate.isEmpty(longDescription))
                {
                    prodCatWarningList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "BlankCategoryLongDescWarning", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                    serviceLogProdCatMessageList.add(warningLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "BlankCategoryLongDescWarning", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                    
                }
                if(UtilValidate.isNotEmpty(plpImageName))
                {
                	if(!UtilValidate.isUrl(plpImageName))
                	{
            	        boolean isFileExist = (new File(osafeThemeImagePath, plpImageName)).exists();
            	        if(!isFileExist)
            	        {
            	            prodCatWarningList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "PLPImageNotFoundWarning", UtilMisc.toMap("rowNo", rowNo.toString(), "plpImageData", plpImageName), locale));
            	            serviceLogProdCatMessageList.add(warningLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "PLPImageNotFoundWarning", UtilMisc.toMap("rowNo", rowNo.toString(), "plpImageData", plpImageName), locale));
            	            
            	        }
                	}
                }
                
                if(UtilValidate.isNotEmpty(fromDate))
                {
                    if(!OsafeAdminUtil.isValidDate(fromDate))
                    {
                    	prodCatErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InValidProductCategoryFromDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", fromDate, "idData", productCategoryId), locale));
                    	serviceLogProdCatMessageList.add(errorLogText +UtilProperties.getMessage("OSafeAdminUiLabels", "InValidProductCategoryFromDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", fromDate, "idData", productCategoryId), locale));
                    	
                    }    
                }
                if(UtilValidate.isNotEmpty(thruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(thruDate))
                    {
                    	prodCatErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InValidProductCategoryThruDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", thruDate, "idData", productCategoryId), locale));
                    	serviceLogProdCatMessageList.add(errorLogText +UtilProperties.getMessage("OSafeAdminUiLabels", "InValidProductCategoryThruDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", thruDate, "idData", productCategoryId), locale));
                    	
                    }    
                }
                
                if(UtilValidate.isNotEmpty(productCategoryId) && UtilValidate.isNotEmpty(parentCategoryId))
                {
                	if(productCategoryId.equals(parentCategoryId))
                	{
                		prodCatErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductCategoryIdSameError", UtilMisc.toMap("rowNo", rowNo.toString(), "productCategoryId", productCategoryId, "parentCategoryId", parentCategoryId), locale));
                		serviceLogProdCatMessageList.add(errorLogText +UtilProperties.getMessage("OSafeAdminUiLabels", "ProductCategoryIdSameError", UtilMisc.toMap("rowNo", rowNo.toString(), "productCategoryId", productCategoryId, "parentCategoryId", parentCategoryId), locale));
                		
                	}
                }
                
                serviceLogProdCatMessageList.add("ROW COMPLETE: [Processed row #"+rowNo.toString()+" Parent Category ID: "+parentCategoryId+" Product Category ID: "+ productCategoryId+"]");
                
                rowNo++;
            }

            List newManufacturerIdList = FastList.newInstance();
            List existingManufacturerIdList = FastList.newInstance();
            for(Map manufacturerData : manufacturerDataList) 
            {
                String manufacturerId = (String)manufacturerData.get("partyId");
                if(UtilValidate.isNotEmpty(manufacturerId)) 
                {
                    newManufacturerIdList.add(manufacturerId);
                } 
            }
            List<GenericValue> partyManufacturers = _delegator.findByAnd("PartyRole", UtilMisc.toMap("roleTypeId","MANUFACTURER"),UtilMisc.toList("partyId"));
            for (GenericValue partyManufacturer : partyManufacturers) 
            {
            	GenericValue party = (GenericValue) partyManufacturer.getRelatedOne("Party");
                String partyId=party.getString("partyId");
                existingManufacturerIdList.add(partyId);
            }

            //Validation for Product
            Map variantProductIdMap = FastMap.newInstance();
            Map virtualProductIdMap = FastMap.newInstance();
            Map finishedGoodProductIdMap = FastMap.newInstance();

            for(Map product : productDataList) 
            {
            	String masterProductId = (String)product.get("masterProductId");
                String productId = (String)product.get("productId");
                if(UtilValidate.isNotEmpty(masterProductId) && UtilValidate.isNotEmpty(productId) && masterProductId.equals(productId))
            	{
                	virtualProductIdMap.put(masterProductId, productId);
            	}
                else
                {
                	if(UtilValidate.isNotEmpty(masterProductId) && UtilValidate.isNotEmpty(productId) && !masterProductId.equals(productId))
                	{
                		variantProductIdMap.put(masterProductId, productId);
                	}
                	else
                	{
                		if(UtilValidate.isNotEmpty(masterProductId) && UtilValidate.isEmpty(productId))
                    	{
                			finishedGoodProductIdMap.put(masterProductId, "");
                    	}	
                	}
                }
                	
            }

            List newProductIdList = FastList.newInstance();
            List existingProductIdList = FastList.newInstance();
            rowNo = new Integer(1);

            Map masterProductIdMap = FastMap.newInstance();
            Map rowNoMasterProductIdMap = FastMap.newInstance();
            List virtualFinishProductIdList = FastList.newInstance();
            
        	List<GenericValue> productContentAndTextList = FastList.newInstance();
        	Map<String, Object> productNameByIdMap = FastMap.newInstance();
        	if(UtilValidate.isNotEmpty(productDataList))
    		{
        		productContentAndTextList = _delegator.findList("ProductContentAndText", EntityCondition.makeCondition("productContentTypeId", EntityOperator.EQUALS, "PRODUCT_NAME"), null, null, null, false);
        		for(GenericValue productContentAndText : productContentAndTextList)
    			{
        			String productNameTextData = (String) productContentAndText.get("textData");
        			if(UtilValidate.isNotEmpty(productNameTextData))
        			{
        				productNameByIdMap.put(productContentAndText.getString("productId"), productNameTextData.toUpperCase());
        			}
    			}
    		}

            for(Map product : productDataList) 
            {
            	String errorLogText = UtilProperties.getMessage(resource, "ErrorLogTextLabel", UtilMisc.toMap("rowNo", rowNo.toString()), locale);
				String warningLogText =  UtilProperties.getMessage(resource, "WarningLogTextLabel", UtilMisc.toMap("rowNo", rowNo.toString()), locale);
            	
            	String productCategoryId = (String)product.get("productCategoryId");
                String longDescription = (String)product.get("longDescription");
                String defaultPrice = (String)product.get("defaultPrice");
                String listPrice = (String)product.get("listPrice");
                String thruDate = (String)product.get("discoDate");
                String fromDate = (String)product.get("introDate");
                String internalName = (String)product.get("internalName");
            	String productName = (String)product.get("productName");
                String plpImage = (String)product.get("smallImage");
                String pdpRegularImage = (String)product.get("largeImage");
                String masterProductId = (String)product.get("masterProductId");
                String productId = (String)product.get("productId");
                String manufacturerId = (String)product.get("manufacturerId");
                String bfTotalInventory = (String)product.get("bfInventoryTot");
                String bfWHInventory = (String)product.get("bfInventoryWhs");
                String multiVariant = (String)product.get("multiVariant");
                String productHeight = (String)product.get("productHeight");
                String productWidth = (String)product.get("productWidth");
                String productDepth = (String)product.get("productDepth");
                String productWeight = (String)product.get("weight");
                String listPriceFromDate = (String)product.get("listPriceFromDate");
                String listPriceThruDate = (String)product.get("listPriceThruDate");
                String defaultPriceFromDate = (String)product.get("defaultPriceFromDate");
                String defaultPriceThruDate = (String)product.get("defaultPriceThruDate");
                String categoryFromDate= (String)product.get(productCategoryId+"_fromDate");
                String categoryThruDate= (String)product.get(productCategoryId+"_thruDate");
                String plpSwatchImageThruDate = (String)product.get("plpSwatchImageThruDate");
                String pdpSwatchImageThruDate = (String)product.get("pdpSwatchImageThruDate");
                String smallImageThruDate = (String)product.get("smallImageThruDate");
                String smallImageAltThruDate = (String)product.get("smallImageAltThruDate");
                String thumbImageThruDate = (String)product.get("thumbImageThruDate");
                String largeImageThruDate = (String)product.get("largeImageThruDate");
                String detailImageThruDate = (String)product.get("detailImageThruDate");
                String pdpVideoUrlThruDate = (String)product.get("pdpVideoUrlThruDate");
                String pdpVideo360UrlThruDate = (String)product.get("pdpVideo360UrlThruDate");
                String totPdpAdditionalThumbImage = (String)product.get("totPdpAdditionalThumbImage");
                String totPdpAdditionalLargeImage = (String)product.get("totPdpAdditionalLargeImage");
                String totPdpAdditionalDetailImage = (String)product.get("totPdpAdditionalDetailImage");
                String giftMessage = (String)product.get("giftMessage");
                String pdpQtyMin = (String)product.get("pdpQtyMin");
                String pdpQtyMax = (String)product.get("pdpQtyMax");
                String pdpQtyDefault = (String)product.get("pdpQtyDefault");
                String pdpInStoreOnly = (String)product.get("pdpInStoreOnly");
                
                if(UtilValidate.isNotEmpty(product.get("totSelectableFeatures")))
                {
                	totalSelectableFeature = Integer.parseInt((String)product.get("totSelectableFeatures"));
                }
                
                if(UtilValidate.isNotEmpty(product.get("totDescriptiveFeatures")))
                {
                	totalDescriptiveFeature = Integer.parseInt((String)product.get("totDescriptiveFeatures"));
                }
                
                serviceLogProductMessageList.add("IN PROGRESS: [Processing row #"+rowNo.toString()+" Master-Product-ID "+masterProductId+"; Product ID: "+ productId+"]");
                
                if(UtilValidate.isNotEmpty(masterProductId))
                {
                    if(!OsafeAdminUtil.isValidId(masterProductId))
                    {
                        productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InValidIdError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "Master Product ID", "idData", masterProductId), locale));
                        serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InValidIdError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "Master Product ID", "idData", masterProductId), locale));
                    }
                    rowNoMasterProductIdMap.put(rowNo, masterProductId);
                }
                if(UtilValidate.isNotEmpty(productId))
                {
                    if(!OsafeAdminUtil.isValidId(productId))
                    {
                        productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InValidIdError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "Product ID", "idData", productId), locale));
                        serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InValidIdError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "Product ID", "idData", productId), locale));
                        
                    }
                }
                
                if(UtilValidate.isNotEmpty(masterProductId) && masterProductId.length() > 20)
                {
                	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "IdLengthExceedError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "Master Product ID", "fieldData", masterProductId), locale));
                	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "IdLengthExceedError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "Master Product ID", "fieldData", masterProductId), locale));
                	
                }
                if(UtilValidate.isNotEmpty(productId) && productId.length() > 20)
                {
                	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "IdLengthExceedError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "Product ID", "fieldData", productId), locale));
                	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "IdLengthExceedError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "Product ID", "fieldData", productId), locale));
                	
                }
                if(UtilValidate.isEmpty(masterProductId))
                {
                    productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "MasterProductIdMissingError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                    serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "MasterProductIdMissingError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                    
                }
                if(UtilValidate.isNotEmpty(masterProductId))
                {
                    newProductIdList.add(masterProductId);
                }
                
                if(UtilValidate.isNotEmpty(masterProductId))
                {
                	GenericValue masterProductDef = _delegator.findByPrimaryKey("Product", UtilMisc.toMap("productId", masterProductId));
                	if(UtilValidate.isNotEmpty(masterProductDef))
                	{
                		boolean isVirtualMasterProduct = false;
                		boolean isVariantMasterProduct = false;
                		boolean isFinishedMasterProduct = false;
                		String productDefinitionDefined = "";
                		String productDefinitionToChange = "";
                		
                		if("Y".equals(masterProductDef.getString("isVirtual")))
                		{
                			isVirtualMasterProduct = true;
                			productDefinitionDefined = "VIRTUAL";
                		}
                		if("Y".equals(masterProductDef.getString("isVariant")))
                		{
                			isVariantMasterProduct = true;
                			productDefinitionDefined = "VARIANT";
                		}
                		if("N".equals(masterProductDef.getString("isVirtual")) && "N".equals(masterProductDef.getString("isVariant")))
                		{
                			isFinishedMasterProduct = true;
                			productDefinitionDefined = "FINISHED_GOOD";
                		}
                		boolean changeDefinition = false; 
                		
                		//Trying to Make Virtual/Variant to Finished Good
                		if((isVirtualMasterProduct || isVariantMasterProduct)  && (UtilValidate.isEmpty(productId)))
                		{
                			changeDefinition = true;
                			productDefinitionToChange = "FINISHED_GOOD";
                		}
                		
                		//Trying to Make Variant to Virtual
                		if(isVariantMasterProduct  && UtilValidate.isNotEmpty(masterProductId) && UtilValidate.isNotEmpty(productId) && masterProductId.equals(productId))
                		{
                			changeDefinition = true;
                			productDefinitionToChange = "VIRTUAL";
                		}
                		
                		if(changeDefinition)
                		{
                			productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductDefinitionChangeError", UtilMisc.toMap("rowNo", rowNo.toString(), "masterProductId", masterProductId, "productId", productId, "productDefinitionDefined", productDefinitionDefined, "productDefinitionToChange", productDefinitionToChange), locale));
                			serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ProductDefinitionChangeError", UtilMisc.toMap("rowNo", rowNo.toString(), "masterProductId", masterProductId, "productId", productId, "productDefinitionDefined", productDefinitionDefined, "productDefinitionToChange", productDefinitionToChange), locale));
                			
                		}
                	}
                }
                
                if(UtilValidate.isNotEmpty(productId))
                {
                	GenericValue productDef = _delegator.findByPrimaryKey("Product", UtilMisc.toMap("productId", productId));
                	if(UtilValidate.isNotEmpty(productDef))
                	{
                		boolean isVirtualProduct = false;
                		boolean isVariantProduct = false;
                		boolean isFinishedProduct = false;
                		String productDefinitionDefined = "";
                		String productDefinitionToChange = "";
                		if("Y".equals(productDef.getString("isVirtual")))
                		{
                			isVirtualProduct = true;
                			productDefinitionDefined = "VIRTUAL";
                		}
                		if("Y".equals(productDef.getString("isVariant")))
                		{
                			isVariantProduct = true;
                			productDefinitionDefined = "VARIANT";
                		}
                		if("N".equals(productDef.getString("isVirtual")) && "N".equals(productDef.getString("isVariant")))
                		{
                			isFinishedProduct = true;
                			productDefinitionDefined = "FINISHED_GOOD";
                		}
                		boolean changeDefinition = false; 
                		
                		//Trying to Make Virtual/Finished to Variant
                		if((isVirtualProduct || isFinishedProduct)  && UtilValidate.isNotEmpty(masterProductId) && !masterProductId.equals(productId))
                		{
                			changeDefinition = true;
                			productDefinitionToChange = "VARIANT";
                		}
                		
                		if(changeDefinition)
                		{
                			productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductDefinitionChangeError", UtilMisc.toMap("rowNo", rowNo.toString(), "masterProductId", masterProductId, "productId", productId, "productDefinitionDefined", productDefinitionDefined, "productDefinitionToChange", productDefinitionToChange), locale));
                			serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ProductDefinitionChangeError", UtilMisc.toMap("rowNo", rowNo.toString(), "masterProductId", masterProductId, "productId", productId, "productDefinitionDefined", productDefinitionDefined, "productDefinitionToChange", productDefinitionToChange), locale));
                			
                		}
                	}
                }
                
                
                //Check that if there is any varaint associated with virtual product either in feed file or in DB
                if(UtilValidate.isNotEmpty(masterProductId) && UtilValidate.isNotEmpty(productId) && masterProductId.equals(productId))
                {
                	boolean variantProductExist = false;
                	if(variantProductIdMap.containsKey(masterProductId))
                	{
                		variantProductExist = true;	
                	}
                	else
                	{
                		List productAssocs = _delegator.findByAnd("ProductAssoc", UtilMisc.toMap("productId",masterProductId, "productAssocTypeId", "PRODUCT_VARIANT"));
                		productAssocs = EntityUtil.filterByDate(productAssocs);
                		if(productAssocs.size() > 0)
                		{
                			variantProductExist = true;
                		}
                	}
                	if(!variantProductExist)
                	{
                		productWarningList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "VariantProductNotExistsWarning", UtilMisc.toMap("rowNo", rowNo.toString(), "masterProductId", masterProductId), locale));
                		serviceLogProductMessageList.add(warningLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "VariantProductNotExistsWarning", UtilMisc.toMap("rowNo", rowNo.toString(), "masterProductId", masterProductId), locale));
                	}
                }
                
                
                if(UtilValidate.isNotEmpty(productId) && UtilValidate.isNotEmpty(masterProductId))
                {
                    if(masterProductId.equals(productId)) 
                    {
                        masterProductIdMap.put(masterProductId, masterProductId);
                        if(!virtualFinishProductIdList.contains(masterProductId))
                        {
                            virtualFinishProductIdList.add(masterProductId);
                        }
                        else
                        {
                            productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "VirtualFinishProductIdExistingError", UtilMisc.toMap("rowNo", rowNo.toString(), "masterProductId", masterProductId, "productId", productId), locale));
                            serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "VirtualFinishProductIdExistingError", UtilMisc.toMap("rowNo", rowNo.toString(), "masterProductId", masterProductId, "productId", productId), locale));
                            
                        }
                        
                    }
                }
                
                if(UtilValidate.isEmpty(productId) && UtilValidate.isNotEmpty(masterProductId))
                {
                    if(!virtualFinishProductIdList.contains(masterProductId))
                    {
                        virtualFinishProductIdList.add(masterProductId);
                    }
                    else
                    {
                        productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "VirtualFinishProductIdExistingError", UtilMisc.toMap("rowNo", rowNo.toString(), "masterProductId", masterProductId), locale));
                        serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "VirtualFinishProductIdExistingError", UtilMisc.toMap("rowNo", rowNo.toString(), "masterProductId", masterProductId), locale));
                        
                    }
                }
                
                if(UtilValidate.isNotEmpty(productId) && UtilValidate.isNotEmpty(masterProductId))
                {
                    if(!masterProductId.equals(productId)) 
                    {
                        boolean virtualProductExists = false;
            	        if(masterProductIdMap.containsKey(masterProductId))
            	        {
            	            virtualProductExists = true;
            	        }
            	        else
            	        {
            	            if(ProductWorker.isVirtual(_delegator, masterProductId))
            	            {
            	                virtualProductExists = true;
            	            } 
            	        }
            	        
            	        if(!virtualProductExists)
            	        {
            	            productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ValidVirtualProductReferenceError", UtilMisc.toMap("rowNo", rowNo.toString(), "productId", productId, "masterProductId", masterProductId), locale));
            	            serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ValidVirtualProductReferenceError", UtilMisc.toMap("rowNo", rowNo.toString(), "productId", productId, "masterProductId", masterProductId), locale));
            	            
            	        }
                    }
                }
                if(UtilValidate.isNotEmpty(masterProductId))
                {
                	if((UtilValidate.isNotEmpty(productId) && masterProductId.equals(productId)) || UtilValidate.isEmpty(productId))
                    {
                		if(UtilValidate.isEmpty(productCategoryId))
                        {
            	            productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "AtLeastOneCategoryMemberShipRequiredError", UtilMisc.toMap("rowNo", rowNo.toString(), "idData", masterProductId), locale));
            	            serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "AtLeastOneCategoryMemberShipRequiredError", UtilMisc.toMap("rowNo", rowNo.toString(), "idData", masterProductId), locale));
            	            
            	        }
                    }
                }
                
                if(UtilValidate.isNotEmpty(productCategoryId))
                {
                   List<String> productCategoryIdList = StringUtil.split(productCategoryId,",");
                   boolean categoryIdMatch = true;
                   for (String productCatId: productCategoryIdList) 
                   {
                       categoryIdMatch = false;
                       if(newProdCatIdList.contains(productCatId.trim()))
                       {
                           categoryIdMatch = true;
                       }
                       if(!categoryIdMatch)
                       {
                           if(existingProdCatIdList.contains(productCatId.trim()))
                           {
                               categoryIdMatch = true;
                           } 
                       }
                       if(!categoryIdMatch)
                       {
                           productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "CategoryIdMatchingError", UtilMisc.toMap("rowNo", rowNo.toString(), "categoryId", productCatId), locale));
                           serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "CategoryIdMatchingError", UtilMisc.toMap("rowNo", rowNo.toString(), "categoryId", productCatId), locale));
                           
                       }
                   }
                }
                
                
                //If VIRTUAL a long description must be entered
                if(UtilValidate.isNotEmpty(productId) && UtilValidate.isNotEmpty(masterProductId))
                {
                    if(masterProductId.equals(productId)) 
                    {
                        if(UtilValidate.isEmpty(longDescription))
                        {    
                        	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "BlankLongDescError", UtilMisc.toMap("rowNo", rowNo.toString(), "productType", "VIRTUAL"), locale));
                        	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "BlankLongDescError", UtilMisc.toMap("rowNo", rowNo.toString(), "productType", "VIRTUAL"), locale));
                        	
                        }
                    }
                }
                else
                {
                    //If FINISHED GOOD a long description must be entered
                    if(UtilValidate.isEmpty(productId) && UtilValidate.isNotEmpty(masterProductId))
                    {
                        if(UtilValidate.isEmpty(longDescription))
                        {    
                        	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "BlankLongDescError", UtilMisc.toMap("rowNo", rowNo.toString(), "productType", "FINISHED-GOOD"), locale));
                        	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "BlankLongDescError", UtilMisc.toMap("rowNo", rowNo.toString(), "productType", "FINISHED-GOOD"), locale));
                        	
                        }
                    }
                }
                
                if(UtilValidate.isNotEmpty(fromDate))
                {
                    if(!OsafeAdminUtil.isValidDate(fromDate))
                    {
                    	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidProductIntroDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", fromDate, "idData", productId), locale));
                    	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidProductIntroDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", fromDate, "idData", productId), locale));
                    	
                    }    
                }
                if(UtilValidate.isNotEmpty(thruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(thruDate))
                    {
                    	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidProductDiscoDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", thruDate, "idData", productId), locale));
                    	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidProductDiscoDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", thruDate, "idData", productId), locale));
                    	
                    }    
                }
                
                
               if(UtilValidate.isNotEmpty(listPriceFromDate))
                {
                    if(!OsafeAdminUtil.isValidDate(listPriceFromDate))
                    {
                    	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "From", "idData", listPriceFromDate), locale));
                    	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "From", "idData", listPriceFromDate), locale));
                    	
                    }    
                }
                if(UtilValidate.isNotEmpty(listPriceThruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(listPriceThruDate))
                    {
                    	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", listPriceThruDate), locale));
                    	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", listPriceThruDate), locale));
                    	
                    }    
                }
                
                if(UtilValidate.isNotEmpty(defaultPriceFromDate))
                {
                    if(!OsafeAdminUtil.isValidDate(defaultPriceFromDate))
                    {
                    	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "From", "idData", defaultPriceFromDate), locale));
                    	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "From", "idData", defaultPriceFromDate), locale));
                    	
                    }    
                }
                if(UtilValidate.isNotEmpty(defaultPriceThruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(defaultPriceThruDate))
                    {
                    	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", defaultPriceThruDate), locale));
                    	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", defaultPriceThruDate), locale));
                    	
                    }    
                }
                
                if(UtilValidate.isNotEmpty(categoryFromDate))
                {
                    if(!OsafeAdminUtil.isValidDate(categoryFromDate))
                    {
                    	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "From", "idData", categoryFromDate), locale));
                    	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "From", "idData", categoryFromDate), locale));
                    	
                    }    
                }
                if(UtilValidate.isNotEmpty(categoryThruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(categoryThruDate))
                    {
                    	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", categoryThruDate), locale));
                    	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", categoryThruDate), locale));
                    	
                    }    
                }
                
                if(UtilValidate.isNotEmpty(plpSwatchImageThruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(plpSwatchImageThruDate))
                    {
                    	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", plpSwatchImageThruDate), locale));
                    	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", plpSwatchImageThruDate), locale));
                    	
                    }    
                }
                if(UtilValidate.isNotEmpty(pdpSwatchImageThruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(pdpSwatchImageThruDate))
                    {
                    	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", pdpSwatchImageThruDate), locale));
                    	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", pdpSwatchImageThruDate), locale));
                    	
                    }    
                }
                if(UtilValidate.isNotEmpty(smallImageThruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(smallImageThruDate))
                    {
                    	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", smallImageThruDate), locale));
                    	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", smallImageThruDate), locale));
                    	
                    }    
                }
                if(UtilValidate.isNotEmpty(smallImageAltThruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(smallImageAltThruDate))
                    {
                    	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", smallImageAltThruDate), locale));
                    	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", smallImageAltThruDate), locale));
                    	
                    }    
                }
                if(UtilValidate.isNotEmpty(thumbImageThruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(thumbImageThruDate))
                    {
                    	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", thumbImageThruDate), locale));
                    	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", thumbImageThruDate), locale));
                    	
                    }    
                }
                if(UtilValidate.isNotEmpty(largeImageThruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(largeImageThruDate))
                    {
                    	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", largeImageThruDate), locale));
                    	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", largeImageThruDate), locale));
                    	
                    }    
                }
                if(UtilValidate.isNotEmpty(detailImageThruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(detailImageThruDate))
                    {
                    	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", detailImageThruDate), locale));
                    	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", detailImageThruDate), locale));
                    	
                    }    
                }
                if(UtilValidate.isNotEmpty(pdpVideoUrlThruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(pdpVideoUrlThruDate))
                    {
                    	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", pdpVideoUrlThruDate), locale));
                    	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", pdpVideoUrlThruDate), locale));
                    	
                    }    
                }
                if(UtilValidate.isNotEmpty(pdpVideo360UrlThruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(pdpVideo360UrlThruDate))
                    {
                    	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", pdpVideo360UrlThruDate), locale));
                    	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", pdpVideo360UrlThruDate), locale));
                    	
                    }    
                }
                if(UtilValidate.isNotEmpty(totPdpAdditionalThumbImage))
                {
                  for(int i=1;i<=Integer.parseInt(totPdpAdditionalThumbImage);i++)
                  {
                	String addImageThruDate = (String)product.get("addImage"+i+"ThruDate");
                	if(UtilValidate.isNotEmpty(addImageThruDate))
                    {
                		if(!OsafeAdminUtil.isValidDate(addImageThruDate))
                        {
                        	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", addImageThruDate), locale));
                        	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", addImageThruDate), locale));
                        	
                        }  
                    }
                  }
                }
                if(UtilValidate.isNotEmpty(totPdpAdditionalLargeImage))
                {	
                  for(int i=1;i<=Integer.parseInt(totPdpAdditionalLargeImage);i++)
                  {
                	String xtraLargeImageThruDate = (String)product.get("xtraLargeImage"+i+"ThruDate");
                	if(UtilValidate.isNotEmpty(xtraLargeImageThruDate))
                    {
                		if(!OsafeAdminUtil.isValidDate(xtraLargeImageThruDate))
                        {
                        	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", xtraLargeImageThruDate), locale));
                        	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", xtraLargeImageThruDate), locale));
                        	
                        }  
                    }
                  }
                }
                if(UtilValidate.isNotEmpty(totPdpAdditionalLargeImage))
                {
                  for(int i=1;i<=Integer.parseInt(totPdpAdditionalDetailImage);i++)
                  {
                	String xtraDetailImageThruDate = (String)product.get("xtraDetailImage"+i+"ThruDate");
                	if(UtilValidate.isNotEmpty(xtraDetailImageThruDate))
                    {
                		if(!OsafeAdminUtil.isValidDate(xtraDetailImageThruDate))
                        {
                        	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", xtraDetailImageThruDate), locale));
                        	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", xtraDetailImageThruDate), locale));
                        	
                        }  
                    }
                  }
                }
                
                //If VIRTUAL a sales price must be entered
                if(UtilValidate.isNotEmpty(productId) && UtilValidate.isNotEmpty(masterProductId))
                {
                    if(masterProductId.equals(productId)) 
                    {
                        if(UtilValidate.isEmpty(defaultPrice))
                        {    
                             productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "EmptySalesPriceError", UtilMisc.toMap("rowNo", rowNo.toString(), "productType", "VIRTUAL"), locale));
                             serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "EmptySalesPriceError", UtilMisc.toMap("rowNo", rowNo.toString(), "productType", "VIRTUAL"), locale));
                             
                        }
                    }
                }
                else
                {
                    //If FINISHED GOOD a sales price must be entered
                    if(UtilValidate.isEmpty(productId) && UtilValidate.isNotEmpty(masterProductId))
                    {
                        if(UtilValidate.isEmpty(defaultPrice))
                        {    
                             productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "EmptySalesPriceError", UtilMisc.toMap("rowNo", rowNo.toString(), "productType", "FINISHED-GOOD"), locale));
                             serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "EmptySalesPriceError", UtilMisc.toMap("rowNo", rowNo.toString(), "productType", "FINISHED-GOOD"), locale));
                             
                        }
                    }
                }
                
                //If entered check if List Price is a valid float
                if(UtilValidate.isNotEmpty(listPrice))
                {
                    boolean checkFloatResult = OsafeAdminUtil.isFloat(listPrice);
                    if(!checkFloatResult)
                    {
                        productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ValidListPriceError", UtilMisc.toMap("rowNo", rowNo.toString(), "listPrice", listPrice), locale));
                        serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ValidListPriceError", UtilMisc.toMap("rowNo", rowNo.toString(), "listPrice", listPrice), locale));
                        
                    }
                }
                
                //If entered check if Sales Price is a valid float
                if(UtilValidate.isNotEmpty(defaultPrice))
                {
                    boolean checkFloatResult = OsafeAdminUtil.isFloat(defaultPrice);
                    if(!checkFloatResult)
                    {
                        productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ValidSalesPriceError", UtilMisc.toMap("rowNo", rowNo.toString(), "salesPrice", defaultPrice), locale));
                        serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ValidSalesPriceError", UtilMisc.toMap("rowNo", rowNo.toString(), "salesPrice", defaultPrice), locale));
                        
                    }
                }
                if(UtilValidate.isNotEmpty(manufacturerId))
                {
                    boolean manufacturerIdMatch = false;
                
                    if(newManufacturerIdList.contains(manufacturerId) || existingManufacturerIdList.contains(manufacturerId))
                    {
                        manufacturerIdMatch = true;
                    }
                    if(!manufacturerIdMatch)
                    {
                        productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ManufacturerIdMatchingError", UtilMisc.toMap("rowNo", rowNo.toString(), "manuId", manufacturerId), locale));
                        serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ManufacturerIdMatchingError", UtilMisc.toMap("rowNo", rowNo.toString(), "manuId", manufacturerId), locale));
                        
                    }
                }
                
                if(UtilValidate.isNotEmpty(plpImage))
                {
                    boolean isPlpImageExist = (new File(osafeThemeImagePath, plpImage)).exists();
                    if(!UtilValidate.isUrl(plpImage))
                	{
                    	if(!isPlpImageExist)
                        {
                            productWarningList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "PLPImageNotFoundWarning", UtilMisc.toMap("rowNo", rowNo.toString(), "plpImageData", plpImage), locale));
                            serviceLogProductMessageList.add(warningLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "PLPImageNotFoundWarning", UtilMisc.toMap("rowNo", rowNo.toString(), "plpImageData", plpImage), locale));
                            
                        }
                	}
                }
                if(UtilValidate.isNotEmpty(pdpRegularImage))
                {
                    boolean isPdpRegularImageExist = (new File(osafeThemeImagePath, pdpRegularImage)).exists();
                    if(!UtilValidate.isUrl(pdpRegularImage))
                	{
                    	if(!isPdpRegularImageExist)
                        {
                            productWarningList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "PDPRegularImageNotFoundWarning", UtilMisc.toMap("rowNo", rowNo.toString(), "pdpRegularImage", pdpRegularImage), locale));
                            serviceLogProductMessageList.add(warningLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "PDPRegularImageNotFoundWarning", UtilMisc.toMap("rowNo", rowNo.toString(), "pdpRegularImage", pdpRegularImage), locale));
                            
                        }	
                	}
                    
                }
                   
                if(UtilValidate.isNotEmpty(bfWHInventory))
                {
                    boolean bfWHInventoryVaild = UtilValidate.isSignedInteger(bfWHInventory);
                    if(!bfWHInventoryVaild)
                    {
                        productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ValidBFWHInventoryRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                        serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ValidBFWHInventoryRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                        
                    }
                    else
                    {
                        if(Integer.parseInt(bfWHInventory) < -9999 || Integer.parseInt(bfWHInventory) > 99999)
                        {
                            productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ValidBFWHInventoryRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                            serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ValidBFWHInventoryRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                            
                        } 
                    }
                }   
                  
                if(UtilValidate.isNotEmpty(bfTotalInventory))
                {
                    boolean bfTotalInventoryVaild = UtilValidate.isSignedInteger(bfTotalInventory);
                    if(!bfTotalInventoryVaild)
                    {
                        productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ValidBFTotalInventoryRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                        serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ValidBFTotalInventoryRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                        
                    }
                    else
                    {
                        if(Integer.parseInt(bfTotalInventory) < -9999 || Integer.parseInt(bfTotalInventory) > 99999)
                        {
                            productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ValidBFTotalInventoryRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                            serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ValidBFTotalInventoryRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                            
                        }
                    }
                }
                    
                if(UtilValidate.isNotEmpty(internalName))
                {
            		if(internalName.length() > 255)
            		{
            			productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InternalNameLengthExceedRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
            			serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InternalNameLengthExceedRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
            			
            		}
            		if(!OsafeAdminUtil.isValidName(internalName))
                    {
            			productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InternalNameInvalidRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
            			serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InternalNameInvalidRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
            			
                    }
                    List itenNoRowList = FastList.newInstance();
                    if(itenNoMap.get(internalName) != null)
                    {
                        itenNoRowList = (List)itenNoMap.get(internalName);
                    } 
                    else 
                    {
                        itenNoRowList = FastList.newInstance();
                    }
                    itenNoRowList.add(rowNo);
                    itenNoMap.put(internalName,itenNoRowList);
                }
                
            	if(UtilValidate.isNotEmpty(productName))
            	{
            		if(productName.length() > 100)
            		{
            			productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductNameLengthExceedRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
            			serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ProductNameLengthExceedRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
            			
            		}
            		if(!OsafeAdminUtil.isValidName(productName))
                    {
            			productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductNameInvalidRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
            			serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ProductNameInvalidRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
            			
                    }
            		
            		if(UtilValidate.isNotEmpty(masterProductId) && ((UtilValidate.isEmpty(productId) || masterProductId.equals(productId))))
            		{	
               		  List prodNoRowList = FastList.newInstance();
                      if(prodNoMap.get(productName) != null)
                      {
                    	  prodNoRowList = (List)prodNoMap.get(productName);
                      } 
                      else 
                      {
                    	  prodNoRowList = FastList.newInstance();
                      }
                      prodNoRowList.add(rowNo);
                      prodNoMap.put(productName,prodNoRowList);
            	   }
            		
            	}
            	for(int i = 1; i <= totalSelectableFeature; i++)
                {
            		if(UtilValidate.isNotEmpty(product.get("selectabeFeature_"+i)))
            		{
            			String parseSelectabeFeatureType = (String)product.get("selectabeFeature_"+i);
            			int iSelIdx = parseSelectabeFeatureType.indexOf(':');
            			if(iSelIdx > -1)
            			{
            				String selectabeFeature = parseSelectabeFeatureType.substring(0,iSelIdx).trim();
            				if(UtilValidate.isNotEmpty(selectabeFeature))
                            {
            					//when we attempt to convert this feature type to an ID, we will remove spaces.  Test if this will result in a valid BF ID
            					String testSelectableFeature = StringUtil.removeSpaces(selectabeFeature);
                                if(!OsafeAdminUtil.isValidId(testSelectableFeature))
                                {
                                	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidFeatureError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureData", selectabeFeature), locale));
                                	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidFeatureError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureData", selectabeFeature), locale));
                                	
                                } 
                                if(!OsafeAdminUtil.isValidFeatureFormat(parseSelectabeFeatureType))
                                {
                                	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidFeatureIDError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureData", parseSelectabeFeatureType), locale));
                                	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidFeatureIDError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureData", parseSelectabeFeatureType), locale));
                                	
                                }
                            }
            				String featureFromDate= (String)product.get(selectabeFeature+"_fromDate");
            	            String featureThruDate= (String)product.get(selectabeFeature+"_thruDate");

            	            if(UtilValidate.isNotEmpty(featureFromDate))
                            {
                                if(!OsafeAdminUtil.isValidDate(featureFromDate))
                                {
                                	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "From", "idData", featureFromDate), locale));
                                	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "From", "idData", featureFromDate), locale));
                                	
                                }    
                            }
            	            if(UtilValidate.isNotEmpty(featureThruDate))
                            {
                                if(!OsafeAdminUtil.isValidDate(featureThruDate))
                                {
                                	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", featureThruDate), locale));
                                	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", featureThruDate), locale));
                                	
                                }    
                            }
            			}
            		}
                }
            	for(int i = 1; i <= totalDescriptiveFeature; i++)
                {
            		if(UtilValidate.isNotEmpty(product.get("descriptiveFeature_"+i)))
            		{	
            			String parseDescriptiveFeatureType = (String)product.get("descriptiveFeature_"+i);
            			int iDescIdx = parseDescriptiveFeatureType.indexOf(':');
            			if(iDescIdx > -1)
            			{	
            				String descriptiveFeature = parseDescriptiveFeatureType.substring(0,iDescIdx).trim();
            				
            				String featureFromDate= (String)product.get(descriptiveFeature+"_fromDate");
            	            String featureThruDate= (String)product.get(descriptiveFeature+"_thruDate");
            	            if(UtilValidate.isNotEmpty(descriptiveFeature))
                            {
                                //when we attempt to convert this feature type to an ID, we will remove spaces.  Test if this will result in a valid BF ID
            					String testDescriptiveFeature = StringUtil.removeSpaces(descriptiveFeature);
                                if(!OsafeAdminUtil.isValidId(testDescriptiveFeature))
                                {
                                	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidFeatureError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureData", descriptiveFeature), locale));
                                	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidFeatureError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureData", descriptiveFeature), locale));
                                	
                                } 
                                if(!OsafeAdminUtil.isValidFeatureFormat(parseDescriptiveFeatureType))
                                {
                                	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidFeatureIDError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureData", parseDescriptiveFeatureType), locale));
                                	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidFeatureIDError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureData", parseDescriptiveFeatureType), locale));
                                }
                            }
            	            if(UtilValidate.isNotEmpty(featureFromDate))
                            {
                                if(!OsafeAdminUtil.isValidDate(featureFromDate))
                                {
                                	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "From", "idData", featureFromDate), locale));
                                	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "From", "idData", featureFromDate), locale));
                                	
                                }    
                            }
            	            if(UtilValidate.isNotEmpty(featureThruDate))
                            {
                                if(!OsafeAdminUtil.isValidDate(featureThruDate))
                                {
                                	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", featureThruDate), locale));
                                	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", featureThruDate), locale));
                                	
                                }    
                            }
            			}
            		}
                }
                for(int i = 1; i <= totalSelectableFeature; i++)
                {
            		if(UtilValidate.isNotEmpty(product.get("selectabeFeature_"+i)))
            		{
            		  String parseSelectabeFeatureType = (String)product.get("selectabeFeature_"+i);
            		  if(UtilValidate.isNotEmpty(parseSelectabeFeatureType))
            		  {
            			  for(int j = 1; j <= totalDescriptiveFeature; j++)
                          {
                			String parseDescriptiveFeatureType = (String)product.get("descriptiveFeature_"+j);
                			if(UtilValidate.isNotEmpty(parseDescriptiveFeatureType))
                			{
                				int iSelIdx = parseSelectabeFeatureType.indexOf(':');
                    			int iDescIdx = parseDescriptiveFeatureType.indexOf(':');
                    	        if (iSelIdx > -1 && iDescIdx > -1)
                    	        {
                    	            String selectabeFeature = parseSelectabeFeatureType.substring(0,iSelIdx).trim();
                    	            String descriptiveFeature = parseDescriptiveFeatureType.substring(0,iDescIdx).trim();
                    	            if(selectabeFeature.equals(descriptiveFeature))
                        	        {
                        	           productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "DuplicateFeatureError", UtilMisc.toMap("rowNo", rowNo.toString(), "idData", productId), locale));
                        	           serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "DuplicateFeatureError", UtilMisc.toMap("rowNo", rowNo.toString(), "idData", productId), locale));
                        	           
                        	        }
                    	        }
                			}
                			
                	      }  
            		  }
            		  
            		}
                 }
                
                boolean selectableFeatureExist = false; 
            	for(int j = 1; j <= totalSelectableFeature; j++)
                {
            		if(UtilValidate.isNotEmpty(product.get("selectabeFeature_"+j)))
            		{
            			selectableFeatureExist = true;
            			String parseFeatureType = (String)product.get("selectabeFeature_"+j);
            			int iFeatIdx = parseFeatureType.indexOf(':');
            	        if (iFeatIdx > -1)
            	        {
            	            String featureType = parseFeatureType.substring(0,iFeatIdx).trim();
            	            String sFeatures = parseFeatureType.substring(iFeatIdx +1);
            	            String[] featureTokens = sFeatures.split(",");
            	            for (int f=0;f < featureTokens.length;f++)
            	            { 
            					String featureTypeFeatureId = featureType+":"+featureTokens[f].trim();
            					String tempFeatureTypeFeatureId = (featureType+""+featureTokens[f].trim()).replaceAll(" ", "_");
            					//Removing this ID check, we need a beter plan in generating the feature ID
//            					if(!OsafeAdminUtil.isValidId(tempFeatureTypeFeatureId))
//            					{
//            						productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InValidIdError", UtilMisc.toMap("rowNo", rowNo, "idField", "Feature", "idData", featureTokens[f].trim()), locale));
//            					    serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InValidIdError", UtilMisc.toMap("rowNo", rowNo, "idField", "Feature", "idData", featureTokens[f].trim()), locale));
            					
//            					}
            					featureType = featureType.toUpperCase();
            	                productFeatureSet.add(featureType+":"+featureTokens[f].trim());
            	                productFeatureGroupSet.add(featureType);
            	            }
            	        }
            		}
                }
            	for(int j = 1; j <= totalDescriptiveFeature; j++)
                {
            		if(UtilValidate.isNotEmpty(product.get("descriptiveFeature_"+j)))
            		{
            			String parseFeatureType = (String)product.get("descriptiveFeature_"+j);
            			int iFeatIdx = parseFeatureType.indexOf(':');
            	        if (iFeatIdx > -1)
            	        {
            	            String featureType = parseFeatureType.substring(0,iFeatIdx).trim();
            	            String sFeatures = parseFeatureType.substring(iFeatIdx +1);
            	            String[] featureTokens = sFeatures.split(",");
            	            for (int f=0;f < featureTokens.length;f++)
            	            { 
            	            	String featureTypeFeatureId = featureType+":"+featureTokens[f].trim();
            	            	String tempFeatureTypeFeatureId = (featureType+""+featureTokens[f].trim()).replaceAll(" ", "_");
            					//Removing this ID check, we need a beter plan in generating the feature ID
//            					if(!OsafeAdminUtil.isValidId(tempFeatureTypeFeatureId))
//            					{
//            						productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InValidIdError", UtilMisc.toMap("rowNo", rowNo, "idField", "Feature", "idData", featureTokens[f].trim()), locale));
//            					}
            	            	featureType = featureType.toUpperCase();
            	                productFeatureSet.add(featureType+":"+featureTokens[f].trim());
            	                productFeatureGroupSet.add(featureType);
            	            }
            	        }
            		}
                }
            	
            	//If VIRTUAL a Selectable Feature must not be entered
            	if(UtilValidate.isNotEmpty(productId) && UtilValidate.isNotEmpty(masterProductId))
                {
                    if(masterProductId.equals(productId)) 
                    {
                    	if(selectableFeatureExist)
                    	{
                    		productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "VirtualSelectableFeatureError", UtilMisc.toMap("rowNo", rowNo.toString(), "idData", masterProductId, "productType", "VIRTUAL"), locale));
                    		serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "VirtualSelectableFeatureError", UtilMisc.toMap("rowNo", rowNo.toString(), "idData", masterProductId, "productType", "VIRTUAL"), locale));
                    		
                    	}
                    }
                }
            	else
            	{
            		//If FINISHED GOOD a Selectable Feature must not be entered
            		if(UtilValidate.isEmpty(productId) && UtilValidate.isNotEmpty(masterProductId))
            		{
            	        if(selectableFeatureExist)
            	        {
            	            productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "VirtualSelectableFeatureError", UtilMisc.toMap("rowNo", rowNo.toString(), "idData", masterProductId, "productType", "FINISHED-GOOD"), locale));
            	            serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "VirtualSelectableFeatureError", UtilMisc.toMap("rowNo", rowNo.toString(), "idData", masterProductId, "productType", "FINISHED-GOOD"), locale));
            	            
            	        }
            	    }	
            	}
            	
                if(UtilValidate.isNotEmpty(multiVariant) && !(multiVariant.equalsIgnoreCase("NONE")|| multiVariant.equalsIgnoreCase("CHECKBOX")||multiVariant.equalsIgnoreCase("QTY")))
                {
                     productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidMultiVariantError", UtilMisc.toMap("rowNo", rowNo.toString(), "idData", productId), locale));
                     serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidMultiVariantError", UtilMisc.toMap("rowNo", rowNo.toString(), "idData", productId), locale));
                     
                }
                
                if(UtilValidate.isNotEmpty(productHeight))
                {
                    boolean checkFloatResult = OsafeAdminUtil.isFloat(productHeight);
                    if(!checkFloatResult)
                    {
                        productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidNumberError", UtilMisc.toMap("rowNo", rowNo.toString(),  "idField", "Product Height","idData", productHeight), locale));
                        serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidNumberError", UtilMisc.toMap("rowNo", rowNo.toString(),  "idField", "Product Height","idData", productHeight), locale));
                        
                    }
                }
                
                if(UtilValidate.isNotEmpty(productWidth))
                {
                    boolean checkFloatResult = OsafeAdminUtil.isFloat(productWidth);
                    if(!checkFloatResult)
                    {
                        productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidNumberError", UtilMisc.toMap("rowNo", rowNo.toString(),  "idField", "Product Width","idData", productWidth), locale));
                        serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidNumberError", UtilMisc.toMap("rowNo", rowNo.toString(),  "idField", "Product Width","idData", productWidth), locale));
                        
                    }
                }
                
                if(UtilValidate.isNotEmpty(productDepth))
                {
                    boolean checkFloatResult = OsafeAdminUtil.isFloat(productDepth);
                    if(!checkFloatResult)
                    {
                        productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidNumberError", UtilMisc.toMap("rowNo", rowNo.toString(),  "idField", "Product Depth","idData", productDepth), locale));
                        serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidNumberError", UtilMisc.toMap("rowNo", rowNo.toString(),  "idField", "Product Depth","idData", productDepth), locale));
                        
                    }
                }
                
                if(UtilValidate.isNotEmpty(productWeight))
                {
                    boolean checkFloatResult = OsafeAdminUtil.isFloat(productWeight);
                    if(!checkFloatResult)
                    {
                        productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidNumberError", UtilMisc.toMap("rowNo", rowNo.toString(),  "idField", "Product Weight","idData", productWeight), locale));
                        serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidNumberError", UtilMisc.toMap("rowNo", rowNo.toString(),  "idField", "Product Weight","idData", productWeight), locale));
                        
                    }
                }
                
                if((UtilValidate.isNotEmpty(pdpQtyMin) && UtilValidate.isEmpty(pdpQtyMax)) || (UtilValidate.isEmpty(pdpQtyMin) && UtilValidate.isNotEmpty(pdpQtyMax)))
                {
                	productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "BlankPdpQtyMinMaxRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                	serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "BlankPdpQtyMinMaxRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                	
                }
                
                boolean pdpQtyMinVaild = false;
                if(UtilValidate.isNotEmpty(pdpQtyMin))
                {
                    pdpQtyMinVaild = OsafeAdminUtil.isNumber(pdpQtyMin);
                    if(!pdpQtyMinVaild)
                    {
                        productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ValidPdpQtyMinRowError", UtilMisc.toMap("rowNo", rowNo.toString(), "pdpQtyMin", pdpQtyMin), locale));
                        serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ValidPdpQtyMinRowError", UtilMisc.toMap("rowNo", rowNo.toString(), "pdpQtyMin", pdpQtyMin), locale));
                        
                    }
                    else
                    {
                        if(Integer.parseInt(pdpQtyMin) <= 0)
                        {
                            productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ValidPdpQtyMinRowError", UtilMisc.toMap("rowNo", rowNo.toString(), "pdpQtyMin", pdpQtyMin), locale));
                            serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ValidPdpQtyMinRowError", UtilMisc.toMap("rowNo", rowNo.toString(), "pdpQtyMin", pdpQtyMin), locale));
                            
                        } 
                    }
                }   
                
                boolean pdpQtyMaxVaild = false;
                if(UtilValidate.isNotEmpty(pdpQtyMax))
                {
                    pdpQtyMaxVaild = OsafeAdminUtil.isNumber(pdpQtyMax);
                    if(!pdpQtyMaxVaild)
                    {
                        productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ValidPdpQtyMaxRowError", UtilMisc.toMap("rowNo", rowNo.toString(), "pdpQtyMax", pdpQtyMax), locale));
                        serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ValidPdpQtyMaxRowError", UtilMisc.toMap("rowNo", rowNo.toString(), "pdpQtyMax", pdpQtyMax), locale));
                        
                    }
                    else
                    {
                        if(Integer.parseInt(pdpQtyMax) <= 0 )
                        {
                            productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ValidPdpQtyMaxRowError", UtilMisc.toMap("rowNo", rowNo.toString(), "pdpQtyMax", pdpQtyMax), locale));
                            serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ValidPdpQtyMaxRowError", UtilMisc.toMap("rowNo", rowNo.toString(), "pdpQtyMax", pdpQtyMax), locale));
                            
                        }
                        else
                        {
                            if((UtilValidate.isNotEmpty(pdpQtyMin)) && (Integer.parseInt(pdpQtyMax) <  Integer.parseInt(pdpQtyMin)))
                            {
                                productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ValidPdpQtyMaxMinRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                                serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ValidPdpQtyMaxMinRowError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                                
                            }
                        }
                    }
                }
                if(UtilValidate.isNotEmpty(pdpQtyDefault))
                {
                    boolean pdpQtyDefaultVaild = OsafeAdminUtil.isNumber(pdpQtyDefault);
                    if(!pdpQtyDefaultVaild)
                    {
                        productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ValidPdpQtyDefaultRowError", UtilMisc.toMap("rowNo", rowNo.toString(), "pdpQtyDefault", pdpQtyDefault), locale));
                        serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ValidPdpQtyDefaultRowError", UtilMisc.toMap("rowNo", rowNo.toString(), "pdpQtyDefault", pdpQtyDefault), locale));
                        
                    }
                }
                
                if(UtilValidate.isNotEmpty(pdpInStoreOnly) && !(pdpInStoreOnly.equalsIgnoreCase("Y")|| pdpInStoreOnly.equalsIgnoreCase("N")))
                {
                     productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidPdpInStoreOnlyRowError", UtilMisc.toMap("rowNo", rowNo.toString(), "idData", pdpInStoreOnly), locale));
                     serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidPdpInStoreOnlyRowError", UtilMisc.toMap("rowNo", rowNo.toString(), "idData", pdpInStoreOnly), locale));
                     
                }
                serviceLogProductMessageList.add("ROW COMPLETE: [Processed row #"+rowNo.toString()+" Master-Product-ID "+masterProductId+"; Product ID: "+ productId+"]");
                
                rowNo++;
            }
            for (Map.Entry<String, List> entry : itenNoMap.entrySet()) 
            {
                List<Integer> itenNoRowList = (List)entry.getValue();
                String internalName = (String)entry.getKey();
                if(itenNoRowList.size() > 1)
                {
                    for(Integer itemRowNo : itenNoRowList)
                    {
                    	String warningLogText =  UtilProperties.getMessage(resource, "WarningLogTextLabel", UtilMisc.toMap("rowNo", itemRowNo.toString()), locale);
                        productWarningList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "UniqueItemNoWarning", UtilMisc.toMap("rowNo", itemRowNo.toString(), "internalName", internalName), locale));
                        serviceLogProductMessageList.add(warningLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "UniqueItemNoWarning", UtilMisc.toMap("rowNo", itemRowNo.toString(), "internalName", internalName), locale));
                        
                    }
                }
            }
            for (Map.Entry<String, List> entry : prodNoMap.entrySet()) 
            {
                List<Integer> prodNoRowList = (List)entry.getValue();
                String productName = entry.getKey();
                for(Integer prodRowNo : prodNoRowList)
                {
                	String masterProductId = (String) rowNoMasterProductIdMap.get(prodRowNo);
                	GenericValue product = _delegator.findByPrimaryKey("Product", UtilMisc.toMap("productId", masterProductId));
                	boolean productExists = false;
                	boolean productNameUnique = true;
                	if(UtilValidate.isNotEmpty(product))
                	{
                		productExists = true;
                	}
                	String productNameUpCase = productName.trim().toUpperCase();
                	List productExpr= FastList.newInstance();
                	productExpr.add(EntityCondition.makeCondition(EntityFunction.UPPER_FIELD("productName"), EntityOperator.EQUALS, productNameUpCase));
                	productExpr.add(EntityCondition.makeCondition("isVariant", EntityOperator.EQUALS, "N"));
                	List<GenericValue> productList = _delegator.findList("Product", EntityCondition.makeCondition(productExpr, EntityOperator.AND), null, null, null, false);
                	
                	if(UtilValidate.isNotEmpty(productList))
                	{
                		if(productExists)
                		{
                			if(productList.size() > 1)
                			{
                				productNameUnique = false;
                			}
                		}
                		else
                		{
                			if(productList.size() > 0)
                			{
                				productNameUnique = false;
                			}
                		}
                	}
                	
                	if(productNameUnique)
                	{
                		if(UtilValidate.isNotEmpty(productNameByIdMap))
                    	{
                			if(productNameByIdMap.containsValue(productNameUpCase))
                			{
	                    		if(productExists)
	                    		{
	                    			for (Map.Entry<String, Object> productNameByIdEntry : productNameByIdMap.entrySet()) 
	                    			{
	                    			    String productNameValue = (String) productNameByIdEntry.getValue();
	                    			    if(productNameValue.equals(productNameUpCase))
	                    				{
	                    			    	String productIdKey = productNameByIdEntry.getKey();
	                    			    	if(!productIdKey.equals(masterProductId))
		                    				{
	                    			    		GenericValue productByContent = _delegator.findByPrimaryKey("Product", UtilMisc.toMap("productId", productIdKey));
	                    			    		if(UtilValidate.isNotEmpty(productByContent) && UtilValidate.isNotEmpty(productByContent.getString("isVariant")) && productByContent.getString("isVariant").equals("N"))
		                    					{
		                    						productNameUnique = false;
		                    					}
		                    				}
	                    				}
	                    			}
	                    		}
	                    		else
	                    		{
	                    			for (Map.Entry<String, Object> productNameByIdEntry : productNameByIdMap.entrySet()) 
	                    			{
	                    				String productNameValue = (String) productNameByIdEntry.getValue();
	                    			    if(productNameValue.equals(productNameUpCase))
	                    				{
	                    			    	String productIdKey = productNameByIdEntry.getKey();
	                    			    	GenericValue productByContent = _delegator.findByPrimaryKey("Product", UtilMisc.toMap("productId", productIdKey));
	                    			    	if(UtilValidate.isNotEmpty(productByContent) && UtilValidate.isNotEmpty(productByContent.getString("isVariant")) && productByContent.getString("isVariant").equals("N"))
		                					{
		                						productNameUnique = false;
		                					}
	                    				}
	                    			}
	                    		}
                			}
                    	}
                	}
                	String errorLogText = UtilProperties.getMessage(resource, "ErrorLogTextLabel", UtilMisc.toMap("rowNo", prodRowNo.toString()), locale);
    				String warningLogText =  UtilProperties.getMessage(resource, "WarningLogTextLabel", UtilMisc.toMap("rowNo", prodRowNo.toString()), locale);
                	if(!productNameUnique)
                	{
                		productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "UniqueProductNameError", UtilMisc.toMap("rowNo", prodRowNo.toString(), "productId", masterProductId), locale));
                		serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "UniqueProductNameError", UtilMisc.toMap("rowNo", prodRowNo.toString(), "productId", masterProductId), locale));
                		
                	}
                	else
                	{
                		if(prodNoRowList.size() > 1)
                        {
                            productErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "UniqueProductNameError", UtilMisc.toMap("rowNo", prodRowNo.toString(), "productId", masterProductId), locale));
                            serviceLogProductMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "UniqueProductNameError", UtilMisc.toMap("rowNo", prodRowNo.toString(), "productId", masterProductId), locale));
                            
                        }	
                	}
                }
            }
            

            //Validation for Product Associations
            rowNo = new Integer(1);
            List existingProductList = _delegator.findList("Product", null, null, null, null, false);
            if(UtilValidate.isNotEmpty(existingProductList))
            {
            	existingProductList = EntityUtil.filterByAnd(existingProductList, UtilMisc.toMap("isVariant" , "N"));
                existingProductIdList = EntityUtil.getFieldListFromEntityList(existingProductList, "productId", true);
            }
            for(Map productAssoc : productAssocDataList) 
            {
            	
            	String errorLogText = UtilProperties.getMessage(resource, "ErrorLogTextLabel", UtilMisc.toMap("rowNo", rowNo.toString()), locale);
				String warningLogText =  UtilProperties.getMessage(resource, "WarningLogTextLabel", UtilMisc.toMap("rowNo", rowNo.toString()), locale);
				
                String productId = (String)productAssoc.get("productId");
                String productIdTo = (String)productAssoc.get("productIdTo");
                String thruDate = (String)productAssoc.get("thruDate");
                String fromDate = (String)productAssoc.get("fromDate");
                boolean productIdMatch = false;
                boolean productIdToMatch = false;
                
                serviceLogProductAssocMessageList.add("IN PROGRESS: [Processing row #"+rowNo.toString()+" Product-ID "+productId+"; Product-ID-To: "+ productIdTo+"]");
                
                if(newProductIdList.contains(productId) || existingProductIdList.contains(productId))
                {
                    productIdMatch = true;
                }
                if(!productIdMatch)
                {
                    productAssocErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductIdMatchingError", UtilMisc.toMap("rowNo", rowNo.toString(), "productId", productId), locale));
                    serviceLogProductAssocMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ProductIdMatchingError", UtilMisc.toMap("rowNo", rowNo.toString(), "productId", productId), locale));
                }
                
                if(newProductIdList.contains(productIdTo) || existingProductIdList.contains(productIdTo))
                {
                    productIdToMatch = true;
                }
                if(!productIdToMatch)
                {
                    productAssocErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductIdToMatchingError", UtilMisc.toMap("rowNo", rowNo.toString(), "productIdTo", productIdTo), locale));
                    serviceLogProductAssocMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ProductIdToMatchingError", UtilMisc.toMap("rowNo", rowNo.toString(), "productIdTo", productIdTo), locale));
                    
                }
                
                if(UtilValidate.isNotEmpty(fromDate))
                {
                    if(!OsafeAdminUtil.isValidDate(fromDate))
                    {
                    	productAssocErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InValidProductAssocFromDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", fromDate, "idData", productId), locale));
                    	serviceLogProductAssocMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InValidProductAssocFromDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", fromDate, "idData", productId), locale));
                    	
                    }    
                }
                if(UtilValidate.isNotEmpty(thruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(thruDate))
                    {
                    	productAssocErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InValidProductAssocThruDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", thruDate, "idData", productId), locale));
                    	serviceLogProductAssocMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InValidProductAssocThruDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", thruDate, "idData", productId), locale));
                    	
                    }    
                }
                serviceLogProductAssocMessageList.add("ROW COMPLETE: [Processed row #"+rowNo.toString()+" Product-ID "+productId+"; Product-ID-To: "+ productIdTo+"]");
                rowNo++;
            }
            
          //Validation for Product Facet Group
            rowNo = new Integer(1);
            
            List productFeatureTypeIds = FastList.newInstance();
            if(UtilValidate.isNotEmpty(productFeatures))
            {
            	productFeatureTypeIds = EntityUtil.getFieldListFromEntityList(productFeatures,"productFeatureTypeId", true);
            }
            for(Map productFacetGroup : productFacetGroupDataList) 
            {
            	String errorLogText = UtilProperties.getMessage(resource, "ErrorLogTextLabel", UtilMisc.toMap("rowNo", rowNo.toString()), locale);
				String warningLogText =  UtilProperties.getMessage(resource, "WarningLogTextLabel", UtilMisc.toMap("rowNo", rowNo.toString()), locale);
				
            	String featureGroup = (String)productFacetGroup.get("facetGroupId");
            	
            	serviceLogProductFacetGroupMessageList.add("IN PROGRESS: [Processing row #"+rowNo.toString()+" Facet-Group-ID "+featureGroup+"]");
            	
                if(UtilValidate.isNotEmpty(featureGroup))
                {
                	if((!productFeatureGroupSet.contains(featureGroup)) && (!productFeatureTypeIds.contains(featureGroup)))
                    {
                		productFeatureGroupSet.add(featureGroup);
                    }
                    //when we attempt to convert this feature type to an ID, we will remove spaces.  Test if this will result in a valid BF ID
					String testFeatureGroup = StringUtil.removeSpaces(featureGroup);
                    if(!OsafeAdminUtil.isValidId(testFeatureGroup))
                    {
                    	productFacetGroupErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidFacetGroupError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureData", featureGroup), locale));
                    	serviceLogProductFacetGroupMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidFacetGroupError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureData", featureGroup), locale));
                    } 
                }
                
                String productCategoryId = (String)productFacetGroup.get("productCategoryId");
                if(UtilValidate.isNotEmpty(productCategoryId))
                {
                	if((!existingProdCatIdList.contains(productCategoryId)) && (!newProdCatIdList.contains(productCategoryId)))
                    {
                		productFacetGroupErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductCategoryIdMatchError", UtilMisc.toMap("rowNo", rowNo.toString(), "productCategoryId", productCategoryId), locale));
                		serviceLogProductFacetGroupMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ProductCategoryIdMatchError", UtilMisc.toMap("rowNo", rowNo.toString(), "productCategoryId", productCategoryId), locale));
                		
                    }
                	
                }
            	
            	String sequenceNum = (String)productFacetGroup.get("sequenceNum");
                if (UtilValidate.isNotEmpty(sequenceNum) && !UtilValidate.isInteger(sequenceNum))
                {
                	productFacetGroupErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetSeqError", UtilMisc.toMap("rowNo", rowNo.toString(), "sequenceNum", sequenceNum), locale));
                	serviceLogProductFacetGroupMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetSeqError", UtilMisc.toMap("rowNo", rowNo.toString(), "sequenceNum", sequenceNum), locale));
                	
                }
                
                String minDisplay = (String)productFacetGroup.get("minDisplay");
                if (UtilValidate.isNotEmpty(minDisplay) && !UtilValidate.isInteger(minDisplay))
                {
                	productFacetGroupErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetMinDisplayError", UtilMisc.toMap("rowNo", rowNo.toString(), "minDisplay", minDisplay), locale));
                	serviceLogProductFacetGroupMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetMinDisplayError", UtilMisc.toMap("rowNo", rowNo.toString(), "minDisplay", minDisplay), locale));
                	
                }
                
                String maxDisplay = (String)productFacetGroup.get("maxDisplay");
                if (UtilValidate.isNotEmpty(maxDisplay) && !UtilValidate.isInteger(maxDisplay))
                {
                	productFacetGroupErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetMaxDisplayError", UtilMisc.toMap("rowNo", rowNo.toString(), "maxDisplay", maxDisplay), locale));
                	serviceLogProductFacetGroupMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetMaxDisplayError", UtilMisc.toMap("rowNo", rowNo.toString(), "maxDisplay", maxDisplay), locale));
                	
                }
                
                if (UtilValidate.isNotEmpty(minDisplay) && UtilValidate.isNotEmpty(maxDisplay) && UtilValidate.isInteger(minDisplay) && UtilValidate.isInteger(maxDisplay))
                {
                	BigDecimal minDisplayBD = new BigDecimal(minDisplay);
                	BigDecimal maxDisplayBD = new BigDecimal(maxDisplay);
                	if(minDisplayBD.compareTo(maxDisplayBD) > 0)
                	{
                		productFacetGroupErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetMinMaxDisplayError", UtilMisc.toMap("rowNo", rowNo.toString(), "minDisplay", minDisplay, "maxDisplay", maxDisplay), locale));
                		serviceLogProductFacetGroupMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetMinMaxDisplayError", UtilMisc.toMap("rowNo", rowNo.toString(), "minDisplay", minDisplay, "maxDisplay", maxDisplay), locale));
                		
                	}
                }
                String fromDate = (String)productFacetGroup.get("fromDate");
                if(UtilValidate.isNotEmpty(fromDate))
                {
                    if(!OsafeAdminUtil.isValidDate(fromDate))
                    {
                    	productFacetGroupErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetGroupFromDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureGroup", featureGroup, "fromDate", fromDate), locale));
                    	serviceLogProductFacetGroupMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetGroupFromDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureGroup", featureGroup, "fromDate", fromDate), locale));
                    	
                    }    
                }

                String thruDate = (String)productFacetGroup.get("thruDate");
                if(UtilValidate.isNotEmpty(thruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(thruDate))
                    {
                    	productFacetGroupErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetGroupThruDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureGroup", featureGroup, "thruDate", thruDate), locale));
                    	serviceLogProductFacetGroupMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetGroupThruDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureGroup", featureGroup, "thruDate", thruDate), locale));
                    	
                    }    
                }
                serviceLogProductFacetGroupMessageList.add("ROW COMPLETE: [Processed row #"+rowNo.toString()+" Facet-Group-ID "+featureGroup+"]");
                rowNo++;
            }
            
            //Validation for Product Facet Value
            rowNo = new Integer(1);
            for(Map productFacetValue : productFacetValueDataList) 
            {
            	String errorLogText = UtilProperties.getMessage(resource, "ErrorLogTextLabel", UtilMisc.toMap("rowNo", rowNo.toString()), locale);
				String warningLogText =  UtilProperties.getMessage(resource, "WarningLogTextLabel", UtilMisc.toMap("rowNo", rowNo.toString()), locale);
				
            	String productFeatureId = (String)productFacetValue.get("facetValueId");
            	String facetGroupId = (String)productFacetValue.get("facetGroupId");
            	String featureValueDesc = (String)productFacetValue.get("description");
            	
            	serviceLogProductFacetValueMessageList.add("IN PROGRESS: [Processing row #"+rowNo.toString()+" Facet-Value-ID "+productFeatureId+"]");
            	
            	if(UtilValidate.isNotEmpty(productFeatureId))
                {
            		if(!OsafeAdminUtil.isValidFeatureFormat(productFeatureId))
                    {
            			productFacetValueErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidFacetValueError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureData", productFeatureId), locale));
            			serviceLogProductFacetValueMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidFacetValueError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureData", productFeatureId), locale));
            			
                    }
                }
            	
            	if(UtilValidate.isNotEmpty(facetGroupId))
                {
                	if((!productFeatureGroupSet.contains(facetGroupId)) && (!productFeatureTypeIds.contains(facetGroupId)))
                    {
                		productFacetValueErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetGroupValueMatchError", UtilMisc.toMap("rowNo", rowNo.toString(), "facetGroup", facetGroupId, "featureId", productFeatureId), locale));
                		serviceLogProductFacetValueMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetGroupValueMatchError", UtilMisc.toMap("rowNo", rowNo.toString(), "facetGroup", facetGroupId, "featureId", productFeatureId), locale));
                		
                    }
                	//when we attempt to convert this feature type to an ID, we will remove spaces.  Test if this will result in a valid BF ID
					String testFeatureGroup = StringUtil.removeSpaces(facetGroupId);
                    if(!OsafeAdminUtil.isValidId(testFeatureGroup))
                    {
                    	productFacetValueErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidFacetGroupError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureData", facetGroupId), locale));
                    	serviceLogProductFacetValueMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidFacetGroupError", UtilMisc.toMap("rowNo", rowNo.toString(), "featureData", facetGroupId), locale));
                    	
                    }
                }
                
                String sequenceNum = (String)productFacetValue.get("sequenceNum");
                if (UtilValidate.isNotEmpty(sequenceNum) && !UtilValidate.isInteger(sequenceNum))
                {
                	productFacetValueErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetSeqError", UtilMisc.toMap("rowNo", rowNo.toString(), "sequenceNum", sequenceNum), locale));
                	serviceLogProductFacetValueMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetSeqError", UtilMisc.toMap("rowNo", rowNo.toString(), "sequenceNum", sequenceNum), locale));
                	
                }
                
                String fromDate = (String)productFacetValue.get("fromDate");
                if(UtilValidate.isNotEmpty(fromDate))
                {
                    if(!OsafeAdminUtil.isValidDate(fromDate))
                    {
                    	productFacetValueErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetValueFromDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "productFeatureId", productFeatureId, "fromDate", fromDate), locale));
                    	serviceLogProductFacetValueMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetValueFromDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "productFeatureId", productFeatureId, "fromDate", fromDate), locale));
                    	
                    }    
                }

                String thruDate = (String)productFacetValue.get("thruDate");
                if(UtilValidate.isNotEmpty(thruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(thruDate))
                    {
                    	productFacetValueErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetValueThruDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "productFeatureId", productFeatureId, "thruDate", thruDate), locale));
                    	serviceLogProductFacetValueMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ProductFacetValueThruDateError", UtilMisc.toMap("rowNo", rowNo.toString(), "productFeatureId", productFeatureId, "thruDate", thruDate), locale));
                    	
                    }    
                }
                serviceLogProductFacetValueMessageList.add("ROW COMPLETE: [Processed row #"+rowNo.toString()+" Facet-Value-ID "+productFeatureId+"]");
                rowNo++;
            }


            //Validation for Product Manufacturers
            rowNo = new Integer(1);
            for(Map manufacturer : manufacturerDataList) 
            {
            	String errorLogText = UtilProperties.getMessage(resource, "ErrorLogTextLabel", UtilMisc.toMap("rowNo", rowNo.toString()), locale);
				String warningLogText =  UtilProperties.getMessage(resource, "WarningLogTextLabel", UtilMisc.toMap("rowNo", rowNo.toString()), locale);
				
                String manufacturerId = (String)manufacturer.get("partyId");
                String manufacturerImageThruDate = (String)manufacturer.get("manufacturerImageThruDate");
                String manufacturerState = (String)manufacturer.get("state");
                String manufacturerCountry = (String)manufacturer.get("country");
                
                serviceLogProductManufacturerMessageList.add("IN PROGRESS: [Processing row #"+rowNo.toString()+" Manufacturer-ID "+manufacturerId+"]");
                
                if(UtilValidate.isNotEmpty(manufacturerId))
                {
                    if(!OsafeAdminUtil.isValidId(manufacturerId))
                    {
                        productManufacturerErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InValidIdError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "ManuId", "idData", manufacturerId), locale));
                    }
                    if(manufacturerId.length() > 20)
                    {
                    	productManufacturerErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "IdLengthExceedError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "Manu ID", "fieldData", manufacturerId), locale));
                    	serviceLogProductManufacturerMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "IdLengthExceedError", UtilMisc.toMap("rowNo", rowNo.toString(), "idField", "Manu ID", "fieldData", manufacturerId), locale));
                    	
                    }
                }
                else
                {
                	productManufacturerErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "BlankManuIdError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                	serviceLogProductManufacturerMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "BlankManuIdError", UtilMisc.toMap("rowNo", rowNo.toString()), locale));
                	
                }
                if(UtilValidate.isNotEmpty(manufacturerImageThruDate))
                {
                    if(!OsafeAdminUtil.isValidDate(manufacturerImageThruDate))
                    {
                    	productManufacturerErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", manufacturerImageThruDate), locale));
                    	serviceLogProductManufacturerMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InvalidDateError", UtilMisc.toMap("idField", "Thru", "idData", manufacturerImageThruDate), locale));
                    	
                    }    
                }
                if(UtilValidate.isNotEmpty(manufacturerState))
                {
                	GenericValue gvManufacturerState = _delegator.findByPrimaryKey("Geo", UtilMisc.toMap("geoId", manufacturerState)); 
                	if(UtilValidate.isEmpty(gvManufacturerState) || (!("STATE".equalsIgnoreCase(gvManufacturerState.getString("geoTypeId"))) && !("PROVINCE".equalsIgnoreCase(gvManufacturerState.getString("geoTypeId")))))
                    {
                		productManufacturerErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ManufacturerStateInvalidError", UtilMisc.toMap("rowNo", rowNo.toString(), "state", manufacturerState, "manufacturerId", manufacturerId), locale));
                		serviceLogProductManufacturerMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ManufacturerStateInvalidError", UtilMisc.toMap("rowNo", rowNo.toString(), "state", manufacturerState, "manufacturerId", manufacturerId), locale));
                		
                    }
                }
                if(UtilValidate.isNotEmpty(manufacturerCountry))
                {
                	GenericValue gvManufacturerCountry = _delegator.findByPrimaryKey("Geo", UtilMisc.toMap("geoId", manufacturerCountry)); 
                	if(UtilValidate.isEmpty(gvManufacturerCountry) || !("COUNTRY".equalsIgnoreCase(gvManufacturerCountry.getString("geoTypeId"))))
                    {
                		productManufacturerErrorList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ManufacturerCountryInvalidError", UtilMisc.toMap("rowNo", rowNo.toString(), "country", manufacturerCountry, "manufacturerId", manufacturerId), locale));
                		serviceLogProductManufacturerMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "ManufacturerCountryInvalidError", UtilMisc.toMap("rowNo", rowNo.toString(), "country", manufacturerCountry, "manufacturerId", manufacturerId), locale));
                		
                    }
                }
                
                serviceLogProductManufacturerMessageList.add("ROW COMPLETE: [Processed row #"+rowNo.toString()+" Manufacturer-ID "+manufacturerId+"]");
                
                rowNo++;
            }
        }
        catch(Exception e)
        {
        	e.printStackTrace();
        	errorMessageList.add(e.getMessage());
        }
        
        result.put("prodCatErrorList", prodCatErrorList);
        result.put("prodCatWarningList", prodCatWarningList);
        result.put("productErrorList", productErrorList);
        result.put("productWarningList", productWarningList);
        result.put("productAssocErrorList", productAssocErrorList);
        result.put("productAssocWarningList", productAssocWarningList);
        result.put("productFacetGroupErrorList", productFacetGroupErrorList);
        result.put("productFacetGroupWarningList", productFacetGroupWarningList);
        result.put("productFacetValueErrorList", productFacetValueErrorList);
        result.put("productFacetValueWarningList", productFacetValueWarningList);
        result.put("productManufacturerErrorList", productManufacturerErrorList);
        result.put("productManufacturerWarningList", productManufacturerWarningList);
        result.put("errorMessageList", errorMessageList);
        
        result.put("serviceLogProdCatMessageList", serviceLogProdCatMessageList);
        result.put("serviceLogProductMessageList", serviceLogProductMessageList);
        result.put("serviceLogProductAssocMessageList", serviceLogProductAssocMessageList);
        result.put("serviceLogProductFacetGroupMessageList", serviceLogProductFacetGroupMessageList);
        result.put("serviceLogProductFacetValueMessageList", serviceLogProductFacetValueMessageList);
        result.put("serviceLogProductManufacturerMessageList", serviceLogProductManufacturerMessageList);
        
        return result;
    }
    
    public static Map<String, Object> getProductDataListFromFile(DispatchContext ctx, Map<String, ?> context) 
    {
    	
        LocalDispatcher dispatcher = ctx.getDispatcher();
        _delegator = ctx.getDelegator();
        Locale locale = (Locale) context.get("locale");
        
        List<Map> productCatDataList = FastList.newInstance();
        List<Map> productDataList = FastList.newInstance();
        List<Map> productAssocDataList = FastList.newInstance();
        List<Map> productFacetGroupDataList = FastList.newInstance();
        List<Map> productFacetValueDataList = FastList.newInstance();
        List<Map> manufacturerDataList = FastList.newInstance();
        
        final List<String> errorMessageList = FastList.newInstance();
        
        String productFilePath = (String)context.get("productFilePath");
        String productFileName = (String)context.get("productFileName");
        
        Map result = ServiceUtil.returnSuccess();
        
        if(UtilValidate.isNotEmpty(productFileName) && productFileName.endsWith(".xls"))
        {
          try 
          {
              WorkbookSettings ws = new WorkbookSettings();
              ws.setLocale(new Locale("en", "EN"));
              Workbook wb = Workbook.getWorkbook(new File(productFilePath + productFileName),ws);
              
              // Gets the sheets from workbook
              for (int sheet = 0; sheet < wb.getNumberOfSheets(); sheet++) 
              {
                  BufferedWriter bw = null; 
                  try 
                  {
                      Sheet s = wb.getSheet(sheet);
                      
                      String sTabName=s.getName();
                      if (sheet == 1)
                      {
                      	  List dataRows = OsafeProductLoaderHelper.buildDataRows(ImportServices.buildCategoryHeader(),s);
                          productCatDataList = OsafeProductLoaderHelper.getDataList(dataRows);
                      }
                      if (sheet == 2)
                      {
                      	  List dataRows = OsafeProductLoaderHelper.buildDataRows(ImportServices.buildProductHeader(),s);
                          productDataList = OsafeProductLoaderHelper.getDataList(dataRows);
                      }
                      if (sheet == 3)
                      {
                      	  List dataRows = OsafeProductLoaderHelper.buildDataRows(ImportServices.buildProductAssocHeader(),s);
                          productAssocDataList = OsafeProductLoaderHelper.getDataList(dataRows);
                      }
                      if (sheet == 4)
                      {
                      	  List dataRows = OsafeProductLoaderHelper.buildDataRows(ImportServices.buildProductFacetGroupHeader(),s);
                          productFacetGroupDataList = OsafeProductLoaderHelper.getDataList(dataRows);
                      }
                      if (sheet == 5)
                      {
                      	  List dataRows = OsafeProductLoaderHelper.buildDataRows(ImportServices.buildProductFacetValueHeader(),s);
                          productFacetValueDataList = OsafeProductLoaderHelper.getDataList(dataRows);
                      }
                      if (sheet == 6)
                      {
                      	  List dataRows = OsafeProductLoaderHelper.buildDataRows(ImportServices.buildManufacturerHeader(),s);
                          manufacturerDataList = OsafeProductLoaderHelper.getDataList(dataRows);
                      }
                  } 
                  catch (Exception exc) 
                  {
                	  errorMessageList.add(exc.getMessage());
                      Debug.logError(exc, module);
                  } 
              }
          }
          catch (FileNotFoundException fne) 
          {
        	  errorMessageList.add(fne.getMessage());
              Debug.logError(fne, module);
          }
          catch (BiffException be) 
          {
        	  errorMessageList.add(be.getMessage());
              Debug.logError(be, module);
          } 
          catch (Exception exc) 
          {
        	  errorMessageList.add(exc.getMessage());
              Debug.logError(exc, module);
          }
        }
        if(productFileName.endsWith(".xml"))
        {
            try 
            {
	            JAXBContext jaxbContext = JAXBContext.newInstance("com.osafe.feeds.osafefeeds");
	            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
	            
	            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
	            Schema schema = schemaFactory.newSchema(new File(schemaLocation));
	            unmarshaller.setSchema(schema);
	            
	            unmarshaller.setEventHandler(new javax.xml.bind.helpers.DefaultValidationEventHandler()
	            {
	            	public boolean handleEvent(ValidationEvent ve) 
	            	{  
                        // ignore warnings  
                        if (ve.getSeverity() != ValidationEvent.WARNING) 
                        {  
                            ValidationEventLocator vel = ve.getLocator();
                            errorMessageList.add("Line:Col[" + vel.getLineNumber() +  
                                ":" + vel.getColumnNumber() +  
                                "]:" + ve.getMessage());
                              
                        }  
                        return true;  
                    }
	            }
	            );
	            
	            JAXBElement<BigFishProductFeedType> bfProductFeedType = (JAXBElement<BigFishProductFeedType>)unmarshaller.unmarshal(new File(productFilePath + productFileName));
	                  	
	            List<ProductType> products = FastList.newInstance();
	            List<CategoryType> productCategories = FastList.newInstance();
	            List<AssociationType> productAssociations = FastList.newInstance();
	            List<FacetCatGroupType> productFacetCatGroups = FastList.newInstance();
	            List<FacetValueType> productFacetValues = FastList.newInstance();
	            List<ManufacturerType> productManufacturers = FastList.newInstance();
	                  	
	            ProductsType productsType = bfProductFeedType.getValue().getProducts();
	            if(UtilValidate.isNotEmpty(productsType)) 
	            {
	                products = productsType.getProduct();
	                if(products.size() > 0) 
	                {
	                    List dataRows = ImportServices.buildProductXMLDataRows(products);
	                    productDataList = OsafeProductLoaderHelper.getDataList(dataRows);
	                }
	            }
	                  	
	            ProductCategoryType productCategoryType = bfProductFeedType.getValue().getProductCategory();
	            if(UtilValidate.isNotEmpty(productCategoryType)) 
	            {
	                productCategories = productCategoryType.getCategory();
	                if(productCategories.size() > 0) 
	                {
	                    List dataRows = ImportServices.buildProductCategoryXMLDataRows(productCategories);
	                    productCatDataList = OsafeProductLoaderHelper.getDataList(dataRows);
	                }
	            }
	                  	
	            ProductAssociationType productAssociationType = bfProductFeedType.getValue().getProductAssociation();
	            if(UtilValidate.isNotEmpty(productAssociationType)) 
	            {
	                productAssociations = productAssociationType.getAssociation();
	                if(productAssociations.size() > 0) 
	                {
	                    List dataRows = ImportServices.buildProductAssociationXMLDataRows(productAssociations);
	                    productAssocDataList = OsafeProductLoaderHelper.getDataList(dataRows);
	                }
	            }
	            ProductFacetCatGroupType productFacetCatGroupType = bfProductFeedType.getValue().getProductFacetGroup();
	            if(UtilValidate.isNotEmpty(productFacetCatGroupType)) 
	            {
	            	productFacetCatGroups = productFacetCatGroupType.getFacetCatGroup();
	                if(productFacetCatGroups.size() > 0) 
	                {
	                    List dataRows = ImportServices.buildProductFacetGroupXMLDataRows(productFacetCatGroups);
	                    productFacetGroupDataList = OsafeProductLoaderHelper.getDataList(dataRows);
	                }
	            }
	            ProductFacetValueType productFacetValueType = bfProductFeedType.getValue().getProductFacetValue();
	            if(UtilValidate.isNotEmpty(productFacetValueType)) 
	            {
	            	productFacetValues = productFacetValueType.getFacetValue();
	                if(productFacetValues.size() > 0) 
	                {
	                    List dataRows = ImportServices.buildProductFacetValueXMLDataRows(productFacetValues);
	                    productFacetValueDataList = OsafeProductLoaderHelper.getDataList(dataRows);
	                }
	            }
	            ProductManufacturerType productManufacturerType = bfProductFeedType.getValue().getProductManufacturer();
	            if(UtilValidate.isNotEmpty(productManufacturerType)) 
	            {
	                productManufacturers = productManufacturerType.getManufacturer();
	                if(productManufacturers.size() > 0) 
	                {
	                    List dataRows = ImportServices.buildProductManufacturerXMLDataRows(productManufacturers);
	                    manufacturerDataList = OsafeProductLoaderHelper.getDataList(dataRows);
	                }
	            }
            }
            catch (UnmarshalException ume)
            {
            	if(UtilValidate.isNotEmpty(errorMessageList))
	            {
	                result.put("errorMessageList", errorMessageList);
	                return result;
	            }
            	errorMessageList.add(ume.getMessage());
            	Debug.logError(ume, module);
            }
            catch(JAXBException je)
            {
            	errorMessageList.add(je.getMessage());
            	Debug.logError(je, module);
            }
            catch(Exception exc)
            {
            	errorMessageList.add(exc.getMessage());
            	Debug.logError(exc, module);
            }
        }
        result.put("productCatDataList", productCatDataList);
        result.put("productDataList", productDataList);
        result.put("productAssocDataList", productAssocDataList);
        result.put("productFacetGroupDataList", productFacetGroupDataList);
        result.put("productFacetValueDataList", productFacetValueDataList);
        result.put("manufacturerDataList", manufacturerDataList);
        result.put("errorMessageList", errorMessageList);
        return result;
    }
    
    public static Map<String, Object> getOrderStatusDataListFromFile(DispatchContext ctx, Map<String, ?> context) 
    {
    	
        LocalDispatcher dispatcher = ctx.getDispatcher();
        _delegator = ctx.getDelegator();
        Locale locale = (Locale) context.get("locale");
        
        List<Map> orderStatusDataList = FastList.newInstance();
        
        final List<String> errorMessageList = FastList.newInstance();
        
        String orderStatusFilePath = (String)context.get("orderStatusFilePath");
        String orderStatusFileName = (String)context.get("orderStatusFileName");
        
        Map result = ServiceUtil.returnSuccess();
        String orderStatusCount = "";
        if(orderStatusFileName.endsWith(".xml"))
        {
            try 
            {
	            JAXBContext jaxbContext = JAXBContext.newInstance("com.osafe.feeds.osafefeeds");
	            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
	            
	            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
	            Schema schema = schemaFactory.newSchema(new File(schemaLocation));
	            unmarshaller.setSchema(schema);
	            
	            unmarshaller.setEventHandler(new javax.xml.bind.helpers.DefaultValidationEventHandler()
	            {
	            	public boolean handleEvent(ValidationEvent ve) 
	            	{  
                        // ignore warnings  
                        if (ve.getSeverity() != ValidationEvent.WARNING) 
                        {  
                            ValidationEventLocator vel = ve.getLocator();
                            errorMessageList.add("Line:Col[" + vel.getLineNumber() +  
                                ":" + vel.getColumnNumber() +  
                                "]:" + ve.getMessage());
                              
                        }  
                        return true;  
                    }
	            }
	            );
	            
	            JAXBElement<BigFishOrderStatusUpdateFeedType> bfOrderStatusUpdateFeedType = (JAXBElement<BigFishOrderStatusUpdateFeedType>)unmarshaller.unmarshal(new File(orderStatusFilePath + orderStatusFileName));
	            
	            orderStatusCount = bfOrderStatusUpdateFeedType.getValue().getCount();
	            
                List<OrderStatusType> orderStatusList = bfOrderStatusUpdateFeedType.getValue().getOrder();
            	
            	if(orderStatusList.size() > 0) 
            	{
            		List dataRows = buildOrderStatusXMLDataRows(orderStatusList);
            		orderStatusDataList = OsafeProductLoaderHelper.getDataList(dataRows);
            	}
            }
            catch (UnmarshalException ume)
            {
            	if(UtilValidate.isNotEmpty(errorMessageList))
	            {
	                result.put("errorMessageList", errorMessageList);
	                return result;
	            }
            	errorMessageList.add(ume.getMessage());
            	Debug.logError(ume, module);
            }
            catch(JAXBException je)
            {
            	errorMessageList.add(je.getMessage());
            	Debug.logError(je, module);
            }
            catch(Exception exc)
            {
            	errorMessageList.add(exc.getMessage());
            	Debug.logError(exc, module);
            }
        }
        result.put("errorMessageList", errorMessageList);
        result.put("orderStatusDataList", orderStatusDataList);
        result.put("orderStatusCount", orderStatusCount);
        return result;
    }
    
    
    public static Map<String, Object> validateOrderStatusData(DispatchContext ctx, Map<String, ?> context) 
    {
	    LocalDispatcher dispatcher = ctx.getDispatcher();
	    _delegator = ctx.getDelegator();
	    Locale locale = (Locale) context.get("locale");
	    
	    List<Map> orderStatusDataList = (List) context.get("orderStatusDataList");
	    String productStoreId = (String)context.get("productStoreId");
	    
	    List<String> errorMessageList = FastList.newInstance();
	    List<String> validateMessageList = FastList.newInstance();
	    List<String> serviceLogValidateMessageList = FastList.newInstance();
	    List<String> serviceLogWarningMessageList = FastList.newInstance();
	    
	    Map result = ServiceUtil.returnSuccess();
		
		List<String> orderIdList = FastList.newInstance();
		List<String> processedOrderIdList = FastList.newInstance();
		
		List<String> productStoreIdList =  FastList.newInstance();
	    List<String> productIdList =  FastList.newInstance();
	    
		List<GenericValue> carrierShipmentMethodList = FastList.newInstance();
		try 
		{
			carrierShipmentMethodList = _delegator.findByAnd("ProductStoreShipmentMethView", UtilMisc.toMap());
			
			
			List<GenericValue> orderHeaderList = _delegator.findByAnd("OrderHeader", UtilMisc.toMap());
			if(UtilValidate.isNotEmpty(orderHeaderList))
			{
				orderIdList = EntityUtil.getFieldListFromEntityList(orderHeaderList, "orderId", Boolean.TRUE);
			}
			
            List<GenericValue> productStoreList = _delegator.findList("ProductStore", null, UtilMisc.toSet("productStoreId"), null, null, false);
	    	
        	if(UtilValidate.isNotEmpty(productStoreList))
			{
        		productStoreIdList = EntityUtil.getFieldListFromEntityList(productStoreList, "productStoreId", Boolean.TRUE);
			}
		} 
		catch (GenericEntityException e1) 
		{
			e1.printStackTrace();
		}
	    
	    try
	    {
	    	if(orderStatusDataList.size() > 0) 
	    	{
	    		Integer rowNo = new Integer(1);
				for (int i=0 ; i < orderStatusDataList.size() ; i++) 
                {
					String errorLogText = UtilProperties.getMessage(resource, "ErrorLogTextLabel", UtilMisc.toMap("rowNo", rowNo.toString()), locale);
					String warningLogText =  UtilProperties.getMessage(resource, "WarningLogTextLabel", UtilMisc.toMap("rowNo", rowNo.toString()), locale);
					
	                Map mRow = (Map)orderStatusDataList.get(i);
	                serviceLogValidateMessageList.add("IN PROGRESS: [Processing row #"+(i+1)+" Order ID: "+(String)mRow.get("orderId")+"]");
	                List<String> carrierIdList = FastList.newInstance();
	        		List<String> shippingMethodIdList = FastList.newInstance();
	                List<GenericValue> carrierShipmentMethodListStore = FastList.newInstance();
	                
	                String orderId = (String)mRow.get("orderId");
	                String productStoreIdRow = (String)mRow.get("productStoreId");
	                GenericValue orderHeader = null;
	                Timestamp orderDate = null;
	            	boolean orderIdMatch = true;
	            	boolean productStoreIdMatch = true;
	            	boolean errorInOrder = false;
	            	if(UtilValidate.isEmpty(orderId) || !orderIdList.contains(orderId))
	            	{
	            	    orderIdMatch = false;
	            	}
	            	else
	            	{
	            		orderHeader = _delegator.findByPrimaryKey("OrderHeader", UtilMisc.toMap("orderId", orderId)); 
	            		orderDate = orderHeader.getTimestamp("orderDate");
	            	}
	            	
	            	
	            	if(!orderIdMatch)
	            	{
	            		validateMessageList.add(UtilProperties.getMessage(resource, "OrderIdMatchingError", UtilMisc.toMap("orderId", mRow.get("orderId")), locale));
	            		serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderIdMatchingError", UtilMisc.toMap("orderId", mRow.get("orderId")), locale));
	            	    errorInOrder = true;
	            	}
	            	else
	            	{
	            		if(UtilValidate.isEmpty(productStoreIdRow) || !productStoreIdList.contains(productStoreIdRow))
		            	{
	            			productStoreIdMatch = false;
	            			validateMessageList.add(UtilProperties.getMessage(resource, "ProductStoreIdMatchingError", UtilMisc.toMap("productStoreId", productStoreIdRow), locale));
	            			serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "ProductStoreIdMatchingError", UtilMisc.toMap("productStoreId", productStoreIdRow), locale));
	            			
	            			errorInOrder = true;
		            	}
	            		else
	            		{
	            			if(!productStoreIdRow.equals(orderHeader.getString("productStoreId")))
	            			{
	            				validateMessageList.add(UtilProperties.getMessage(resource, "OrderProductStoreIdMatchingError", UtilMisc.toMap("productStoreId", productStoreIdRow), locale));
	            				serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderProductStoreIdMatchingError", UtilMisc.toMap("productStoreId", productStoreIdRow), locale));
	            				productStoreIdMatch = false;
	            				errorInOrder = true;
	            			}
	            		}
	            		if(productStoreIdMatch)
	            		{
	            			if(UtilValidate.isNotEmpty(carrierShipmentMethodList)) 
	            			{
	            				carrierShipmentMethodListStore = EntityUtil.filterByAnd(carrierShipmentMethodList, UtilMisc.toMap("productStoreId", productStoreIdRow));
	            				for(GenericValue carrierMethod : carrierShipmentMethodListStore) 
	            				{
	            					carrierIdList.add(carrierMethod.getString("partyId"));
	            					shippingMethodIdList.add(carrierMethod.getString("shipmentMethodTypeId"));
	            				}
	            			}
	            		}
	            	    if(Integer.parseInt((String)mRow.get("totalOrderItems")) > 0) 
			            {
			                for(int orderItemNo = 0; orderItemNo < Integer.parseInt((String)mRow.get("totalOrderItems")); orderItemNo++) 
			            	{
			            	    boolean carrierIdMatch = true;
			            		boolean shippingMethodIdMatch = true;
			            		boolean orderItemStatusMatch = true;
			            		boolean orderItemStatusApproved = false;
			            		String currentOrderItemStatus = "";
			            		List<GenericValue> orderItems = FastList.newInstance();
			            		String orderItemCarrier = "";
			            		String orderItemShipMethod = "";
			            		if(UtilValidate.isNotEmpty(mRow.get("orderItemCarrier_" + (orderItemNo + 1))))
			            		{
			            			orderItemCarrier = (String)mRow.get("orderItemCarrier_" + (orderItemNo + 1));
			            		}
			            		if(UtilValidate.isNotEmpty(mRow.get("orderItemShipMethod_" + (orderItemNo + 1))))
			            		{
			            			orderItemShipMethod = (String)mRow.get("orderItemShipMethod_" + (orderItemNo + 1));
			            		}
			            		
			            		if(UtilValidate.isEmpty(mRow.get("productId_" + (orderItemNo + 1))) && UtilValidate.isEmpty(mRow.get("orderItemSequenceId_" + (orderItemNo + 1))) && UtilValidate.isEmpty(mRow.get("shipGroupSeqId_" + (orderItemNo + 1))))
	             		    	{
			            			validateMessageList.add(UtilProperties.getMessage(resource, "OrderItemProductSeqShipGroupIdBlankError", UtilMisc.toMap("orderId", mRow.get("orderId")), locale));
			            			serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderItemProductSeqShipGroupIdBlankError", UtilMisc.toMap("orderId", mRow.get("orderId")), locale));
	             		    		errorInOrder = true;
	             		    	}
			            		else
			            		{
			            			//IF ONLY SHIP_GROUP_SEQUENCE_ID IS SUPPLIED
			            			if(UtilValidate.isNotEmpty(mRow.get("shipGroupSeqId_" + (orderItemNo + 1))) && (UtilValidate.isEmpty(mRow.get("productId_" + (orderItemNo + 1))) && UtilValidate.isEmpty(mRow.get("orderItemSequenceId_" + (orderItemNo + 1)))))
		             		    	{
			            				GenericValue orderItemShipGroup = _delegator.findByPrimaryKey("OrderItemShipGroup", UtilMisc.toMap("orderId", mRow.get("orderId"), "shipGroupSeqId", mRow.get("shipGroupSeqId_" + (orderItemNo + 1))));
			            				if(UtilValidate.isNotEmpty(orderItemShipGroup))
			            				{
			            					List<GenericValue> orderItemShipGroupAssocs = orderItemShipGroup.getRelated("OrderItemShipGroupAssoc");
			            					if(UtilValidate.isNotEmpty(orderItemShipGroupAssocs))
			            					{
			            						for(GenericValue orderItemShipGroupAssoc : orderItemShipGroupAssocs)
			            						{
			            							orderItems.add((GenericValue) orderItemShipGroupAssoc.getRelatedOne("OrderItem"));
			            						}
			            					}
			            				}
			            				 
			            				if(UtilValidate.isEmpty(orderItemShipGroup))
			            				{
			            					validateMessageList.add(UtilProperties.getMessage(resource, "OrderItemShipGroupAssociationError", UtilMisc.toMap("orderId", mRow.get("orderId"), "shipGroupSeqId", mRow.get("shipGroupSeqId_" + (orderItemNo + 1))), locale));
			            					serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderItemShipGroupAssociationError", UtilMisc.toMap("orderId", mRow.get("orderId"), "shipGroupSeqId", mRow.get("shipGroupSeqId_" + (orderItemNo + 1))), locale));
			            					errorInOrder = true;
			            				}
		             		    	}
			            			
			            			//IF ONLY ORDER_ITEM_SEQUENCE_ID IS SUPPLIED
			            			if((UtilValidate.isEmpty(mRow.get("shipGroupSeqId_" + (orderItemNo + 1))) && UtilValidate.isEmpty(mRow.get("productId_" + (orderItemNo + 1)))) && UtilValidate.isNotEmpty(mRow.get("orderItemSequenceId_" + (orderItemNo + 1))))
		             		    	{
			            				orderItems = _delegator.findByAnd("OrderItem", UtilMisc.toMap("orderId", mRow.get("orderId"), "orderItemSeqId", mRow.get("orderItemSequenceId_" + (orderItemNo + 1))));
		         					    if(UtilValidate.isEmpty(orderItems)) 
		         					    {
		         					    	validateMessageList.add(UtilProperties.getMessage(resource, "OrderItemProductIdSeqIdMatchingError", UtilMisc.toMap("orderId", mRow.get("orderId")), locale));
		         					    	serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderItemProductIdSeqIdMatchingError", UtilMisc.toMap("orderId", mRow.get("orderId")), locale));
		         						    errorInOrder = true;
		         					    }
		             		    	}
			            			
			            			//IF ONLY PRODUCT_ID IS SUPPLIED
			            			if((UtilValidate.isEmpty(mRow.get("shipGroupSeqId_" + (orderItemNo + 1))) && UtilValidate.isEmpty(mRow.get("orderItemSequenceId_" + (orderItemNo + 1)))) && UtilValidate.isNotEmpty(mRow.get("productId_" + (orderItemNo + 1))))
		             		    	{
			            				orderItems = _delegator.findByAnd("OrderItem", UtilMisc.toMap("orderId", mRow.get("orderId"), "productId", mRow.get("productId_" + (orderItemNo + 1))));
		         					    if(UtilValidate.isEmpty(orderItems)) 
		         					    {
		         					    	validateMessageList.add(UtilProperties.getMessage(resource, "OrderItemProductIdMatchingError", UtilMisc.toMap("orderId", mRow.get("orderId")), locale));
		         					    	serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderItemProductIdMatchingError", UtilMisc.toMap("orderId", mRow.get("orderId")), locale));
		         					    	errorInOrder = true;
		         					    }
		             		    	}
			            			//IF ONLY SHIP_GROUP_SEQUENCE_ID AND PRODUCT_ID IS SUPPLIED, ORDER_ITEM_SEQUENCE_ID (AND IF IT IS SUPPLIED THEN, WILL BE IGNORED) IS NOT SUPPLIED
			            			if((UtilValidate.isNotEmpty(mRow.get("shipGroupSeqId_" + (orderItemNo + 1))) && UtilValidate.isNotEmpty(mRow.get("productId_" + (orderItemNo + 1)))))
		             		    	{
			            				List orderItemAndShipGroupAssocs = _delegator.findByAnd("OrderItemAndShipGroupAssoc", UtilMisc.toMap("orderId", mRow.get("orderId"), "productId", mRow.get("productId_" + (orderItemNo + 1)), "shipGroupSeqId", mRow.get("shipGroupSeqId_" + (orderItemNo + 1))));
			            				orderItems = _delegator.findByAnd("OrderItem", UtilMisc.toMap("orderId", mRow.get("orderId"), "productId", mRow.get("productId_" + (orderItemNo + 1))));
		         					    if(UtilValidate.isEmpty(orderItemAndShipGroupAssocs)) 
		         					    {
		         					    	validateMessageList.add(UtilProperties.getMessage(resource, "OrderItemShipGroupSeqIdProductIdMatchingError", UtilMisc.toMap("orderId", mRow.get("orderId"), "productId", mRow.get("productId_" + (orderItemNo + 1)), "shipGroupSeqId", mRow.get("shipGroupSeqId_" + (orderItemNo + 1))), locale));
		         					    	serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderItemShipGroupSeqIdProductIdMatchingError", UtilMisc.toMap("orderId", mRow.get("orderId"), "productId", mRow.get("productId_" + (orderItemNo + 1)), "shipGroupSeqId", mRow.get("shipGroupSeqId_" + (orderItemNo + 1))), locale));
		         						    errorInOrder = true;
		         					    }
		             		    	}
			            			
			            			//IF ONLY SHIP_GROUP_SEQUENCE_ID AND ORDER_ITEM_SEQUENCE_ID IS SUPPLIED, PRODUCT_ID IS NOT SUPPLIED
			            			if((UtilValidate.isNotEmpty(mRow.get("shipGroupSeqId_" + (orderItemNo + 1))) && UtilValidate.isNotEmpty(mRow.get("orderItemSequenceId_" + (orderItemNo + 1)))) && UtilValidate.isEmpty(mRow.get("productId_" + (orderItemNo + 1))))
		             		    	{
			            				List orderItemAndShipGroupAssocs = _delegator.findByAnd("OrderItemAndShipGroupAssoc", UtilMisc.toMap("orderId", mRow.get("orderId"), "orderItemSeqId", mRow.get("orderItemSequenceId_" + (orderItemNo + 1)), "shipGroupSeqId", mRow.get("shipGroupSeqId_" + (orderItemNo + 1))));
			            				orderItems = _delegator.findByAnd("OrderItem", UtilMisc.toMap("orderId", mRow.get("orderId"), "orderItemSeqId", mRow.get("orderItemSequenceId_" + (orderItemNo + 1))));
		         					    if(UtilValidate.isEmpty(orderItemAndShipGroupAssocs)) 
		         					    {
		         					    	validateMessageList.add(UtilProperties.getMessage(resource, "OrderItemShipGroupSeqIdItemSeqIdMatchingError", UtilMisc.toMap("orderId", mRow.get("orderId"), "orderItemSeqId", mRow.get("orderItemSequenceId_" + (orderItemNo + 1)), "shipGroupSeqId", mRow.get("shipGroupSeqId_" + (orderItemNo + 1))), locale));
		         					    	serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderItemShipGroupSeqIdItemSeqIdMatchingError", UtilMisc.toMap("orderId", mRow.get("orderId"), "orderItemSeqId", mRow.get("orderItemSequenceId_" + (orderItemNo + 1)), "shipGroupSeqId", mRow.get("shipGroupSeqId_" + (orderItemNo + 1))), locale));
		         						    errorInOrder = true;
		         					    }
		             		    	}
			            			if(UtilValidate.isEmpty(mRow.get("orderItemStatus_"+ (orderItemNo + 1))) || (!mRow.get("orderItemStatus_"+ (orderItemNo + 1)).toString().equalsIgnoreCase("COMPLETED") && !mRow.get("orderItemStatus_"+ (orderItemNo + 1)).toString().equalsIgnoreCase("CANCELLED")))
					            	{
		             		    		orderItemStatusMatch = false;
					            	}
		             		    	else
		             		    	{    
		             		    		if(UtilValidate.isNotEmpty(orderItems))
		             		    	    { 
		             		    		    for(GenericValue orderItem : orderItems)
		             		    		    {
		             		    		    	currentOrderItemStatus = orderItem.getString("statusId");
		             		    			    if(currentOrderItemStatus.equalsIgnoreCase("ITEM_APPROVED"))
		             		    			    {
		             		    			    	orderItemStatusApproved = true;
		             		    			    }
		             		    			    else
		             		    			    {
		             		    			    	orderItemStatusApproved = false;
		             		    			    	break;
		             		    			    }
		             		    	 	    }
		             		    		    if(!orderItemStatusApproved)
							            	{
		             		    		    	validateMessageList.add(UtilProperties.getMessage(resource, "OrderItemStatusApprovedError", UtilMisc.toMap("currentOrderItemStatus", currentOrderItemStatus,"currentOrderId",orderId),locale));
		             		    		    	serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderItemStatusApprovedError", UtilMisc.toMap("currentOrderItemStatus", currentOrderItemStatus,"currentOrderId",orderId),locale));
							            		errorInOrder = true;
							            	}
		             		    	    }
		             		    	}
		             		    	
		             		    	if(!orderItemStatusMatch)
					            	{
		             		    		validateMessageList.add(UtilProperties.getMessage(resource, "OrderItemStatusMatchingError", UtilMisc.toMap("orderItemStatus", mRow.get("orderItemStatus_"+ (orderItemNo + 1)),"currentOrderId",orderId),locale));
		             		    		serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderItemStatusMatchingError", UtilMisc.toMap("orderItemStatus", mRow.get("orderItemStatus_"+ (orderItemNo + 1)),"currentOrderId",orderId),locale));
					            		errorInOrder = true;
					            	}
		             		    	
		             		    	if(UtilValidate.isNotEmpty(mRow.get("orderItemShipDate_"+ (orderItemNo + 1))))
					                {
					                    if(!OsafeAdminUtil.isValidDate(mRow.get("orderItemShipDate_"+ (orderItemNo + 1)).toString()))
					                    {
					                    	validateMessageList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InValidOrderItemShipDateError", UtilMisc.toMap("orderItemShipDate", mRow.get("orderItemShipDate_"+ (orderItemNo + 1)),"currentOrderId",orderId), locale));
					                    	serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InValidOrderItemShipDateError", UtilMisc.toMap("orderItemShipDate", mRow.get("orderItemShipDate_"+ (orderItemNo + 1)),"currentOrderId",orderId), locale));
					                    	errorInOrder = true;
					                    }
					                    else
					                    {
					                    	String orderItemShipDateStr = (String)mRow.get("orderItemShipDate_"+ (orderItemNo + 1));
					                    	java.util.Date orderItemShipDate = (java.util.Date) OsafeAdminUtil.validDate(orderItemShipDateStr);
					                    	Timestamp orderItemShipDateTs = OsafeAdminUtil.toTimestamp(_sdf.format(orderItemShipDate), "yyyy-MM-dd HH:mm:ss");
					                    	orderItemShipDateTs = UtilDateTime.getDayEnd(orderItemShipDateTs);
					                    	if(orderItemShipDateTs.before(orderDate))
					                    	{
					                    		validateMessageList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "OrderItemShipDateBeforeOrderDateError", UtilMisc.toMap("currentOrderId",orderId), locale));
					                    		serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "OrderItemShipDateBeforeOrderDateError", UtilMisc.toMap("currentOrderId",orderId), locale));
					                    		errorInOrder = true;
					                    	}
					                    }
					                }
		             		    	if(productStoreIdMatch)
		    	            		{
		             		    		if(UtilValidate.isNotEmpty(orderItemCarrier)) 
			             		    	{
			             		    		if(!carrierIdList.contains(orderItemCarrier)) 
			             		    		{
			             		    			carrierIdMatch = false;
			             		    		}
			             		    	}
			             		    	else
			             		    	{
			             		    		if(UtilValidate.isNotEmpty(orderItemShipMethod)) 
				             		    	{
			             		    			carrierIdMatch = false;
				             		    	}
			             		    	}
			             		    	if(UtilValidate.isNotEmpty(orderItemShipMethod)) 
			             		    	{
		                                    if(!shippingMethodIdList.contains(orderItemShipMethod)) 
		                                    {
		                                    	shippingMethodIdMatch = false;
			             		    		}
			             		    	}
			             		    	else
			             		    	{
			             		    		if(UtilValidate.isNotEmpty(orderItemCarrier)) 
				             		    	{
			             		    			shippingMethodIdMatch = false;
			             		    		}
			             		    	}
			             		    	
			             		    	if(UtilValidate.isNotEmpty(orderItemCarrier) && UtilValidate.isNotEmpty(orderItemShipMethod))
			             		    	{
			             		    		if(UtilValidate.isNotEmpty(carrierShipmentMethodListStore)) 
			             					{
			             		    			List carrierShipmentMethodListOrderItem = EntityUtil.filterByAnd(carrierShipmentMethodListStore, UtilMisc.toMap("shipmentMethodTypeId", orderItemShipMethod, "partyId", orderItemCarrier, "roleTypeId", "CARRIER"));
			             		    			if(UtilValidate.isEmpty(carrierShipmentMethodListOrderItem))
			             		    			{
			             		    				validateMessageList.add(UtilProperties.getMessage(resource, "OrderCarrierShippingMethodMatchingError", UtilMisc.toMap("carrierId", orderItemCarrier, "shippingMethodId", orderItemShipMethod,"currentOrderId",orderId),locale));
			             		    				serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderCarrierShippingMethodMatchingError", UtilMisc.toMap("carrierId", orderItemCarrier, "shippingMethodId", orderItemShipMethod,"currentOrderId",orderId),locale));
			             		    				errorInOrder = true;
			             		    			}
			             					}
			             		    	}
			             		    	
			             		    	if(!carrierIdMatch) 
				             		    {
			             		    		validateMessageList.add(UtilProperties.getMessage(resource, "OrderItemCarrierMatchingError", UtilMisc.toMap("orderItemCarrierId", orderItemCarrier,"currentOrderId",orderId),locale));
			             		    		serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderItemCarrierMatchingError", UtilMisc.toMap("orderItemCarrierId", orderItemCarrier,"currentOrderId",orderId),locale));
				             		    	errorInOrder = true;
				             		    }
				             		    if(!shippingMethodIdMatch) 
				             		    {
				             		    	validateMessageList.add(UtilProperties.getMessage(resource, "OrderItemShippingMethodMatchingError", UtilMisc.toMap("orderItemShippingMethodId", orderItemShipMethod,"currentOrderId",orderId),locale));
				             		    	serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderItemShippingMethodMatchingError", UtilMisc.toMap("orderItemShippingMethodId", orderItemShipMethod,"currentOrderId",orderId),locale));
				             		    	errorInOrder = true;
				             		    }
		    	            		}
			            		}
			            	}
			            } 
			            else 
			            {
			            	String currentOrderStatus = "";
			                boolean carrierIdMatch = true;
			            	boolean shippingMethodIdMatch = true;
			            	boolean orderStatusMatch = true;
			            	boolean orderStatusApproved = false;
			            	String orderShipCarrier = "";
			            	String orderShipMethod = "";
			            	if(UtilValidate.isNotEmpty(mRow.get("orderShipCarrier")))
			            	{
			            		orderShipCarrier = (String)mRow.get("orderShipCarrier");
			            	}
			            	if(UtilValidate.isNotEmpty(mRow.get("orderShipMethod")))
			            	{
			            		orderShipMethod = (String)mRow.get("orderShipMethod");
			            	}
			            	
			            	if(UtilValidate.isEmpty(mRow.get("orderStatus")) || (!mRow.get("orderStatus").toString().equalsIgnoreCase("COMPLETED") && !mRow.get("orderStatus").toString().equalsIgnoreCase("CANCELLED")))
			            	{
			            		orderStatusMatch = false;
			            	}
			            	else
			            	{
			            		if(UtilValidate.isNotEmpty(orderHeader))
				            	{
			            			currentOrderStatus = orderHeader.getString("statusId");
				            		if(UtilValidate.isNotEmpty(currentOrderStatus) && currentOrderStatus.equalsIgnoreCase("ORDER_APPROVED"))
				            		{
				            			orderStatusApproved = true;
				            		}
				            	}
			            	}
			            	
			            	if(!orderStatusMatch)
			            	{
			            		validateMessageList.add(UtilProperties.getMessage(resource, "OrderStatusMatchingError", UtilMisc.toMap("orderStatus", mRow.get("orderStatus"),"currentOrderId",orderId),locale));
			            		serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderStatusMatchingError", UtilMisc.toMap("orderStatus", mRow.get("orderStatus"),"currentOrderId",orderId),locale));
			            		errorInOrder = true;
			            	}
			            	else if(!orderStatusApproved)
			            	{
			            		validateMessageList.add(UtilProperties.getMessage(resource, "OrderStatusApprovedError", UtilMisc.toMap("currentOrderStatus", currentOrderStatus,"currentOrderId", orderId),locale));
			            		serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderStatusApprovedError", UtilMisc.toMap("currentOrderStatus", currentOrderStatus,"currentOrderId", orderId),locale));
			            		errorInOrder = true;
			            	}
			            	
			            	if(UtilValidate.isNotEmpty(mRow.get("orderShipDate")))
			                {
			                    if(!OsafeAdminUtil.isValidDate(mRow.get("orderShipDate").toString()))
			                    {
			                    	validateMessageList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "InValidOrderShipDateError", UtilMisc.toMap("orderShipDate", mRow.get("orderShipDate"),"currentOrderId",orderId), locale));
			                    	serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "InValidOrderShipDateError", UtilMisc.toMap("orderShipDate", mRow.get("orderShipDate"),"currentOrderId",orderId), locale));
			                    	errorInOrder = true;
			                    }
			                    else
			                    {
			                    	String orderShipDateStr = (String)mRow.get("orderShipDate");
			                    	java.util.Date orderShipDate = (java.util.Date) OsafeAdminUtil.validDate(orderShipDateStr);
			                    	Timestamp orderShipDateTs = OsafeAdminUtil.toTimestamp(_sdf.format(orderShipDate), "yyyy-MM-dd HH:mm:ss");
			                    	orderShipDateTs = UtilDateTime.getDayEnd(orderShipDateTs);
			                    	if(orderShipDateTs.before(orderDate))
			                    	{
			                    		validateMessageList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "OrderShipDateBeforeOrderDateError", UtilMisc.toMap("currentOrderId",orderId), locale));
			                    		serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage("OSafeAdminUiLabels", "OrderShipDateBeforeOrderDateError", UtilMisc.toMap("currentOrderId",orderId), locale));
			                    		errorInOrder = true;
			                    	}
			                    }
			                }
			            	if(productStoreIdMatch)
    	            		{
			            		if(UtilValidate.isNotEmpty(orderShipCarrier)) 
				            	{
		                            if(!carrierIdList.contains(orderShipCarrier)) 
		                            {
		                                carrierIdMatch = false;
		             		        }
		             		    }
				            	else
				            	{
				            		if(UtilValidate.isNotEmpty(orderShipMethod)) 
					            	{
				            			carrierIdMatch = false;
					            	}
				            	}
				            	if(UtilValidate.isNotEmpty(orderShipMethod)) 
				            	{
		                            if(!shippingMethodIdList.contains(orderShipMethod)) 
		                            {
		                                shippingMethodIdMatch = false;
		             		    	}
		             		    }
				            	else
				            	{
				            		if(UtilValidate.isNotEmpty(orderShipCarrier)) 
					            	{
				            			shippingMethodIdMatch = false;
					            	}
				            	}
		             		    if(!carrierIdMatch) 
		             		    {
		             		    	validateMessageList.add(UtilProperties.getMessage(resource, "OrderCarrierMatchingError", UtilMisc.toMap("carrierId", orderShipCarrier,"currentOrderId",orderId),locale));
		             		    	serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderCarrierMatchingError", UtilMisc.toMap("carrierId", orderShipCarrier,"currentOrderId",orderId),locale));
		             		    	errorInOrder = true;
		             		    }
		             		    if(!shippingMethodIdMatch) 
		             		    {
		             		    	validateMessageList.add(UtilProperties.getMessage(resource, "OrderShippingMethodMatchingError", UtilMisc.toMap("shippingMethodId", orderShipMethod,"currentOrderId",orderId),locale));
		             		    	serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderShippingMethodMatchingError", UtilMisc.toMap("shippingMethodId", orderShipMethod,"currentOrderId",orderId),locale));
		             		    	errorInOrder = true;
		             		    }
		             		    
		             		    if(UtilValidate.isNotEmpty(orderShipCarrier) && UtilValidate.isNotEmpty(orderShipMethod))
	            		    	{
	            		    		if(UtilValidate.isNotEmpty(carrierShipmentMethodListStore)) 
	            					{
	            		    			List carrierShipmentMethodListOrder = EntityUtil.filterByAnd(carrierShipmentMethodListStore, UtilMisc.toMap("shipmentMethodTypeId", orderShipMethod, "partyId", orderShipCarrier, "roleTypeId", "CARRIER"));
	            		    			if(UtilValidate.isEmpty(carrierShipmentMethodListOrder))
	            		    			{
	            		    				validateMessageList.add(UtilProperties.getMessage(resource, "OrderCarrierShippingMethodMatchingError", UtilMisc.toMap("carrierId", orderShipCarrier, "shippingMethodId", orderShipMethod,"currentOrderId",orderId),locale));
	            		    				serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "OrderCarrierShippingMethodMatchingError", UtilMisc.toMap("carrierId", orderShipCarrier, "shippingMethodId", orderShipMethod,"currentOrderId",orderId),locale));
	            		    				errorInOrder = true;
	            		    			}
	            					}
	            		    	}
    	            		}
			            }
	            	}
	            	if(!errorInOrder && UtilValidate.isNotEmpty(orderId))
	            	{
	            		processedOrderIdList.add(orderId);
	            	}
	            	serviceLogValidateMessageList.add("ROW COMPLETE: [Processed row #"+(i+1)+" Order ID: "+(String)mRow.get("orderId")+"]");
	            	rowNo++;
	            }
	        }
	    }
	    catch(Exception e)
	    {
	        e.printStackTrace();
	        errorMessageList.add(e.getMessage());
	    }
	    result.put("errorMessageList", errorMessageList);
	    result.put("validateMessageList", validateMessageList);
	    result.put("serviceLogValidateMessageList", serviceLogValidateMessageList);
	    result.put("serviceLogWarningMessageList", serviceLogWarningMessageList);
	    result.put("processedOrderIdList", processedOrderIdList);
	    return result;
    }
    
    
    public static Map<String, Object> getProductRatingDataListFromFile(DispatchContext ctx, Map<String, ?> context) 
    {
    	
        LocalDispatcher dispatcher = ctx.getDispatcher();
        _delegator = ctx.getDelegator();
        Locale locale = (Locale) context.get("locale");
        
        List<Map> productRatingDataList = FastList.newInstance();
        
        final List<String> errorMessageList = FastList.newInstance();
        
        String productRatingFilePath = (String)context.get("productRatingFilePath");
        String productRatingFileName = (String)context.get("productRatingFileName");
        
        Map result = ServiceUtil.returnSuccess();
        String productRatingCount = "";
        
        if(productRatingFileName.endsWith(".xml"))
        {
            try 
            {
	            JAXBContext jaxbContext = JAXBContext.newInstance("com.osafe.feeds.osafefeeds");
	            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
	            
	            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
	            Schema schema = schemaFactory.newSchema(new File(schemaLocation));
	            unmarshaller.setSchema(schema);
	            
	            unmarshaller.setEventHandler(new javax.xml.bind.helpers.DefaultValidationEventHandler()
	            {
	            	public boolean handleEvent(ValidationEvent ve) 
	            	{  
                        // ignore warnings  
                        if (ve.getSeverity() != ValidationEvent.WARNING) 
                        {  
                            ValidationEventLocator vel = ve.getLocator();
                            errorMessageList.add("Line:Col[" + vel.getLineNumber() +  
                                ":" + vel.getColumnNumber() +  
                                "]:" + ve.getMessage());
                              
                        }  
                        return true;  
                    }
	            }
	            );
	            
	            JAXBElement<BigFishProductRatingFeedType> bfProductRatingFeedType = (JAXBElement<BigFishProductRatingFeedType>)unmarshaller.unmarshal(new File(productRatingFilePath + productRatingFileName));
	            
                List<ProductRatingType> productRatingList = bfProductRatingFeedType.getValue().getProductRating();
            	
                productRatingCount = bfProductRatingFeedType.getValue().getCount();
                
            	if(productRatingList.size() > 0) 
            	{
            		List dataRows = buildProductRatingXMLDataRows(productRatingList);
            		productRatingDataList = OsafeProductLoaderHelper.getDataList(dataRows);
            	}
            }
            catch (UnmarshalException ume)
            {
            	if(UtilValidate.isNotEmpty(errorMessageList))
	            {
	                result.put("errorMessageList", errorMessageList);
	                return result;
	            }
            	errorMessageList.add(ume.getMessage());
            	Debug.logError(ume, module);
            }
            catch(JAXBException je)
            {
            	errorMessageList.add(je.getMessage());
            	Debug.logError(je, module);
            }
            catch(Exception exc)
            {
            	errorMessageList.add(exc.getMessage());
            	Debug.logError(exc, module);
            }
        }
        result.put("errorMessageList", errorMessageList);
        result.put("productRatingDataList", productRatingDataList);
        result.put("productRatingCount", productRatingCount);
        return result;
    }
    

    public static Map<String, Object> validateProductRatingData(DispatchContext ctx, Map<String, ?> context) 
    {
	    LocalDispatcher dispatcher = ctx.getDispatcher();
	    _delegator = ctx.getDelegator();
	    Locale locale = (Locale) context.get("locale");
	    
	    List<Map> productRatingDataList = (List) context.get("productRatingDataList");
	
	    List<String> errorMessageList = FastList.newInstance();
	    List<String> productStoreIdList =  FastList.newInstance();
	    List<String> productIdList =  FastList.newInstance();
	    List<String> validateMessageList = FastList.newInstance();
	    List<String> serviceLogValidateMessageList = FastList.newInstance();
	    List<String> serviceLogWarningMessageList = FastList.newInstance();
	    List<String> processedProductIdList = FastList.newInstance();
	    
	    try
	    {
	    	List<GenericValue> productStoreList = _delegator.findList("ProductStore", null, UtilMisc.toSet("productStoreId"), null, null, false);
	    	List<GenericValue> productsList = _delegator.findList("Product", null, UtilMisc.toSet("productId"), null, null, false);
	    	
        	if(UtilValidate.isNotEmpty(productStoreList))
			{
        		productStoreIdList = EntityUtil.getFieldListFromEntityList(productStoreList, "productStoreId", Boolean.TRUE);
			}
        	if(UtilValidate.isNotEmpty(productsList))
			{
        		productIdList = EntityUtil.getFieldListFromEntityList(productsList, "productId", Boolean.TRUE);
			}
        	
	    	if(productRatingDataList.size() > 0) 
	    	{
				for (int i=0 ; i < productRatingDataList.size() ; i++) 
                {
	                Map mRow = (Map)productRatingDataList.get(i);
	                String productStoreId = (String)mRow.get("productStoreId");
	                String productRatingScore = (String)mRow.get("productRatingScore");
	                String productId = (String)mRow.get("productId");
	                String validProductId = "";
	                String sku = (String)mRow.get("sku");
	                
	                String errorLogText = UtilProperties.getMessage(resource, "ErrorLogTextLabel", UtilMisc.toMap("rowNo", (i+1)), locale);
					String warningLogText =  UtilProperties.getMessage(resource, "WarningLogTextLabel", UtilMisc.toMap("rowNo", (i+1)), locale);
	                
	                boolean productStoreIdMatch = true;
	                boolean productRatingScoreValid = true;
	                boolean errorInProductRating = false;
	                
	            	if(UtilValidate.isEmpty(productStoreId) || !productStoreIdList.contains((String)productStoreId))
	            	{
	            		productStoreIdMatch = false;
	            	}
	            	if(UtilValidate.isNotEmpty(productId))
	            	{
	            		serviceLogValidateMessageList.add("IN PROGRESS: [Processing row #"+(i+1)+" Product ID: "+productId+"]");
	            	}
	            	else
	            	{
	            		if(UtilValidate.isNotEmpty(sku))
		            	{
		            		serviceLogValidateMessageList.add("IN PROGRESS: [Processing row #"+(i+1)+" SKU: "+sku+"]");
		            	}
	            	}
	            	
	            	if(!productStoreIdMatch)
	            	{
	            		validateMessageList.add(UtilProperties.getMessage(resource, "ProductStoreIdMatchingError", UtilMisc.toMap("productStoreId", productStoreId), locale));
	            		serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "ProductStoreIdMatchingError", UtilMisc.toMap("productStoreId", productStoreId), locale));
	            	    errorInProductRating = true;
	            	}
	            	
	                if(UtilValidate.isNotEmpty(productRatingScore))
	                {
	                    boolean checkFloatResult = OsafeAdminUtil.isFloat(productRatingScore);
	                    if(!checkFloatResult)
	                    {
	                    	validateMessageList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ValidProductRatingScoreError", UtilMisc.toMap("productRatingScore", productRatingScore), locale));
	                    	serviceLogValidateMessageList.add(errorLogText +UtilProperties.getMessage("OSafeAdminUiLabels", "ValidProductRatingScoreError", UtilMisc.toMap("productRatingScore", productRatingScore), locale));
	                    	errorInProductRating = true;
	                    }
	                    else
	                    {
	                    	float productRatingScoreF = Float.parseFloat(productRatingScore);
	                    	if(productRatingScoreF < 0 || productRatingScoreF > 10)
	                    	{
	                    		validateMessageList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ValidProductRatingScoreError", UtilMisc.toMap("productRatingScore", productRatingScore), locale));
	                    		serviceLogValidateMessageList.add(errorLogText +UtilProperties.getMessage("OSafeAdminUiLabels", "ValidProductRatingScoreError", UtilMisc.toMap("productRatingScore", productRatingScore), locale));
	                    		errorInProductRating = true;
	                    	}
	                    }
	                }
	                else
	                {
	                	validateMessageList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ValidProductRatingScoreError", UtilMisc.toMap("productRatingScore", productRatingScore), locale));
	                	serviceLogValidateMessageList.add(errorLogText +UtilProperties.getMessage("OSafeAdminUiLabels", "ValidProductRatingScoreError", UtilMisc.toMap("productRatingScore", productRatingScore), locale));
	                	errorInProductRating = true;
	                }
	                if(UtilValidate.isEmpty(productId) && UtilValidate.isEmpty(sku))
	                {
	                	validateMessageList.add(UtilProperties.getMessage("OSafeAdminUiLabels", "ProductIdSkuBlankError", UtilMisc.toMap(), locale));
	                	serviceLogValidateMessageList.add(errorLogText +UtilProperties.getMessage("OSafeAdminUiLabels", "ProductIdSkuBlankError", UtilMisc.toMap(), locale));
	                	errorInProductRating = true;
	                }
	                
	                if(UtilValidate.isNotEmpty(productId))
	            	{
	                	if(!productIdList.contains(productId))
	                	{
	                		validateMessageList.add(UtilProperties.getMessage(resource, "ProductRatingScoreProductIdMatchingError", UtilMisc.toMap("productId", productId), locale));
	                		serviceLogValidateMessageList.add(errorLogText +UtilProperties.getMessage(resource, "ProductRatingScoreProductIdMatchingError", UtilMisc.toMap("productId", productId), locale));
	                		errorInProductRating = true;
	                	}
	                	else
	                	{
	                		validProductId = productId;
	                	}
	            	}
	                else
	                {
	                	if(UtilValidate.isNotEmpty(sku))
	                	{
	                		List<GenericValue> goodIdentificationList = _delegator.findByAnd("GoodIdentification", UtilMisc.toMap("goodIdentificationTypeId", "SKU", "idValue", sku));
		            		if(UtilValidate.isEmpty(goodIdentificationList)) 
		            		{
		            			validateMessageList.add(UtilProperties.getMessage(resource, "SkuMatchingError", UtilMisc.toMap("sku", sku), locale));
		            			serviceLogValidateMessageList.add(errorLogText +UtilProperties.getMessage(resource, "SkuMatchingError", UtilMisc.toMap("sku", sku), locale));
		            			errorInProductRating = true;
		            		}
		            		else
		            		{
		            			validProductId = EntityUtil.getFirst(goodIdentificationList).getString("productId");
		            		}
	                	}
	                }
	                if(UtilValidate.isNotEmpty(validProductId))
	                {
	                	GenericValue product = _delegator.findByPrimaryKey("Product", UtilMisc.toMap("productId", validProductId));
	                	if(UtilValidate.isNotEmpty(product))
	                	{
	                		if("Y".equals(product.getString("isVariant")))
	                		{
	                			validateMessageList.add(UtilProperties.getMessage(resource, "RatingApplicableTypeError", UtilMisc.toMap(), locale));
	                			serviceLogValidateMessageList.add(errorLogText +UtilProperties.getMessage(resource, "RatingApplicableTypeError", UtilMisc.toMap(), locale));
	                			errorInProductRating = true;
	                		}
	                	}
	                }
	                
	                if(!errorInProductRating)
	            	{
	                	if(UtilValidate.isNotEmpty(productId))
		            	{
	                		processedProductIdList.add(productId);
		            	}
		            	else
		            	{
		            		if(UtilValidate.isNotEmpty(sku))
			            	{
		            			processedProductIdList.add(sku);
			            	}
		            	}
	            	}
	                
	                if(UtilValidate.isNotEmpty(productId))
	            	{
	            		serviceLogValidateMessageList.add("ROW COMPLETE: [Processed row #"+(i+1)+" Product ID: "+productId+"]");
	            	}
	            	else
	            	{
	            		if(UtilValidate.isNotEmpty(sku))
		            	{
		            		serviceLogValidateMessageList.add("ROW COMPLETE: [Processed row #"+(i+1)+" SKU: "+sku+"]");
		            	}
	            	}
	            }
	        }
	    }
	    catch(Exception e)
	    {
	        e.printStackTrace();
	        errorMessageList.add(e.getMessage());
	    }
	
	    Map result = ServiceUtil.returnSuccess();
	    
	    result.put("errorMessageList", errorMessageList);
	    result.put("validateMessageList", validateMessageList);
	    result.put("serviceLogValidateMessageList", serviceLogValidateMessageList);
	    result.put("serviceLogWarningMessageList", serviceLogWarningMessageList);
	    result.put("processedProductIdList", processedProductIdList);
	    
	    return result;
    }
    
    public static Map<String, Object> getStoreDataListFromFile(DispatchContext ctx, Map<String, ?> context) 
    {
    	
        LocalDispatcher dispatcher = ctx.getDispatcher();
        _delegator = ctx.getDelegator();
        Locale locale = (Locale) context.get("locale");
        
        List<Map> storeDataList = FastList.newInstance();
        
        final List<String> errorMessageList = FastList.newInstance();
        
        String storeFilePath = (String)context.get("storeFilePath");
        String storeFileName = (String)context.get("storeFileName");
        
        Map result = ServiceUtil.returnSuccess();
        String storeCount = "";
        
        if(storeFileName.endsWith(".xml"))
        {
            try 
            {
	            JAXBContext jaxbContext = JAXBContext.newInstance("com.osafe.feeds.osafefeeds");
	            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
	            
	            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
	            Schema schema = schemaFactory.newSchema(new File(schemaLocation));
	            unmarshaller.setSchema(schema);
	            
	            unmarshaller.setEventHandler(new javax.xml.bind.helpers.DefaultValidationEventHandler()
	            {
	            	public boolean handleEvent(ValidationEvent ve) 
	            	{  
                        // ignore warnings  
                        if (ve.getSeverity() != ValidationEvent.WARNING) 
                        {  
                            ValidationEventLocator vel = ve.getLocator();
                            errorMessageList.add("Line:Col[" + vel.getLineNumber() +  
                                ":" + vel.getColumnNumber() +  
                                "]:" + ve.getMessage());
                              
                        }  
                        return true;  
                    }
	            }
	            );
	            
	            JAXBElement<BigFishStoreFeedType> bfStoreFeedType = (JAXBElement<BigFishStoreFeedType>)unmarshaller.unmarshal(new File(storeFilePath + storeFileName));
	            
                List<StoreType> storeList = bfStoreFeedType.getValue().getStore();
            	
                storeCount = bfStoreFeedType.getValue().getCount();
                
            	if(storeList.size() > 0) 
            	{
            		List dataRows = buildStoreXMLDataRows(storeList);
            		storeDataList = OsafeProductLoaderHelper.getDataList(dataRows);
            	}
            }
            catch (UnmarshalException ume)
            {
            	if(UtilValidate.isNotEmpty(errorMessageList))
	            {
	                result.put("errorMessageList", errorMessageList);
	                return result;
	            }
            	errorMessageList.add(ume.getMessage());
            	Debug.logError(ume, module);
            }
            catch(JAXBException je)
            {
            	errorMessageList.add(je.getMessage());
            	Debug.logError(je, module);
            }
            catch(Exception exc)
            {
            	errorMessageList.add(exc.getMessage());
            	Debug.logError(exc, module);
            }
        }
        result.put("errorMessageList", errorMessageList);
        result.put("storeDataList", storeDataList);
        result.put("storeCount", storeCount);
        return result;
    }
    
    
    public static Map<String, Object> validateStoreData(DispatchContext ctx, Map<String, ?> context) 
    {
	    LocalDispatcher dispatcher = ctx.getDispatcher();
	    _delegator = ctx.getDelegator();
	    Locale locale = (Locale) context.get("locale");
	    
	    List<Map> storeDataList = (List) context.get("storeDataList");
	
	    List<String> errorMessageList = FastList.newInstance();
	    List<String> productStoreIdList =  FastList.newInstance();
	    List<String> validateMessageList = FastList.newInstance();
	    List<String> serviceLogValidateMessageList = FastList.newInstance();
	    List<String> serviceLogWarningMessageList = FastList.newInstance();
	    List<String> processedStoreCodeList = FastList.newInstance();
	    
	    try
	    {
	    	List<GenericValue> productStoreList = _delegator.findList("ProductStore", null, UtilMisc.toSet("productStoreId"), null, null, false);
	    	
        	if(UtilValidate.isNotEmpty(productStoreList))
			{
        		productStoreIdList = EntityUtil.getFieldListFromEntityList(productStoreList, "productStoreId", Boolean.TRUE);
			}
        	
	    	if(storeDataList.size() > 0) 
	    	{
				for (int i=0 ; i < storeDataList.size() ; i++) 
                {
	                Map mRow = (Map)storeDataList.get(i);
	                String productStoreId = (String)mRow.get("productStoreId");
	                String storeCode = (String)mRow.get("storeCode");
	                
	                serviceLogValidateMessageList.add("IN PROGRESS: [Processing row #"+(i+1)+" Store Code: "+storeCode+"]");
	                
	                String errorLogText = UtilProperties.getMessage(resource, "ErrorLogTextLabel", UtilMisc.toMap("rowNo", (i+1)), locale);
					String warningLogText =  UtilProperties.getMessage(resource, "WarningLogTextLabel", UtilMisc.toMap("rowNo", (i+1)), locale);
	                
	                boolean productStoreIdMatch = true;
	                boolean errorInStore = false;
	                
	            	if(UtilValidate.isEmpty(productStoreId) || !productStoreIdList.contains((String)productStoreId))
	            	{
	            		productStoreIdMatch = false;
	            	}
	            	
	            	if(!productStoreIdMatch)
	            	{
	            		validateMessageList.add(UtilProperties.getMessage(resource, "ProductStoreIdMatchingError", UtilMisc.toMap("productStoreId", productStoreId), locale));
	            		serviceLogValidateMessageList.add(errorLogText + UtilProperties.getMessage(resource, "ProductStoreIdMatchingError", UtilMisc.toMap("productStoreId", productStoreId), locale));
	            		errorInStore = true;
	            	}
	            	
	                if(!errorInStore)
	            	{
	                	if(UtilValidate.isNotEmpty(storeCode))
		            	{
	                		processedStoreCodeList.add(storeCode);
		            	}
	            	}
	                
	                serviceLogValidateMessageList.add("ROW COMPLETE: [Processed row #"+(i+1)+" Store Code: "+storeCode+"]");
	                
	            }
	        }
	    }
	    catch(Exception e)
	    {
	        e.printStackTrace();
	        errorMessageList.add(e.getMessage());
	    }
	
	    Map result = ServiceUtil.returnSuccess();
	    
	    result.put("errorMessageList", errorMessageList);
	    result.put("validateMessageList", validateMessageList);
	    result.put("serviceLogValidateMessageList", serviceLogValidateMessageList);
	    result.put("serviceLogWarningMessageList", serviceLogWarningMessageList);
	    result.put("processedStoreCodeList", processedStoreCodeList);
	    
	    return result;
    }
    
    
    public static Map<String, Object> getCustomerDataListFromFile(DispatchContext ctx, Map<String, ?> context) 
    {
    	
        LocalDispatcher dispatcher = ctx.getDispatcher();
        _delegator = ctx.getDelegator();
        Locale locale = (Locale) context.get("locale");
        
        List<Map> customerDataList = FastList.newInstance();
        
        final List<String> errorMessageList = FastList.newInstance();
        
        String customerFilePath = (String)context.get("customerFilePath");
        String customerFileName = (String)context.get("customerFileName");
        
        Map result = ServiceUtil.returnSuccess();
        
        if(customerFileName.endsWith(".xml"))
        {
            try 
            {
	            JAXBContext jaxbContext = JAXBContext.newInstance("com.osafe.feeds.osafefeeds");
	            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
	            
	            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
	            Schema schema = schemaFactory.newSchema(new File(schemaLocation));
	            unmarshaller.setSchema(schema);
	            
	            unmarshaller.setEventHandler(new javax.xml.bind.helpers.DefaultValidationEventHandler()
	            {
	            	public boolean handleEvent(ValidationEvent ve) 
	            	{  
                        // ignore warnings  
                        if (ve.getSeverity() != ValidationEvent.WARNING) 
                        {  
                            ValidationEventLocator vel = ve.getLocator();
                            errorMessageList.add("Line:Col[" + vel.getLineNumber() +  
                                ":" + vel.getColumnNumber() +  
                                "]:" + ve.getMessage());
                              
                        }  
                        return true;  
                    }
	            }
	            );
	            
	            JAXBElement<BigFishCustomerFeedType> bfCustomerFeedType = (JAXBElement<BigFishCustomerFeedType>)unmarshaller.unmarshal(new File(customerFilePath + customerFileName));
	            
                List<CustomerType> customerList = bfCustomerFeedType.getValue().getCustomer();
            	
            	if(customerList.size() > 0) 
            	{
            		List dataRows = buildCustomerXMLDataRows(customerList);
            		customerDataList = OsafeProductLoaderHelper.getDataList(dataRows);
            	}
            }
            catch (UnmarshalException ume)
            {
            	if(UtilValidate.isNotEmpty(errorMessageList))
	            {
	                result.put("errorMessageList", errorMessageList);
	                return result;
	            }
            	errorMessageList.add(ume.getMessage());
            	Debug.logError(ume, module);
            }
            catch(JAXBException je)
            {
            	errorMessageList.add(je.getMessage());
            	Debug.logError(je, module);
            }
            catch(Exception exc)
            {
            	errorMessageList.add(exc.getMessage());
            	Debug.logError(exc, module);
            }
        }
        result.put("errorMessageList", errorMessageList);
        result.put("customerDataList", customerDataList);
        return result;
    }
    
    public static Map<String, Object> validateCustomerData(DispatchContext ctx, Map<String, ?> context) 
    {
	    LocalDispatcher dispatcher = ctx.getDispatcher();
	    _delegator = ctx.getDelegator();
	    Locale locale = (Locale) context.get("locale");
	    
	    List<Map> customerDataList = (List) context.get("customerDataList");
	
	    List<String> errorMessageList = FastList.newInstance();
	    
	    if(customerDataList.size() > 0) 
	    {
			for (int i=0 ; i < customerDataList.size() ; i++) 
            {
            	 Map mRow = (Map)customerDataList.get(i);
            	 if(UtilValidate.isNotEmpty(mRow.get("userName"))) 
            	 {
            		 if(UtilValidate.isNotEmpty(mRow.get("customerId")))
	            	 {
	            		 try 
	            		 {
	            		     GenericValue userLoginGv = _delegator.findByPrimaryKey("UserLogin", UtilMisc.toMap("userLoginId", mRow.get("userName")));
	            		     if(UtilValidate.isNotEmpty(userLoginGv) && !userLoginGv.getString("partyId").equals(mRow.get("customerId")))
	            		     {
	            		    	 errorMessageList.add(UtilProperties.getMessage(resource, "UserNameUniqueError", UtilMisc.toMap(), locale));
	            		     }
	            		 }
	            		 catch (GenericEntityException gee) 
	            		 {
	            			 gee.printStackTrace();
	            		 }
	            	 } 
            		 else 
            		 {
	            		 try 
	            		 {
	            			 GenericValue userLoginGv = _delegator.findByPrimaryKey("UserLogin", UtilMisc.toMap("userLoginId", mRow.get("userName")));
		            		 if(UtilValidate.isNotEmpty(userLoginGv)) 
		            		 {
		            			 errorMessageList.add(UtilProperties.getMessage(resource, "UserNameAssociateAnotherPartyIdError", UtilMisc.toMap(), locale));
		            		 }	 
	            		 } 
	            		 catch (GenericEntityException gee) 
	            		 {
	            			 gee.printStackTrace();
						}
	            	 }
            	 }
            	 
             }
		}
	
	    Map result = ServiceUtil.returnSuccess();
	    result.put("errorMessageList", errorMessageList);
	    
	    return result;
    }
    
    public static Map<String, Object> importOrderStatusXML(DispatchContext ctx, Map<String, ?> context) {LocalDispatcher dispatcher = ctx.getDispatcher();
    _delegator = ctx.getDelegator();
    List<String> messages = FastList.newInstance();

    String xmlDataFilePath = (String)context.get("xmlDataFile");
    String xmlDataDirPath = (String)context.get("xmlDataDir");
    Boolean autoLoad = (Boolean) context.get("autoLoad");
    GenericValue userLogin = (GenericValue) context.get("userLogin");
    List processedOrderIdList = (List)context.get("processedOrderIdList");
    if (autoLoad == null) autoLoad = Boolean.FALSE;

    File inputWorkbook = null;
    String tempDataFile = null;
    File baseDataDir = null;
    File baseFilePath = null;
    BufferedWriter fOutProduct=null;
    if (UtilValidate.isNotEmpty(xmlDataFilePath) && UtilValidate.isNotEmpty(xmlDataDirPath)) 
    {
    	baseFilePath = new File(xmlDataFilePath);
        try 
        {
            URL xlsDataFileUrl = UtilURL.fromFilename(xmlDataFilePath);
            InputStream ins = xlsDataFileUrl.openStream();

            if (ins != null && (xmlDataFilePath.toUpperCase().endsWith("XML"))) 
            {
                baseDataDir = new File(xmlDataDirPath);
                if (baseDataDir.isDirectory() && baseDataDir.canWrite()) 
                {

                    // ############################################
                    // move the existing xml files in dump directory
                    // ############################################
                    File dumpXmlDir = null;
                    File[] fileArray = baseDataDir.listFiles();
                    for (File file: fileArray) 
                    {
                        try 
                        {
                            if (file.getName().toUpperCase().endsWith("XML")) {
                                if (dumpXmlDir == null) 
                                {
                                    dumpXmlDir = new File(baseDataDir, "dumpxml_"+UtilDateTime.nowDateString());
                                }
                                FileUtils.copyFileToDirectory(file, dumpXmlDir);
                                file.delete();
                            }
                        } 
                        catch (IOException ioe) 
                        {
                            Debug.logError(ioe, module);
                        } 
                        catch (Exception exc) 
                        {
                            Debug.logError(exc, module);
                        }
                    }
                    // ######################################
                    //save the temp xls data file on server 
                    // ######################################
                    try 
                    {
                    	tempDataFile = UtilDateTime.nowAsString()+"."+FilenameUtils.getExtension(xmlDataFilePath);
                        inputWorkbook = new File(baseDataDir,  tempDataFile);
                        if (inputWorkbook.createNewFile()) 
                        {
                            Streams.copy(ins, new FileOutputStream(inputWorkbook), true, new byte[1]); 
                        }
                    }
                    catch (IOException ioe) 
                    {
                            Debug.logError(ioe, module);
                    } 
                    catch (Exception exc) 
                    {
                            Debug.logError(exc, module);
                    }
                }
                else 
                {
                    messages.add("xml data dir path not found or can't be write");
                }
            }
            else
            {
                messages.add(" path specified for Excel sheet file is wrong , doing nothing.");
            }

        } 
        catch (IOException ioe) 
        {
            Debug.logError(ioe, module);
        } 
        catch (Exception exc) 
        {
            Debug.logError(exc, module);
        }
    }
    else 
    {
        messages.add("No path specified for Excel sheet file or xml data direcotry, doing nothing.");
    }

    // ######################################
    //read the temp xls file and generate xml 
    // ######################################
    List dataRows = FastList.newInstance();
    try 
    {
	    if (inputWorkbook != null && baseDataDir  != null) 
	    {
	    	try 
	    	{
	    		JAXBContext jaxbContext = JAXBContext.newInstance("com.osafe.feeds.osafefeeds");
	        	Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
	        	JAXBElement<BigFishOrderStatusUpdateFeedType> bfOrderStatusUpdateFeedType = (JAXBElement<BigFishOrderStatusUpdateFeedType>)unmarshaller.unmarshal(inputWorkbook);
	        	
	        	List<OrderStatusType> orderList = bfOrderStatusUpdateFeedType.getValue().getOrder();
	        	
	        	if(orderList.size() > 0) 
	        	{
	        		dataRows = buildOrderStatusXMLDataRows(orderList);
	        		updateOrderShipGroupFeed(dataRows, userLogin, dispatcher, processedOrderIdList);
	        	}
	    	} 
	    	catch (Exception e) 
	    	{
	    		Debug.logError(e, module);
			}
	    	finally 
	    	{
	            try 
	            {
	                if (fOutProduct != null) 
	                {
	                	fOutProduct.close();
	                }
	            } catch (IOException ioe) 
	            {
	                Debug.logError(ioe, module);
	            }
	        }
	    }
	    
    } 
    catch (Exception exc) 
    {
        Debug.logError(exc, module);
    }
    finally 
    {
        inputWorkbook.delete();
    } 
            
    Map<String, Object> resp = UtilMisc.toMap("messages", (Object) messages);
    return resp;  
    }
    
    
    private static void updateOrderShipGroupFeed(List dataRows, GenericValue userLogin, LocalDispatcher dispatcher, List processedOrderIdList) 
    {
        for (int i=0 ; i < dataRows.size() ; i++) 
        {
        	Map mRow = (Map)dataRows.get(i);
        	String orderId = (String)mRow.get("orderId");
        	if(UtilValidate.isNotEmpty(orderId) && processedOrderIdList.contains(orderId))
        	{
            	GenericValue orderHeader = null;
    			try 
    			{
    				orderHeader = _delegator.findByPrimaryKey("OrderHeader", UtilMisc.toMap("orderId", (String)mRow.get("orderId")));
    			} 
    			catch (GenericEntityException e2) 
    			{
    				e2.printStackTrace();
    			} 
            	 
            	 OrderReadHelper orderReadHelper = new OrderReadHelper(orderHeader);  
            	 
            	 Map<String, Set> shipGroupOrderItemSeqIdMap = FastMap.newInstance();
            	 Map<String, String> shipGroupOrderItemStatusMap = FastMap.newInstance();
            	 HashSet orderItemSeqIdList = new HashSet();
            	 
                 if(Integer.parseInt((String)mRow.get("totalOrderItems")) > 0) 
                 {
                	 for(int orderItemNo = 0; orderItemNo < Integer.parseInt((String)mRow.get("totalOrderItems")); orderItemNo++) 
                	 {

            			 List andExprs = FastList.newInstance();
            			 
            			 andExprs.add(EntityCondition.makeCondition("orderId", EntityOperator.EQUALS, mRow.get("orderId")));
            			 
            			 if(UtilValidate.isEmpty(mRow.get("productId_" + (orderItemNo + 1))) && UtilValidate.isNotEmpty(mRow.get("orderItemSequenceId_" + (orderItemNo + 1))))
            			 {
            				 andExprs.add(EntityCondition.makeCondition("orderItemSeqId", EntityOperator.EQUALS, mRow.get("orderItemSequenceId_" + (orderItemNo + 1))));
            			 }
            			 if(UtilValidate.isNotEmpty(mRow.get("productId_" + (orderItemNo + 1))))
            			 {
            				 andExprs.add(EntityCondition.makeCondition("productId", EntityOperator.EQUALS, mRow.get("productId_" + (orderItemNo + 1))));
            			 }
            			 if(UtilValidate.isNotEmpty(mRow.get("shipGroupSeqId_" + (orderItemNo + 1))))
            			 {
            				 andExprs.add(EntityCondition.makeCondition("shipGroupSeqId", EntityOperator.EQUALS, mRow.get("shipGroupSeqId_" + (orderItemNo + 1))));
            			 }
            			 
            			 List<GenericValue> orderItemAndShipGroupAssocs = FastList.newInstance();
            			 
                 		 try 
                 		 {
                 		     orderItemAndShipGroupAssocs = _delegator.findList("OrderItemAndShipGroupAssoc", EntityCondition.makeCondition(andExprs, EntityOperator.AND), null, null, null, false);
                 		 } 
                 		 catch (GenericEntityException e) 
                 		 {
             			     e.printStackTrace();
             			 }
                 		 if(UtilValidate.isNotEmpty(orderItemAndShipGroupAssocs))
                 		 {
                 		     for(GenericValue orderItemAndShipGroupAssoc : orderItemAndShipGroupAssocs) 
                 			 {
                 		    	 boolean sameShipment = false;
                 		    	 List<GenericValue> orderItemShipGroupAssocList = FastList.newInstance();
    							 try 
    							 {
    								 orderItemShipGroupAssocList = _delegator.findByAnd("OrderItemShipGroupAssoc", UtilMisc.toMap("orderId", (String)mRow.get("orderId"), "shipGroupSeqId", orderItemAndShipGroupAssoc.getString("shipGroupSeqId")), UtilMisc.toList("+orderItemSeqId"));
    							 }  
    							 catch (GenericEntityException e1) 
    							 {
    								 e1.printStackTrace();
    							 }
                 		    	 if(orderItemShipGroupAssocList.size() > 1) 
                				 {
                					 sameShipment = true;
                				 }
                 		    	 if(UtilValidate.isEmpty(mRow.get("orderItemSequenceId_" + (orderItemNo + 1))) && (UtilValidate.isNotEmpty(mRow.get("shipGroupSeqId_" + (orderItemNo + 1))) || UtilValidate.isNotEmpty(mRow.get("productId_" + (orderItemNo + 1)))))
                 		    	 {
                 		    		sameShipment = false;
                 		    	 }
                 		    	 if(sameShipment) 
    	            			 {
                 		    		 //Create New OrderItemShipGroup
                 		    		 Long maxShipGroupSeqId = Long.valueOf("1");
                 		    		 List<GenericValue> allOrderItemShipGroupAssocList = FastList.newInstance();
    								 try 
    								 {
    									 allOrderItemShipGroupAssocList = _delegator.findByAnd("OrderItemShipGroupAssoc", UtilMisc.toMap("orderId", (String)mRow.get("orderId")), UtilMisc.toList("+shipGroupSeqId"));
    								 }
    								 catch (GenericEntityException e) 
    								 {
    									e.printStackTrace();
    								 }
    	            				 for(GenericValue allOrderItemShipGroupAssoc : allOrderItemShipGroupAssocList) 
    	            				 {
    	            					 Long curShipGroupSeqId = Long.parseLong(allOrderItemShipGroupAssoc.getString("shipGroupSeqId"));
    	            					 if(curShipGroupSeqId > maxShipGroupSeqId) 
    	            					 {
    	            						 maxShipGroupSeqId = curShipGroupSeqId;
    	            					 }
    	            				 }
    	            				 for(GenericValue orderItemShipGroupAssoc : orderItemShipGroupAssocList) 
    	            				 {
    	            					 if(orderItemShipGroupAssoc.getString("orderItemSeqId").equals(orderItemAndShipGroupAssoc.getString("orderItemSeqId")))
    	            					 {
    	            						 Set orderItemSeqIds = FastSet.newInstance();
    	            						 maxShipGroupSeqId = maxShipGroupSeqId + 1;
    		            					 String shipGroupSeqId = UtilFormatOut.formatPaddedNumber(maxShipGroupSeqId.longValue(), 5);
    			            				 GenericValue orderItemShipGroup = null;
    										 try 
    										 {
    											 orderItemShipGroup = _delegator.findByPrimaryKey("OrderItemShipGroup", UtilMisc.toMap("orderId", orderItemShipGroupAssoc.getString("orderId"), "shipGroupSeqId", orderItemShipGroupAssoc.getString("shipGroupSeqId")));
    										 }
    										 catch (GenericEntityException e) 
    										 {
    											 e.printStackTrace();
    										 }
    			            				 GenericValue orderItemShipGroupClone = (GenericValue) orderItemShipGroup.clone();
    			            				 orderItemShipGroupClone.set("shipGroupSeqId", shipGroupSeqId);
    			            				 if(UtilValidate.isNotEmpty((String)mRow.get("orderItemShipMethod_" + (orderItemNo + 1))))
    		    				             {
    			            					 orderItemShipGroupClone.set("shipmentMethodTypeId", (String)mRow.get("orderItemShipMethod_" + (orderItemNo + 1)));
    		    				             }
    			            				 if(UtilValidate.isNotEmpty((String)mRow.get("orderItemCarrier_" + (orderItemNo + 1))))
    		    				             {
    			            					 orderItemShipGroupClone.set("carrierPartyId", (String)mRow.get("orderItemCarrier_" + (orderItemNo + 1)));
    		    				             }
    			            				 if(UtilValidate.isNotEmpty((String)mRow.get("orderItemTrackingNumber_" + (orderItemNo + 1)))) 
    		    				             {
    			            					 orderItemShipGroupClone.set("trackingNumber", (String)mRow.get("orderItemTrackingNumber_" + (orderItemNo + 1)));
    		    				             }
    			            				 
    			            				 String sEstimatedShipDate = (String)mRow.get("orderItemShipDate_" + (orderItemNo + 1));
    		    				             if(UtilValidate.isNotEmpty(sEstimatedShipDate))
    		    				             {
    		    				            	 try 
    			                            	 {
    		    				            		 java.util.Date formattedShipDate=OsafeAdminUtil.validDate(sEstimatedShipDate);
    												 Timestamp estimatedShipDate = (Timestamp) ObjectType.simpleTypeConvert(_sdf.format(formattedShipDate), "Timestamp", "yyyy-MM-dd HH:mm:ss", null);
    												 orderItemShipGroupClone.set("estimatedShipDate",estimatedShipDate);
    											 } 
    			                            	 catch (GeneralException e) 
    			                            	 {
    												e.printStackTrace();
    								 			 }
    		    				             }
    		    				             
    		    				             try 
    		    				             {
    											orderItemShipGroupClone.create();
    										 }
    		    				             catch (GenericEntityException e) 
    		    				             {
    											e.printStackTrace();
    										 }
    		    				             
    		    				             //Create the OrderItemShipGroupAssoc for new create ShipGroupSeqId.
    		    				             GenericValue newOrderItemShipGroupAssoc = _delegator.makeValue("OrderItemShipGroupAssoc", UtilMisc.toMap("orderId", orderItemShipGroupAssoc.getString("orderId"), "orderItemSeqId", orderItemShipGroupAssoc.getString("orderItemSeqId"), "shipGroupSeqId", shipGroupSeqId));
    		    				             newOrderItemShipGroupAssoc.set("quantity", orderItemShipGroupAssoc.getBigDecimal("quantity"));
    		    				             if(((String)mRow.get("orderItemStatus_" + (orderItemNo + 1))).equalsIgnoreCase("CANCELLED"))
    		    			                 {
    		    				            	 newOrderItemShipGroupAssoc.set("cancelQuantity", orderItemShipGroupAssoc.getBigDecimal("quantity"));
    		    			                 }
    		    				             else
    		    				             {
    		    				            	 newOrderItemShipGroupAssoc.set("cancelQuantity", orderItemShipGroupAssoc.getBigDecimal("cancelQuantity"));
    		    				             }
    		    				             try 
    		    				             {
    											 newOrderItemShipGroupAssoc.create();
    										 } 
    		    				             catch (GenericEntityException e) 
    		    				             {
    											e.printStackTrace();
    										 }
    		    				             
    		    				             //Remove the existing orderItemShipGroupAssoc Record
    		    				             try 
    		    				             {
    											_delegator.removeValue(orderItemShipGroupAssoc);
    										 } 
    		    				             catch (GenericEntityException e) 
    										 {
    											e.printStackTrace();
    										 }
    		    				             
    		    				             //ONLY REQUIRED FOR COMPLETED STATUS
    		    				             if(((String)mRow.get("orderItemStatus_" + (orderItemNo + 1))).equalsIgnoreCase("COMPLETED"))
    		    				             {
    		    				            	 if(UtilValidate.isNotEmpty(shipGroupOrderItemSeqIdMap.get(shipGroupSeqId)))
    			    				             {
    		    				            		 Set orderItemSeqIdSet =  shipGroupOrderItemSeqIdMap.get(orderItemShipGroup.getString("shipGroupSeqId"));
    		    			                		 orderItemSeqIdSet.add(orderItemShipGroupAssoc.getString("orderItemSeqId"));
    		    					            	 orderItemSeqIds.addAll(orderItemSeqIdSet);
    			    				             }
    			    				             else
    			    				             {
    			    				            	 orderItemSeqIds.add(orderItemShipGroupAssoc.getString("orderItemSeqId"));
    			    				             }
    			    				             shipGroupOrderItemSeqIdMap.put(shipGroupSeqId, orderItemSeqIds);
    		    				             }
    		    				             
    		    				             orderItemSeqIdList.add(orderItemShipGroupAssoc.getString("orderItemSeqId"));
    		    				             shipGroupOrderItemStatusMap.put(shipGroupSeqId, (String)mRow.get("orderItemStatus_" + (orderItemNo + 1)));
    	            					 }
    	            				 }
    	            			 }
                 		    	 else
                 		    	 {
                 		    	     //Update Existing OrderItemShipGroup
                 		    		 for(GenericValue orderItemShipGroupAssoc : orderItemShipGroupAssocList) 
    	            				 {
                 		    			if(orderItemShipGroupAssoc.getString("orderItemSeqId").equals(orderItemAndShipGroupAssoc.getString("orderItemSeqId")))
    	            					{
                 		    				 Set orderItemSeqIds = FastSet.newInstance();
                 		    				 GenericValue orderItemShipGroup = null; 
    										 try 
    										 {
    											orderItemShipGroup = orderItemShipGroupAssoc.getRelatedOne("OrderItemShipGroup");
    										 } 
    										 catch (GenericEntityException e1) 
    										 {
    											e1.printStackTrace();
    										 }
    	             		    		     
    		    				             Map updateOrderItemShipGroupParams = UtilMisc.toMap("orderId", orderItemShipGroup.getString("orderId"),
    		    			                            "shipGroupSeqId",orderItemShipGroup.getString("shipGroupSeqId"),
    		    			                            "userLogin", userLogin);
    		    				             if (UtilValidate.isNotEmpty(mRow.get("orderItemShipMethod_" + (orderItemNo + 1))))
    		    				             {
    		    				            	 if(!mRow.get("orderItemShipMethod_" + (orderItemNo + 1)).equals(orderItemShipGroup.getString("shipmentMethodTypeId")))
    		    				            	 {
    		    				            		 updateOrderItemShipGroupParams.put("shipmentMethodTypeId", mRow.get("orderItemShipMethod_" + (orderItemNo + 1)));
    		    				            	 }
    		    				             }
    		    				             if (UtilValidate.isNotEmpty(mRow.get("orderItemCarrier_" + (orderItemNo + 1))))
    		    				             {
    		    				            	 if(!mRow.get("orderItemCarrier_" + (orderItemNo + 1)).equals(orderItemShipGroup.getString("carrierPartyId")))
    		    				            	 {
    		    				            		 updateOrderItemShipGroupParams.put("carrierPartyId", mRow.get("orderItemCarrier_" + (orderItemNo + 1)));
    		    				            	 }
    		    				             }
    		    				             if (UtilValidate.isNotEmpty(mRow.get("orderItemTrackingNumber_" + (orderItemNo + 1))))
    		    				             {
    				            				 if(!mRow.get("orderItemTrackingNumber_" + (orderItemNo + 1)).equals(orderItemShipGroup.getString("trackingNumber"))) 
    				            				 {
    				            					 updateOrderItemShipGroupParams.put("trackingNumber", mRow.get("orderItemTrackingNumber_" + (orderItemNo + 1)));
    				            				 }
    		    				             }
    		    				             
    		    				             String sEstimatedShipDate = (String)mRow.get("orderItemShipDate_" + (orderItemNo + 1));
    		    				             if(UtilValidate.isNotEmpty(sEstimatedShipDate))
    		    				             {
    		    				            	 try 
    			                            	 {
    		    				            		 java.util.Date formattedShipDate=OsafeAdminUtil.validDate(sEstimatedShipDate);
    												 Timestamp estimatedShipDate = (Timestamp) ObjectType.simpleTypeConvert(_sdf.format(formattedShipDate), "Timestamp", "yyyy-MM-dd HH:mm:ss", null);
    												 updateOrderItemShipGroupParams.put("estimatedShipDate",estimatedShipDate);
    											 } 
    			                            	 catch (GeneralException e) 
    			                            	 {
    												e.printStackTrace();
    								 			 }
    		    				             }
    		                            	 
    		    			                   try 
    		    			                   {
    		    			                       Map result = dispatcher.runSync("updateOrderItemShipGroup", updateOrderItemShipGroupParams);
    		    			                   } 
    		    			                   catch(GenericServiceException e)
    		    			                   {
    		    			                       Debug.logError(e, module);
    		    			                   }
    		    			                   
    		    			                   //ONLY REQUIRED FOR COMPLETED STATUS
    		    			                   if(((String)mRow.get("orderItemStatus_" + (orderItemNo + 1))).equalsIgnoreCase("COMPLETED"))
    		    			                   {
    		    			                	   if(UtilValidate.isNotEmpty(shipGroupOrderItemSeqIdMap.get(orderItemShipGroup.getString("shipGroupSeqId"))))
    			    				               {
    		    			                		   Set orderItemSeqIdSet =  shipGroupOrderItemSeqIdMap.get(orderItemShipGroup.getString("shipGroupSeqId"));
    		    			                		   orderItemSeqIdSet.add(orderItemShipGroupAssoc.getString("orderItemSeqId"));
    			    				            	   orderItemSeqIds.addAll(orderItemSeqIdSet);
    			    				               }
    			    				               else
    			    				               {
    			    				            	   orderItemSeqIds.add(orderItemShipGroupAssoc.getString("orderItemSeqId"));
    			    				               }
    			    			                   shipGroupOrderItemSeqIdMap.put(orderItemShipGroup.getString("shipGroupSeqId"), orderItemSeqIds);
    		    			                   }
    		    			                   if(((String)mRow.get("orderItemStatus_" + (orderItemNo + 1))).equalsIgnoreCase("CANCELLED"))
    		    			                   {
    		    			                	   orderItemShipGroupAssoc.set("cancelQuantity", orderItemShipGroupAssoc.getBigDecimal("quantity"));
    		    			                	   try 
    		    			                	   {
    											       _delegator.store(orderItemShipGroupAssoc);
    											   } 
    		    			                	   catch (GenericEntityException e) 
    		    			                	   {
    											       e.printStackTrace();
    											   }
    		    			                   }
    		    			                   
    		    			                   orderItemSeqIdList.add(orderItemShipGroupAssoc.getString("orderItemSeqId"));
    		    			                   shipGroupOrderItemStatusMap.put(orderItemShipGroup.getString("shipGroupSeqId"), (String)mRow.get("orderItemStatus_" + (orderItemNo + 1)));
    	            					   }
    	            				   }
                 		    	   }
                 			   }
                 		   }
                	 }
                 } 
                 else 
                 {

                	 List<GenericValue> orderItemShipGroupAssocList = FastList.newInstance();
    				 try 
    				 {
    					 orderItemShipGroupAssocList = _delegator.findByAnd("OrderItemShipGroupAssoc", UtilMisc.toMap("orderId", (String)mRow.get("orderId")), UtilMisc.toList("+orderItemSeqId"));
    				 } 
    				 catch (GenericEntityException e1) 
    				 {
    					 e1.printStackTrace();
    				 }
                	 if(UtilValidate.isNotEmpty(orderItemShipGroupAssocList)) 
                	 {
                		 for(GenericValue orderItemShipGroupAssoc : orderItemShipGroupAssocList) 
                		 {
                			 Set orderItemSeqIds = FastSet.newInstance();
                			 GenericValue orderItemShipGroup = null;
    						 try 
    						 {
    						     orderItemShipGroup = orderItemShipGroupAssoc.getRelatedOne("OrderItemShipGroup");
    						 } 
    						 catch (GenericEntityException e1) 
    						 {
    							 e1.printStackTrace();
    						 }
     		    		     
    			             Map updateOrderItemShipGroupParams = UtilMisc.toMap("orderId", orderItemShipGroup.getString("orderId"),
    		                            "shipGroupSeqId",orderItemShipGroup.getString("shipGroupSeqId"),
    		                            "userLogin", userLogin);
    			             
    			             if (UtilValidate.isNotEmpty(mRow.get("orderShipMethod")))
    			             {
    			            	 if(!mRow.get("orderShipMethod").equals(orderItemShipGroup.getString("shipmentMethodTypeId")))
    			            	 {
    			            		 updateOrderItemShipGroupParams.put("shipmentMethodTypeId",(String)mRow.get("orderShipMethod"));
    			            	 }
    			             }
    			             
    			             if (UtilValidate.isNotEmpty(mRow.get("orderShipCarrier")))
    			             {
    			            	 if(!mRow.get("orderShipCarrier").equals(orderItemShipGroup.getString("carrierPartyId")))
    			            	 {
    			            		 updateOrderItemShipGroupParams.put("carrierPartyId",(String)mRow.get("orderShipCarrier"));
    			            	 }
    			             }
    			             
    			             if (UtilValidate.isNotEmpty(mRow.get("orderTrackingNumber")))
    			             {
                				 if(!mRow.get("orderTrackingNumber").equals(orderItemShipGroup.getString("trackingNumber"))) 
                				 {
                					 updateOrderItemShipGroupParams.put("trackingNumber",(String)mRow.get("orderTrackingNumber"));
                				 }
    			             }
    			             if(UtilValidate.isNotEmpty(mRow.get("orderShipDate"))) 
                             {
                            	 String sEstimatedShipDate=(String)mRow.get("orderShipDate");
                            	 try 
                            	 {
                            		 java.util.Date formattedShipDate=OsafeAdminUtil.validDate(sEstimatedShipDate);
    								 Timestamp estimatedShipDate = (Timestamp) ObjectType.simpleTypeConvert(_sdf.format(formattedShipDate), "Timestamp", "yyyy-MM-dd HH:mm:ss", null);
    								 updateOrderItemShipGroupParams.put("estimatedShipDate",estimatedShipDate);
    							 } 
                            	 catch (GeneralException e) 
                            	 {
    								e.printStackTrace();
    				 			 }
                             }
                             else
                             {
                            	 updateOrderItemShipGroupParams.put("estimatedShipDate", UtilDateTime.nowTimestamp());
                             }
    			             
    		                 try 
    		                 {
    		                     Map result = dispatcher.runSync("updateOrderItemShipGroup", updateOrderItemShipGroupParams);
    		                 } 
    		                 catch(GenericServiceException e)
    		                 {
    		                     Debug.logError(e, module);
    		                 }
    		                 
    		                 if(((String)mRow.get("orderStatus")).equalsIgnoreCase("COMPLETED"))
    		                 {
    		                	 if(UtilValidate.isNotEmpty(shipGroupOrderItemSeqIdMap.get(orderItemShipGroup.getString("shipGroupSeqId"))))
    				             {
    		                		 Set orderItemSeqIdSet =  shipGroupOrderItemSeqIdMap.get(orderItemShipGroup.getString("shipGroupSeqId"));
    		                		 orderItemSeqIdSet.add(orderItemShipGroupAssoc.getString("orderItemSeqId"));
    				            	 orderItemSeqIds.addAll(orderItemSeqIdSet);
    				             }
    				             else
    				             {
    				                 orderItemSeqIds.add(orderItemShipGroupAssoc.getString("orderItemSeqId"));
    				             }
    			                 shipGroupOrderItemSeqIdMap.put(orderItemShipGroup.getString("shipGroupSeqId"), orderItemSeqIds);
    		                 }
    		                 
    		                 if(((String)mRow.get("orderStatus")).equalsIgnoreCase("CANCELLED"))
    		                 {
    		                	 orderItemShipGroupAssoc.set("cancelQuantity", orderItemShipGroupAssoc.getBigDecimal("quantity"));
    		                	 try 
    		                	 {
    							     _delegator.store(orderItemShipGroupAssoc);
    							 } 
    		                	 catch (GenericEntityException e) 
    		                	 {
    							     e.printStackTrace();
    							 }
    		                 }
    		                 
    		                 orderItemSeqIdList.add(orderItemShipGroupAssoc.getString("orderItemSeqId"));
    		                 shipGroupOrderItemStatusMap.put(orderItemShipGroup.getString("shipGroupSeqId"), (String)mRow.get("orderStatus"));
                		 }
                	 }
                 }
                 
                 //Create Shipment for Order Items.
                 if(UtilValidate.isNotEmpty(shipGroupOrderItemSeqIdMap))
                 {
                	 for (Map.Entry<String, Set> entry : shipGroupOrderItemSeqIdMap.entrySet())
                     {
                		 List orderItemSeqIdListTemp = FastList.newInstance();
                		 orderItemSeqIdListTemp.addAll(entry.getValue());
                    	 Map quickShipOrderItemsParams = UtilMisc.toMap("orderId", (String)mRow.get("orderId"),
                                 "shipmentShipGroupSeqId",entry.getKey(), "orderItemSeqIdList", orderItemSeqIdListTemp,
                                 "userLogin", userLogin);
                    	 try 
                         {
                             Map result = dispatcher.runSync("quickShipOrderItems", quickShipOrderItemsParams);
                         } 
                         catch(GenericServiceException e)
                         {
                             Debug.logError(e, module);
                         }
                     }
                 }
                 
                 Map changeShipGroupOrderItemStatusCtx = UtilMisc.toMap("orderId", (String)mRow.get("orderId"),
                         "shipGroupOrderItemStatusMap",shipGroupOrderItemStatusMap,
                         "userLogin", userLogin);
            	 try 
                 {
                     Map result = dispatcher.runSync("changeShipGroupOrderItemStatus", changeShipGroupOrderItemStatusCtx);
                 } 
                 catch(GenericServiceException e)
                 {
                     Debug.logError(e, module);
                 }
                 
                 //Create Order Note
                 String orderNote = (String)mRow.get("orderNote");
                 if(UtilValidate.isNotEmpty(orderNote))
                 {
                	 Map createOrderNoteMap = UtilMisc.toMap("orderId", (String)mRow.get("orderId"),
                             "note",orderNote,"internalNote","Y", "userLogin", userLogin);
                	 try 
                     {
                         Map result = dispatcher.runSync("createOrderNote", createOrderNoteMap);
                     } 
                     catch(GenericServiceException e)
                     {
                         Debug.logError(e, module);
                     }
                 }
        	}
        }
     }

    private static Boolean displayCountryFieldAsLong(String productStoreId)
	{
    	return displayAddressFieldAsLong(productStoreId, "COUNTRY");
	}

    private static Boolean displayStateFieldAsLong(String productStoreId)
	{
    	return displayAddressFieldAsLong(productStoreId, "STATE");
	}

    private static Boolean displayZipFieldAsLong(String productStoreId)
	{
    	return displayAddressFieldAsLong(productStoreId, "ZIP");
	}

    /**
     * read FORMAT_ADDRESS system parameter and format the field.
     * @param productStoreId String product store id
     * @param fieldType String address field type ex: COUNTRY, STATE, ZIP
     * @return a boolean field
     */
    private static Boolean displayAddressFieldAsLong(String productStoreId, String fieldType)
	{
		Boolean isAddressFieldLong = Boolean.FALSE;
        if (UtilValidate.isEmpty(productStoreId) || UtilValidate.isEmpty(fieldType)) 
        {
            return isAddressFieldLong;
        }
        String addressFormat = OsafeAdminUtil.getProductStoreParm(_delegator, productStoreId, "FORMAT_ADDRESS");
        if(UtilValidate.isNotEmpty(addressFormat))
        {
            for(String column : StringUtil.split(StringUtil.removeSpaces(addressFormat), ","))
            {
                if (column.indexOf("_") > 0)
                {
                    List<String> nameValueList = StringUtil.split(column, "_");
                    {
                    	if (fieldType.equalsIgnoreCase(nameValueList.get(0)) && "LONG".equalsIgnoreCase(nameValueList.get(1)))
                    	{
                    		isAddressFieldLong = Boolean.TRUE;
                    	}
                    }
                }
            }
        }
        return isAddressFieldLong;
	}

    private static String getGeoName(String geoId)
    {
    	if (UtilValidate.isEmpty(geoId))
    	{
    		return null;
    	}
    	String geoName = geoId;
    	try
    	{
    		GenericValue geo = _delegator.findByPrimaryKey("Geo", UtilMisc.toMap("geoId", geoId));
    		if(UtilValidate.isNotEmpty(geo))
    	    {
    			geoName = geo.getString("geoName");
    	    }
    	}
    	catch (Exception e)
    	{
			e.printStackTrace();
	    }
		return geoName;
    }
    
    public static Map<String, Object> changeShipGroupOrderItemStatus(DispatchContext ctx, Map<String, ?> context) 
    {
    	LocalDispatcher dispatcher = ctx.getDispatcher();
        _delegator = ctx.getDelegator();
         List<String> messages = FastList.newInstance();

	    String orderId = (String)context.get("orderId");
	    Map<String, String> shipGroupOrderItemStatusMap = (Map)context.get("shipGroupOrderItemStatusMap");
	    Boolean autoLoad = (Boolean) context.get("autoLoad");
	    GenericValue userLogin = (GenericValue) context.get("userLogin");
	    
	    GenericValue orderHeader = null;
		try 
		{
			orderHeader = _delegator.findByPrimaryKey("OrderHeader", UtilMisc.toMap("orderId", orderId));
		} 
		catch (GenericEntityException e1) 
		{
			e1.printStackTrace();
		}
	    OrderReadHelper orderReadHelper = new OrderReadHelper(orderHeader);
	    //Change Order Item Status.
        if(UtilValidate.isNotEmpty(shipGroupOrderItemStatusMap))
        {
       	    List processedOrderItemSeqIds = FastList.newInstance();
       	    for (Map.Entry<String, String> entry : shipGroupOrderItemStatusMap.entrySet())
            {
       		 String shipGroupSeqId = (String)entry.getKey();
       		 List<GenericValue> orderItemShipGroupAssocs = FastList.newInstance();
       		 try 
       		 {
					orderItemShipGroupAssocs = _delegator.findByAnd("OrderItemShipGroupAssoc", UtilMisc.toMap("orderId", orderId, "shipGroupSeqId", shipGroupSeqId));
				 } 
       		 catch (GenericEntityException e) 
       		 {
					e.printStackTrace();
				 }
       		 if(UtilValidate.isNotEmpty(orderItemShipGroupAssocs))
       		 {
       			 for(GenericValue orderItemShipGroupAssoc : orderItemShipGroupAssocs)
       			 {
       				 try 
       				 {
							GenericValue orderItem = orderItemShipGroupAssoc.getRelatedOne("OrderItem");
							if(!processedOrderItemSeqIds.contains(orderItem.getString("orderItemSeqId")))
							{
								BigDecimal itemShippedQty  = BigDecimal.ZERO;
								
								List<GenericValue> orderItemShipments = _delegator.findByAnd("OrderShipment", UtilMisc.toMap("orderId",orderItem.getString("orderId"), "orderItemSeqId", orderItem.getString("orderItemSeqId")));
								if(UtilValidate.isNotEmpty(orderItemShipments))
								{
									for(GenericValue orderItemShipment : orderItemShipments)
									{
										itemShippedQty = itemShippedQty.add(orderItemShipment.getBigDecimal("quantity"));
										
									}
								}
								BigDecimal orderedQty = orderItem.getBigDecimal("quantity");
								BigDecimal shipGroupCancelQty = BigDecimal.ZERO;
								List<GenericValue> itemShipGroupAssocs = orderReadHelper.getOrderItemShipGroupAssocs(orderItem);
								for(GenericValue itemShipGroupAssoc: itemShipGroupAssocs)
								{
									if(UtilValidate.isNotEmpty(itemShipGroupAssoc.getBigDecimal("cancelQuantity")))
									{
										shipGroupCancelQty = shipGroupCancelQty.add(itemShipGroupAssoc.getBigDecimal("cancelQuantity"));
									}
								}
								
								if(itemShippedQty.add(shipGroupCancelQty).compareTo(orderedQty) < 0)
								{
									//ITEM APPROVED -- DO NOTHING
								}
								else
								{
									if(shipGroupCancelQty.compareTo(orderedQty) == 0)
									{
										// ITEM CANCELLED
					                     Map changeOrderItemStatusMap = UtilMisc.toMap("orderId", orderId,
					                             "orderItemSeqId",orderItem.getString("orderItemSeqId"), "statusId", "ITEM_CANCELLED",
					                             "userLogin", userLogin);
					                	 try 
					                     {
					                         Map result = dispatcher.runSync("changeOrderItemStatus", changeOrderItemStatusMap);
					                     } 
					                     catch(GenericServiceException e)
					                     {
					                         Debug.logError(e, module);
					                     }
									}
									else
									{
										//ITEM COMPLETED
										Map changeOrderItemStatusMap = UtilMisc.toMap("orderId", orderId,
					                             "orderItemSeqId",orderItem.getString("orderItemSeqId"), "statusId", "ITEM_COMPLETED",
					                             "userLogin", userLogin);
					                	 try 
					                     {
					                         Map result = dispatcher.runSync("changeOrderItemStatus", changeOrderItemStatusMap);
					                     } 
					                     catch(GenericServiceException e)
					                     {
					                         Debug.logError(e, module);
					                     }
										
									}
								}
								processedOrderItemSeqIds.add(orderItem.getString("orderItemSeqId"));
							}
						 } 
       				 catch (GenericEntityException e) 
						 {
							e.printStackTrace();
						 }
       			 }
       		 }
            }
        }
        
        return ServiceUtil.returnSuccess();
    }

    /**
     * process the object and rtuen the string.
     * @param tsObj object
     * @return a String.
     */
    public static String getString(Object tsObj) 
    {
        if (UtilValidate.isNotEmpty(tsObj))
        {
            return tsObj.toString();
        }
        else
        {
            return "";
        }
    }

    /**
     * process the object and return the formatted time.
     * @param tsObj object
     * @return a formatted time String.
     */
    public static String formatDate(Object tsObj) 
    {
        if (UtilValidate.isNotEmpty(tsObj))
        {
            return _sdf.format(new Date(((Timestamp)tsObj).getTime()));
        }
        else
        {
            return "";
        }
    }

    /**
     * process the object and return the formatted number.
     * @param tsObj object
     * @return a formatted number String.
     */
    public static String formatBigDecimal(Object bdObj) 
    {
        if (UtilValidate.isNotEmpty(bdObj))
        {
            return _df.format((BigDecimal) bdObj);
        }
        else
        {
            return "";
        }
    }

    public static Timestamp getProductContentThruDateTs(String productId ,String productContentTypeId,List lproductContent)
    {
        Timestamp returnTs=null;
        try
        {
            
            List<GenericValue> lContent = EntityUtil.filterByCondition(lproductContent, EntityCondition.makeCondition("productContentTypeId", EntityOperator.EQUALS, productContentTypeId));
            if (UtilValidate.isNotEmpty(lContent))
            {
                GenericValue contentContent = EntityUtil.getFirst(lContent);
                returnTs = contentContent.getTimestamp("thruDate");
            }
        }
        catch (Exception e)
        {
             Debug.logError(e, module);
        }
        return returnTs;
    }

    public static Timestamp getPartyContentThruDateTs(String partyId ,String partyContentTypeId,List lpartyContent)
    {
        Timestamp returnTs=null;
        try
        {
            
            List<GenericValue> lContent = EntityUtil.filterByCondition(lpartyContent, EntityCondition.makeCondition("partyContentTypeId", EntityOperator.EQUALS, partyContentTypeId));
            if (UtilValidate.isNotEmpty(lContent))
            {
                GenericValue contentContent = EntityUtil.getFirst(lContent);
                returnTs = contentContent.getTimestamp("thruDate");
            }
        }
        catch (Exception e)
        {
             Debug.logError(e, module);
        }
        return returnTs;
    }

    /**
     * process the XLS sheet and build the category data rows
     * @param s XLS sheet object
     * @return a List of Map.
     */
    public static List<Map<String, Object>> buildProductCategoryDataRows(Sheet s) 
    {
        List<Map<String, Object>> dataRows = FastList.newInstance();

        try
        {
            List xlsDataRows = buildDataRows(buildCategoryHeader(), s);
    		for (int i=0 ; i < xlsDataRows.size() ; i++) 
            {
                Map<String, Object> dataRow = FastMap.newInstance();
                Map mRow = (Map)xlsDataRows.get(i);
                dataRow.put(Constants.CATEGORY_ID_DATA_KEY, mRow.get("productCategoryId"));
                dataRow.put(Constants.CATEGORY_PARENT_DATA_KEY, mRow.get("parentCategoryId"));
                dataRow.put(Constants.CATEGORY_NAME_DATA_KEY, mRow.get("categoryName"));
                dataRow.put(Constants.CATEGORY_DESC_DATA_KEY, mRow.get("description"));
                dataRow.put(Constants.CATEGORY_LONG_DESC_DATA_KEY, mRow.get("longDescription"));
                dataRow.put(Constants.CATEGORY_PLP_TEXT_DATA_KEY, mRow.get("plpText"));
                dataRow.put(Constants.CATEGORY_PDP_TEXT_DATA_KEY, mRow.get("pdpText"));
                dataRow.put(Constants.CATEGORY_PLP_IMG_NAME_DATA_KEY, mRow.get("plpImageName"));
                if (UtilValidate.isNotEmpty(mRow.get("fromDate")))
                {
                    dataRow.put(Constants.CATEGORY_FROM_DATE_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(mRow.get("fromDate").toString())));
                }
                if (UtilValidate.isNotEmpty(mRow.get("thruDate")))
                {
                    dataRow.put(Constants.CATEGORY_THRU_DATE_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(mRow.get("thruDate").toString())));
                }
                if (UtilValidate.isNotEmpty(dataRow))
                {
                    dataRows.add(dataRow);
                }
            }
        }
        catch (Exception e) 
        {
            Debug.logError(e, module);
        }
        return dataRows;
    }

    /**
     * process the XML and build the category data rows
     * @param productCategories JAXB CategoryType object list 
     * @return a List of Map.
     */
    public static List<Map<String, Object>> buildProductCategoryDataRows(List<CategoryType> productCategories) 
    {
        List<Map<String, Object>> dataRows = FastList.newInstance();

        try
        {
            for (int rowCount = 0 ; rowCount < productCategories.size() ; rowCount++)
            {
                CategoryType productCategory = (CategoryType) productCategories.get(rowCount);

                Map<String, Object> dataRow = FastMap.newInstance();
                dataRow.put(Constants.CATEGORY_ID_DATA_KEY, productCategory.getCategoryId());
                dataRow.put(Constants.CATEGORY_PARENT_DATA_KEY, productCategory.getParentCategoryId());
                dataRow.put(Constants.CATEGORY_NAME_DATA_KEY, productCategory.getCategoryName());
                dataRow.put(Constants.CATEGORY_DESC_DATA_KEY, productCategory.getDescription());
                dataRow.put(Constants.CATEGORY_LONG_DESC_DATA_KEY, productCategory.getLongDescription());
                dataRow.put(Constants.CATEGORY_PLP_TEXT_DATA_KEY, productCategory.getAdditionalPlpText());
                dataRow.put(Constants.CATEGORY_PDP_TEXT_DATA_KEY, productCategory.getAdditionalPdpText());
                dataRow.put(Constants.CATEGORY_PLP_IMG_NAME_DATA_KEY, productCategory.getPlpImage());
                if (UtilValidate.isNotEmpty(productCategory.getFromDate()))
                {
                    dataRow.put(Constants.CATEGORY_FROM_DATE_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(productCategory.getFromDate().toString())));
                }
                if (UtilValidate.isNotEmpty(productCategory.getThruDate()))
                {
                    dataRow.put(Constants.CATEGORY_THRU_DATE_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(productCategory.getThruDate().toString())));
                }
                if (UtilValidate.isNotEmpty(dataRow))
                {
                    dataRows.add(dataRow);
                }
             }
        }
        catch (Exception e) 
        {
            Debug.logError(e, module);
        }
        return dataRows;
    }

    /**
     * process the DB and build the category data rows
     * @param context service context 
     * @return a List of Map.
     */
    public static List<Map<String, Object>> buildProductCategoryDataRows(Map<String, ?> context) 
    {
        String productStoreId = (String) context.get("productStoreId");
        String browseRootProductCategoryId = (String) context.get("browseRootProductCategoryId");
        List<Map<String, Object>> dataRows = FastList.newInstance();
        List<String> lTopLevelCategories = FastList.newInstance();

        try
        {
            List<GenericValue> topLevelCategoryList =  _delegator.findByAnd("ProductCategoryRollupAndChild", UtilMisc.toMap("parentProductCategoryId",browseRootProductCategoryId),UtilMisc.toList("sequenceNum"));
            for (GenericValue topLevelCategory : topLevelCategoryList) 
            {
                Map<String, Object> dataRow = buildProductCategoryDataRow(topLevelCategory);
                if (UtilValidate.isNotEmpty(dataRow))
                {
                    dataRows.add(dataRow);
                }
                if (!lTopLevelCategories.contains(topLevelCategory.getString("productCategoryId")))
                {
                	lTopLevelCategories.add(topLevelCategory.getString("productCategoryId"));
                	
                    List<GenericValue> subLavelCategoryList =  _delegator.findByAnd("ProductCategoryRollupAndChild", UtilMisc.toMap("parentProductCategoryId", topLevelCategory.getString("productCategoryId")),UtilMisc.toList("sequenceNum"));
                    for (GenericValue subLavelCategory : subLavelCategoryList) 
                    {
                        dataRow = buildProductCategoryDataRow(subLavelCategory);
                        if (UtilValidate.isNotEmpty(dataRow))
                        {
                            dataRows.add(dataRow);
                        }
                    }
                	
                }
            }
        }
        catch (Exception e) 
        {
            Debug.logError(e, module);
        }
        return dataRows;
    }

    public static Map<String, Object> buildProductCategoryDataRow(GenericValue productCategoryGV) 
    {
        Map<String, Object> dataRow = FastMap.newInstance();
        if ("CATALOG_CATEGORY".equals(productCategoryGV.getString("productCategoryTypeId"))) 
        {
            try
            {
                dataRow.put(Constants.CATEGORY_ID_DATA_KEY, productCategoryGV.getString("productCategoryId"));
                dataRow.put(Constants.CATEGORY_PARENT_DATA_KEY, productCategoryGV.getString("parentProductCategoryId"));
                dataRow.put(Constants.CATEGORY_NAME_DATA_KEY, productCategoryGV.getString("categoryName"));
                dataRow.put(Constants.CATEGORY_DESC_DATA_KEY, productCategoryGV.getString("description"));
                dataRow.put(Constants.CATEGORY_LONG_DESC_DATA_KEY, productCategoryGV.getString("longDescription"));
                dataRow.put(Constants.CATEGORY_FROM_DATE_DATA_KEY, productCategoryGV.getTimestamp("fromDate"));
                dataRow.put(Constants.CATEGORY_THRU_DATE_DATA_KEY, productCategoryGV.getTimestamp("thruDate"));

                String categoryImageURL = productCategoryGV.getString("categoryImageUrl");
                if (UtilValidate.isNotEmpty(categoryImageURL))
                {
                    if (!UtilValidate.isUrl(categoryImageURL))
                    {
                        String categoryImagePath = getOsafeImagePath("CATEGORY_IMAGE_URL");
                        List<String> pathElements = StringUtil.split(categoryImageURL, "/");
                        categoryImageURL = categoryImagePath + pathElements.get(pathElements.size() - 1);
                    }
                }
                dataRow.put(Constants.CATEGORY_PLP_IMG_NAME_DATA_KEY, categoryImageURL);

                List<GenericValue> categoryContent = _delegator.findByAnd("ProductCategoryContent", UtilMisc.toMap("productCategoryId", productCategoryGV.getString("productCategoryId")),UtilMisc.toList("-fromDate"));
                categoryContent = EntityUtil.filterByDate(categoryContent, UtilDateTime.nowTimestamp());
                dataRow.put(Constants.CATEGORY_PLP_TEXT_DATA_KEY, getProductCategoryContent(productCategoryGV.getString("productCategoryId"), "PLP_ESPOT_CONTENT", categoryContent));
                dataRow.put(Constants.CATEGORY_PDP_TEXT_DATA_KEY, getProductCategoryContent(productCategoryGV.getString("productCategoryId"), "PDP_ADDITIONAL", categoryContent));
            }
            catch (Exception e) 
            {
                Debug.logError(e, module);
            }
        }
        return dataRow;
    }

    /**
     * process the XML and build the product data rows
     * @param products JAXB ProductType object list 
     * @return a List of Map.
     */
    public static List<Map<String, Object>>  buildProductDataRows(List<ProductType> products) 
    {
        List<Map<String, Object>> dataRows = FastList.newInstance();
		try 
		{

			for (int rowCount = 0 ; rowCount < products.size() ; rowCount++) 
			{
            	ProductType product = (ProductType) products.get(rowCount);
                Map<String, Object> dataRow = FastMap.newInstance();
                dataRow.put(Constants.PRODUCT_MASTER_ID_DATA_KEY, product.getMasterProductId());
                dataRow.put(Constants.PRODUCT_ID_DATA_KEY, product.getProductId());
                dataRow.put(Constants.PRODUCT_INTERNAL_NAME_DATA_KEY, product.getInternalName());
                if (UtilValidate.isNotEmpty(product.getProductWidth()))
                {
                	dataRow.put(Constants.PRODUCT_WIDTH_DATA_KEY, BigDecimal.valueOf(UtilMisc.toDouble(product.getProductWidth())));
                }
                if (UtilValidate.isNotEmpty(product.getProductHeight()))
                {
                	dataRow.put(Constants.PRODUCT_HEIGHT_DATA_KEY, BigDecimal.valueOf(UtilMisc.toDouble(product.getProductHeight())));
                }
                if (UtilValidate.isNotEmpty(product.getProductDepth()))
                {
                	dataRow.put(Constants.PRODUCT_DEPTH_DATA_KEY, BigDecimal.valueOf(UtilMisc.toDouble(product.getProductDepth())));
                }
                if (UtilValidate.isNotEmpty(product.getProductWeight()))
                {
                	dataRow.put(Constants.PRODUCT_WEIGHT_DATA_KEY, BigDecimal.valueOf(UtilMisc.toDouble(product.getProductWeight())));
                }
                dataRow.put(Constants.PRODUCT_RETURN_ABLE_DATA_KEY, product.getReturnable());
                dataRow.put(Constants.PRODUCT_TAX_ABLE_DATA_KEY, product.getTaxable());
                dataRow.put(Constants.PRODUCT_CHARGE_SHIP_DATA_KEY, product.getChargeShipping());
                if (UtilValidate.isNotEmpty(product.getIntroDate()))
                {
                	dataRow.put(Constants.PRODUCT_INTRO_DATE_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(product.getIntroDate().toString())));
                }
                if (UtilValidate.isNotEmpty(product.getDiscoDate()))
                {
                	dataRow.put(Constants.PRODUCT_DISCO_DATE_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(product.getDiscoDate().toString())));
                }
                dataRow.put(Constants.PRODUCT_MANUFACT_PARTY_ID_DATA_KEY, product.getManufacturerId());
                dataRow.put(Constants.PRODUCT_NAME_DATA_KEY, product.getProductName());
                dataRow.put(Constants.PRODUCT_SALES_PITCH_DATA_KEY, product.getSalesPitch());
                dataRow.put(Constants.PRODUCT_LONG_DESC_DATA_KEY, product.getLongDescription());
                dataRow.put(Constants.PRODUCT_SPCL_INS_DATA_KEY, product.getSpecialInstructions());
                dataRow.put(Constants.PRODUCT_DELIVERY_INFO_DATA_KEY, product.getDeliveryInfo());
                dataRow.put(Constants.PRODUCT_DIRECTIONS_DATA_KEY, product.getDirections());
                dataRow.put(Constants.PRODUCT_TERMS_COND_DATA_KEY, product.getTermsAndConds());
                dataRow.put(Constants.PRODUCT_INGREDIENTS_DATA_KEY, product.getIngredients());
                dataRow.put(Constants.PRODUCT_WARNING_DATA_KEY, product.getWarnings());
                dataRow.put(Constants.PRODUCT_PLP_LABEL_DATA_KEY, product.getPlpLabel());
                dataRow.put(Constants.PRODUCT_PDP_LABEL_DATA_KEY, product.getPdpLabel());
                
                ProductPriceType productPrice = product.getProductPrice();
                if(UtilValidate.isNotEmpty(productPrice)) 
                {
                	ListPriceType listPrice = productPrice.getListPrice();
                	if(UtilValidate.isNotEmpty(listPrice)) 
                	{
                        if (UtilValidate.isNotEmpty(listPrice.getPrice()))
                        {
                        	dataRow.put(Constants.PRODUCT_LIST_PRICE_DATA_KEY, BigDecimal.valueOf(UtilMisc.toDouble(listPrice.getPrice())));
                        }
                        dataRow.put(Constants.PRODUCT_LIST_PRICE_CUR_DATA_KEY, listPrice.getCurrency());
                        if (UtilValidate.isNotEmpty(listPrice.getFromDate()))
                        {
                        	dataRow.put(Constants.PRODUCT_LIST_PRICE_FROM_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(listPrice.getFromDate().toString())));
                        }
                        if (UtilValidate.isNotEmpty(listPrice.getThruDate()))
                        {
                        	dataRow.put(Constants.PRODUCT_LIST_PRICE_THRU_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(listPrice.getThruDate().toString())));
                        }
                	}
                    
                    SalesPriceType salesPrice = productPrice.getSalesPrice();
                    if(UtilValidate.isNotEmpty(salesPrice)) 
                    {
                        if (UtilValidate.isNotEmpty(salesPrice.getPrice()))
                        {
                        	dataRow.put(Constants.PRODUCT_DEFAULT_PRICE_DATA_KEY, BigDecimal.valueOf(UtilMisc.toDouble(salesPrice.getPrice())));
                        }
                        dataRow.put(Constants.PRODUCT_DEFAULT_PRICE_CUR_DATA_KEY, salesPrice.getCurrency());
                        if (UtilValidate.isNotEmpty(salesPrice.getFromDate()))
                        {
                        	dataRow.put(Constants.PRODUCT_DEFAULT_PRICE_FROM_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(salesPrice.getFromDate().toString())));
                        }
                        if (UtilValidate.isNotEmpty(salesPrice.getThruDate()))
                        {
                        	dataRow.put(Constants.PRODUCT_DEFAULT_PRICE_THRU_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(salesPrice.getThruDate().toString())));
                        }
                    }
                }
                
                int cnt = 1;
                ProductCategoryMemberType productCategory = product.getProductCategoryMember();
                if(UtilValidate.isNotEmpty(productCategory)) 
                {
                	List<CategoryMemberType> categoryList = productCategory.getCategory();
                    
                    if(UtilValidate.isNotEmpty(categoryList)) 
                    {
                    	
                    	for(int i = 0; i < categoryList.size(); i++) 
                    	{
                    		CategoryMemberType category = (CategoryMemberType)categoryList.get(i);
                            dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_ID_DATA_KEY, UtilMisc.toMap("count", cnt)), category.getCategoryId());
                            dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_SEQ_NUM_DATA_KEY, UtilMisc.toMap("count", cnt)), category.getSequenceNum());
                            if (UtilValidate.isNotEmpty(category.getFromDate()))
                            {
                            	dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_FROM_DATE_DATA_KEY, UtilMisc.toMap("count", cnt)), UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(category.getFromDate().toString())));
                            }
                            if (UtilValidate.isNotEmpty(category.getThruDate()))
                            {
                            	dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_THRU_DATE_DATA_KEY, UtilMisc.toMap("count", cnt)), UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(category.getThruDate().toString())));
                            }
                            cnt++;
                    	}
                    }
                    dataRow.put(Constants.PRODUCT_CAT_COUNT_DATA_KEY, cnt-1);
                }

                cnt = 1;
                ProductSelectableFeatureType selectableFeature = product.getProductSelectableFeature();
                if(UtilValidate.isNotEmpty(selectableFeature)) 
                {
                	List<FeatureType> selectableFeatureList = selectableFeature.getFeature();
                    if(UtilValidate.isNotEmpty(selectableFeatureList)) 
                    {
                    	for(int i = 0; i < selectableFeatureList.size(); i++) 
                    	{
                    		String featureId = new String("");
                    		FeatureType feature = (FeatureType)selectableFeatureList.get(i);
                    		if(UtilValidate.isNotEmpty(feature.getFeatureId())) 
                    		{
                    		    StringBuffer featureValue = new StringBuffer("");
                    		    List featureValues = feature.getValue();
                    		    if(UtilValidate.isNotEmpty(featureValues)) 
                    		    {
                            	
                            	    for(int value = 0; value < featureValues.size(); value++) 
                            	    {
                            		    if(!featureValues.get(value).equals("")) 
                            		    {
                            		        featureValue.append(featureValues.get(value) + ",");
                            		    }
                            	    }
                            	    if(featureValue.length() > 1) 
                            	    {
                            	        featureValue.setLength(featureValue.length()-1);
                            	    }
                                }
                    		    if(featureValue.length() > 0) 
                    		    {
                                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_TYPE_ID_DATA_KEY, UtilMisc.toMap("count", cnt)), feature.getFeatureId());
                                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_TYPE_DESC_DATA_KEY, UtilMisc.toMap("count", cnt)), feature.getDescription());
                                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_DESC_DATA_KEY, UtilMisc.toMap("count", cnt)), featureValue.toString());
                                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_SEQ_NUM_DATA_KEY, UtilMisc.toMap("count", cnt)), feature.getSequenceNum());
                                    if (UtilValidate.isNotEmpty(feature.getFromDate()))
                                    {
                                    	dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_FROM_DATA_KEY, UtilMisc.toMap("count", cnt)), UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(feature.getFromDate().toString())));
                                    }
                                    if (UtilValidate.isNotEmpty(feature.getThruDate()))
                                    {
                                    	dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_THRU_DATA_KEY, UtilMisc.toMap("count", cnt)), UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(feature.getThruDate().toString())));
                                    }
                                    cnt++;
                    		    }
                    		}
                    	}
                    }
                }

                cnt = 1;
                ProductDescriptiveFeatureType descriptiveFeature = product.getProductDescriptiveFeature();
                if(UtilValidate.isNotEmpty(descriptiveFeature)) 
                {
                	List<FeatureType> descriptiveFeatureList = descriptiveFeature.getFeature();
                    if(UtilValidate.isNotEmpty(descriptiveFeatureList)) 
                    {
                    	for(int i = 0; i < descriptiveFeatureList.size(); i++) 
                    	{
                    		String featureId = new String("");
                    		FeatureType feature = (FeatureType)descriptiveFeatureList.get(i);
                    		if(UtilValidate.isNotEmpty(feature.getFeatureId())) 
                    		{
                    		    StringBuffer featureValue = new StringBuffer("");
                    		    List featureValues = feature.getValue();
                    		    if(UtilValidate.isNotEmpty(featureValues)) 
                    		    {
                            	
                            	    for(int value = 0; value < featureValues.size(); value++) 
                            	    {
                            		    if(!featureValues.get(value).equals("")) 
                            		    {
                            		        featureValue.append(featureValues.get(value) + ",");
                            		    }
                            	    }
                            	    if(featureValue.length() > 1) 
                            	    {
                            	        featureValue.setLength(featureValue.length()-1);
                            	    }
                                }
                    		    if(featureValue.length() > 0) 
                    		    {
                                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_TYPE_ID_DATA_KEY, UtilMisc.toMap("count", cnt)), feature.getFeatureId());
                                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_TYPE_DESC_DATA_KEY, UtilMisc.toMap("count", cnt)), feature.getDescription());
                                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_DESC_DATA_KEY, UtilMisc.toMap("count", cnt)), featureValue.toString());
                                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_SEQ_NUM_DATA_KEY, UtilMisc.toMap("count", cnt)), feature.getSequenceNum());
                                    if (UtilValidate.isNotEmpty(feature.getFromDate()))
                                    {
                                    	dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_FROM_DATA_KEY, UtilMisc.toMap("count", cnt)), UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(feature.getFromDate().toString())));
                                    }
                                    if (UtilValidate.isNotEmpty(feature.getThruDate()))
                                    {
                                    	dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_THRU_DATA_KEY, UtilMisc.toMap("count", cnt)), UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(feature.getThruDate().toString())));
                                    }
                                    cnt++;
                    		    }
                    		}
                    	}
                    }
                }

                ProductImageType productImage = product.getProductImage();
                if(UtilValidate.isNotEmpty(productImage)) 
                {
                	PlpSwatchType plpSwatch = productImage.getPlpSwatch();
                	if(UtilValidate.isNotEmpty(plpSwatch)) 
                	{
                        dataRow.put(Constants.PRODUCT_PLP_SWATCH_IMG_DATA_KEY, plpSwatch.getUrl());
                        if (UtilValidate.isNotEmpty(plpSwatch.getThruDate()))
                        {
                        	dataRow.put(Constants.PRODUCT_PLP_SWATCH_IMG_THRU_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(plpSwatch.getThruDate().toString())));
                        }
                	}
                    
                    PdpSwatchType pdpSwatch = productImage.getPdpSwatch();
                    if(UtilValidate.isNotEmpty(pdpSwatch)) 
                    {
                        dataRow.put(Constants.PRODUCT_PDP_SWATCH_IMG_DATA_KEY, pdpSwatch.getUrl());
                        if (UtilValidate.isNotEmpty(pdpSwatch.getThruDate()))
                        {
                        	dataRow.put(Constants.PRODUCT_PDP_SWATCH_IMG_THRU_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(pdpSwatch.getThruDate().toString())));
                        }
                    }
                    
                    PlpSmallImageType plpSmallImage = productImage.getPlpSmallImage();
                    if(UtilValidate.isNotEmpty(plpSmallImage)) 
                    {
                        dataRow.put(Constants.PRODUCT_SMALL_IMG_DATA_KEY, plpSmallImage.getUrl());
                        if (UtilValidate.isNotEmpty(plpSmallImage.getThruDate()))
                        {
                        	dataRow.put(Constants.PRODUCT_SMALL_IMG_THRU_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(plpSmallImage.getThruDate().toString())));
                        }
                    }
                    
                    PlpSmallAltImageType plpSmallAltImage = productImage.getPlpSmallAltImage();
                    if(UtilValidate.isNotEmpty(plpSmallAltImage)) 
                    {
                        dataRow.put(Constants.PRODUCT_SMALL_IMG_ALT_DATA_KEY, plpSmallAltImage.getUrl());
                        if (UtilValidate.isNotEmpty(plpSmallAltImage.getThruDate()))
                        {
                        	dataRow.put(Constants.PRODUCT_SMALL_IMG_ALT_THRU_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(plpSmallAltImage.getThruDate().toString())));
                        }
                    }
                    
                    PdpThumbnailImageType pdpThumbnailImage = productImage.getPdpThumbnailImage();
                    if(UtilValidate.isNotEmpty(pdpThumbnailImage)) 
                    {
                        dataRow.put(Constants.PRODUCT_THUMB_IMG_DATA_KEY, pdpThumbnailImage.getUrl());
                        if (UtilValidate.isNotEmpty(pdpThumbnailImage.getThruDate()))
                        {
                        	dataRow.put(Constants.PRODUCT_THUMB_IMG_THRU_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(pdpThumbnailImage.getThruDate().toString())));
                        }
                    }
                    
                    PdpLargeImageType plpLargeImage = productImage.getPdpLargeImage();
                    if(UtilValidate.isNotEmpty(plpLargeImage)) 
                    {
                        dataRow.put(Constants.PRODUCT_LARGE_IMG_DATA_KEY, plpLargeImage.getUrl());
                        if (UtilValidate.isNotEmpty(plpLargeImage.getThruDate()))
                        {
                        	dataRow.put(Constants.PRODUCT_LARGE_IMG_THRU_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(plpLargeImage.getThruDate().toString())));
                        }
                    }
                    
                    PdpDetailImageType pdpDetailImage = productImage.getPdpDetailImage();
                    if(UtilValidate.isNotEmpty(pdpDetailImage)) 
                    {
                        dataRow.put(Constants.PRODUCT_DETAIL_IMG_DATA_KEY, pdpDetailImage.getUrl());
                        if (UtilValidate.isNotEmpty(pdpDetailImage.getThruDate()))
                        {
                        	dataRow.put(Constants.PRODUCT_DETAIL_IMG_THRU_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(pdpDetailImage.getThruDate().toString())));
                        }
                    }
                    
                    PdpVideoType pdpVideo = productImage.getPdpVideoImage();
                    if(UtilValidate.isNotEmpty(pdpVideo)) 
                    {
                        dataRow.put(Constants.PRODUCT_VIDEO_URL_DATA_KEY, pdpVideo.getUrl());
                        if (UtilValidate.isNotEmpty(pdpVideo.getThruDate()))
                        {
                        	dataRow.put(Constants.PRODUCT_VIDEO_URL_THRU_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(pdpVideo.getThruDate().toString())));
                        }
                    }
                    
                    PdpVideo360Type pdpVideo360 = productImage.getPdpVideo360Image();
                    if(UtilValidate.isNotEmpty(pdpVideo360)) 
                    {
                        dataRow.put(Constants.PRODUCT_VIDEO_360_URL_DATA_KEY, pdpVideo360.getUrl());
                        if (UtilValidate.isNotEmpty(pdpVideo360.getThruDate()))
                        {
                        	dataRow.put(Constants.PRODUCT_VIDEO_360_URL_THRU_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(pdpVideo360.getThruDate().toString())));
                        }
                    }
                    
                    PdpAlternateImageType pdpAlternateImage = productImage.getPdpAlternateImage();
                    if(UtilValidate.isNotEmpty(pdpAlternateImage)) 
                    {
                    	List pdpAdditionalImages = pdpAlternateImage.getPdpAdditionalImage();
                        if(UtilValidate.isNotEmpty(pdpAdditionalImages)) 
                        { 
                        	for(int i = 0; i < pdpAdditionalImages.size(); i++) 
                        	{
                        		PdpAdditionalImageType pdpAdditionalImage = (PdpAdditionalImageType) pdpAdditionalImages.get(i);
                        	    
                        		PdpAdditionalThumbImageType pdpAdditionalThumbImage = pdpAdditionalImage.getPdpAdditionalThumbImage();
                        		if(UtilValidate.isNotEmpty(pdpAdditionalThumbImage)) 
                        		{
                                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_ADDNL_IMG_DATA_KEY, UtilMisc.toMap("count", i)), pdpAdditionalThumbImage.getUrl());
                                    if (UtilValidate.isNotEmpty(pdpAdditionalThumbImage.getThruDate()))
                                    {
                                    	dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_ADDNL_IMG_THRU_DATA_KEY, UtilMisc.toMap("count", i)), UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(pdpAdditionalThumbImage.getThruDate().toString())));
                                    }
                        		}
                        	    
                        	    PdpAdditionalLargeImageType pdpAdditionalLargeImage = pdpAdditionalImage.getPdpAdditionalLargeImage();
                        	    if(UtilValidate.isNotEmpty(pdpAdditionalLargeImage)) 
                        	    {
                                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_LARGE_IMG_DATA_KEY, UtilMisc.toMap("count", i)), pdpAdditionalLargeImage.getUrl());
                                    if (UtilValidate.isNotEmpty(pdpAdditionalLargeImage.getThruDate()))
                                    {
                                    	dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_LARGE_IMG_THRU_DATA_KEY, UtilMisc.toMap("count", i)), UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(pdpAdditionalLargeImage.getThruDate().toString())));
                                    }
                        	    }
                        	    
                        	    PdpAdditionalDetailImageType pdpAdditionalDetailImage = pdpAdditionalImage.getPdpAdditionalDetailImage();
                        	    if(UtilValidate.isNotEmpty(pdpAdditionalDetailImage)) 
                        	    {
                                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_DETAIL_IMG_DATA_KEY, UtilMisc.toMap("count", i)), pdpAdditionalDetailImage.getUrl());
                                    if (UtilValidate.isNotEmpty(pdpAdditionalDetailImage.getThruDate()))
                                    {
                                    	dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_DETAIL_IMG_THRU_DATA_KEY, UtilMisc.toMap("count", i)), UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(pdpAdditionalDetailImage.getThruDate().toString())));
                                    }
                        	    }
                        	}
                        }
                    }
                    
                }

                GoodIdentificationType goodIdentification = product.getProductGoodIdentification();
                if(UtilValidate.isNotEmpty(goodIdentification)) 
                {
                    dataRow.put(Constants.PRODUCT_SKU_DATA_KEY, goodIdentification.getSku());
                    dataRow.put(Constants.PRODUCT_GOOGLE_ID_DATA_KEY, goodIdentification.getGoogleId());
                    dataRow.put(Constants.PRODUCT_ISBN_DATA_KEY, goodIdentification.getIsbn());
                    dataRow.put(Constants.PRODUCT_MANUFACTURER_ID_NO_DATA_KEY, goodIdentification.getManuId());
                }
                
                
                ProductInventoryType productInventory = product.getProductInventory();
                if(UtilValidate.isNotEmpty(productInventory)) 
                {
                    dataRow.put(Constants.PRODUCT_BF_INVENTORY_TOT_DATA_KEY, productInventory.getBigfishInventoryTotal());
                    dataRow.put(Constants.PRODUCT_BF_INVENTORY_WHS_DATA_KEY, productInventory.getBigfishInventoryWarehouse());
                }
                
                ProductAttributeType productAttribute = product.getProductAttribute();
                if(UtilValidate.isNotEmpty(productAttribute)) 
                {
                    dataRow.put(Constants.PRODUCT_MULTI_VARIANT_DATA_KEY, productAttribute.getPdpSelectMultiVariant());
                    dataRow.put(Constants.PRODUCT_GIFT_MESSAGE_DATA_KEY, productAttribute.getPdpCheckoutGiftMessage());
                    dataRow.put(Constants.PRODUCT_QTY_MIN_DATA_KEY, productAttribute.getPdpQtyMin());
                    dataRow.put(Constants.PRODUCT_QTY_MAX_DATA_KEY, productAttribute.getPdpQtyMax());
                    dataRow.put(Constants.PRODUCT_QTY_DEFAULT_DATA_KEY, productAttribute.getPdpQtyDefault());
                    dataRow.put(Constants.PRODUCT_IN_STORE_ONLY_DATA_KEY, productAttribute.getPdpInStoreOnly());
                }
                
                dataRows.add(dataRow);
             }
    	}
      	catch (Exception e) 
      	{
      		e.printStackTrace();
   	    }
      	return dataRows;
   }

    /**
     * process the XLS sheet and build the product data rows
     * @param s XLS sheet object 
     * @return a List of Map.
     */
    public static List<Map<String, Object>> buildProductDataRows(Sheet s) 
    {
        List<Map<String, Object>> dataRows = FastList.newInstance();

        try
        {
            List xlsDataRows = buildDataRows(buildProductHeader(), s);
    		for (int i=0 ; i < xlsDataRows.size() ; i++) 
            {
                Map mRow = (Map)xlsDataRows.get(i);
    			if(mRow.get("masterProductId").equals(mRow.get("productId")) || UtilValidate.isEmpty(mRow.get("productId")))
    			{
    				//CREATE VIRTUAL/FINISHED GOOD PRODUCT ROW
    				String productId = (String)mRow.get("productId");
                    Map<String, Object> dataRow = buildProductDataRow(mRow, productId, null);
                    if (UtilValidate.isNotEmpty(dataRow))
                    {
                        dataRows.add(dataRow);
                    }
    				
    			}
    			else
    			{
    				List<List> selectableFeatureList = FastList.newInstance();
        			int totSelectableFeatures = 5;
              	    for(int j = 1; j <= totSelectableFeatures; j++)
          	        {
              	        selectableFeatureList = createFeatureVariantProductId(selectableFeatureList , (String)mRow.get("selectabeFeature_"+j));
          	        }
              	    if(selectableFeatureList.size() == 1)
              	    {
              	        //CREATE ONE VARIANT PRODUCT ROW
              	    	String productId = (String)mRow.get("productId");
              	    	List<String> selectableFeature = (List)selectableFeatureList.get(0);
                        Map<String, Object> dataRow = buildProductDataRow(mRow, productId, selectableFeature);
                        if (UtilValidate.isNotEmpty(dataRow))
                        {
                            dataRows.add(dataRow);
                        }
              	    }
              	    else if(selectableFeatureList.size() > 1)
              	    {
                 	    //CREATE MULTIPLE VARIANT PRODUCT ROW
              	    	int variantProductIdNo = 1;
                  	    for(List selectableFeature: selectableFeatureList)
                  	    {
                  	    	String variantProductId = (String)mRow.get("productId");
                  	    	if(variantProductIdNo < 10)
                  	    	{
                  	    		variantProductId = variantProductId + "-0"+variantProductIdNo;
                  	    	}
                  	    	else
                  	    	{
                  	    		variantProductId = variantProductId + "-"+variantProductIdNo;
                  	    	}
                            Map<String, Object> dataRow = buildProductDataRow(mRow, variantProductId, selectableFeature);
                            if (UtilValidate.isNotEmpty(dataRow))
                            {
                                dataRows.add(dataRow);
                            }
                  	    	variantProductIdNo++;
                  	    }
              	    }
              	    
    			}
            }
        }
        catch (Exception e) 
        {
            Debug.logError(e, module);
        }
        return dataRows;
    }

    public static Map<String, Object> buildProductDataRow(Map mRow, String productId, List<String> finalSelectableFeature) 
    {
        Map<String, Object> dataRow = FastMap.newInstance();
        try
        {
        	String currencyUomId = UtilProperties.getPropertyValue("general.properties", "currency.uom.id.default", "USD");
            dataRow.put(Constants.PRODUCT_MASTER_ID_DATA_KEY, mRow.get("masterProductId"));
            dataRow.put(Constants.PRODUCT_ID_DATA_KEY, productId);

            int cnt = 1;
            String productCategory =  (String)mRow.get("productCategoryId");
            String sequenceNum = (String)mRow.get("sequenceNum");
            List<String> productCategoryIds = null;
            if(UtilValidate.isNotEmpty(productCategory))
            {
            	productCategoryIds = StringUtil.split(productCategory, ",");
            }
            if(UtilValidate.isNotEmpty(productCategoryIds))
            {
                StringBuffer catMembers =new StringBuffer();
                for (String productCategoryId : productCategoryIds) 
                {
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_ID_DATA_KEY, UtilMisc.toMap("count", cnt)), productCategoryId);
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_SEQ_NUM_DATA_KEY, UtilMisc.toMap("count", cnt)), sequenceNum);
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_FROM_DATE_DATA_KEY, UtilMisc.toMap("count", cnt)), "");
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_THRU_DATE_DATA_KEY, UtilMisc.toMap("count", cnt)), "");
                    cnt++;
                }
                dataRow.put(Constants.PRODUCT_CAT_COUNT_DATA_KEY, cnt-1);
            }
    		
            dataRow.put(Constants.PRODUCT_INTERNAL_NAME_DATA_KEY, mRow.get("internalName"));
            if (UtilValidate.isNotEmpty(mRow.get("productWidth")))
            {
            	dataRow.put(Constants.PRODUCT_WIDTH_DATA_KEY, BigDecimal.valueOf(UtilMisc.toDouble(mRow.get("productWidth"))));
            }
            if (UtilValidate.isNotEmpty(mRow.get("productHeight")))
            {
            	dataRow.put(Constants.PRODUCT_HEIGHT_DATA_KEY, BigDecimal.valueOf(UtilMisc.toDouble(mRow.get("productHeight"))));
            }
            if (UtilValidate.isNotEmpty(mRow.get("productDepth")))
            {
            	dataRow.put(Constants.PRODUCT_DEPTH_DATA_KEY, BigDecimal.valueOf(UtilMisc.toDouble(mRow.get("productDepth"))));
            }
            if (UtilValidate.isNotEmpty(mRow.get("weight")))
            {
            	dataRow.put(Constants.PRODUCT_WEIGHT_DATA_KEY, BigDecimal.valueOf(UtilMisc.toDouble(mRow.get("weight"))));
            }
            dataRow.put(Constants.PRODUCT_RETURN_ABLE_DATA_KEY, mRow.get("returnable"));
            dataRow.put(Constants.PRODUCT_TAX_ABLE_DATA_KEY, mRow.get("taxable"));
            dataRow.put(Constants.PRODUCT_CHARGE_SHIP_DATA_KEY, mRow.get("chargeShipping"));
            if (UtilValidate.isNotEmpty(mRow.get("introDate")))
            {
            	dataRow.put(Constants.PRODUCT_INTRO_DATE_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(mRow.get("introDate").toString())));
            }
            if (UtilValidate.isNotEmpty(mRow.get("discoDate")))
            {
            	dataRow.put(Constants.PRODUCT_DISCO_DATE_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(mRow.get("discoDate").toString())));
            }
            dataRow.put(Constants.PRODUCT_MANUFACT_PARTY_ID_DATA_KEY, mRow.get("manufacturerId"));

            dataRow.put(Constants.PRODUCT_NAME_DATA_KEY, mRow.get("productName"));
            dataRow.put(Constants.PRODUCT_SALES_PITCH_DATA_KEY, mRow.get("salesPitch"));
            dataRow.put(Constants.PRODUCT_LONG_DESC_DATA_KEY, mRow.get("longDescription"));
            dataRow.put(Constants.PRODUCT_SPCL_INS_DATA_KEY, mRow.get("specialInstructions"));
            dataRow.put(Constants.PRODUCT_DELIVERY_INFO_DATA_KEY, mRow.get("deliveryInfo"));
            dataRow.put(Constants.PRODUCT_DIRECTIONS_DATA_KEY, mRow.get("directions"));
            dataRow.put(Constants.PRODUCT_TERMS_COND_DATA_KEY, mRow.get("termsConditions"));
            dataRow.put(Constants.PRODUCT_INGREDIENTS_DATA_KEY, mRow.get("ingredients"));
            dataRow.put(Constants.PRODUCT_WARNING_DATA_KEY, mRow.get("warnings"));
            dataRow.put(Constants.PRODUCT_PLP_LABEL_DATA_KEY, mRow.get("plpLabel"));
            dataRow.put(Constants.PRODUCT_PDP_LABEL_DATA_KEY, mRow.get("pdpLabel"));

            if (UtilValidate.isNotEmpty(mRow.get("listPrice")))
            {
    	        dataRow.put(Constants.PRODUCT_LIST_PRICE_DATA_KEY, BigDecimal.valueOf(UtilMisc.toDouble(mRow.get("listPrice"))));
    	        dataRow.put(Constants.PRODUCT_LIST_PRICE_CUR_DATA_KEY, currencyUomId);
    	        dataRow.put(Constants.PRODUCT_LIST_PRICE_FROM_DATA_KEY, "");
    	        dataRow.put(Constants.PRODUCT_LIST_PRICE_THRU_DATA_KEY, "");
            }

            if (UtilValidate.isNotEmpty(mRow.get("defaultPrice")))
            {
                dataRow.put(Constants.PRODUCT_DEFAULT_PRICE_DATA_KEY, BigDecimal.valueOf(UtilMisc.toDouble(mRow.get("defaultPrice"))));
                dataRow.put(Constants.PRODUCT_DEFAULT_PRICE_CUR_DATA_KEY, currencyUomId);
                dataRow.put(Constants.PRODUCT_DEFAULT_PRICE_FROM_DATA_KEY, "");
                dataRow.put(Constants.PRODUCT_DEFAULT_PRICE_THRU_DATA_KEY, "");
            }

            if(UtilValidate.isNotEmpty(finalSelectableFeature)) 
            {
            	cnt = 1;
                for (String featureValue : finalSelectableFeature)
                {
                	featureValue = featureValue.trim();
                	String[] featureValueArr = featureValue.split("~");
                	if(featureValueArr.length > 0)
                	{
                        dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_TYPE_ID_DATA_KEY, UtilMisc.toMap("count", cnt)), featureValueArr[0].trim());
                	}
                	if(featureValueArr.length > 1)
                	{
                        dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_DESC_DATA_KEY, UtilMisc.toMap("count", cnt)), featureValueArr[1].trim());
                	}
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_TYPE_DESC_DATA_KEY, UtilMisc.toMap("count", cnt)), "");
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_SEQ_NUM_DATA_KEY, UtilMisc.toMap("count", cnt)), "");
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_FROM_DATA_KEY, UtilMisc.toMap("count", cnt)), "");
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_THRU_DATA_KEY, UtilMisc.toMap("count", cnt)), "");
                    cnt++;
                }
            }

        	cnt = 1;
            int totDescriptiveFeatures = 5;
      	    for(int j = 1; j <= totDescriptiveFeatures; j++)
    	    {
      	    	
      	    	String parseFeatureType = (String)mRow.get("descriptiveFeature_"+j);
      	    	if (UtilValidate.isNotEmpty(parseFeatureType))
      	    	{
      	        	int iFeatIdx = parseFeatureType.indexOf(':');
      	        	if (iFeatIdx > -1)
      	        	{
      	            	String featureType = parseFeatureType.substring(0,iFeatIdx).trim();
                        dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_TYPE_ID_DATA_KEY, UtilMisc.toMap("count", cnt)), featureType);
      	            	
      	            	String sFeatures = parseFeatureType.substring(iFeatIdx +1);
      	                String[] featureTokens = sFeatures.split(",");
      	            	Map mFeatureMap = FastMap.newInstance();
      	                for (int f=0;f < featureTokens.length;f++)
      	                {
                            dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_DESC_DATA_KEY, UtilMisc.toMap("count", cnt)), featureTokens[f].trim());
      	                }
      	                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_TYPE_DESC_DATA_KEY, UtilMisc.toMap("count", cnt)), "");
      	                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_SEQ_NUM_DATA_KEY, UtilMisc.toMap("count", cnt)), "");
      	                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_FROM_DATA_KEY, UtilMisc.toMap("count", cnt)), "");
      	                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_THRU_DATA_KEY, UtilMisc.toMap("count", cnt)), "");
                  	    cnt++;
      	        	}
      	    	}
    	    }

            dataRow.put(Constants.PRODUCT_SKU_DATA_KEY, mRow.get("goodIdentificationSkuId"));
            dataRow.put(Constants.PRODUCT_GOOGLE_ID_DATA_KEY, mRow.get("goodIdentificationGoogleId"));
            dataRow.put(Constants.PRODUCT_ISBN_DATA_KEY, mRow.get("goodIdentificationIsbnId"));
            dataRow.put(Constants.PRODUCT_MANUFACTURER_ID_NO_DATA_KEY, mRow.get("goodIdentificationManufacturerId"));
            dataRow.put(Constants.PRODUCT_BF_INVENTORY_TOT_DATA_KEY, mRow.get("bfInventoryTot"));
            dataRow.put(Constants.PRODUCT_BF_INVENTORY_WHS_DATA_KEY, mRow.get("bfInventoryWhs"));
            dataRow.put(Constants.PRODUCT_MULTI_VARIANT_DATA_KEY, mRow.get("multiVariant"));
            dataRow.put(Constants.PRODUCT_GIFT_MESSAGE_DATA_KEY, mRow.get("giftMessage"));
            dataRow.put(Constants.PRODUCT_QTY_MIN_DATA_KEY, mRow.get("pdpQtyMin"));
            dataRow.put(Constants.PRODUCT_QTY_MAX_DATA_KEY, mRow.get("pdpQtyMax"));
            dataRow.put(Constants.PRODUCT_QTY_DEFAULT_DATA_KEY, mRow.get("pdpQtyDefault"));
            dataRow.put(Constants.PRODUCT_IN_STORE_ONLY_DATA_KEY, mRow.get("pdpInStoreOnly"));

            if(UtilValidate.isNotEmpty(mRow.get("plpSwatchImage"))) 
            {
                dataRow.put(Constants.PRODUCT_PLP_SWATCH_IMG_DATA_KEY, mRow.get("plpSwatchImage"));
                dataRow.put(Constants.PRODUCT_PLP_SWATCH_IMG_THRU_DATA_KEY, "");
            }

            if(UtilValidate.isNotEmpty(mRow.get("pdpSwatchImage"))) 
            {
                dataRow.put(Constants.PRODUCT_PDP_SWATCH_IMG_DATA_KEY, mRow.get("pdpSwatchImage"));
                dataRow.put(Constants.PRODUCT_PDP_SWATCH_IMG_THRU_DATA_KEY, "");
            }

            if(UtilValidate.isNotEmpty(mRow.get("smallImage"))) 
            {
                dataRow.put(Constants.PRODUCT_SMALL_IMG_DATA_KEY, mRow.get("smallImage"));
                dataRow.put(Constants.PRODUCT_SMALL_IMG_THRU_DATA_KEY, "");
            }

            if(UtilValidate.isNotEmpty(mRow.get("smallImageAlt"))) 
            {
                dataRow.put(Constants.PRODUCT_SMALL_IMG_ALT_DATA_KEY, mRow.get("smallImageAlt"));
                dataRow.put(Constants.PRODUCT_SMALL_IMG_ALT_THRU_DATA_KEY, "");
            }

            if(UtilValidate.isNotEmpty(mRow.get("thumbImage"))) 
            {
                dataRow.put(Constants.PRODUCT_THUMB_IMG_DATA_KEY, mRow.get("thumbImage"));
                dataRow.put(Constants.PRODUCT_THUMB_IMG_THRU_DATA_KEY, "");
            }

            if(UtilValidate.isNotEmpty(mRow.get("largeImage"))) 
            {
                dataRow.put(Constants.PRODUCT_LARGE_IMG_DATA_KEY, mRow.get("largeImage"));
                dataRow.put(Constants.PRODUCT_LARGE_IMG_THRU_DATA_KEY, "");
            }

            if(UtilValidate.isNotEmpty(mRow.get("detailImage"))) 
            {
                dataRow.put(Constants.PRODUCT_DETAIL_IMG_DATA_KEY, mRow.get("detailImage"));
                dataRow.put(Constants.PRODUCT_DETAIL_IMG_THRU_DATA_KEY, "");
            }

            if(UtilValidate.isNotEmpty(mRow.get("pdpVideoUrl"))) 
            {
                dataRow.put(Constants.PRODUCT_VIDEO_URL_DATA_KEY, mRow.get("pdpVideoUrl"));
                dataRow.put(Constants.PRODUCT_VIDEO_URL_THRU_DATA_KEY, "");
            }

            if(UtilValidate.isNotEmpty(mRow.get("pdpVideo360Url"))) 
            {
                dataRow.put(Constants.PRODUCT_VIDEO_360_URL_DATA_KEY, mRow.get("pdpVideo360Url"));
                dataRow.put(Constants.PRODUCT_VIDEO_360_URL_THRU_DATA_KEY, "");
            }
            for (cnt = 1; cnt <= 10; cnt++)
            {
        	    if(UtilValidate.isNotEmpty(mRow.get("addImage"+cnt))) 
        	    {
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_ADDNL_IMG_DATA_KEY, UtilMisc.toMap("count", cnt)), mRow.get("addImage"+cnt));
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_ADDNL_IMG_THRU_DATA_KEY, UtilMisc.toMap("count", cnt)), "");
        	    }
        	    if(UtilValidate.isNotEmpty(mRow.get("xtraLargeImage"+cnt))) 
        	    {
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_LARGE_IMG_DATA_KEY, UtilMisc.toMap("count", cnt)), mRow.get("xtraLargeImage"+cnt));
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_LARGE_IMG_THRU_DATA_KEY, UtilMisc.toMap("count", cnt)), "");
        	    }
        	    if(UtilValidate.isNotEmpty(mRow.get("xtraDetailImage"+cnt))) 
        	    {
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_DETAIL_IMG_DATA_KEY, UtilMisc.toMap("count", cnt)), mRow.get("xtraDetailImage"+cnt));
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_DETAIL_IMG_THRU_DATA_KEY, UtilMisc.toMap("count", cnt)), "");
        	    }
            }
        }
        catch (Exception e) 
        {
            Debug.logError(e, module);
        }
        return dataRow;
    }

    /**
     * process the DB and build the product data rows
     * @param context service context 
     * @return a List of Map.
     */
    public static List<Map<String, Object>> buildProductDataRows(Map<String, ?> context) 
    {
        String productStoreId = (String) context.get("productStoreId");
        String browseRootProductCategoryId = (String) context.get("browseRootProductCategoryId");
        List<Map<String, Object>> dataRows = FastList.newInstance();

        try
        {
            HashMap productExists = new HashMap();
            Map<String, String> productFeatureTypesMap = FastMap.newInstance();
            List<GenericValue> productFeatureTypesList = _delegator.findList("ProductFeatureType", null, null, null, null, false);
            
            //get the whole list of ProductFeatureGroup and ProductFeatureGroupAndAppl
            List productFeatureGroupList = _delegator.findList("ProductFeatureGroup", null, null, null, null, false);
            List productFeatureGroupAndApplList = _delegator.findList("ProductFeatureGroupAndAppl", null, null, null, null, false);
            productFeatureGroupAndApplList = EntityUtil.filterByDate(productFeatureGroupAndApplList);
            
            if(UtilValidate.isNotEmpty(productFeatureTypesList))
            {
                for (GenericValue productFeatureType : productFeatureTypesList)
                {
                    //filter the ProductFeatureGroupAndAppl list based on productFeatureTypeId to get the ProductFeatureGroupId
                    List productFeatureGroupAndAppls = EntityUtil.filterByAnd(productFeatureGroupAndApplList, UtilMisc.toMap("productFeatureTypeId", productFeatureType.getString("productFeatureTypeId")));
                    String description = "";
                    if(UtilValidate.isNotEmpty(productFeatureGroupAndAppls))
                    {
                        GenericValue productFeatureGroupAndAppl = EntityUtil.getFirst(productFeatureGroupAndAppls);
                        List productFeatureGroups = EntityUtil.filterByAnd(productFeatureGroupList, UtilMisc.toMap("productFeatureGroupId", productFeatureGroupAndAppl.getString("productFeatureGroupId")));
                        GenericValue productFeatureGroup = EntityUtil.getFirst(productFeatureGroups);
                        description = productFeatureGroup.getString("description");
                    }
                    else
                    {
                        description = productFeatureType.getString("description");
                    }
                    productFeatureTypesMap.put(productFeatureType.getString("productFeatureTypeId"),description);
                }
                
            }

            List<Map<String, Object>> productCategories = OsafeAdminCatalogServices.getRelatedCategories(_delegator, browseRootProductCategoryId, null, true, false, true);
            for (Map<String, Object> workingCategoryMap : productCategories) 
            {
                GenericValue workingCategory = (GenericValue) workingCategoryMap.get("ProductCategory");
                List<GenericValue> productCategoryMembers = workingCategory.getRelated("ProductCategoryMember");
                // Remove any expired
                productCategoryMembers = EntityUtil.filterByDate(productCategoryMembers, true);
                for (GenericValue productCategoryMember : productCategoryMembers) 
                {
                    GenericValue product = productCategoryMember.getRelatedOne("Product");
                    if (UtilValidate.isNotEmpty(product) && !productExists.containsKey(product.getString("productId")))
                    {
                        productExists.put(product.getString("productId"), product.getString("productId"));
                        String isVariant = product.getString("isVariant");
                        if (UtilValidate.isEmpty(isVariant))
                        {
                            isVariant = "N";
                        }
                        if ("N".equals(isVariant)) 
                        {
                            Map<String, Object> dataRow = buildProductDataRow(product.getString("productId"), product, productFeatureTypesMap);
                            if (UtilValidate.isNotEmpty(dataRow))
                            {
                                dataRows.add(dataRow);
                            }
                            List<GenericValue> productAssocitations = _delegator.findByAnd("ProductAssoc", UtilMisc.toMap("productId", product.getString("productId"), "productAssocTypeId", "PRODUCT_VARIANT"),UtilMisc.toList("sequenceNum"));
                            if (UtilValidate.isNotEmpty(productAssocitations))
                            {
                                for (GenericValue productAssoc : productAssocitations) 
                                {
                                    GenericValue variantProduct = productAssoc.getRelatedOne("AssocProduct");
                                    dataRow = buildProductDataRow(product.getString("productId"), variantProduct, productFeatureTypesMap);
                                    if (UtilValidate.isNotEmpty(dataRow))
                                    {
                                        dataRows.add(dataRow);
                                    }
                                }
                            }
                        }
                    }
                }
            
            }
        }
        catch (Exception e) 
        {
            Debug.logError(e, module);
        }
        return dataRows;
    }

    public static Map<String, Object> buildProductDataRow(String masterProductId, GenericValue productGV, Map<String, String> productFeatureTypesMap) 
    {
        Map<String, Object> dataRow = FastMap.newInstance();
        try
        {
            dataRow.put(Constants.PRODUCT_MASTER_ID_DATA_KEY, masterProductId);
            if(productGV.getString("isVirtual").equals("Y") || productGV.getString("isVariant").equals("Y")) 
            {
                dataRow.put(Constants.PRODUCT_ID_DATA_KEY, productGV.getString("productId"));
            }
            else
            {
                dataRow.put(Constants.PRODUCT_ID_DATA_KEY, "");
            }

            int cnt = 1;
            List<GenericValue> categoryMembers = productGV.getRelated("ProductCategoryMember");
            if(UtilValidate.isNotEmpty(categoryMembers))
            {
                categoryMembers = EntityUtil.filterByDate(categoryMembers, true);
            }
            if(UtilValidate.isNotEmpty(categoryMembers))
            {
                StringBuffer catMembers =new StringBuffer();
                for (GenericValue categoryMember : categoryMembers) 
                {
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_ID_DATA_KEY, UtilMisc.toMap("count", cnt)), categoryMember.getString("productCategoryId"));
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_SEQ_NUM_DATA_KEY, UtilMisc.toMap("count", cnt)), categoryMember.getString("sequenceNum"));
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_FROM_DATE_DATA_KEY, UtilMisc.toMap("count", cnt)), categoryMember.getTimestamp("fromDate"));
                    dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_THRU_DATE_DATA_KEY, UtilMisc.toMap("count", cnt)), categoryMember.getTimestamp("thruDate"));
                    cnt++;
                }
                dataRow.put(Constants.PRODUCT_CAT_COUNT_DATA_KEY, cnt-1);
            }
            dataRow.put(Constants.PRODUCT_INTERNAL_NAME_DATA_KEY, productGV.getString("internalName"));
            dataRow.put(Constants.PRODUCT_WIDTH_DATA_KEY, productGV.getBigDecimal("productWidth"));
            dataRow.put(Constants.PRODUCT_HEIGHT_DATA_KEY, productGV.getBigDecimal("productHeight"));
            dataRow.put(Constants.PRODUCT_DEPTH_DATA_KEY, productGV.getBigDecimal("productDepth"));
            dataRow.put(Constants.PRODUCT_WEIGHT_DATA_KEY, productGV.getBigDecimal("weight"));
            dataRow.put(Constants.PRODUCT_RETURN_ABLE_DATA_KEY, productGV.getString("returnable"));
            dataRow.put(Constants.PRODUCT_TAX_ABLE_DATA_KEY, productGV.getString("taxable"));
            dataRow.put(Constants.PRODUCT_CHARGE_SHIP_DATA_KEY, productGV.getString("chargeShipping"));
            dataRow.put(Constants.PRODUCT_INTRO_DATE_DATA_KEY, productGV.getTimestamp("introductionDate"));
            dataRow.put(Constants.PRODUCT_DISCO_DATE_DATA_KEY, productGV.getTimestamp("salesDiscontinuationDate"));
            dataRow.put(Constants.PRODUCT_MANUFACT_PARTY_ID_DATA_KEY, productGV.getString("manufacturerPartyId"));

            List<GenericValue> productContent = _delegator.findByAnd("ProductContent", UtilMisc.toMap("productId", productGV.getString("productId")),UtilMisc.toList("-fromDate"));
            productContent = EntityUtil.filterByDate(productContent, UtilDateTime.nowTimestamp());

            dataRow.put(Constants.PRODUCT_NAME_DATA_KEY, getProductContent(productGV.getString("productId"), "PRODUCT_NAME", productContent));
            dataRow.put(Constants.PRODUCT_SALES_PITCH_DATA_KEY, getProductContent(productGV.getString("productId"), "SHORT_SALES_PITCH", productContent));
            dataRow.put(Constants.PRODUCT_LONG_DESC_DATA_KEY, getProductContent(productGV.getString("productId"), "LONG_DESCRIPTION", productContent));
            dataRow.put(Constants.PRODUCT_SPCL_INS_DATA_KEY, getProductContent(productGV.getString("productId"), "SPECIALINSTRUCTIONS", productContent));
            dataRow.put(Constants.PRODUCT_DELIVERY_INFO_DATA_KEY, getProductContent(productGV.getString("productId"), "DELIVERY_INFO", productContent));
            dataRow.put(Constants.PRODUCT_DIRECTIONS_DATA_KEY, getProductContent(productGV.getString("productId"), "DIRECTIONS", productContent));
            dataRow.put(Constants.PRODUCT_TERMS_COND_DATA_KEY, getProductContent(productGV.getString("productId"), "TERMS_AND_CONDS", productContent));
            dataRow.put(Constants.PRODUCT_INGREDIENTS_DATA_KEY, getProductContent(productGV.getString("productId"), "INGREDIENTS", productContent));
            dataRow.put(Constants.PRODUCT_WARNING_DATA_KEY, getProductContent(productGV.getString("productId"), "WARNINGS", productContent));
            dataRow.put(Constants.PRODUCT_PLP_LABEL_DATA_KEY, getProductContent(productGV.getString("productId"), "PLP_LABEL", productContent));
            dataRow.put(Constants.PRODUCT_PDP_LABEL_DATA_KEY, getProductContent(productGV.getString("productId"), "PDP_LABEL", productContent));

            List productPriceList = _delegator.findByAnd("ProductPrice", UtilMisc.toMap("productId", productGV.getString("productId"), "productPriceTypeId", "LIST_PRICE"));
            if(UtilValidate.isNotEmpty(productPriceList))
            {
                productPriceList = EntityUtil.filterByDate(productPriceList);
                if(UtilValidate.isNotEmpty(productPriceList))
                {
                    GenericValue gvProductPrice = EntityUtil.getFirst(productPriceList);
                    dataRow.put(Constants.PRODUCT_LIST_PRICE_DATA_KEY, gvProductPrice.getBigDecimal("price"));
                    dataRow.put(Constants.PRODUCT_LIST_PRICE_CUR_DATA_KEY, gvProductPrice.getString("currencyUomId"));
                    dataRow.put(Constants.PRODUCT_LIST_PRICE_FROM_DATA_KEY, gvProductPrice.getTimestamp("fromDate"));
                    dataRow.put(Constants.PRODUCT_LIST_PRICE_THRU_DATA_KEY, gvProductPrice.getTimestamp("thruDate"));
                }
            }
            productPriceList = _delegator.findByAnd("ProductPrice", UtilMisc.toMap("productId", productGV.getString("productId"), "productPriceTypeId", "DEFAULT_PRICE"));
            if(UtilValidate.isNotEmpty(productPriceList))
            {
                productPriceList = EntityUtil.filterByDate(productPriceList);
                if(UtilValidate.isNotEmpty(productPriceList))
                {
                    GenericValue gvProductPrice = EntityUtil.getFirst(productPriceList);
                    dataRow.put(Constants.PRODUCT_DEFAULT_PRICE_DATA_KEY, gvProductPrice.getBigDecimal("price"));
                    dataRow.put(Constants.PRODUCT_DEFAULT_PRICE_CUR_DATA_KEY, gvProductPrice.getString("currencyUomId"));
                    dataRow.put(Constants.PRODUCT_DEFAULT_PRICE_FROM_DATA_KEY, gvProductPrice.getTimestamp("fromDate"));
                    dataRow.put(Constants.PRODUCT_DEFAULT_PRICE_THRU_DATA_KEY, gvProductPrice.getTimestamp("thruDate"));
                }
            }
            
            List<GenericValue> productSelectableFeatures = FastList.newInstance();
            productSelectableFeatures = _delegator.findByAnd("ProductFeatureAndAppl", UtilMisc.toMap("productId", productGV.getString("productId"), "productFeatureApplTypeId", "STANDARD_FEATURE"),UtilMisc.toList("productFeatureTypeId","sequenceNum"));
            productSelectableFeatures = EntityUtil.filterByDate(productSelectableFeatures);
            cnt = 1;
            for (GenericValue productSelectableFeature : productSelectableFeatures) 
            {
                String productFeatureTypeDesc = (String) productFeatureTypesMap.get(productSelectableFeature.getString("productFeatureTypeId"));
                if(UtilValidate.isEmpty(productFeatureTypeDesc))
                {
                    productFeatureTypeDesc = productSelectableFeature.getString("productFeatureTypeId");
                }
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_TYPE_ID_DATA_KEY, UtilMisc.toMap("count", cnt)), productSelectableFeature.getString("productFeatureTypeId"));
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_TYPE_DESC_DATA_KEY, UtilMisc.toMap("count", cnt)), productFeatureTypeDesc);
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_DESC_DATA_KEY, UtilMisc.toMap("count", cnt)), productSelectableFeature.getString("description"));
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_SEQ_NUM_DATA_KEY, UtilMisc.toMap("count", cnt)), productSelectableFeature.getString("sequenceNum"));
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_FROM_DATA_KEY, UtilMisc.toMap("count", cnt)), productSelectableFeature.getTimestamp("fromDate"));
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_THRU_DATA_KEY, UtilMisc.toMap("count", cnt)), productSelectableFeature.getTimestamp("thruDate"));
                cnt++;
            }
            
            List<GenericValue> productDistinguishFeatures = FastList.newInstance();
            productDistinguishFeatures = _delegator.findByAnd("ProductFeatureAndAppl", UtilMisc.toMap("productId", productGV.getString("productId"), "productFeatureApplTypeId", "DISTINGUISHING_FEAT"),UtilMisc.toList("productFeatureTypeId","sequenceNum"));
            productDistinguishFeatures = EntityUtil.filterByDate(productDistinguishFeatures);    
            cnt=1;
            for (GenericValue productDistinguishFeature : productDistinguishFeatures) 
            {
                String productFeatureTypeDesc = (String) productFeatureTypesMap.get(productDistinguishFeature.getString("productFeatureTypeId"));
                if(UtilValidate.isEmpty(productFeatureTypeDesc))
                {
                    productFeatureTypeDesc = productDistinguishFeature.getString("productFeatureTypeId");
                }
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_TYPE_ID_DATA_KEY, UtilMisc.toMap("count", cnt)), productDistinguishFeature.getString("productFeatureTypeId"));
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_TYPE_DESC_DATA_KEY, UtilMisc.toMap("count", cnt)), productFeatureTypeDesc);
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_DESC_DATA_KEY, UtilMisc.toMap("count", cnt)), productDistinguishFeature.getString("description"));
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_SEQ_NUM_DATA_KEY, UtilMisc.toMap("count", cnt)), productDistinguishFeature.getString("sequenceNum"));
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_FROM_DATA_KEY, UtilMisc.toMap("count", cnt)), productDistinguishFeature.getTimestamp("fromDate"));
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_THRU_DATA_KEY, UtilMisc.toMap("count", cnt)), productDistinguishFeature.getTimestamp("thruDate"));
                cnt++;
            }

            List<GenericValue> productGoodIdentifications = _delegator.findByAnd("GoodIdentification", UtilMisc.toMap("productId", productGV.getString("productId")),UtilMisc.toList("goodIdentificationTypeId"));
            Map mGoodIdentifications = FastMap.newInstance();
            for (GenericValue productGoodIdentification : productGoodIdentifications) 
            {
                mGoodIdentifications.put(productGoodIdentification.getString("goodIdentificationTypeId"), productGoodIdentification.getString("idValue"));
            }
            dataRow.put(Constants.PRODUCT_SKU_DATA_KEY, mGoodIdentifications.get("SKU"));
            dataRow.put(Constants.PRODUCT_GOOGLE_ID_DATA_KEY, mGoodIdentifications.get("GOOGLE_ID"));
            dataRow.put(Constants.PRODUCT_ISBN_DATA_KEY, mGoodIdentifications.get("ISBN"));
            dataRow.put(Constants.PRODUCT_MANUFACTURER_ID_NO_DATA_KEY, mGoodIdentifications.get("MANUFACTURER_ID_NO"));

            List<GenericValue> productAttributes = productGV.getRelated("ProductAttribute");
            Map productAttrMap = FastMap.newInstance();
            if (UtilValidate.isNotEmpty(productAttributes))
            {
                for (GenericValue productAttribute : productAttributes) 
                {
                    productAttrMap.put(productAttribute.getString("attrName"), productAttribute.getString("attrValue"));
                }
            }
            dataRow.put(Constants.PRODUCT_BF_INVENTORY_TOT_DATA_KEY, productAttrMap.get("BF_INVENTORY_TOT"));
            dataRow.put(Constants.PRODUCT_BF_INVENTORY_WHS_DATA_KEY, productAttrMap.get("BF_INVENTORY_WHS"));
            dataRow.put(Constants.PRODUCT_MULTI_VARIANT_DATA_KEY, productAttrMap.get("PDP_SELECT_MULTI_VARIANT"));
            dataRow.put(Constants.PRODUCT_GIFT_MESSAGE_DATA_KEY, productAttrMap.get("CHECKOUT_GIFT_MESSAGE"));
            dataRow.put(Constants.PRODUCT_QTY_MIN_DATA_KEY, productAttrMap.get("PDP_QTY_MIN"));
            dataRow.put(Constants.PRODUCT_QTY_MAX_DATA_KEY, productAttrMap.get("PDP_QTY_MAX"));
            dataRow.put(Constants.PRODUCT_QTY_DEFAULT_DATA_KEY, productAttrMap.get("PDP_QTY_DEFAULT"));
            dataRow.put(Constants.PRODUCT_IN_STORE_ONLY_DATA_KEY, productAttrMap.get("PDP_IN_STORE_ONLY"));
            
            String imageURL = getProductContent(productGV.getString("productId"), "PLP_SWATCH_IMAGE_URL", productContent);
            if (UtilValidate.isNotEmpty(imageURL))
            {
                if (!UtilValidate.isUrl(imageURL))
                {
                    String imagePath = getOsafeImagePath("PLP_SWATCH_IMAGE_URL");
                    List<String> pathElements = StringUtil.split(imageURL, "/");
                    imageURL = imagePath + pathElements.get(pathElements.size() - 1);
                }
            }
            dataRow.put(Constants.PRODUCT_PLP_SWATCH_IMG_DATA_KEY, imageURL);
            dataRow.put(Constants.PRODUCT_PLP_SWATCH_IMG_THRU_DATA_KEY, getProductContentThruDateTs(productGV.getString("productId"), "PLP_SWATCH_IMAGE_URL", productContent));
            
            imageURL = getProductContent(productGV.getString("productId"), "PDP_SWATCH_IMAGE_URL", productContent);
            if (UtilValidate.isNotEmpty(imageURL))
            {
                if (!UtilValidate.isUrl(imageURL))
                {
                    String imagePath = getOsafeImagePath("PDP_SWATCH_IMAGE_URL");
                    List<String> pathElements = StringUtil.split(imageURL, "/");
                    imageURL = imagePath + pathElements.get(pathElements.size() - 1);
                }
            }
            dataRow.put(Constants.PRODUCT_PDP_SWATCH_IMG_DATA_KEY, imageURL);
            dataRow.put(Constants.PRODUCT_PDP_SWATCH_IMG_THRU_DATA_KEY, getProductContentThruDateTs(productGV.getString("productId"), "PDP_SWATCH_IMAGE_URL", productContent));
            
            imageURL = getProductContent(productGV.getString("productId"), "SMALL_IMAGE_URL", productContent);
            if (UtilValidate.isNotEmpty(imageURL))
            {
                if (!UtilValidate.isUrl(imageURL))
                {
                    String imagePath = getOsafeImagePath("SMALL_IMAGE_URL");
                    List<String> pathElements = StringUtil.split(imageURL, "/");
                    imageURL = imagePath + pathElements.get(pathElements.size() - 1);
                }
            }
            dataRow.put(Constants.PRODUCT_SMALL_IMG_DATA_KEY, imageURL);
            dataRow.put(Constants.PRODUCT_SMALL_IMG_THRU_DATA_KEY, getProductContentThruDateTs(productGV.getString("productId"), "SMALL_IMAGE_URL", productContent));

            imageURL = getProductContent(productGV.getString("productId"), "SMALL_IMAGE_ALT_URL", productContent);
            if (UtilValidate.isNotEmpty(imageURL))
            {
                if (!UtilValidate.isUrl(imageURL))
                {
                    String imagePath = getOsafeImagePath("SMALL_IMAGE_ALT_URL");
                    List<String> pathElements = StringUtil.split(imageURL, "/");
                    imageURL = imagePath + pathElements.get(pathElements.size() - 1);
                }
            }
            dataRow.put(Constants.PRODUCT_SMALL_IMG_ALT_DATA_KEY, imageURL);
            dataRow.put(Constants.PRODUCT_SMALL_IMG_ALT_THRU_DATA_KEY, getProductContentThruDateTs(productGV.getString("productId"), "SMALL_IMAGE_ALT_URL", productContent));

            imageURL = getProductContent(productGV.getString("productId"), "THUMBNAIL_IMAGE_URL", productContent);
            if (UtilValidate.isNotEmpty(imageURL))
            {
                if (!UtilValidate.isUrl(imageURL))
                {
                    String imagePath = getOsafeImagePath("THUMBNAIL_IMAGE_URL");
                    List<String> pathElements = StringUtil.split(imageURL, "/");
                    imageURL = imagePath + pathElements.get(pathElements.size() - 1);
                }
            }
            dataRow.put(Constants.PRODUCT_THUMB_IMG_DATA_KEY, imageURL);
            dataRow.put(Constants.PRODUCT_THUMB_IMG_THRU_DATA_KEY, getProductContentThruDateTs(productGV.getString("productId"), "THUMBNAIL_IMAGE_URL", productContent));

            imageURL = getProductContent(productGV.getString("productId"), "LARGE_IMAGE_URL", productContent);
            if (UtilValidate.isNotEmpty(imageURL))
            {
                if (!UtilValidate.isUrl(imageURL))
                {
                    String imagePath = getOsafeImagePath("LARGE_IMAGE_URL");
                    List<String> pathElements = StringUtil.split(imageURL, "/");
                    imageURL = imagePath + pathElements.get(pathElements.size() - 1);
                }
            }
            dataRow.put(Constants.PRODUCT_LARGE_IMG_DATA_KEY, imageURL);
            dataRow.put(Constants.PRODUCT_LARGE_IMG_THRU_DATA_KEY, getProductContentThruDateTs(productGV.getString("productId"), "LARGE_IMAGE_URL", productContent));

            imageURL = getProductContent(productGV.getString("productId"), "DETAIL_IMAGE_URL", productContent);
            if (UtilValidate.isNotEmpty(imageURL))
            {
                if (!UtilValidate.isUrl(imageURL))
                {
                    String imagePath = getOsafeImagePath("DETAIL_IMAGE_URL");
                    List<String> pathElements = StringUtil.split(imageURL, "/");
                    imageURL = imagePath + pathElements.get(pathElements.size() - 1);
                }
            }
            dataRow.put(Constants.PRODUCT_DETAIL_IMG_DATA_KEY, imageURL);
            dataRow.put(Constants.PRODUCT_DETAIL_IMG_THRU_DATA_KEY, getProductContentThruDateTs(productGV.getString("productId"), "DETAIL_IMAGE_URL", productContent));

            imageURL = getProductContent(productGV.getString("productId"), "PDP_VIDEO_URL", productContent);
            if (UtilValidate.isNotEmpty(imageURL))
            {
                if (!UtilValidate.isUrl(imageURL))
                {
                    String imagePath = getOsafeImagePath("PDP_VIDEO_URL");
                    List<String> pathElements = StringUtil.split(imageURL, "/");
                    imageURL = imagePath + pathElements.get(pathElements.size() - 1);
                }
            }
            dataRow.put(Constants.PRODUCT_VIDEO_URL_DATA_KEY, imageURL);
            dataRow.put(Constants.PRODUCT_VIDEO_URL_THRU_DATA_KEY, getProductContentThruDateTs(productGV.getString("productId"), "PDP_VIDEO_URL", productContent));

            imageURL = getProductContent(productGV.getString("productId"), "PDP_VIDEO_360_URL", productContent);
            if (UtilValidate.isNotEmpty(imageURL))
            {
                if (!UtilValidate.isUrl(imageURL))
                {
                    String imagePath = getOsafeImagePath("PDP_VIDEO_360_URL");
                    List<String> pathElements = StringUtil.split(imageURL, "/");
                    imageURL = imagePath + pathElements.get(pathElements.size() - 1);
                }
            }
            dataRow.put(Constants.PRODUCT_VIDEO_360_URL_DATA_KEY, imageURL);
            dataRow.put(Constants.PRODUCT_VIDEO_360_URL_THRU_DATA_KEY, getProductContentThruDateTs(productGV.getString("productId"), "PDP_VIDEO_360_URL", productContent));

            for (cnt = 1; cnt <= 10; cnt++)
            {
                imageURL = getProductContent(productGV.getString("productId"), "ADDITIONAL_IMAGE_" + cnt, productContent);
                if (UtilValidate.isNotEmpty(imageURL))
                {
                    if (!UtilValidate.isUrl(imageURL))
                    {
                        String imagePath = getOsafeImagePath("ADDITIONAL_IMAGE_" + cnt);
                        List<String> pathElements = StringUtil.split(imageURL, "/");
                        imageURL = imagePath + pathElements.get(pathElements.size() - 1);
                    }
                }
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_ADDNL_IMG_DATA_KEY, UtilMisc.toMap("count", cnt)), imageURL);
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_ADDNL_IMG_THRU_DATA_KEY, UtilMisc.toMap("count", cnt)), getProductContentThruDateTs(productGV.getString("productId"), "ADDITIONAL_IMAGE_" + cnt, productContent));

                imageURL = getProductContent(productGV.getString("productId"), "XTRA_IMG_" + cnt +"_LARGE", productContent);
                if (UtilValidate.isNotEmpty(imageURL))
                {
                    if (!UtilValidate.isUrl(imageURL))
                    {
                        String imagePath = getOsafeImagePath("XTRA_IMG_" + cnt +"_LARGE");
                        List<String> pathElements = StringUtil.split(imageURL, "/");
                        imageURL = imagePath + pathElements.get(pathElements.size() - 1);
                    }
                }
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_LARGE_IMG_DATA_KEY, UtilMisc.toMap("count", cnt)), imageURL);
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_LARGE_IMG_THRU_DATA_KEY, UtilMisc.toMap("count", cnt)), getProductContentThruDateTs(productGV.getString("productId"), "XTRA_IMG_" + cnt +"_LARGE", productContent));

                imageURL = getProductContent(productGV.getString("productId"), "XTRA_IMG_" + cnt +"_DETAIL", productContent);
                if (UtilValidate.isNotEmpty(imageURL))
                {
                    if (!UtilValidate.isUrl(imageURL))
                    {
                        String imagePath = getOsafeImagePath("XTRA_IMG_" + cnt +"_DETAIL");
                        List<String> pathElements = StringUtil.split(imageURL, "/");
                        imageURL = imagePath + pathElements.get(pathElements.size() - 1);
                    }
                }
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_DETAIL_IMG_DATA_KEY, UtilMisc.toMap("count", cnt)), imageURL);
                dataRow.put(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_DETAIL_IMG_THRU_DATA_KEY, UtilMisc.toMap("count", cnt)), getProductContentThruDateTs(productGV.getString("productId"), "XTRA_IMG_" + cnt +"_DETAIL", productContent));
            }
        }
        catch (Exception e) 
        {
            Debug.logError(e, module);
        }
        return dataRow;
    }

    /**
     * process the XLS sheet and build the Product Assoc data rows
     * @param s XLS sheet object
     * @return a List of Map.
     */
    public static List<Map<String, Object>> buildProductAssocDataRows(Sheet s) 
    {
        List<Map<String, Object>> dataRows = FastList.newInstance();

        try
        {
            List xlsDataRows = buildDataRows(buildProductAssocHeader(), s);
    		for (int i=0 ; i < xlsDataRows.size() ; i++) 
            {
                Map<String, Object> dataRow = FastMap.newInstance();
                Map mRow = (Map)xlsDataRows.get(i);
    	        dataRow.put(Constants.PRODUCT_ASSOC_ID_DATA_KEY, mRow.get("productId"));
    	        dataRow.put(Constants.PRODUCT_ASSOC_ID_TO_DATA_KEY, mRow.get("productIdTo"));
    	        dataRow.put(Constants.PRODUCT_ASSOC_TYPE_DATA_KEY, mRow.get("productAssocType"));
                if (UtilValidate.isNotEmpty(mRow.get("fromDate")))
                {
                    dataRow.put(Constants.PRODUCT_ASSOC_FROM_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(mRow.get("fromDate").toString())));
                }
                if (UtilValidate.isNotEmpty(mRow.get("thruDate")))
                {
                    dataRow.put(Constants.PRODUCT_ASSOC_THRU_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(mRow.get("thruDate").toString())));
                }
                if (UtilValidate.isNotEmpty(dataRow))
                {
                    dataRows.add(dataRow);
                }
            }
        }
        catch (Exception e) 
        {
            Debug.logError(e, module);
        }
        return dataRows;
    }

    /**
     * process the DB and build the Product Assoc data rows
     * @param context service context 
     * @return a List of Map.
     */
    public static List<Map<String, Object>> buildProductAssocDataRows(Map<String, ?> context) 
    {
        String productStoreId = (String) context.get("productStoreId");
        String browseRootProductCategoryId = (String) context.get("browseRootProductCategoryId");
        List<Map<String, Object>> dataRows = FastList.newInstance();

        try
        {
            List<Map<String, Object>> productCategories = OsafeAdminCatalogServices.getRelatedCategories(_delegator, browseRootProductCategoryId, null, true, false, true);
            HashMap productExists = new HashMap();
            for (Map<String, Object> workingCategoryMap : productCategories) 
            {
                GenericValue workingCategory = (GenericValue) workingCategoryMap.get("ProductCategory");
                List<GenericValue> productCategoryMembers = workingCategory.getRelated("ProductCategoryMember");
                // Remove any expired
                productCategoryMembers = EntityUtil.filterByDate(productCategoryMembers, true);
                for (GenericValue productCategoryMember : productCategoryMembers) 
                {
                    GenericValue product = productCategoryMember.getRelatedOne("Product");
                    if (UtilValidate.isNotEmpty(product) && !productExists.containsKey(product.getString("productId")))
                    {
                        productExists.put(product.getString("productId"), product.getString("productId"));
                        List<GenericValue> productAssocitations = product.getRelated("MainProductAssoc");
                        if(UtilValidate.isNotEmpty(productAssocitations))
                        {
                            productAssocitations = EntityUtil.filterByDate(productAssocitations, true);
                        }
                        List<GenericValue> complementProductAssoc = FastList.newInstance();
                        if(UtilValidate.isNotEmpty(productAssocitations))
                        {
                            complementProductAssoc = EntityUtil.filterByAnd(productAssocitations, UtilMisc.toMap("productAssocTypeId", "PRODUCT_COMPLEMENT"));
                            complementProductAssoc = EntityUtil.orderBy(complementProductAssoc,UtilMisc.toList("sequenceNum"));
                        }
                        for (GenericValue productAssoc : complementProductAssoc) 
                        {
                            Map<String, Object> dataRow = buildProductAssocDataRow(productAssoc);
                            if (UtilValidate.isNotEmpty(dataRow))
                            {
                                dataRows.add(dataRow);
                            }
                        }
                        List<GenericValue> accessoryProductAssoc = FastList.newInstance();
                        if(UtilValidate.isNotEmpty(productAssocitations))
                        {
                            accessoryProductAssoc = EntityUtil.filterByAnd(productAssocitations, UtilMisc.toMap("productAssocTypeId", "PRODUCT_ACCESSORY"));
                            accessoryProductAssoc = EntityUtil.orderBy(accessoryProductAssoc,UtilMisc.toList("sequenceNum"));
                        }
                        for (GenericValue productAssoc : accessoryProductAssoc) 
                        {
                            Map<String, Object> dataRow = buildProductAssocDataRow(productAssoc);
                            if (UtilValidate.isNotEmpty(dataRow))
                            {
                                dataRows.add(dataRow);
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e) 
        {
            Debug.logError(e, module);
        }
        return dataRows;
    }

    public static Map<String, Object> buildProductAssocDataRow(GenericValue productAssocGV) 
    {
        Map<String, Object> dataRow = FastMap.newInstance();
        dataRow.put(Constants.PRODUCT_ASSOC_ID_DATA_KEY, productAssocGV.getString("productId"));
        dataRow.put(Constants.PRODUCT_ASSOC_ID_TO_DATA_KEY, productAssocGV.getString("productIdTo"));
        dataRow.put(Constants.PRODUCT_ASSOC_TYPE_DATA_KEY, productAssocGV.getString("productAssocTypeId"));
        dataRow.put(Constants.PRODUCT_ASSOC_FROM_DATA_KEY, productAssocGV.getTimestamp("fromDate"));
        dataRow.put(Constants.PRODUCT_ASSOC_THRU_DATA_KEY, productAssocGV.getTimestamp("thruDate"));
        return dataRow;
    }

    /**
     * process the XLS sheet and build the Facet group data rows
     * @param s XLS sheet object
     * @return a List of Map.
     */
    public static List<Map<String, Object>> buildFacetGroupDataRows(Sheet s) 
    {
        List<Map<String, Object>> dataRows = FastList.newInstance();

        try
        {
            List xlsDataRows = buildDataRows(buildProductFacetGroupHeader(), s);
    		for (int i=0 ; i < xlsDataRows.size() ; i++) 
            {
                Map<String, Object> dataRow = FastMap.newInstance();
                Map mRow = (Map)xlsDataRows.get(i);
                dataRow.put(Constants.FACET_GRP_ID_DATA_KEY, mRow.get("facetGroupId"));
                dataRow.put(Constants.FACET_GRP_DESC_DATA_KEY, mRow.get("description"));
                dataRow.put(Constants.FACET_GRP_PROD_CAT_ID_DATA_KEY, mRow.get("productCategoryId"));
                dataRow.put(Constants.FACET_GRP_SEQ_NUM_DATA_KEY, mRow.get("sequenceNum"));
                dataRow.put(Constants.FACET_GRP_TOOLTIP_DATA_KEY, mRow.get("tooltip"));
                dataRow.put(Constants.FACET_GRP_MIN_DATA_KEY, mRow.get("minDisplay"));
                dataRow.put(Constants.FACET_GRP_MAX_DATA_KEY, mRow.get("maxDisplay"));
                if (UtilValidate.isNotEmpty(mRow.get("fromDate")))
                {
                    dataRow.put(Constants.FACET_GRP_FROM_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(mRow.get("fromDate").toString())));
                }
                if (UtilValidate.isNotEmpty(mRow.get("thruDate")))
                {
                    dataRow.put(Constants.FACET_GRP_THRU_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(mRow.get("thruDate").toString())));
                }
                if (UtilValidate.isNotEmpty(dataRow))
                {
                    dataRows.add(dataRow);
                }
            }
        }
        catch (Exception e) 
        {
            Debug.logError(e, module);
        }
        return dataRows;
    }

    /**
     * process the DB and build the Facet group data rows
     * @param context service context 
     * @return a List of Map.
     */
    public static List<Map<String, Object>> buildFacetGroupDataRows(Map<String, ?> context) 
    {
        String productStoreId = (String) context.get("productStoreId");
        String browseRootProductCategoryId = (String) context.get("browseRootProductCategoryId");
        List<Map<String, Object>> dataRows = FastList.newInstance();
        
        try 
        {
            List<GenericValue> productFeatureCatGrpApplList = _delegator.findList("ProductFeatureCatGrpAppl", null, null, null, null, false);
            List<GenericValue> productFeatureGroupList = _delegator.findList("ProductFeatureGroup", null, null, null, null, false);
            
            if(UtilValidate.isNotEmpty(productFeatureCatGrpApplList))
            {
                for (GenericValue productFeatureCatGrpAppl : productFeatureCatGrpApplList)
                {
                    Map<String, Object> dataRow = FastMap.newInstance();
                    dataRow.put(Constants.FACET_GRP_ID_DATA_KEY, productFeatureCatGrpAppl.getString("productFeatureGroupId"));
                    List productFeatureGroups = EntityUtil.filterByAnd(productFeatureGroupList, UtilMisc.toMap("productFeatureGroupId", productFeatureCatGrpAppl.getString("productFeatureGroupId")));
                    if(UtilValidate.isNotEmpty(productFeatureGroups))
                    {
                        GenericValue productFeatureGroup = EntityUtil.getFirst(productFeatureGroups);
                        dataRow.put(Constants.FACET_GRP_DESC_DATA_KEY, productFeatureGroup.getString("description"));
                    }
                    dataRow.put(Constants.FACET_GRP_PROD_CAT_ID_DATA_KEY, productFeatureCatGrpAppl.getString("productCategoryId"));
                    dataRow.put(Constants.FACET_GRP_SEQ_NUM_DATA_KEY, productFeatureCatGrpAppl.getLong("sequenceNum"));
                    dataRow.put(Constants.FACET_GRP_TOOLTIP_DATA_KEY, productFeatureCatGrpAppl.getString("facetTooltip"));
                    dataRow.put(Constants.FACET_GRP_MIN_DATA_KEY, productFeatureCatGrpAppl.getLong("facetValueMin"));
                    dataRow.put(Constants.FACET_GRP_MAX_DATA_KEY, productFeatureCatGrpAppl.getLong("facetValueMax"));
                    dataRow.put(Constants.FACET_GRP_FROM_DATA_KEY, productFeatureCatGrpAppl.getTimestamp("fromDate"));
                    dataRow.put(Constants.FACET_GRP_THRU_DATA_KEY, productFeatureCatGrpAppl.getTimestamp("thruDate"));
                    if (UtilValidate.isNotEmpty(dataRow))
                    {
                        dataRows.add(dataRow);
                    }
                }
            }
        } 
        catch (Exception e) 
        {
            Debug.logError(e, module);
        }
        return dataRows;
    }

    /**
     * process the XLS sheet and build the Facet value data rows
     * @param s XLS sheet object
     * @return a List of Map.
     */
    public static List<Map<String, Object>> buildFacetValueDataRows(Sheet s) 
    {
        List<Map<String, Object>> dataRows = FastList.newInstance();

        try
        {
            List xlsDataRows = buildDataRows(buildProductFacetValueHeader(), s);
    		for (int i=0 ; i < xlsDataRows.size() ; i++) 
            {
                Map<String, Object> dataRow = FastMap.newInstance();
                Map mRow = (Map)xlsDataRows.get(i);
        		Map mFeatureTypeMap = FastMap.newInstance();
                buildFeatureMap(mFeatureTypeMap, (String)mRow.get("facetValueId"));
    			if(mFeatureTypeMap.size() > 0) 
    			{
    	    	    Set featureTypeSet = mFeatureTypeMap.keySet();
    			    Iterator iterFeatureType = featureTypeSet.iterator(); 
    			    while (iterFeatureType.hasNext())
    			    {
    			    	String featureType =(String)iterFeatureType.next();
    				    String featureTypeId = StringUtil.removeSpaces(featureType).toUpperCase();
    	                dataRow.put(Constants.FACET_VAL_GRP_ID_DATA_KEY, featureTypeId);
    	                dataRow.put(Constants.FACET_VAL_FEAT_TYPE_ID_DATA_KEY, featureTypeId);

    				    FastMap mFeatureMap=(FastMap)mFeatureTypeMap.get(featureType);
    	  		        Set featureSet = mFeatureMap.keySet();
    	  		        Iterator iterFeature = featureSet.iterator();
    	  		    
    	  		        while (iterFeature.hasNext())
    	  		        {
    	  			        String featureValue = (String) mFeatureMap.get(iterFeature.next());
    	  	                dataRow.put(Constants.FACET_VAL_FEAT_ID_DATA_KEY,  featureValue);
    	  		        }
    			    }
    	  	    }
                dataRow.put(Constants.FACET_VAL_FEAT_DESC_DATA_KEY, mRow.get("description"));
                dataRow.put(Constants.FACET_VAL_SEQ_NUM_DATA_KEY, mRow.get("sequenceNum"));
                if (UtilValidate.isNotEmpty(mRow.get("plpSwatchUrl")))
                {
                    dataRow.put(Constants.FACET_VAL_PLP_SWATCH_DATA_KEY, mRow.get("plpSwatchUrl"));
                }
                if (UtilValidate.isNotEmpty(mRow.get("pdpSwatchUrl")))
                {
                    dataRow.put(Constants.FACET_VAL_PDP_SWATCH_DATA_KEY, mRow.get("pdpSwatchUrl"));
                }
                if (UtilValidate.isNotEmpty(mRow.get("fromDate")))
                {
                    dataRow.put(Constants.FACET_VAL_FROM_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(mRow.get("fromDate").toString())));
                }
                if (UtilValidate.isNotEmpty(mRow.get("thruDate")))
                {
                    dataRow.put(Constants.FACET_VAL_THRU_DATA_KEY, UtilDateTime.toTimestamp(OsafeAdminUtil.validDate(mRow.get("thruDate").toString())));
                }
                if (UtilValidate.isNotEmpty(dataRow))
                {
                    dataRows.add(dataRow);
                }
            }
        }
        catch (Exception e) 
        {
            Debug.logError(e, module);
        }
        return dataRows;
    }

    /**
     * process the DB and build the Facet value data rows
     * @param context service context 
     * @return a List of Map.
     */
    public static List<Map<String, Object>> buildFacetValueDataRows(Map<String, ?> context) 
    {
        String productStoreId = (String) context.get("productStoreId");
        String browseRootProductCategoryId = (String) context.get("browseRootProductCategoryId");
        List<Map<String, Object>> dataRows = FastList.newInstance();

        try 
        {
            List<GenericValue> productFeatureGroupApplList = _delegator.findList("ProductFeatureGroupAppl", null, null, null, null, false);
            List<GenericValue> productFeatureList = _delegator.findList("ProductFeature", null, null, null, null, false);
            List<GenericValue> productFeatureTypeList = _delegator.findList("ProductFeatureType", null, null, null, null, false);
            List<GenericValue> plpSwatchProductFeatureDataResourceList = _delegator.findByAnd("ProductFeatureDataResource", UtilMisc.toMap("featureDataResourceTypeId","PLP_SWATCH_IMAGE_URL"),UtilMisc.toList("dataResourceId"));
            List<GenericValue> pdpSwatchProductFeatureDataResourceList = _delegator.findByAnd("ProductFeatureDataResource", UtilMisc.toMap("featureDataResourceTypeId","PDP_SWATCH_IMAGE_URL"),UtilMisc.toList("dataResourceId"));
            
            if(UtilValidate.isNotEmpty(productFeatureGroupApplList))
            {
                for (GenericValue productFeatureGroupAppl : productFeatureGroupApplList)
                {
                    Map<String, Object> dataRow = FastMap.newInstance();
                    dataRow.put(Constants.FACET_VAL_GRP_ID_DATA_KEY, productFeatureGroupAppl.getString("productFeatureGroupId"));

                    List productFeatures = EntityUtil.filterByAnd(productFeatureList, UtilMisc.toMap("productFeatureId", productFeatureGroupAppl.getString("productFeatureId")));
                    GenericValue productFeature = null;
                    if(UtilValidate.isNotEmpty(productFeatures))
                    {
                        productFeature = EntityUtil.getFirst(productFeatures);
                    }
                    if(UtilValidate.isNotEmpty(productFeature))
                    {
                        dataRow.put(Constants.FACET_VAL_FEAT_ID_DATA_KEY,  productFeature.getString("productFeatureId"));
                        dataRow.put(Constants.FACET_VAL_FEAT_TYPE_ID_DATA_KEY, productFeature.getString("productFeatureTypeId"));
                        dataRow.put(Constants.FACET_VAL_FEAT_DESC_DATA_KEY, productFeature.getString("description"));
                    }
                    dataRow.put(Constants.FACET_VAL_SEQ_NUM_DATA_KEY, productFeatureGroupAppl.getLong("sequenceNum"));
                    
                    if(UtilValidate.isNotEmpty(productFeature))
                    {
                        List plpSwatchProductFeatureDataResources = EntityUtil.filterByAnd(plpSwatchProductFeatureDataResourceList, UtilMisc.toMap("productFeatureId", productFeature.getString("productFeatureId")));
                        if(UtilValidate.isNotEmpty(plpSwatchProductFeatureDataResources))
                        {
                            GenericValue plpSwatchProductFeatureDataResource = EntityUtil.getFirst(plpSwatchProductFeatureDataResources);
                            GenericValue dataResource = (GenericValue) plpSwatchProductFeatureDataResource.getRelatedOne("DataResource");
                            String dataResourceName = dataResource.getString("dataResourceName");
                            dataRow.put(Constants.FACET_VAL_PLP_SWATCH_DATA_KEY, dataResourceName);
                        }
                        
                        List pdpSwatchProductFeatureDataResources = EntityUtil.filterByAnd(pdpSwatchProductFeatureDataResourceList, UtilMisc.toMap("productFeatureId", productFeature.getString("productFeatureId")));
                        if(UtilValidate.isNotEmpty(pdpSwatchProductFeatureDataResources))
                        {
                            GenericValue pdpSwatchProductFeatureDataResource = EntityUtil.getFirst(pdpSwatchProductFeatureDataResources);
                            GenericValue dataResource = (GenericValue) pdpSwatchProductFeatureDataResource.getRelatedOne("DataResource");
                            String dataResourceName = dataResource.getString("dataResourceName");
                            dataRow.put(Constants.FACET_VAL_PDP_SWATCH_DATA_KEY, dataResourceName);
                        }
                    }

                    dataRow.put(Constants.FACET_VAL_FROM_DATA_KEY, productFeatureGroupAppl.getTimestamp("fromDate"));
                    dataRow.put(Constants.FACET_VAL_THRU_DATA_KEY, productFeatureGroupAppl.getTimestamp("thruDate"));
                    if (UtilValidate.isNotEmpty(dataRow))
                    {
                        dataRows.add(dataRow);
                    }
                }
            }
        } 
        catch (Exception e) 
        {
            Debug.logError(e, module);
        }
        return dataRows;
    }

    /**
     * process the XLS sheet and build the Manufacturer data rows
     * @param s XLS sheet object
     * @return a List of Map.
     */
    public static List<Map<String, Object>> buildManufacturerDataRows(Sheet s) 
    {
        List<Map<String, Object>> dataRows = FastList.newInstance();

        try
        {
            List xlsDataRows = buildDataRows(buildManufacturerHeader(), s);
    		for (int i=0 ; i < xlsDataRows.size() ; i++) 
            {
                Map<String, Object> dataRow = FastMap.newInstance();
                Map mRow = (Map)xlsDataRows.get(i);
                dataRow.put(Constants.MANUFACTURER_ID_DATA_KEY, mRow.get("partyId"));
                dataRow.put(Constants.MANUFACTURER_NAME_DATA_KEY,  mRow.get("manufacturerName"));
                dataRow.put(Constants.MANUFACTURER_SHORT_DESC_DATA_KEY, mRow.get("shortDescription"));
                dataRow.put(Constants.MANUFACTURER_LONG_DESC_DATA_KEY, mRow.get("longDescription"));
                dataRow.put(Constants.MANUFACTURER_ADDR1_DATA_KEY, mRow.get("address1"));
                dataRow.put(Constants.MANUFACTURER_CITY_DATA_KEY, mRow.get("city"));
                dataRow.put(Constants.MANUFACTURER_STATE_DATA_KEY, mRow.get("state"));
                dataRow.put(Constants.MANUFACTURER_ZIP_DATA_KEY, mRow.get("zip"));
                dataRow.put(Constants.MANUFACTURER_COUNTRY_DATA_KEY, mRow.get("country"));
    			
    			if(UtilValidate.isNotEmpty(mRow.get("manufacturerImage"))) 
    			{
                    dataRow.put(Constants.MANUFACTURER_IMG_DATA_KEY, mRow.get("manufacturerImage"));
                    dataRow.put(Constants.MANUFACTURER_IMG_THRU_DATA_KEY, "");
    			}
                if (UtilValidate.isNotEmpty(dataRow))
                {
                    dataRows.add(dataRow);
                }
            }
        }
        catch (Exception e) 
        {
            Debug.logError(e, module);
        }
        return dataRows;
    }

    /**
     * process the DB and build the Manufacturer data rows
     * @param context service context 
     * @return a List of Map.
     */
    public static List<Map<String, Object>> buildManufacturerDataRows(Map<String, ?> context) 
    {
        List<Map<String, Object>> dataRows = FastList.newInstance();
        try 
        {
            List<GenericValue> partyManufacturers = _delegator.findByAnd("PartyRole", UtilMisc.toMap("roleTypeId","MANUFACTURER"),UtilMisc.toList("partyId"));
            if(UtilValidate.isNotEmpty(partyManufacturers))
            {
                for (GenericValue partyManufacturer : partyManufacturers)
                {

                    Map<String, Object> dataRow = FastMap.newInstance();
                    GenericValue party = partyManufacturer.getRelatedOne("Party");
                    dataRow.put(Constants.MANUFACTURER_ID_DATA_KEY, party.getString("partyId"));;

                    List<GenericValue> partyContent = _delegator.findByAnd("PartyContent", UtilMisc.toMap("partyId", party.getString("partyId")),UtilMisc.toList("-fromDate"));
                    partyContent = EntityUtil.filterByDate(partyContent,UtilDateTime.nowTimestamp());
                    dataRow.put(Constants.MANUFACTURER_NAME_DATA_KEY, getPartyContent(party.getString("partyId"), "PROFILE_NAME", partyContent));

                    GenericValue  partyContactMechPurpose = null;
                    Collection<GenericValue> partyContactMechPurposes = ContactHelper.getContactMechByPurpose(party,"GENERAL_LOCATION",false);
                    Iterator<GenericValue> partyContactMechPurposesIterator = partyContactMechPurposes.iterator();
                    while (partyContactMechPurposesIterator.hasNext()) 
                    {
                        partyContactMechPurpose = (GenericValue) partyContactMechPurposesIterator.next();
                    }
                    if (UtilValidate.isNotEmpty(partyContactMechPurpose))
                    {
                        GenericValue postalAddress = partyContactMechPurpose.getRelatedOne("PostalAddress");
                        dataRow.put(Constants.MANUFACTURER_ADDR1_DATA_KEY, postalAddress.getString("address1"));
                        dataRow.put(Constants.MANUFACTURER_CITY_DATA_KEY, postalAddress.getString("city"));
                        dataRow.put(Constants.MANUFACTURER_STATE_DATA_KEY, postalAddress.getString("stateProvinceGeoId"));
                        dataRow.put(Constants.MANUFACTURER_ZIP_DATA_KEY, postalAddress.getString("postalCode"));
                        dataRow.put(Constants.MANUFACTURER_COUNTRY_DATA_KEY, postalAddress.getString("countryGeoId"));
                    }
                    dataRow.put(Constants.MANUFACTURER_SHORT_DESC_DATA_KEY, getPartyContent(party.getString("partyId"), "DESCRIPTION", partyContent));
                    dataRow.put(Constants.MANUFACTURER_LONG_DESC_DATA_KEY, getPartyContent(party.getString("partyId"), "LONG_DESCRIPTION", partyContent));

                    String imageURL =getPartyContent(party.getString("partyId"), "PROFILE_IMAGE_URL", partyContent);
                    if (UtilValidate.isNotEmpty(imageURL))
                    {
                        if (!UtilValidate.isUrl(imageURL))
                        {
                            String imagePath = getOsafeImagePath("PROFILE_IMAGE_URL");
                            List<String> pathElements = StringUtil.split(imageURL, "/");
                            imageURL = imagePath + pathElements.get(pathElements.size() - 1);
                        }
                    }
                    dataRow.put(Constants.MANUFACTURER_IMG_DATA_KEY, imageURL);
                    dataRow.put(Constants.MANUFACTURER_IMG_THRU_DATA_KEY, getPartyContentThruDateTs(party.getString("partyId"), "PROFILE_IMAGE_URL", partyContent));
                    if (UtilValidate.isNotEmpty(dataRow))
                    {
                        dataRows.add(dataRow);
                    }
                }
            }
        } 
        catch (Exception e) 
        {
            Debug.logError(e, module);
        }
        return dataRows;
    }

    /**
     * Generate the category XLS
     * @param excelSheet  Writable XLS Sheet object
     * @param dataRows  data rows
     */
    public static void generateProductCategoryXLS(WritableSheet excelSheet, List<Map<String, Object>> dataRows) 
    {
        int iRowIdx=1;
        for (Map<String, Object> dataRow : dataRows) 
        {
            int iColIdx=0;
            createWorkBookRow(excelSheet, dataRow.get(Constants.CATEGORY_ID_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.CATEGORY_PARENT_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.CATEGORY_NAME_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.CATEGORY_DESC_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.CATEGORY_LONG_DESC_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.CATEGORY_PLP_IMG_NAME_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.CATEGORY_PLP_TEXT_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.CATEGORY_PDP_TEXT_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, formatDate(dataRow.get(Constants.CATEGORY_FROM_DATE_DATA_KEY)), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, formatDate(dataRow.get(Constants.CATEGORY_THRU_DATE_DATA_KEY)), iColIdx++, iRowIdx);
            iRowIdx++;
        }
    }

    /**
     * Generate the category XML
     * @param factory  JAXB object createtion factory object
     * @param productCategoryList  JAXB object list
     * @param dataRows  data rows
     */
    public static void generateProductCategoryXML(ObjectFactory factory, List productCategoryList, List<Map<String, Object>> dataRows) 
    {
        for (Map<String, Object> dataRow : dataRows) 
        {
            CategoryType category = factory.createCategoryType();
            category.setCategoryId(getString(dataRow.get(Constants.CATEGORY_ID_DATA_KEY)));
            category.setParentCategoryId(getString(dataRow.get(Constants.CATEGORY_PARENT_DATA_KEY)));
            category.setCategoryName(getString(dataRow.get(Constants.CATEGORY_NAME_DATA_KEY)));
            category.setDescription(getString(dataRow.get(Constants.CATEGORY_DESC_DATA_KEY)));
            category.setLongDescription(getString(dataRow.get(Constants.CATEGORY_LONG_DESC_DATA_KEY)));
            PlpImageType plpImage = factory.createPlpImageType();
            plpImage.setUrl(getString(dataRow.get(Constants.CATEGORY_PLP_IMG_NAME_DATA_KEY)));
            category.setPlpImage(plpImage);
            category.setAdditionalPlpText(getString(dataRow.get(Constants.CATEGORY_PLP_TEXT_DATA_KEY)));
            category.setAdditionalPdpText(getString(dataRow.get(Constants.CATEGORY_PDP_TEXT_DATA_KEY)));
            category.setFromDate(formatDate(dataRow.get(Constants.CATEGORY_FROM_DATE_DATA_KEY)));
            category.setThruDate(formatDate(dataRow.get(Constants.CATEGORY_THRU_DATE_DATA_KEY)));
            productCategoryList.add(category);
        }
    }

    /**
     * Generate the product XLS
     * @param excelSheet  Writable XLS Sheet object
     * @param dataRows  data rows
     */
    public static void generateProductXLS(WritableSheet excelSheet, List<Map<String, Object>> dataRows) 
    {
        int iRowIdx=1;
        int cnt = 0;
        for (Map<String, Object> dataRow : dataRows) 
        {
            int iColIdx=0;
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_MASTER_ID_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_ID_DATA_KEY), iColIdx++, iRowIdx);
            if (UtilValidate.isNotEmpty(dataRow.get(Constants.PRODUCT_CAT_COUNT_DATA_KEY)))
            {
                cnt = (Integer) dataRow.get(Constants.PRODUCT_CAT_COUNT_DATA_KEY);
                StringBuffer categories =new StringBuffer();
                for (int i = 1; i <= cnt; i++)
                {
                    categories.append(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_ID_DATA_KEY, UtilMisc.toMap("count", i))) + ",");
                }
                categories.setLength(categories.length() - 1);
                createWorkBookRow(excelSheet,categories.toString(), iColIdx++, iRowIdx);
            }
            else
            {
                createWorkBookRow(excelSheet, "", iColIdx++, iRowIdx);
            }
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_INTERNAL_NAME_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_NAME_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_SALES_PITCH_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_LONG_DESC_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_SPCL_INS_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_DELIVERY_INFO_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_DIRECTIONS_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_TERMS_COND_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_INGREDIENTS_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_WARNING_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_PLP_LABEL_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_PDP_LABEL_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, formatBigDecimal(dataRow.get(Constants.PRODUCT_LIST_PRICE_DATA_KEY)), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, formatBigDecimal(dataRow.get(Constants.PRODUCT_DEFAULT_PRICE_DATA_KEY)), iColIdx++, iRowIdx);

            if (UtilValidate.isNotEmpty(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_TYPE_DESC_DATA_KEY, UtilMisc.toMap("count", 1)))))
            {
                String selectFeature = (String) dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_TYPE_DESC_DATA_KEY, UtilMisc.toMap("count", 1)));
                selectFeature = selectFeature + ":" + dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_DESC_DATA_KEY, UtilMisc.toMap("count", 1)));
                createWorkBookRow(excelSheet, selectFeature, iColIdx++, iRowIdx);
            }
            else
            {
                createWorkBookRow(excelSheet, "", iColIdx++, iRowIdx);
            }

            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_PLP_SWATCH_IMG_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_PDP_SWATCH_IMG_DATA_KEY), iColIdx++, iRowIdx);

            for (int i = 2; i <= 5; i++)
            {
                if (UtilValidate.isNotEmpty(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_TYPE_DESC_DATA_KEY, UtilMisc.toMap("count", i)))))
                {
                    String selectFeature = (String) dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_TYPE_DESC_DATA_KEY, UtilMisc.toMap("count", i)));
                    selectFeature = selectFeature + ":" + dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_DESC_DATA_KEY, UtilMisc.toMap("count", i)));
                    createWorkBookRow(excelSheet, selectFeature, iColIdx++, iRowIdx);
                }
                else
                {
                    createWorkBookRow(excelSheet, "", iColIdx++, iRowIdx);
                }
            }

            for (int i = 1; i <= 5; i++)
            {
                if (UtilValidate.isNotEmpty(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_TYPE_DESC_DATA_KEY, UtilMisc.toMap("count", i)))))
                {
                    String distinguishFeature = (String) dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_TYPE_DESC_DATA_KEY, UtilMisc.toMap("count", i)));
                    distinguishFeature = distinguishFeature + ":" + dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_DESC_DATA_KEY, UtilMisc.toMap("count", i)));
                    createWorkBookRow(excelSheet, distinguishFeature, iColIdx++, iRowIdx);
                }
                else
                {
                    createWorkBookRow(excelSheet, "", iColIdx++, iRowIdx);
                }
            }

            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_SMALL_IMG_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_SMALL_IMG_ALT_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_THUMB_IMG_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_DETAIL_IMG_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_LARGE_IMG_DATA_KEY), iColIdx++, iRowIdx);

            for (int i = 1; i <= 10; i++)
            {
                createWorkBookRow(excelSheet, dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_ADDNL_IMG_DATA_KEY, UtilMisc.toMap("count", i))), iColIdx++, iRowIdx);
                createWorkBookRow(excelSheet, dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_DETAIL_IMG_DATA_KEY, UtilMisc.toMap("count", i))), iColIdx++, iRowIdx);
                createWorkBookRow(excelSheet, dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_LARGE_IMG_DATA_KEY, UtilMisc.toMap("count", i))), iColIdx++, iRowIdx);
            }
            createWorkBookRow(excelSheet, formatBigDecimal(dataRow.get(Constants.PRODUCT_HEIGHT_DATA_KEY)), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, formatBigDecimal(dataRow.get(Constants.PRODUCT_WIDTH_DATA_KEY)), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, formatBigDecimal(dataRow.get(Constants.PRODUCT_DEPTH_DATA_KEY)), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_RETURN_ABLE_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_TAX_ABLE_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_CHARGE_SHIP_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, formatDate(dataRow.get(Constants.PRODUCT_INTRO_DATE_DATA_KEY)), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, formatDate(dataRow.get(Constants.PRODUCT_DISCO_DATE_DATA_KEY)), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_MANUFACT_PARTY_ID_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_SKU_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_GOOGLE_ID_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_ISBN_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_MANUFACTURER_ID_NO_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_VIDEO_URL_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_VIDEO_360_URL_DATA_KEY), iColIdx++, iRowIdx);
            if (UtilValidate.isNotEmpty(dataRow.get(Constants.PRODUCT_CAT_COUNT_DATA_KEY)))
            {
                createWorkBookRow(excelSheet, dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_SEQ_NUM_DATA_KEY, UtilMisc.toMap("count", 1))), iColIdx++, iRowIdx);
            }
            else
            {
                createWorkBookRow(excelSheet, "", iColIdx++, iRowIdx);
            }
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_BF_INVENTORY_TOT_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_BF_INVENTORY_WHS_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_MULTI_VARIANT_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, formatBigDecimal(dataRow.get(Constants.PRODUCT_WEIGHT_DATA_KEY)), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_GIFT_MESSAGE_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_QTY_MIN_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_QTY_MAX_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_QTY_DEFAULT_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_IN_STORE_ONLY_DATA_KEY), iColIdx++, iRowIdx);
            iRowIdx++;
        }
    }

    /**
     * Generate the product XML
     * @param factory  JAXB object createtion factory object
     * @param productList  JAXB object list
     * @param dataRows  data rows
     */
    public static void generateProductXML(ObjectFactory factory, List productList, List<Map<String, Object>> dataRows) 
    {
        for (Map<String, Object> dataRow : dataRows) 
        {
            ProductType productType = factory.createProductType();
            productType.setMasterProductId(getString(dataRow.get(Constants.PRODUCT_MASTER_ID_DATA_KEY)));
            productType.setProductId(getString(dataRow.get(Constants.PRODUCT_ID_DATA_KEY)));

            ProductCategoryMemberType productCategoryMemberType = factory.createProductCategoryMemberType();
            if (UtilValidate.isNotEmpty(dataRow.get(Constants.PRODUCT_CAT_COUNT_DATA_KEY)))
            {
                List categoryMemberList = productCategoryMemberType.getCategory();
                int cnt = (Integer) dataRow.get(Constants.PRODUCT_CAT_COUNT_DATA_KEY);
                StringBuffer categories =new StringBuffer();
                for (int i = 1; i <= cnt; i++)
                {
                    CategoryMemberType categoryMemberType = factory.createCategoryMemberType();
                    categoryMemberType.setCategoryId(getString(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_ID_DATA_KEY, UtilMisc.toMap("count", i)))));
                    categoryMemberType.setSequenceNum(getString(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_SEQ_NUM_DATA_KEY, UtilMisc.toMap("count", i)))));
                    categoryMemberType.setFromDate(formatDate(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_FROM_DATE_DATA_KEY, UtilMisc.toMap("count", i)))));
                    categoryMemberType.setThruDate(formatDate(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_CAT_THRU_DATE_DATA_KEY, UtilMisc.toMap("count", i)))));
                    categoryMemberList.add(categoryMemberType);
                }
            }
            productType.setProductCategoryMember(productCategoryMemberType);

            productType.setInternalName(getString(dataRow.get(Constants.PRODUCT_INTERNAL_NAME_DATA_KEY)));
            productType.setProductName(getString(dataRow.get(Constants.PRODUCT_NAME_DATA_KEY)));
            productType.setSalesPitch(getString(dataRow.get(Constants.PRODUCT_SALES_PITCH_DATA_KEY)));
            productType.setLongDescription(getString(dataRow.get(Constants.PRODUCT_LONG_DESC_DATA_KEY)));
            productType.setSpecialInstructions(getString(dataRow.get(Constants.PRODUCT_SPCL_INS_DATA_KEY)));
            productType.setDeliveryInfo(getString(dataRow.get(Constants.PRODUCT_DELIVERY_INFO_DATA_KEY)));
            productType.setDirections(getString(dataRow.get(Constants.PRODUCT_DIRECTIONS_DATA_KEY)));
            productType.setTermsAndConds(getString(dataRow.get(Constants.PRODUCT_TERMS_COND_DATA_KEY)));
            productType.setIngredients(getString(dataRow.get(Constants.PRODUCT_INGREDIENTS_DATA_KEY)));
            productType.setWarnings(getString(dataRow.get(Constants.PRODUCT_WARNING_DATA_KEY)));
            productType.setPlpLabel(getString(dataRow.get(Constants.PRODUCT_PLP_LABEL_DATA_KEY)));
            productType.setPdpLabel(getString(dataRow.get(Constants.PRODUCT_PDP_LABEL_DATA_KEY)));
            productType.setProductHeight(formatBigDecimal(dataRow.get(Constants.PRODUCT_HEIGHT_DATA_KEY)));
            productType.setProductWidth(formatBigDecimal(dataRow.get(Constants.PRODUCT_WIDTH_DATA_KEY)));
            productType.setProductDepth(formatBigDecimal(dataRow.get(Constants.PRODUCT_DEPTH_DATA_KEY)));
            productType.setProductWeight(formatBigDecimal(dataRow.get(Constants.PRODUCT_WEIGHT_DATA_KEY)));
            productType.setReturnable(getString(dataRow.get(Constants.PRODUCT_RETURN_ABLE_DATA_KEY)));
            productType.setTaxable(getString(dataRow.get(Constants.PRODUCT_TAX_ABLE_DATA_KEY)));
            productType.setChargeShipping(getString(dataRow.get(Constants.PRODUCT_CHARGE_SHIP_DATA_KEY)));
            productType.setIntroDate(formatDate(dataRow.get(Constants.PRODUCT_INTRO_DATE_DATA_KEY)));
            productType.setDiscoDate(formatDate(dataRow.get(Constants.PRODUCT_DISCO_DATE_DATA_KEY)));
            productType.setManufacturerId(getString(dataRow.get(Constants.PRODUCT_MANUFACT_PARTY_ID_DATA_KEY)));

            ProductPriceType productPriceType = factory.createProductPriceType();
            ListPriceType listPrice = factory.createListPriceType();
            listPrice.setPrice(formatBigDecimal(dataRow.get(Constants.PRODUCT_LIST_PRICE_DATA_KEY)));
            listPrice.setCurrency(getString(dataRow.get(Constants.PRODUCT_LIST_PRICE_CUR_DATA_KEY)));
            listPrice.setFromDate(formatDate(dataRow.get(Constants.PRODUCT_LIST_PRICE_FROM_DATA_KEY)));
            listPrice.setThruDate(formatDate(dataRow.get(Constants.PRODUCT_LIST_PRICE_THRU_DATA_KEY)));
            productPriceType.setListPrice(listPrice);

            SalesPriceType salesPrice = factory.createSalesPriceType();
            salesPrice.setPrice(formatBigDecimal(dataRow.get(Constants.PRODUCT_DEFAULT_PRICE_DATA_KEY)));
            salesPrice.setCurrency(getString(dataRow.get(Constants.PRODUCT_DEFAULT_PRICE_CUR_DATA_KEY)));
            salesPrice.setFromDate(formatDate(dataRow.get(Constants.PRODUCT_DEFAULT_PRICE_FROM_DATA_KEY)));
            salesPrice.setThruDate(formatDate(dataRow.get(Constants.PRODUCT_DEFAULT_PRICE_THRU_DATA_KEY)));
            productPriceType.setSalesPrice(salesPrice);
            productType.setProductPrice(productPriceType);
            
            ProductSelectableFeatureType productSelectableFeatureType = factory.createProductSelectableFeatureType();
            List selectableFeaturesList = productSelectableFeatureType.getFeature();
            for (int i = 1; i <= 5; i++)
            {
                if (UtilValidate.isNotEmpty(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_TYPE_ID_DATA_KEY, UtilMisc.toMap("count", i)))))
                {
                    FeatureType selectableFeature = factory.createFeatureType();
                    selectableFeature.setFeatureId(getString(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_TYPE_ID_DATA_KEY, UtilMisc.toMap("count", i)))));
                    List valueList = selectableFeature.getValue();
                    valueList.add(getString(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_DESC_DATA_KEY, UtilMisc.toMap("count", i)))));
                    selectableFeature.setDescription(getString(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_TYPE_DESC_DATA_KEY, UtilMisc.toMap("count", i)))));
                    selectableFeature.setSequenceNum(getString(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_SEQ_NUM_DATA_KEY, UtilMisc.toMap("count", i)))));
                    selectableFeature.setFromDate(formatDate(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_FROM_DATA_KEY, UtilMisc.toMap("count", i)))));
                    selectableFeature.setThruDate(formatDate(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_SLCT_FEAT_THRU_DATA_KEY, UtilMisc.toMap("count", i)))));
                    selectableFeaturesList.add(selectableFeature);
                }
            }
            productType.setProductSelectableFeature(productSelectableFeatureType);

            ProductDescriptiveFeatureType productDescriptiveFeatureType = factory.createProductDescriptiveFeatureType();
            List descriptiveFeaturesList = productDescriptiveFeatureType.getFeature();
            for (int i = 1; i <= 5; i++)
            {
                if (UtilValidate.isNotEmpty(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_TYPE_ID_DATA_KEY, UtilMisc.toMap("count", i)))))
                {
                    FeatureType descriptiveFeature = factory.createFeatureType();
                    descriptiveFeature.setFeatureId(getString(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_TYPE_ID_DATA_KEY, UtilMisc.toMap("count", i)))));
                    List valueList = descriptiveFeature.getValue();
                    valueList.add(getString(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_DESC_DATA_KEY, UtilMisc.toMap("count", i)))));
                    descriptiveFeature.setDescription(getString(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_TYPE_DESC_DATA_KEY, UtilMisc.toMap("count", i)))));
                    descriptiveFeature.setSequenceNum(getString(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_SEQ_NUM_DATA_KEY, UtilMisc.toMap("count", i)))));
                    descriptiveFeature.setFromDate(formatDate(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_FROM_DATA_KEY, UtilMisc.toMap("count", i)))));
                    descriptiveFeature.setThruDate(formatDate(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_DESC_FEAT_THRU_DATA_KEY, UtilMisc.toMap("count", i)))));
                    descriptiveFeaturesList.add(descriptiveFeature);
                }
            }
            productType.setProductDescriptiveFeature(productDescriptiveFeatureType);

            ProductImageType productImage = factory.createProductImageType();
      
            PlpSwatchType plpSwatch = factory.createPlpSwatchType();
            plpSwatch.setUrl(getString(dataRow.get(Constants.PRODUCT_PLP_SWATCH_IMG_DATA_KEY)));
            plpSwatch.setThruDate(formatDate(dataRow.get(Constants.PRODUCT_PLP_SWATCH_IMG_THRU_DATA_KEY)));
            productImage.setPlpSwatch(plpSwatch);

            PdpSwatchType pdpSwatch = factory.createPdpSwatchType();
            pdpSwatch.setUrl(getString(dataRow.get(Constants.PRODUCT_PDP_SWATCH_IMG_DATA_KEY)));
            pdpSwatch.setThruDate(formatDate(dataRow.get(Constants.PRODUCT_PDP_SWATCH_IMG_THRU_DATA_KEY)));
            productImage.setPdpSwatch(pdpSwatch);

            PlpSmallImageType plpSmallImage = factory.createPlpSmallImageType();
            plpSmallImage.setUrl(getString(dataRow.get(Constants.PRODUCT_SMALL_IMG_DATA_KEY)));
            plpSmallImage.setThruDate(formatDate(dataRow.get(Constants.PRODUCT_SMALL_IMG_THRU_DATA_KEY)));
            productImage.setPlpSmallImage(plpSmallImage);

            PlpSmallAltImageType plpSmallAltImage = factory.createPlpSmallAltImageType();
            plpSmallAltImage.setUrl(getString(dataRow.get(Constants.PRODUCT_SMALL_IMG_ALT_DATA_KEY)));
            plpSmallAltImage.setThruDate(formatDate(dataRow.get(Constants.PRODUCT_SMALL_IMG_ALT_THRU_DATA_KEY)));
            productImage.setPlpSmallAltImage(plpSmallAltImage);

            PdpThumbnailImageType pdpThumbnailImage = factory.createPdpThumbnailImageType();
            pdpThumbnailImage.setUrl(getString(dataRow.get(Constants.PRODUCT_THUMB_IMG_DATA_KEY)));
            pdpThumbnailImage.setThruDate(formatDate(dataRow.get(Constants.PRODUCT_THUMB_IMG_THRU_DATA_KEY)));
            productImage.setPdpThumbnailImage(pdpThumbnailImage);

            PdpLargeImageType pdpLargeImage = factory.createPdpLargeImageType();
            pdpLargeImage.setUrl(getString(dataRow.get(Constants.PRODUCT_LARGE_IMG_DATA_KEY)));
            pdpLargeImage.setThruDate(formatDate(dataRow.get(Constants.PRODUCT_LARGE_IMG_THRU_DATA_KEY)));
            productImage.setPdpLargeImage(pdpLargeImage);

            PdpDetailImageType pdpDetailImage = factory.createPdpDetailImageType();
            pdpDetailImage.setUrl(getString(dataRow.get(Constants.PRODUCT_DETAIL_IMG_DATA_KEY)));
            pdpDetailImage.setThruDate(formatDate(dataRow.get(Constants.PRODUCT_DETAIL_IMG_THRU_DATA_KEY)));
            productImage.setPdpDetailImage(pdpDetailImage);
            
            PdpVideoType pdpVideo = factory.createPdpVideoType();
            pdpVideo.setUrl(getString(dataRow.get(Constants.PRODUCT_VIDEO_URL_DATA_KEY)));
            pdpVideo.setThruDate(formatDate(dataRow.get(Constants.PRODUCT_VIDEO_URL_THRU_DATA_KEY)));
            productImage.setPdpVideoImage(pdpVideo);

            PdpVideo360Type pdpVideo360 = factory.createPdpVideo360Type();
            pdpVideo360.setUrl(getString(dataRow.get(Constants.PRODUCT_VIDEO_360_URL_DATA_KEY)));
            pdpVideo360.setThruDate(formatDate(dataRow.get(Constants.PRODUCT_VIDEO_360_URL_THRU_DATA_KEY)));
            productImage.setPdpVideo360Image(pdpVideo360);

            PdpAlternateImageType pdpAlternateImage = factory.createPdpAlternateImageType();
            List pdpAdditionalImages = pdpAlternateImage.getPdpAdditionalImage();
            for (int i = 1; i <= 10; i++)
            {
                PdpAdditionalImageType pdpAdditionalImage = factory.createPdpAdditionalImageType();

                PdpAdditionalThumbImageType pdpAddtionalThumbImage = factory.createPdpAdditionalThumbImageType();
                pdpAddtionalThumbImage.setUrl(getString(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_ADDNL_IMG_DATA_KEY, UtilMisc.toMap("count", i)))));
                pdpAddtionalThumbImage.setThruDate(formatDate(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_ADDNL_IMG_THRU_DATA_KEY, UtilMisc.toMap("count", i)))));
                pdpAdditionalImage.setPdpAdditionalThumbImage(pdpAddtionalThumbImage);

                PdpAdditionalLargeImageType pdpAdditionalLargeImage = factory.createPdpAdditionalLargeImageType();
                pdpAdditionalLargeImage.setUrl(getString(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_LARGE_IMG_DATA_KEY, UtilMisc.toMap("count", i)))));
                pdpAdditionalLargeImage.setThruDate(formatDate(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_LARGE_IMG_THRU_DATA_KEY, UtilMisc.toMap("count", i)))));
                pdpAdditionalImage.setPdpAdditionalLargeImage(pdpAdditionalLargeImage);

                PdpAdditionalDetailImageType pdpAdditionalDetailImage = factory.createPdpAdditionalDetailImageType();
                pdpAdditionalDetailImage.setUrl(getString(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_DETAIL_IMG_DATA_KEY, UtilMisc.toMap("count", i)))));
                pdpAdditionalDetailImage.setThruDate(formatDate(dataRow.get(FlexibleStringExpander.expandString(Constants.PRODUCT_XTRA_DETAIL_IMG_THRU_DATA_KEY, UtilMisc.toMap("count", i)))));
                pdpAdditionalImage.setPdpAdditionalDetailImage(pdpAdditionalDetailImage);
      
                pdpAdditionalImages.add(pdpAdditionalImage);
            }
            productImage.setPdpAlternateImage(pdpAlternateImage);

            productType.setProductImage(productImage);

            GoodIdentificationType goodIdentificationType = factory.createGoodIdentificationType();
            goodIdentificationType.setSku(getString(dataRow.get(Constants.PRODUCT_SKU_DATA_KEY)));
            goodIdentificationType.setGoogleId(getString(dataRow.get(Constants.PRODUCT_GOOGLE_ID_DATA_KEY)));
            goodIdentificationType.setIsbn(getString(dataRow.get(Constants.PRODUCT_ISBN_DATA_KEY)));
            goodIdentificationType.setManuId(getString(dataRow.get(Constants.PRODUCT_MANUFACTURER_ID_NO_DATA_KEY)));
            productType.setProductGoodIdentification(goodIdentificationType);

            ProductInventoryType productInventory = factory.createProductInventoryType();
            productInventory.setBigfishInventoryTotal(getString(dataRow.get(Constants.PRODUCT_BF_INVENTORY_TOT_DATA_KEY)));
            productInventory.setBigfishInventoryWarehouse(getString(dataRow.get(Constants.PRODUCT_BF_INVENTORY_WHS_DATA_KEY)));
            productType.setProductInventory(productInventory);

            ProductAttributeType productAttribute = factory.createProductAttributeType();
            productAttribute.setPdpSelectMultiVariant(getString(dataRow.get(Constants.PRODUCT_MULTI_VARIANT_DATA_KEY)));
            productAttribute.setPdpCheckoutGiftMessage(getString(dataRow.get(Constants.PRODUCT_GIFT_MESSAGE_DATA_KEY)));
            productAttribute.setPdpQtyMin(getString(dataRow.get(Constants.PRODUCT_QTY_MIN_DATA_KEY)));
            productAttribute.setPdpQtyMax(getString(dataRow.get(Constants.PRODUCT_QTY_MAX_DATA_KEY)));
            productAttribute.setPdpQtyDefault(getString(dataRow.get(Constants.PRODUCT_QTY_DEFAULT_DATA_KEY)));
            productAttribute.setPdpInStoreOnly(getString(dataRow.get(Constants.PRODUCT_IN_STORE_ONLY_DATA_KEY)));
            productType.setProductAttribute(productAttribute);
            productList.add(productType);
        }
    }


    /**
     * Generate the product assoc XLS
     * @param excelSheet  Writable XLS Sheet object
     * @param dataRows  data rows
     */
    public static void generateProductAssocXLS(WritableSheet excelSheet, List<Map<String, Object>> dataRows) 
    {
        int iRowIdx=1;
        for (Map<String, Object> dataRow : dataRows) 
        {
            int iColIdx=0;
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_ASSOC_ID_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.PRODUCT_ASSOC_ID_TO_DATA_KEY), iColIdx++, iRowIdx);
            String assocType = (String) dataRow.get(Constants.PRODUCT_ASSOC_TYPE_DATA_KEY);
            if (assocType.equals("PRODUCT_COMPLEMENT"))
            {
                assocType = "COMPLEMENT";
            }
            else if (assocType.equals("PRODUCT_ACCESSORY"))
            {
                assocType = "ACCESSORY";
            }
            createWorkBookRow(excelSheet, assocType, iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, formatDate(dataRow.get(Constants.PRODUCT_ASSOC_FROM_DATA_KEY)), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, formatDate(dataRow.get(Constants.PRODUCT_ASSOC_THRU_DATA_KEY)), iColIdx++, iRowIdx);
            iRowIdx++;
        }
    }

    /**
     * Generate the product assoc XML
     * @param factory  JAXB object createtion factory object
     * @param productAssocList  JAXB object list
     * @param dataRows  data rows
     */
    public static void generateProductAssocXML(ObjectFactory factory, List productAssocList, List<Map<String, Object>> dataRows) 
    {
        for (Map<String, Object> dataRow : dataRows) 
        {
            AssociationType productAssocType = factory.createAssociationType();
            productAssocType.setMasterProductId(getString(dataRow.get(Constants.PRODUCT_ASSOC_ID_DATA_KEY)));
            productAssocType.setMasterProductIdTo(getString(dataRow.get(Constants.PRODUCT_ASSOC_ID_TO_DATA_KEY)));
            String assocType = (String) dataRow.get(Constants.PRODUCT_ASSOC_TYPE_DATA_KEY);
            if (assocType.equals("PRODUCT_COMPLEMENT"))
            {
                assocType = "COMPLEMENT";
            }
            else if (assocType.equals("PRODUCT_ACCESSORY"))
            {
                assocType = "ACCESSORY";
            }
            productAssocType.setProductAssocType(assocType);
            productAssocType.setFromDate(formatDate(dataRow.get(Constants.PRODUCT_ASSOC_FROM_DATA_KEY)));
            productAssocType.setThruDate(formatDate(dataRow.get(Constants.PRODUCT_ASSOC_THRU_DATA_KEY)));
            productAssocList.add(productAssocType);
        }
    }

    /**
     * Generate the facet group XLS
     * @param excelSheet  Writable XLS Sheet object
     * @param dataRows  data rows
     */
    public static void generateFacetGroupXLS(WritableSheet excelSheet, List<Map<String, Object>> dataRows) 
    {
        int iRowIdx=1;
        for (Map<String, Object> dataRow : dataRows) 
        {
            int iColIdx=0;
            createWorkBookRow(excelSheet, dataRow.get(Constants.FACET_GRP_ID_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.FACET_GRP_DESC_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.FACET_GRP_PROD_CAT_ID_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.FACET_GRP_SEQ_NUM_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.FACET_GRP_TOOLTIP_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.FACET_GRP_MIN_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.FACET_GRP_MAX_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, formatDate(dataRow.get(Constants.FACET_GRP_FROM_DATA_KEY)), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, formatDate(dataRow.get(Constants.FACET_GRP_THRU_DATA_KEY)), iColIdx++, iRowIdx);
            iRowIdx++;
        }
    }

    /**
     * Generate the facet group XML
     * @param factory  JAXB object createtion factory object
     * @param facetGroupList  JAXB object list
     * @param dataRows  data rows
     */
    public static void generateFacetGroupXML(ObjectFactory factory, List facetGroupList, List<Map<String, Object>> dataRows) 
    {
        for (Map<String, Object> dataRow : dataRows) 
        {
            FacetCatGroupType facetCatGroup = factory.createFacetCatGroupType();

            FacetGroupType facetGroup = factory.createFacetGroupType();
            facetGroup.setFacetGroupId(getString(dataRow.get(Constants.FACET_GRP_ID_DATA_KEY)));
            facetGroup.setDescription(getString(dataRow.get(Constants.FACET_GRP_DESC_DATA_KEY)));
            facetCatGroup.setFacetGroup(facetGroup);

            facetCatGroup.setProductCategoryId(getString(dataRow.get(Constants.FACET_GRP_PROD_CAT_ID_DATA_KEY)));
            facetCatGroup.setSequenceNum(getString(dataRow.get(Constants.FACET_GRP_SEQ_NUM_DATA_KEY)));
            facetCatGroup.setTooltip(getString(dataRow.get(Constants.FACET_GRP_TOOLTIP_DATA_KEY)));
            facetCatGroup.setMinDisplay(getString(dataRow.get(Constants.FACET_GRP_MIN_DATA_KEY)));
            facetCatGroup.setMaxDisplay(getString(dataRow.get(Constants.FACET_GRP_MAX_DATA_KEY)));
            facetCatGroup.setFromDate(formatDate(dataRow.get(Constants.FACET_GRP_FROM_DATA_KEY)));
            facetCatGroup.setThruDate(formatDate(dataRow.get(Constants.FACET_GRP_THRU_DATA_KEY)));
            facetGroupList.add(facetCatGroup);
        }
    }

    /**
     * Generate the facet value XLS
     * @param excelSheet  Writable XLS Sheet object
     * @param dataRows  data rows
     */
    public static void generateFacetValueXLS(WritableSheet excelSheet, List<Map<String, Object>> dataRows) 
    {
        int iRowIdx=1;
        for (Map<String, Object> dataRow : dataRows) 
        {
            int iColIdx=0;
            createWorkBookRow(excelSheet, dataRow.get(Constants.FACET_VAL_GRP_ID_DATA_KEY), iColIdx++, iRowIdx);
            if (UtilValidate.isNotEmpty(dataRow.get(Constants.FACET_VAL_FEAT_TYPE_ID_DATA_KEY)))
            {
                String feature = (String) dataRow.get(Constants.FACET_VAL_FEAT_TYPE_ID_DATA_KEY);
                feature = feature + ":" + dataRow.get(Constants.FACET_VAL_FEAT_DESC_DATA_KEY);
                createWorkBookRow(excelSheet, feature, iColIdx++, iRowIdx);
            }
            else
            {
                createWorkBookRow(excelSheet, "", iColIdx++, iRowIdx);
            }
            createWorkBookRow(excelSheet, dataRow.get(Constants.FACET_VAL_FEAT_DESC_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.FACET_VAL_SEQ_NUM_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.FACET_VAL_PLP_SWATCH_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.FACET_VAL_PDP_SWATCH_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, formatDate(dataRow.get(Constants.FACET_VAL_FROM_DATA_KEY)), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, formatDate(dataRow.get(Constants.FACET_VAL_THRU_DATA_KEY)), iColIdx++, iRowIdx);
            iRowIdx++;
        }
    }

    /**
     * Generate the facet value XML
     * @param factory  JAXB object createtion factory object
     * @param facetValueList  JAXB object list
     * @param dataRows  data rows
     */
    public static void generateFacetValueXML(ObjectFactory factory, List facetValueList, List<Map<String, Object>> dataRows) 
    {
        for (Map<String, Object> dataRow : dataRows) 
        {
            FacetValueType facetValue = factory.createFacetValueType();

            facetValue.setProductFeatureGroupId(getString(dataRow.get(Constants.FACET_VAL_GRP_ID_DATA_KEY)));
            if (UtilValidate.isNotEmpty(dataRow.get(Constants.FACET_VAL_FEAT_TYPE_ID_DATA_KEY)))
            {
                String feature = (String) dataRow.get(Constants.FACET_VAL_FEAT_TYPE_ID_DATA_KEY);
                feature = feature + ":" + dataRow.get(Constants.FACET_VAL_FEAT_DESC_DATA_KEY);
                facetValue.setProductFeatureId(feature);
            }
            else
            {
                facetValue.setProductFeatureId("");
            }
            facetValue.setDescription(getString(dataRow.get(Constants.FACET_VAL_FEAT_DESC_DATA_KEY)));
            facetValue.setSequenceNum(getString(dataRow.get(Constants.FACET_VAL_SEQ_NUM_DATA_KEY)));

            PlpSwatchType plpSwatch = factory.createPlpSwatchType();
            plpSwatch.setUrl(getString(dataRow.get(Constants.FACET_VAL_PLP_SWATCH_DATA_KEY)));
            facetValue.setPlpSwatch(plpSwatch);

            PdpSwatchType pdpSwatch = factory.createPdpSwatchType();
            pdpSwatch.setUrl(getString(dataRow.get(Constants.FACET_VAL_PDP_SWATCH_DATA_KEY)));
            facetValue.setPdpSwatch(pdpSwatch);

            facetValue.setFromDate(formatDate(dataRow.get(Constants.FACET_VAL_FROM_DATA_KEY)));
            facetValue.setThruDate(formatDate(dataRow.get(Constants.FACET_VAL_THRU_DATA_KEY)));

            facetValueList.add(facetValue);
        }
    }

    /**
     * Generate the manufacturer XLS
     * @param excelSheet  Writable XLS Sheet object
     * @param dataRows  data rows
     */
    public static void generateManufacturerXLS(WritableSheet excelSheet, List<Map<String, Object>> dataRows) 
    {
        int iRowIdx=1;
        for (Map<String, Object> dataRow : dataRows) 
        {
            int iColIdx=0;
            createWorkBookRow(excelSheet, dataRow.get(Constants.MANUFACTURER_ID_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.MANUFACTURER_NAME_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.MANUFACTURER_ADDR1_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.MANUFACTURER_CITY_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.MANUFACTURER_STATE_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.MANUFACTURER_ZIP_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.MANUFACTURER_COUNTRY_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.MANUFACTURER_SHORT_DESC_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.MANUFACTURER_LONG_DESC_DATA_KEY), iColIdx++, iRowIdx);
            createWorkBookRow(excelSheet, dataRow.get(Constants.MANUFACTURER_IMG_DATA_KEY), iColIdx++, iRowIdx);
            iRowIdx++;
        }
    }

    /**
     * Generate the manufacturer XML
     * @param factory  JAXB object createtion factory object
     * @param manufacturerList  JAXB object list
     * @param dataRows  data rows
     */
    public static void generateManufacturerXML(ObjectFactory factory, List manufacturerList, List<Map<String, Object>> dataRows) 
    {
        for (Map<String, Object> dataRow : dataRows) 
        {
            ManufacturerType manufacturerType = factory.createManufacturerType();
            manufacturerType.setManufacturerId(getString(dataRow.get(Constants.MANUFACTURER_ID_DATA_KEY)));
            manufacturerType.setManufacturerName(getString(dataRow.get(Constants.MANUFACTURER_NAME_DATA_KEY)));
            manufacturerType.setDescription(getString(dataRow.get(Constants.MANUFACTURER_SHORT_DESC_DATA_KEY)));
            manufacturerType.setLongDescription(getString(dataRow.get(Constants.MANUFACTURER_LONG_DESC_DATA_KEY)));

            ManufacturerImageType manufacturerImage = factory.createManufacturerImageType();
            manufacturerImage.setUrl(getString(dataRow.get(Constants.MANUFACTURER_IMG_DATA_KEY)));
            manufacturerImage.setThruDate(formatDate(dataRow.get(Constants.MANUFACTURER_IMG_THRU_DATA_KEY)));
            manufacturerType.setManufacturerImage(manufacturerImage);

            ManufacturerAddressType manufacturerAddress = factory.createManufacturerAddressType();
            manufacturerAddress.setAddress1(getString(dataRow.get(Constants.MANUFACTURER_ADDR1_DATA_KEY)));
            manufacturerAddress.setCityTown(getString(dataRow.get(Constants.MANUFACTURER_CITY_DATA_KEY)));
            manufacturerAddress.setStateProvince(getString(dataRow.get(Constants.MANUFACTURER_STATE_DATA_KEY)));
            manufacturerAddress.setZipPostCode(getString(dataRow.get(Constants.MANUFACTURER_ZIP_DATA_KEY)));
            manufacturerAddress.setCountry(getString(dataRow.get(Constants.MANUFACTURER_COUNTRY_DATA_KEY)));
            manufacturerType.setAddress(manufacturerAddress);

            manufacturerList.add(manufacturerType);
        }
    }
}
