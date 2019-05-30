package com.evernym.verity.sdk.protocols;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import com.evernym.verity.sdk.utils.VerityConfig;

import org.hyperledger.indy.sdk.IndyException;
import org.json.JSONArray;
import org.json.JSONObject;

public class Schema extends Protocol {

    // Message type definitions
    public static String WRITE_SCHEMA_MESSAGE_TYPE = "vs.service/schema/0.1/write";
    public static String PROBLEM_REPORT_MESSAGE_TYPE = "vs.service/schema/0.1/problem-report";
    public static String STATUS_MESSAGE_TYPE = "vs.service/schema/0.1/status";

    // Status Definitions
    public static Integer WRITE_SUCCESSFUL_STATUS = 0;

    private String name;
    private String version;
    private String[] attrs;

    /**
     * Creates a Schema from a list of attributes
     * @param attrs
     */
    public Schema(String name, String version, String ...attrs) {
        super();
        this.name = name;
        this.version = version;
        this.attrs = attrs;
    }

    @Override
    public String toString() {
        JSONObject message = new JSONObject();
        message.put("@type", Schema.WRITE_SCHEMA_MESSAGE_TYPE);
        message.put("@id", this.id);
        JSONObject schema = new JSONObject();
        schema.put("name", this.name);
        schema.put("version", this.version);
        schema.put("attrNames", new JSONArray(attrs));
        message.put("schema", schema);
        return message.toString();
    }

    /**
     * Sends the write request message to Verity
     * @param verityConfig an instance of VerityConfig configured with the results of the provision_sdk.py script
     * @throws IOException when the HTTP library fails to post to the agency endpoint
     * @throws InterruptedException when there are issues with encryption and decryption
     * @throws ExecutionException when there are issues with encryption and decryption
     * @throws IndyException when there are issues with encryption and decryption
     */
    public void write(VerityConfig verityConfig) throws IOException, InterruptedException, ExecutionException, IndyException {
        this.sendMessage(verityConfig);
    }

}