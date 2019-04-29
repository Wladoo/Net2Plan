package com.net2plan.oaas;



import com.net2plan.interfaces.networkDesign.IAlgorithm;
import com.net2plan.interfaces.networkDesign.IReport;
import com.net2plan.internal.SystemUtils;
import com.net2plan.internal.XMLUtils;
import com.net2plan.utils.Pair;
import com.net2plan.utils.Quadruple;
import com.net2plan.utils.StringUtils;
import com.net2plan.utils.Triple;
import com.shc.easyjson.JSON;
import com.shc.easyjson.JSONArray;
import com.shc.easyjson.JSONObject;
import com.shc.easyjson.JSONValue;
import org.apache.commons.io.FileUtils;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLOutputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.XMLStreamWriter2;

import javax.ws.rs.core.Response;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.util.*;


public class ServerUtils
{
    protected static List<Triple<String, List<IAlgorithm>, List<IReport>>> catalogAlgorithmsAndReports;
    protected static Map<String, String> tokens;

    static
    {
        catalogAlgorithmsAndReports = new LinkedList<>();
        tokens = new LinkedHashMap<>();
    }

    /**
     * Directory where uploaded files will be stored while they are being analyzed
     */
    protected final static File TOMCAT_FILES_DIR, USER_CONFIG_FILE, CATALOG_FILE;
    static
    {
        TOMCAT_FILES_DIR = new File(SystemUtils.getCurrentDir().getAbsolutePath() + File.separator + "tomcatFiles");
        if(!TOMCAT_FILES_DIR.exists())
            TOMCAT_FILES_DIR.mkdirs();
        USER_CONFIG_FILE = new File(TOMCAT_FILES_DIR.getAbsolutePath() + File.separator + "oaas_users.xml");
        CATALOG_FILE = new File(TOMCAT_FILES_DIR.getAbsolutePath() + File.separator + "catalog.n2p");
        if(!USER_CONFIG_FILE.exists())
        {
            try {
                boolean create = USER_CONFIG_FILE.createNewFile();
                if(create)
                    createDefaultUsersFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        if(!CATALOG_FILE.exists())
        {
            try {
                boolean create = CATALOG_FILE.createNewFile();
                if(create)
                    createDefaultCatalog();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
    private static void createDefaultCatalog(){
      writeCatalog();

    }

    private static void createDefaultUsersFile()
    {

        OutputStream output = null;
        try {
            output = new FileOutputStream(USER_CONFIG_FILE);
            XMLOutputFactory2 xmlOutputFactory = (XMLOutputFactory2) XMLOutputFactory.newInstance();
            XMLStreamWriter2 writer = (XMLStreamWriter2) xmlOutputFactory.createXMLStreamWriter(output);
            writer.writeStartDocument("UTF-8", "1.0");

            XMLUtils.indent(writer,0);
            writer.writeStartElement("users");

            //ROOT USER
            XMLUtils.indent(writer,1);
            writer.writeStartElement("user");
            writer.writeAttribute("name","root");
            writer.writeAttribute("password","root");
            //writer.writeAttribute("category","MASTER");
            writer.writeEndElement();

            writer.writeRaw("<!-- Copy this line to create more users -->");

            //ADMIN USER
            XMLUtils.indent(writer,1);
            writer.writeStartElement("user");
            writer.writeAttribute("name","admin");
            writer.writeAttribute("password","admin");
            //writer.writeAttribute("category","MASTER");
            writer.writeEndElement();

            XMLUtils.indent(writer,0);
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.flush();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (XMLStreamException e) {
            e.printStackTrace();
        }

    }
    public static synchronized void writeCatalog(){

        FileManagerJSON.writeCatalogPersistenceFile(catalogAlgorithmsAndReports);

    }
    public static synchronized void readCatalog(){
        catalogAlgorithmsAndReports.clear();
        catalogAlgorithmsAndReports.addAll( new FileManagerJSON().readCatalogPersistenceFile());
    }
    protected static synchronized String addToken(String user)
    {
        String src = user  + Math.random();
        byte [] srcEncoded = Base64.getEncoder().encode(src.getBytes());
        String token = new String(srcEncoded);
        tokens.put(token, user);
        return token;
    }

    /**
     * Checks if a token is valid or not
     * @param token token to check
     * @return true if token is valid, false if not
     */
    protected static synchronized boolean validateToken(String token)
    {
        return tokens.containsKey(token);
    }

    /**
     * Returns the information associated to a token (user and category)
     * @param token token to extract information
     * @return Pair object with its username and its category
     */
    protected static synchronized  String getInfoFromToken(String token)
    {
        return tokens.get(token);
    }

    /**
     * Creates a HTTP response 200, OK including a HTML formatted message
     * @param html HTML formatted message
     * @return HTTP response 200, OK
     */
    protected static Response HTML(String html)
    {
        if(html == null || html.isEmpty())
            return Response.ok().build();
        else
            return Response.ok().entity(html).build();
    }
    /**
     * Creates a HTTP response 200, OK including a response JSON
     * @param json JSON Object to return (null if no JSON is desired)
     * @return HTTP response 200, OK
     */
    protected static Response OK(JSONObject json)
    {
        if(json == null || json.size() == 0)
            return Response.ok().build();
        else
            return Response.ok(JSON.write(json)).build();
    }

    /**
     * Creates a HTTP response 403, UNAUTHORIZED
     * @return HTTP response 403, UNAUTHORIZED
     */
    protected static Response UNAUTHORIZED()
    {
        JSONObject json = new JSONObject();
        json.put("message", new JSONValue("Unauthorized"));
        return Response.status(Response.Status.UNAUTHORIZED).entity(JSON.write(json)).build();
    }

    /**
     * Creates a HTTP response 404, NOT FOUND including a response JSON
     * @param json JSON Object to return (null if no JSON is desired)
     * @return HTTP response 404, NOT FOUND
     */
    protected static Response NOT_FOUND(JSONObject json)
    {
        if(json == null || json.size() == 0)
            return Response.status(Response.Status.NOT_FOUND).build();

        return Response.status(Response.Status.NOT_FOUND).entity(JSON.write(json)).build();
    }

    /**
     * Creates a HTTP response 500, SERVER ERROR including a response JSON
     * @param json JSON Object to return (null if no JSON is desired)
     * @return HTTP response 500, SERVER ERROR
     */
    protected static Response SERVER_ERROR(JSONObject json)
    {
        if(json == null || json.size() == 0)
            return Response.serverError().build();
        else
            return Response.serverError().entity(JSON.write(json)).build();
    }

    /**
     * Deletes all files and directories inside a directory
     * @param folder directory to leave empty
     * @param deleteFolder true if folder will be deleted, false if not
     */
    protected static void cleanFolder(File folder, boolean deleteFolder)
    {
        if(folder.isDirectory())
            return;
        File [] files = folder.listFiles();
        if(files != null)
        {
            for(File f: files)
            {
                if(f.isDirectory())
                {
                    cleanFolder(f, true);
                } else {
                    f.delete();
                }
            }
        }
        if(deleteFolder)
            folder.delete();
    }

    /**
     * Obtains a JSON representation of an algorithm
     * @param alg algorithm to convert to JSON
     * @return JSON representation of the algorithm
     */
    protected static JSONObject parseAlgorithm(IAlgorithm alg)
    {
        JSONObject algorithmJSON = new JSONObject();
        String algName = getAlgorithmName(alg);
        String algDescription = (alg.getDescription() == null) ? "" : alg.getDescription().replaceAll("\"","");
        algorithmJSON.put("name", new JSONValue(algName));
        algorithmJSON.put("type", new JSONValue("algorithm"));
        algorithmJSON.put("description", new JSONValue(algDescription));
        JSONArray parametersArray = new JSONArray();
        if(alg.getParameters() != null)
        {
            for(Triple<String, String, String> param : alg.getParameters())
            {
                JSONObject parameter = new JSONObject();
                String paramName = (param.getFirst() == null) ? "" : param.getFirst();
                String paramDefaultValue = (param.getSecond() == null) ? "" : param.getSecond();
                String paramDescription = (param.getThird() == null) ? "" : param.getThird().replaceAll("\"","");
                parameter.put("name", new JSONValue(paramName));
                parameter.put("defaultValue", new JSONValue(paramDefaultValue));
                parameter.put("description", new JSONValue(paramDescription));
                parametersArray.add(new JSONValue(parameter));
            }
        }
        algorithmJSON.put("parameters", new JSONValue(parametersArray));
        return algorithmJSON;
    }

    /**
     * Obtains a JSON representation of a report
     * @param rep report to convert to JSON
     * @return JSON representation of the report
     */
    protected static JSONObject parseReport(IReport rep)
    {
        JSONObject reportJSON = new JSONObject();
        String repName = getReportName(rep);
        String repTitle = (rep.getTitle() == null) ? "" : rep.getTitle();
        String repDescription = (rep.getDescription() == null) ? "" : rep.getDescription().replaceAll("\"","");
        reportJSON.put("name", new JSONValue(repName));
        reportJSON.put("type", new JSONValue("report"));
        reportJSON.put("title", new JSONValue(repTitle));
        reportJSON.put("description", new JSONValue(repDescription));
        JSONArray parametersArray = new JSONArray();
        if(rep.getParameters() != null)
        {
            for(Triple<String, String, String> param : rep.getParameters())
            {
                JSONObject parameter = new JSONObject();
                String paramName = (param.getFirst() == null) ? "" : param.getFirst();
                String paramDefaultValue = (param.getSecond() == null) ? "" : param.getSecond();
                String paramDescription = (param.getThird() == null) ? "" : param.getThird().replaceAll("\"","");
                parameter.put("name", new JSONValue(paramName));
                parameter.put("defaultValue", new JSONValue(paramDefaultValue));
                parameter.put("description", new JSONValue(paramDescription));
                parametersArray.add(new JSONValue(parameter));
            }
        }
        reportJSON.put("parameters", new JSONValue(parametersArray));
        return reportJSON;
    }

    /**
     * Obtains the JSON representation of a catalog (JAR file)
     * @param catalogEntry Quadruple object with the name of the catalog, catalog's category, a list of its algorithms and a list of its reports
     * @return JSON representation of the catalog
     */
    protected static JSONObject parseCatalog(Triple<String, List<IAlgorithm>, List<IReport>> catalogEntry)
    {
        String catalogName = catalogEntry.getFirst();
        //String catalogCategory = catalogEntry.getSecond();
        List<IAlgorithm> algorithms = catalogEntry.getSecond();
        List<IReport> reports = catalogEntry.getThird();
        JSONObject catalogJSON = new JSONObject();
        JSONArray externalsArray = new JSONArray();
        for(IAlgorithm alg : algorithms)
        {
            JSONObject algJSON = parseAlgorithm(alg);
            externalsArray.add(new JSONValue(algJSON));
        }
        for(IReport rep : reports)
        {
            JSONObject repJSON = parseReport(rep);
            externalsArray.add(new JSONValue(repJSON));
        }
        catalogJSON.put("name", new JSONValue(catalogName));
        //catalogJSON.put("category", new JSONValue(catalogCategory));
        catalogJSON.put("files", new JSONValue(externalsArray));
        return catalogJSON;
    }

    /**
     * Converts a JSONArray of parameters in a Map<String, String>
     * @param paramJSON parameters in JSON format
     * @return Map<String, String> including the parameters
     */
    protected static Map<String, String> parseParametersMap(JSONArray paramJSON)
    {
        Map<String, String> params = new LinkedHashMap<>();
        if(paramJSON.size() == 0)
            return null;
        for(JSONValue json : paramJSON)
        {
            JSONObject this_json = json.getValue();
            params.put(this_json.get("name").getValue(), this_json.get("value").getValue());
        }

        return params;
    }


    /**
     * Obtains a list with the catalog's names
     * @return List of catalog's names
     */
    protected static List<String> getCatalogsNames()
    {
        List<String> catalogs = new LinkedList<>();
        for(Triple<String, List<IAlgorithm>, List<IReport>> entry : catalogAlgorithmsAndReports)
        {
            catalogs.add(entry.getFirst());
        }

        return catalogs;
    }

    /**
     * Obtains the algorithm name from an IAlgorithm representation
     * @param alg IAlgorithm representation
     * @return alg's name
     */
    protected static String getAlgorithmName(IAlgorithm alg)
    {
        try {
        String [] classSplitted = StringUtils.split(alg.getClass().getName(),".");
        int size = classSplitted.length;
        return classSplitted[size - 1];
        }catch(Exception e)
        {
         return "";
        }
    }

    /**
     * Obtains the report name from an IReport representation
     * @param rep IReport representation
     * @return rep's name
     */
    protected static String getReportName(IReport rep)
    {
        try {
            String[] classSplitted = StringUtils.split(rep.getClass().getName(), ".");
            int size = classSplitted.length;
            return classSplitted[size - 1];
        }catch(Exception e)
        {
            return "";
        }
    }

    /**
     * Obtains the category associated to an algorithm or a report
     * @param executeName algorithm or report name
     * @return category
     */
    protected static String getCategoryFromExecutionName(String executeName)
    {
        String category = "";
        /*boolean found = false;
        for(Quadruple<String, String, List<IAlgorithm>, List<IReport>> entry : catalogAlgorithmsAndReports)
        {
            List<IAlgorithm> algs = entry.getThird();
            for(IAlgorithm alg : algs)
            {
                String algName = getAlgorithmName(alg);
                if(executeName.equals(algName))
                {
                    found = true;
                    category = entry.getSecond();
                    break;
                }
            }
            if(found)
                break;
            List<IReport> reps = entry.getFourth();
            for(IReport rep : reps)
            {
                String repName = getReportName(rep);
                if(executeName.equals(repName))
                {
                    found = true;
                    category = entry.getSecond();
                    break;
                }
            }
            if(found)
                break;
        }
*/
        return category;
    }

    /**
     * Checks if the user is authorized to access the API functionalities
     * @param token user's token
     * @return true if the user is authorized, false if not
     */
    public static boolean authorizeUser(String token)
    {
        //String allowedCategory = (allowedCategoryOptional.length == 1) ? allowedCategoryOptional[0] : "INVITED";
        boolean allow = false;

        if(ServerUtils.validateToken(token))
        {
            allow=true;

            /*Pair<String, String> tokenInfo = ServerUtils.getInfoFromToken(token);
            String userCategory = tokenInfo.getSecond();

            if(allowedCategory.equalsIgnoreCase("INVITED"))
            {
                if(userCategory.equalsIgnoreCase("INVITED") || userCategory.equalsIgnoreCase("MASTER"))
                    allow = true;
                else
                    throw new RuntimeException("Unknown user category -> "+userCategory);
            }
            else if(allowedCategory.equalsIgnoreCase("MASTER"))
            {
                if(userCategory.equalsIgnoreCase("MASTER"))
                    allow = true;
                else if(userCategory.equalsIgnoreCase("INVITED"))
                    allow = false;
                else
                    throw new RuntimeException("Unknown user category -> "+userCategory);
            }

            else
                throw new RuntimeException("Unknown catalog category -> "+allowedCategory);*/
        }

        return allow;
    }



}
