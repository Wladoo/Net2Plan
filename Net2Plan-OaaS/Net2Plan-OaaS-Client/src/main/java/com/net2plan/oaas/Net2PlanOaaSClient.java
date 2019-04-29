package com.net2plan.oaas;

import com.net2plan.interfaces.networkDesign.Net2PlanException;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.shc.easyjson.*;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;

import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.Map;

public class Net2PlanOaaSClient
{
    private WebTarget target;
    private final int defaultPort = 8080;
    private String authToken;

    /**
     * Net2Plan OaaS Client constructor
     * @param mode Server mode (HTTP or HTTPS)
     * @param ipAddress IP Address where Net2Plan OaaS is running
     * @param optionalPort port where OaaS is running (default value: 8080)
     */
    public Net2PlanOaaSClient(ClientUtils.ClientMode mode, String ipAddress, int... optionalPort)
    {
        int port;
        if(optionalPort.length == 0)
            port = defaultPort;
        else if(optionalPort.length == 1)
            port = optionalPort[0];
        else
            throw new Net2PlanException("More than one port is not allowed");

        String baseURL = "";
        Client client = null;
        switch(mode)
        {
            case HTTP:
                client = ClientBuilder.newBuilder().build().register(MultiPartFeature.class);
                baseURL = "http://"+ipAddress+":"+port+"/net2plan-oaas-server-0.7.0-SNAPSHOT";
                break;

            case HTTPS:
                client = ClientUtils.createHTTPSClient();
                baseURL = "https://"+ipAddress+":"+port+"/net2plan-oaas-server-0.7.0-SNAPSHOT";
        }
        this.target = client.target(baseURL);
        this.authToken = "";
    }

    /**
     * Authenticates an user
     * @param user user name
     * @param pass user password
     * @return HTTP Response
     */
    public Response authenticateUser(String user, String pass)
    {
        JSONObject json = new JSONObject();
        json.put("username", new JSONValue(user));
        json.put("password", new JSONValue(pass));

        try {
        WebTarget this_target = target.path("/OaaS/authenticate");
        Invocation.Builder inv = this_target.request(MediaType.APPLICATION_JSON_TYPE).accept(MediaType.APPLICATION_JSON_TYPE);
        Response r = inv.post(Entity.entity(JSON.write(json), MediaType.APPLICATION_JSON_TYPE));

        String entity = r.readEntity(String.class);
        JSONObject entityJSON = JSON.parse(entity);
        JSONValue tokenValue = entityJSON.get("token");
        this.authToken = (tokenValue == null) ? "" : tokenValue.getValue();

        } catch (Exception e)
        {
            return Response.serverError().entity(e.getMessage()).build();
        }

        return Response.status(Response.Status.ACCEPTED).build();
    }

    /**
     * Obtains a list of all the available catalogs
     * @return HTTP Response
     */
    public Response getCatalogs()
    {
        WebTarget this_target = target.path("/OaaS/catalogs");
        Invocation.Builder inv = this_target.request().header("token",authToken).accept(MediaType.APPLICATION_JSON);
        Response r = inv.get();
        return r;
    }

    /**
     * Obtains the catalog represented by name {name}
     * @param name catalog´s name
     * @return HTTP Response
     */
    public Response getCatalogByName(String name)
    {
        WebTarget this_target = target.path("/OaaS/catalogs/"+name);
        Invocation.Builder inv = this_target.request().header("token",authToken).accept(MediaType.APPLICATION_JSON);
        Response r = inv.get();
        return r;
    }

    /**
     * Obtains a list of all the available algorithms
     * @return HTTP Response
     */
    public Response getAlgorithms()
    {
        WebTarget this_target = target.path("/OaaS/algorithms");
        Invocation.Builder inv = this_target.request().header("token",authToken).accept(MediaType.APPLICATION_JSON);
        Response r = inv.get();
        return r;
    }

    /**
     * Obtains the algorithm represented by name {name}
     * @param name algorithm's name
     * @return HTTP Response
     */
    public Response getAlgorithmByName(String name)
    {
        WebTarget this_target = target.path("/OaaS/algorithms/"+name);
        Invocation.Builder inv = this_target.request().header("token",authToken).accept(MediaType.APPLICATION_JSON);
        Response r = inv.get();
        return r;
    }

    /**
     * Obtains a list of all the available reports
     * @return HTTP Response
     */
    public Response getReports()
    {
        WebTarget this_target = target.path("/OaaS/reports");
        Invocation.Builder inv = this_target.request().header("token",authToken).accept(MediaType.APPLICATION_JSON);
        Response r = inv.get();
        return r;
    }

    /**
     * Obtains the report represented by name {name}
     * @param name report's name
     * @return HTTP Response
     */
    public Response getReportByName(String name)
    {
        WebTarget this_target = target.path("/OaaS/reports/"+name);
        Invocation.Builder inv = this_target.request().header("token",authToken).accept(MediaType.APPLICATION_JSON);
        Response r = inv.get();
        return r;
    }

    /**
     * Uploads a catalog (JAR file) including different algorithms and/or reports
     * @param catalogFile catalog (JAR file)
     * @param optionalCategory INVITED, MASTER
     * @return HTTP Response
     */
    public Response uploadCatalog(File catalogFile, ClientUtils.Category... optionalCategory)
    {
        ClientUtils.Category category = (optionalCategory.length == 1) ? optionalCategory[0] : ClientUtils.Category.INVITED;
        WebTarget this_target = target.path("/OaaS/catalogs");
        FileDataBodyPart body = new FileDataBodyPart("file",catalogFile);
        MultiPart multi = new MultiPart();
        multi.bodyPart(body);

        Invocation.Builder inv = this_target.request(MediaType.APPLICATION_JSON).header("token",authToken).header("category",category.toString());
        Response r = inv.post(Entity.entity(multi,MediaType.MULTIPART_FORM_DATA));

        return r;
    }

    public Response getPersistenceFile(String name)
    {
        WebTarget this_target = target.path("/OaaS/results/"+name);
        Invocation.Builder inv = this_target.request().header("token",authToken).accept(MediaType.APPLICATION_JSON);
        Response r = inv.get();
        return r;
    }

    /**
     * Sends an execution (algorithm or report) request to OaaS API
     * @param type execution type (ALGORITHM, REPORT)
     * @param name algorithm or report name to executeOperation
     * @param userParams Map including names and customized values of the execution parameters
     * @param netPlan input NetPlan
     * @return HTTP Response
     */
    public Response executeOperation(ClientUtils.ExecutionType type, String name, Map<String, String> userParams, NetPlan netPlan)
    {
        WebTarget this_target = target.path("/OaaS/execute");

        JSONObject json = new JSONObject();
        json.put("type",new JSONValue(type.toString()));
        json.put("name",new JSONValue(name));
        if(userParams == null || userParams.size() == 0)
            json.put("userparams", new JSONValue(new JSONArray()));
        else
        {
            JSONArray paramsArray = ClientUtils.parseUserParameters(userParams);
            json.put("userparams", new JSONValue(paramsArray));
        }

        JSONObject netPlanJSON = netPlan.saveToJSON();
        json.put("netPlan", new JSONValue(netPlanJSON));

        Invocation.Builder inv = this_target.request(MediaType.APPLICATION_JSON_TYPE).header("token",authToken).accept(MediaType.APPLICATION_JSON_TYPE);
        Response r = inv.post(Entity.entity(JSON.write(json), MediaType.APPLICATION_JSON_TYPE));

        return r;
    }
    public static void main(String [] args)
    {
        Net2PlanOaaSClient client = new Net2PlanOaaSClient(ClientUtils.ClientMode.HTTP, "localhost");
        client.authenticateUser("admin","admin");
        System.out.println(client.authToken);
        //System.out.println(client.getCatalogs().readEntity(String.class));
        System.out.println(client.uploadCatalog(new File("C:\\Users\\Manu\\Desktop\\Net2Plan\\AlgoritmosPruebas\\net2plan-algorithm-0.7.0-SNAPSHOT.jar"),ClientUtils.Category.MASTER).readEntity(String.class));
        System.out.println(client.getCatalogs().readEntity(String.class));
        client.executeOperation(ClientUtils.ExecutionType.ALGORITHM,"ExampleUsePersistence",null,new NetPlan());
        System.out.println(client.getPersistenceFile("ExampleUsePersistence").readEntity(String.class));
        client.executeOperation(ClientUtils.ExecutionType.ALGORITHM,"ExampleUsePersistence2",null,new NetPlan());
        System.out.println(client.getPersistenceFile("ExampleUsePersistence2").readEntity(String.class));
        client.uploadCatalog(new File("C:\\Users\\Manu\\Desktop\\Net2Plan\\AlgoritmosPruebas\\net2plan-algorithm-new-0.7.0-SNAPSHOT.jar"),ClientUtils.Category.MASTER).readEntity(String.class);
        System.out.println(client.getCatalogs().readEntity(String.class));
       //
       // client.uploadCatalog(new File("C:\\Users\\Manuel\\Desktop\\Net2Plan\\AlgoritmosPruebas\\net2plan-algorithm-0.7.0-SNAPSHOT.jar"),ClientUtils.Category.MASTER).readEntity(String.class);

        //
       /*
        /*System.out.println(client.getCatalogs().readEntity(String.class));
        client.uploadCatalog(new File("C:\\Users\\Manuel\\Desktop\\Net2Plan\\AlgoritmosPruebas\\net2plan-algorithm-0.7.0-SNAPSHOT.jar"),ClientUtils.Category.MASTER).readEntity(String.class);

        System.out.println(client.getCatalogs().readEntity(String.class));
        System.out.println(client.executeOperation(ClientUtils.ExecutionType.ALGORITHM,"ExampleUsePersistence2",null,new NetPlan()).readEntity(String.class));
       System.out.println(client.getPersistenceFile("ExampleUsePersistence2").readEntity(String.class));*/
    }
}
