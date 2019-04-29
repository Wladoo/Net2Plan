package com.net2plan.oaas;

import com.net2plan.interfaces.networkDesign.IAlgorithm;
import com.net2plan.interfaces.networkDesign.IReport;
import com.net2plan.internal.IExternal;
import com.net2plan.utils.ClassLoaderUtils;
import com.net2plan.utils.Quadruple;
import com.net2plan.utils.Triple;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class FileManagerJSON {

    public static void writeCatalogPersistenceFile(List<Triple<String, List<IAlgorithm>, List<IReport>>> entry){

        JSONObject catalog = new JSONObject();
        catalog.put("catalog",entry);

        try  {

            FileUtils.writeByteArrayToFile(ServerUtils.CATALOG_FILE, "".getBytes());
            FileUtils.writeByteArrayToFile(ServerUtils.CATALOG_FILE, catalog.toString().getBytes());

            }catch(Exception ex){

            ex.printStackTrace();
        }

    }
    public  List<Triple<String, List<IAlgorithm>, List<IReport>>> readCatalogPersistenceFile(){

        List<Triple<String, List<IAlgorithm>, List<IReport>>> entry = new ArrayList<>();

        try{

            byte [] bytes = Files.readAllBytes(ServerUtils.CATALOG_FILE.toPath());

            String everything = new String(bytes, StandardCharsets.UTF_8);
            JSONObject catalogJSON = new JSONObject(everything);
            JSONArray catalogs = catalogJSON.getJSONArray("catalog");

            for(Object obj: catalogs) {

                JSONObject catalog = (JSONObject) obj;

                String catalogName = catalog.getString("first");

                List<IAlgorithm> algorithms = new LinkedList<>();
                List<IReport> reports = new LinkedList<>();

                File uploadedFile = new File(ServerUtils.TOMCAT_FILES_DIR + File.separator + catalogName);

                URLClassLoader cl = new URLClassLoader(new URL[]{uploadedFile.toURI().toURL()}, this.getClass().getClassLoader());
                List<Class<IExternal>> classes = ClassLoaderUtils.getClassesFromFile(uploadedFile, IExternal.class, cl);
                for (Class<IExternal> _class : classes) {
                    IExternal ext = _class.newInstance();
                    if (ext instanceof IAlgorithm) {
                        IAlgorithm alg = (IAlgorithm) ext;
                        algorithms.add(alg);
                    } else if (ext instanceof IReport) {
                        IReport rep = (IReport) ext;
                        reports.add(rep);
                    }
                }


                entry.add(Triple.unmodifiableOf(catalogName, algorithms, reports));
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return entry;

    }
}
