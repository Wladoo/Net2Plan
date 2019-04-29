package com.net2plan.oaas;

import com.net2plan.interfaces.networkDesign.Configuration;
import com.net2plan.interfaces.networkDesign.IAlgorithm;
import com.net2plan.interfaces.networkDesign.IReport;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.internal.IExternal;
import com.net2plan.utils.*;
import com.shc.easyjson.*;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;


/**
 * OaaS Resources
 */
@Path("/OaaS")
public class OaaSResources implements ServletContextListener
{
    private File TOMCAT_FILES_DIR = ServerUtils.TOMCAT_FILES_DIR;
    private File USERS_FILE = ServerUtils.USER_CONFIG_FILE;

    @Context
    HttpServletRequest webRequest;


    @POST
    @Path("/authenticate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    /**
     * Authenticates an user
     * @param authJSON authentication JSON
     * @return HTTP Response
     */
    public Response authenticateUser(String authJSON)
    {
        JSONObject json = new JSONObject();
        try {
            JSONObject auth = JSON.parse(authJSON);
            String username = auth.get("username").getValue();
            String password = auth.get("password").getValue();
            InputStream inputStream = new FileInputStream(USERS_FILE);
            XMLInputFactory2 xmlInputFactory = (XMLInputFactory2) XMLInputFactory2.newInstance();
            XMLStreamReader2 xmlStreamReader = (XMLStreamReader2) xmlInputFactory.createXMLStreamReader(inputStream);
            boolean found = false;
            while(xmlStreamReader.hasNext())
            {
                xmlStreamReader.next();
                switch(xmlStreamReader.getEventType())
                {
                    case XMLEvent.START_ELEMENT:
                        String startElementName = xmlStreamReader.getName().toString();
                        switch(startElementName)
                        {
                            case "user":
                                String user = xmlStreamReader.getAttributeValue(xmlStreamReader.getAttributeIndex(null, "name"));
                                String pass = xmlStreamReader.getAttributeValue(xmlStreamReader.getAttributeIndex(null, "password"));
                                //String category = xmlStreamReader.getAttributeValue(xmlStreamReader.getAttributeIndex(null, "category"));
                                if(username.equals(user) && password.equals(pass))
                                {
                                    String token = ServerUtils.addToken(user);
                                    json.put("message",new JSONValue("Authenticated"));
                                    json.put("token", new JSONValue(token));
                                    found = true;
                                }

                                break;

                        }
                }
                if(found)
                    break;
            }
        } catch (Exception e)
        {
            return ServerUtils.UNAUTHORIZED();
        }
        ServerUtils.readCatalog();
        return ServerUtils.OK(json);
    }

    @GET
    @Path("/catalogs")
    @Produces(MediaType.APPLICATION_JSON)
    /**
     * Obtains the list of available catalogs (URL: /OaaS/catalogs, Operation: GET, Produces: APPLICATION/JSON)
     * @return HTTP Response
     */
    public Response getCatalogs()
    {
        String token = webRequest.getHeader("token");
        if(!ServerUtils.authorizeUser(token))
            return ServerUtils.UNAUTHORIZED();

        JSONObject catalogsJSON = new JSONObject();
        JSONArray catalogsArray = new JSONArray();
        //readCatalog();
        for(Triple<String, List<IAlgorithm>, List<IReport>> catalogEntry : ServerUtils.catalogAlgorithmsAndReports)
        {
            JSONObject catalogJSON = ServerUtils.parseCatalog(catalogEntry);
            catalogsArray.add(new JSONValue(catalogJSON));
        }
        catalogsJSON.put("catalogs", new JSONValue(catalogsArray));



        return ServerUtils.OK(catalogsJSON);
    }



    @POST
    @Path("/catalogs")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    /**
     * Uploads a new catalog (JAR file) (URL: /OaaS/catalogs, Operation: POST, Consumes: MULTIPART FORM DATA (FORM NAME: file), Produces: APPLICATION/JSON)
     * @return HTTP Response
     */
    public Response uploadCatalog(@FormDataParam("file") byte [] input, @FormDataParam("file") FormDataContentDisposition fileMetaData)
    {
        //readCatalog();
        JSONObject json = new JSONObject();
        String token = webRequest.getHeader("token");
        if(!ServerUtils.authorizeUser(token))
            return ServerUtils.UNAUTHORIZED();

        if(!TOMCAT_FILES_DIR.exists())
            TOMCAT_FILES_DIR.mkdirs();

       // String catalogCategory = webRequest.getHeader("category");
        /*if(!catalogCategory.equalsIgnoreCase("INVITED") || !catalogCategory.equalsIgnoreCase("MASTER"))
        {
            json.put("message", new JSONValue("Unknown category -> "+catalogCategory));
            return ServerUtils.SERVER_ERROR(json);
        }*/


        String catalogName = fileMetaData.getFileName();
        List<String> catalogsNames = ServerUtils.getCatalogsNames();

        if(catalogsNames.contains(catalogName))
        {
            json.put("message", new JSONValue("Catalog "+catalogName+" exists"));
            return ServerUtils.SERVER_ERROR(json);
        }


        List<IAlgorithm> algorithms = new LinkedList<>();
        List<IReport> reports = new LinkedList<>();

        File uploadedFile = new File(TOMCAT_FILES_DIR + File.separator + fileMetaData.getFileName());
        try
        {
            OutputStream out = new FileOutputStream(uploadedFile);
            out.write(input);
            out.flush();
            out.close();

            URLClassLoader cl = new URLClassLoader(new URL[]{uploadedFile.toURI().toURL()}, this.getClass().getClassLoader());
            List<Class<IExternal>> classes = ClassLoaderUtils.getClassesFromFile(uploadedFile, IExternal.class, cl);
            for(Class<IExternal> _class : classes)
            {
                IExternal ext = _class.newInstance();
                if(ext instanceof IAlgorithm)
                {
                    IAlgorithm alg = (IAlgorithm)ext;
                    algorithms.add(alg);
                }
                else if(ext instanceof IReport)
                {
                    IReport rep = (IReport)ext;
                    reports.add(rep);
                }
            }
            ServerUtils.readCatalog();
            ServerUtils.catalogAlgorithmsAndReports.add(Triple.unmodifiableOf(catalogName, algorithms, reports));

            ServerUtils.writeCatalog();
            ServerUtils.cleanFolder(TOMCAT_FILES_DIR, false);

            return ServerUtils.OK(null);

        } catch (IOException e)
        {
            json.put("message", new JSONValue(e.getMessage()));
            return ServerUtils.SERVER_ERROR(json);
        } catch (IllegalAccessException e)
        {
            json.put("message", new JSONValue(e.getMessage()));
            return ServerUtils.SERVER_ERROR(json);
        } catch (InstantiationException e)
        {
            json.put("message", new JSONValue(e.getMessage()));
            return ServerUtils.SERVER_ERROR(json);
        }
    }

    @GET
    @Path("/catalogs/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    /**
     * Obtains a catalog by its name (URL: /OaaS/catalogs/{catalogName}, Operation: GET, Produces: APPLICATION/JSON)
     * @return HTTP Response
     */
    public Response getCatalogByName(@PathParam("name") String catalogName)
    {
        String token = webRequest.getHeader("token");
        if(!ServerUtils.authorizeUser(token))
            return ServerUtils.UNAUTHORIZED();

        JSONObject catalogJSON = null;
        for(Triple<String, List<IAlgorithm>, List<IReport>> catalogEntry : ServerUtils.catalogAlgorithmsAndReports)
        {
            String catalog = catalogEntry.getFirst();
            if(catalog.equals(catalogName))
            {
                catalogJSON = ServerUtils.parseCatalog(catalogEntry);
                break;
            }
        }
        if(catalogJSON == null)
        {
            JSONObject json = new JSONObject();
            json.put("message", new JSONValue("Catalog "+catalogName+" not found"));
            return ServerUtils.NOT_FOUND(json);
        }

        return ServerUtils.OK(catalogJSON);
    }

    @GET
    @Path("/algorithms")
    @Produces(MediaType.APPLICATION_JSON)
    /**
     * Obtains the list of available algorithms (URL: /OaaS/algorithms, Operation: GET, Produces: APPLICATION/JSON)
     * @return HTTP Response
     */
    public Response getAlgorithms()
    {
        String token = webRequest.getHeader("token");
        if(!ServerUtils.authorizeUser(token))
            return ServerUtils.UNAUTHORIZED();

        JSONObject algorithmsJSON = new JSONObject();
        JSONArray algorithmsArray = new JSONArray();
        for(Triple<String, List<IAlgorithm>, List<IReport>> catalogEntry : ServerUtils.catalogAlgorithmsAndReports)
        {
            List<IAlgorithm> algs = catalogEntry.getSecond();
            for(IAlgorithm alg : algs)
            {
                JSONObject algorithmJSON = ServerUtils.parseAlgorithm(alg);
                algorithmsArray.add(new JSONValue(algorithmJSON));
            }
        }
        algorithmsJSON.put("algorithms",new JSONValue(algorithmsArray));

        return ServerUtils.OK(algorithmsJSON);
    }

    @GET
    @Path("/algorithms/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    /**
     * Obtains an algorithm by its name (URL: /OaaS/algorithms/{algorithmName}, Operation: GET, Produces: APPLICATION/JSON)
     * @return HTTP Response
     */
    public Response getAlgorithmByName(@PathParam("name") String algorithmName)
    {
        String token = webRequest.getHeader("token");
        if(!ServerUtils.authorizeUser(token))
            return ServerUtils.UNAUTHORIZED();

        JSONObject algorithmJSON = null;
        boolean found = false;
        for(Triple<String, List<IAlgorithm>, List<IReport>> catalogEntry : ServerUtils.catalogAlgorithmsAndReports)
        {
            List<IAlgorithm> algs = catalogEntry.getSecond();
            for(IAlgorithm alg : algs)
            {
                String algName = ServerUtils.getAlgorithmName(alg);
                if(algName.equals(algorithmName))
                {
                    algorithmJSON = ServerUtils.parseAlgorithm(alg);
                    found = true;
                }
            }
            if(found)
                break;
        }

        if(algorithmJSON == null)
        {
            JSONObject json = new JSONObject();
            json.put("message", new JSONValue("Algorithm "+algorithmName+" not found"));
            return ServerUtils.NOT_FOUND(json);
        }

        return ServerUtils.OK(algorithmJSON);
    }

    @GET
    @Path("/reports")
    @Produces(MediaType.APPLICATION_JSON)
    /**
     * Obtains the list of available reports (URL: /OaaS/reports, Operation: GET, Produces: APPLICATION/JSON)
     * @return HTTP Response
     */
    public Response getReports()
    {
        String token = webRequest.getHeader("token");
        if(!ServerUtils.authorizeUser(token))
            return ServerUtils.UNAUTHORIZED();

        JSONObject reportsJSON = new JSONObject();
        JSONArray reportsArray = new JSONArray();
        for(Triple<String, List<IAlgorithm>, List<IReport>> catalogEntry : ServerUtils.catalogAlgorithmsAndReports)
        {
            List<IReport> reps = catalogEntry.getThird();
            for(IReport rep : reps)
            {
                JSONObject reportJSON = ServerUtils.parseReport(rep);
                reportsArray.add(new JSONValue(reportJSON));
            }
        }
        reportsJSON.put("reports",new JSONValue(reportsArray));

        return ServerUtils.OK(reportsJSON);
    }

    @GET
    @Path("/reports/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    /**
     * Obtains a report by its name (URL: /OaaS/reports/{reportName}, Operation: GET, Produces: APPLICATION/JSON)
     * @return HTTP Response
     */
    public Response getReportByName(@PathParam("name") String reportName)
    {
        String token = webRequest.getHeader("token");
        if(!ServerUtils.authorizeUser(token))
            return ServerUtils.UNAUTHORIZED();

        JSONObject reportJSON = null;
        boolean found = false;
        for(Triple<String, List<IAlgorithm>, List<IReport>> catalogEntry : ServerUtils.catalogAlgorithmsAndReports)
        {
            List<IReport> reps = catalogEntry.getThird();
            for(IReport rep : reps)
            {
                String repName = ServerUtils.getReportName(rep);
                if(repName.equals(reportName))
                {
                    reportJSON = ServerUtils.parseReport(rep);
                    found = true;
                }
            }
            if(found)
                break;
        }

        if(reportJSON == null)
        {
            JSONObject json = new JSONObject();
            json.put("message", new JSONValue("Report "+reportName+" not found"));
            return ServerUtils.NOT_FOUND(json);
        }

        return ServerUtils.OK(reportJSON);
    }

    @GET
    @Path("/results/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPersistenceFile(@PathParam("name") String algorithmName)
    {
        String token = webRequest.getHeader("token");
        if(!ServerUtils.authorizeUser(token))
            return ServerUtils.UNAUTHORIZED();

        JSONObject resultsJSON = FileManager.readPersistenceFile(algorithmName,ServerUtils.tokens.get(token));

        if(resultsJSON == null)
        {
            JSONObject json = new JSONObject();
            json.put("message", new JSONValue("Results of "+algorithmName+" not found"));
            return ServerUtils.NOT_FOUND(json);
        }

        return ServerUtils.OK(resultsJSON);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON,MediaType.TEXT_HTML})
    @Path("/execute")
    /**
     * Sends a request to execute an algorithm or a report
     * @param input input JSON Object. It has to be sent using a specific format:
     *              <ul>
     *                  <li type="square">type: ALGORITHM / REPORT</li>
     *                  <li type="square">name: name of the algorithm or report to execute.</li>
     *                  <li type="square">userparams: map defining the user's custom parameter values. (The have to be the same as the defined in the algorithm or report, if not, the execution will fail)</li>
     *                  <li type="square">netPlan: NetPlan design (JSON formatted) which the algorithm or report will be executed on.
     *                  To create this JSON representation, the method saveToJSON() in NetPlan class will help.</li>
     *              </ul>
     * @return HTTP Response
     */
    public Response execute(String input)
    {
        Response executeResponse = null;
        JSONObject errorJSON = new JSONObject();
        String response = "";
        JSONObject inputJSON = null;
        try {
            inputJSON = JSON.parse(input);
        } catch (ParseException e)
        {
            errorJSON.put("message", new JSONValue(e.getMessage()));
            return ServerUtils.SERVER_ERROR(errorJSON);
        }
        String type = inputJSON.get("type").getValue();
        String executeName = inputJSON.get("name").getValue();
        JSONArray userParams = inputJSON.get("userparams").getValue();
        JSONObject inputNetPlan = inputJSON.get("netPlan").getValue();

        String category = ServerUtils.getCategoryFromExecutionName(executeName);

        //System.out.println("CATEGORY"+ category);
        String token = webRequest.getHeader("token");
        if(!ServerUtils.authorizeUser(token))
            return ServerUtils.UNAUTHORIZED();

        NetPlan netPlan = new NetPlan(inputNetPlan);

        if(type.equalsIgnoreCase("ALGORITHM"))
        {
            boolean found = false;
            IAlgorithm algorithm = null;
            for(Triple<String, List<IAlgorithm>, List<IReport>> catalogEntry : ServerUtils.catalogAlgorithmsAndReports)
            {

                List<IAlgorithm> algs = catalogEntry.getSecond();
                for(IAlgorithm alg : algs)
                {
                    String algName = ServerUtils.getAlgorithmName(alg);
                    if(algName.equals(executeName))
                    {
                        algorithm = alg;
                        found = true;
                    }
                }
                if(found)
                    break;
            }

            if(algorithm == null)
            {
                errorJSON.put("message", new JSONValue("Algorithm "+executeName+" not found"));
                return ServerUtils.NOT_FOUND(errorJSON);
            }


            List<Triple<String, String, String>> algorithmParameters_raw = (algorithm.getParameters() == null) ? new LinkedList<>() : algorithm.getParameters();
            Map<String, String> algorithmParameters = new LinkedHashMap<>();
            for(Triple<String, String, String> t : algorithmParameters_raw)
            {
                String paramName = t.getFirst();
                String paramDefaultValue = t.getSecond();
                algorithmParameters.put(paramName, paramDefaultValue);
            }


            Map<String, String> userParametersMap = ServerUtils.parseParametersMap(userParams);
            if(userParametersMap != null)
            {
                for(Map.Entry<String, String> entry : userParametersMap.entrySet())
                {
                    String paramName = entry.getKey();
                    String userParamValue = entry.getValue();
                    if(algorithmParameters.containsKey(paramName))
                    {
                        String paramDefaultValue = algorithmParameters.get(paramName);
                        if(paramDefaultValue.startsWith("#select#"))
                        {
                            List<String> possibleValues = StringUtils.toList(StringUtils.split(paramDefaultValue.replace("#select# ","")));
                            if(possibleValues.contains(userParamValue))
                            {
                                algorithmParameters.put(paramName, userParamValue);
                            }
                            else{
                                errorJSON.put("message", new JSONValue("Parameter "+paramName+ " can't be set as "+userParamValue+". Its possible values are: "+possibleValues));
                                return ServerUtils.SERVER_ERROR(errorJSON);
                            }
                        }
                        else if(paramDefaultValue.startsWith("#boolean#"))
                        {
                            if(userParamValue.equals("true") || userParamValue.equals("false"))
                            {
                                algorithmParameters.put(paramName, userParamValue);
                            }
                            else{
                                errorJSON.put("message", new JSONValue("Parameter "+paramName+ " can't be set as "+userParamValue+". Its possible values are true or false"));
                                return ServerUtils.SERVER_ERROR(errorJSON);
                            }
                        }
                        else{
                            algorithmParameters.put(paramName, userParamValue);
                        }
                    }
                    else{
                        errorJSON.put("message", new JSONValue("Undefined parameter "+paramName+" for this algorithm: "+algorithm.getClass().getName()));
                        return ServerUtils.SERVER_ERROR(errorJSON);
                    }
                }
            }
            else{
                for(Map.Entry<String, String> entry : algorithmParameters.entrySet())
                {
                    String paramName = entry.getKey();
                    String paramDefaultValue = entry.getValue();
                    String paramValue = "";
                    if(paramDefaultValue.startsWith("#select#"))
                    {
                        paramValue = StringUtils.split(paramDefaultValue.replace("#select# ",""))[0];
                    }
                    else if(paramDefaultValue.startsWith("#boolean#"))
                    {
                        paramValue = paramDefaultValue.replace("#boolean# ","");
                    }
                    else{
                        paramValue = paramDefaultValue;
                    }

                    algorithmParameters.put(paramName, paramValue);
                }
            }

            List<Triple<String, String, String>> net2planParameters_raw = Configuration.getNet2PlanParameters();
            Map<String, String> net2planParameters = new LinkedHashMap<>();
            net2planParameters_raw.stream().forEach(t -> net2planParameters.put(t.getFirst(), t.getSecond()));

            try{
                response = algorithm.executeAlgorithm(netPlan, algorithmParameters, net2planParameters);
                FileManager.writePersistenceFile(response,executeName,ServerUtils.tokens.get(token));

            }catch(Exception e)
            {
                errorJSON.put("message", new JSONValue(e.getMessage()));
                return ServerUtils.SERVER_ERROR(errorJSON);
            }

            JSONObject responseJSON = new JSONObject();
            responseJSON.put("outputNetPlan", new JSONValue(netPlan.saveToJSON()));
            try {
                responseJSON.put("executeResponse", new JSONValue(JSON.parse(response)));
            } catch (ParseException e) {
                e.printStackTrace();
            }
            executeResponse = ServerUtils.OK(responseJSON);

        }
        else if(type.equalsIgnoreCase("REPORT"))
        {
            IReport report = null;
            boolean found = false;
            for(Triple<String, List<IAlgorithm>, List<IReport>> catalogEntry : ServerUtils.catalogAlgorithmsAndReports)
            {
                List<IReport> reps = catalogEntry.getThird();
                for(IReport rep : reps)
                {
                    String repName = ServerUtils.getReportName(rep);
                    if(repName.equals(executeName))
                    {
                        report = rep;
                        found = true;
                    }
                }
                if(found)
                    break;
            }
            if(report == null)
            {
                errorJSON.put("message", new JSONValue("Report "+executeName+" not found"));
                return ServerUtils.NOT_FOUND(errorJSON);
            }


            List<Triple<String, String, String>> reportParameters_raw = (report.getParameters() == null) ? new LinkedList<>() : report.getParameters();
            Map<String, String> reportParameters = new LinkedHashMap<>();
            for(Triple<String, String, String> t : reportParameters_raw)
            {
                String paramName = t.getFirst();
                String paramDefaultValue = t.getSecond();
                reportParameters.put(paramName, paramDefaultValue);
            }

            Map<String, String> userParametersMap = ServerUtils.parseParametersMap(userParams);
            if(userParametersMap != null)
            {
                for(Map.Entry<String, String> entry : userParametersMap.entrySet())
                {
                    String paramName = entry.getKey();
                    String userParamValue = entry.getValue();
                    if(reportParameters.containsKey(paramName))
                    {
                        String paramDefaultValue = reportParameters.get(paramName);
                        if(paramDefaultValue.startsWith("#select#"))
                        {
                            List<String> possibleValues = StringUtils.toList(StringUtils.split(paramDefaultValue.replace("#select# ","")));
                            if(possibleValues.contains(userParamValue))
                            {
                                reportParameters.put(paramName, userParamValue);
                            }
                            else{
                                errorJSON.put("message", new JSONValue("Parameter "+paramName+ " can't be set as "+userParamValue+". Its possible values are: "+possibleValues));
                                return ServerUtils.SERVER_ERROR(errorJSON);
                            }
                        }
                        else if(paramDefaultValue.startsWith("#boolean#"))
                        {
                            if(userParamValue.equals("true") || userParamValue.equals("false"))
                            {
                                reportParameters.put(paramName, userParamValue);
                            }
                            else{
                                errorJSON.put("message", new JSONValue("Parameter "+paramName+ " can't be set as "+userParamValue+". Its possible values are true or false"));
                                return ServerUtils.SERVER_ERROR(errorJSON);
                            }
                        }
                        else{
                            reportParameters.put(paramName, userParamValue);
                        }
                    }
                    else{
                        errorJSON.put("message", new JSONValue("Undefined parameter "+paramName+" for this report: "+report.getClass().getName()));
                        return ServerUtils.SERVER_ERROR(errorJSON);
                    }
                }
            }
            else{
                for(Map.Entry<String, String> entry : reportParameters.entrySet())
                {
                    String paramName = entry.getKey();
                    String paramDefaultValue = entry.getValue();
                    String paramValue = "";
                    if(paramDefaultValue.startsWith("#select#"))
                    {
                        paramValue = StringUtils.split(paramDefaultValue.replace("#select# ",""))[0];
                    }
                    else if(paramDefaultValue.startsWith("#boolean#"))
                    {
                        paramValue = paramDefaultValue.replace("#boolean# ","");
                    }
                    else{
                        paramValue = paramDefaultValue;
                    }

                    reportParameters.put(paramName, paramValue);
                }
            }

            List<Triple<String, String, String>> net2planParameters_raw = Configuration.getNet2PlanParameters();
            Map<String, String> net2planParameters = new LinkedHashMap<>();
            net2planParameters_raw.stream().forEach(t -> net2planParameters.put(t.getFirst(), t.getSecond()));

            try{
                response = report.executeReport(netPlan, reportParameters, net2planParameters);
            }catch(Exception e)
            {
                errorJSON.put("message", new JSONValue(e.getMessage()));
                return ServerUtils.SERVER_ERROR(errorJSON);
            }

            executeResponse = ServerUtils.HTML(response);
        }

        return executeResponse;
    }


    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        ServerUtils.readCatalog();

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                ServerUtils.tokens.clear();
            }
        }, 3600000 , 3600000 );
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {

    }
}
