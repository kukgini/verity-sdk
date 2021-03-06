package com.evernym.verity.sdk.protocols.relationship;

import com.evernym.verity.sdk.exceptions.VerityException;
import com.evernym.verity.sdk.protocols.Protocol;
import com.evernym.verity.sdk.protocols.relationship.v1_0.RelationshipV1_0;
import com.evernym.verity.sdk.utils.Context;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;

import static org.hyperledger.indy.sdk.StringUtils.isNullOrWhiteSpace;

class RelationshipImplV1_0 extends Protocol implements RelationshipV1_0 {
    final static String CREATE = "create";
    final static String CONNECTION_INVITATION = "connection-invitation";

    String forRelationship;
    String label;
    URL logoUrl = null;

    // flag if this instance started the interaction
    boolean created = false;

    RelationshipImplV1_0(String label) {
        if (!isNullOrWhiteSpace(label))
            this.label = label;
        else
            this.label = "";

        this.created = true;
    }

    RelationshipImplV1_0(String label, URL logoUrl) {
        if (!isNullOrWhiteSpace(label))
            this.label = label;
        else
            this.label = "";
        this.logoUrl = logoUrl;

        this.created = true;
    }

    RelationshipImplV1_0(String forRelationship, String threadId) {
        super(threadId);
        this.forRelationship = forRelationship;
    }

    @Override
    public JSONObject createMsg(Context context) {
        if(!created) {
            throw new IllegalArgumentException("Unable to create relationship when NOT starting the interaction");
        }

        JSONObject rtn = new JSONObject()
                .put("@type", getMessageType(CREATE))
                .put("@id", getNewId())
                .put("label", label);
        if (logoUrl != null)
            rtn.put("logoUrl", logoUrl.toString());
        addThread(rtn);
        return rtn;
    }

    @Override
    public void create(Context context) throws IOException, VerityException {
        send(context, createMsg(context));
    }

    @Override
    public byte[] createMsgPacked(Context context) throws VerityException {
        return packMsg(context, createMsg(context));
    }

    @Override
    public JSONObject connectionInvitationMsg(Context context) {
        JSONObject rtn = new JSONObject()
                .put("@type", getMessageType(CONNECTION_INVITATION))
                .put("@id", getNewId());

        if(!isNullOrWhiteSpace(forRelationship)) rtn.put("~for_relationship", forRelationship);

        addThread(rtn);
        return rtn;
    }

    @Override
    public void connectionInvitation(Context context) throws IOException, VerityException {
        send(context, connectionInvitationMsg(context));
    }

    @Override
    public byte[] connectionInvitationMsgPacked(Context context) throws VerityException {
        return packMsg(context, connectionInvitationMsg(context));
    }
}
