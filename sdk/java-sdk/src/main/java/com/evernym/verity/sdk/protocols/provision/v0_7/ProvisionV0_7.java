package com.evernym.verity.sdk.protocols.provision.v0_7;

import com.evernym.verity.sdk.exceptions.UndefinedContextException;
import com.evernym.verity.sdk.exceptions.WalletException;
import com.evernym.verity.sdk.protocols.MessageFamily;
import com.evernym.verity.sdk.utils.Context;
import com.evernym.verity.sdk.utils.Util;
import org.json.JSONObject;

import java.io.IOException;

public interface ProvisionV0_7 extends MessageFamily {
    default String qualifier() {return Util.EVERNYM_MSG_QUALIFIER;}
    default String family() {return "agent-provisioning";}
    default String version() {return "0.7";}

    // Messages
    String CREATE_EDGE_AGENT = "create-edge-agent";



    /**
     * Sends the connection create message to Verity
     * @param context an instance of Context configured with the results of the provision_sdk.py script
     * @throws IOException when the HTTP library fails to post to the agency endpoint
     * @throws WalletException when there are issues with encryption and decryption
     * @throws UndefinedContextException when the context don't have enough information for this operation
     * @return new Context with provisioned details
     */
    Context provision(Context context) throws IOException, UndefinedContextException, WalletException;
    JSONObject provisionMsg(Context context) throws UndefinedContextException;
    byte[] provisionMsgPacked(Context context) throws UndefinedContextException, WalletException;
}
