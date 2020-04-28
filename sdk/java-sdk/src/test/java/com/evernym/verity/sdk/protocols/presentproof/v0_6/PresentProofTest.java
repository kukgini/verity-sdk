package com.evernym.verity.sdk.protocols.presentproof.v0_6;

import com.evernym.verity.sdk.TestHelpers;
import com.evernym.verity.sdk.exceptions.VerityException;
import com.evernym.verity.sdk.protocols.presentproof.PresentProof;
import com.evernym.verity.sdk.protocols.presentproof.common.Attribute;
import com.evernym.verity.sdk.protocols.presentproof.common.Restriction;
import com.evernym.verity.sdk.protocols.presentproof.common.RestrictionBuilder;
import com.evernym.verity.sdk.utils.Context;
import com.evernym.verity.sdk.utils.Util;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class PresentProofTest {

    private final String forRelationship = "...someDid...";
    private final String proofRequestName = "Name Check";
    private final JSONArray proofPredicates = getPredicates();
    private final JSONObject revocationInterval = getRevocationInterval();

    private final Restriction r1 = RestrictionBuilder
            .blank()
            .issuerDid("UOISDFOPUASOFIUSAF")
            .build();
    private final Attribute attr1 = PresentProofV0_6.attribute("age", r1);

    @Test
    public void testGetMessageType() {
        PresentProofV0_6 testProtocol = PresentProof.v0_6(forRelationship, "");
        String msgName = "msg name";
        assertEquals(Util.getMessageType(
                Util.EVERNYM_MSG_QUALIFIER,
                testProtocol.family(),
                testProtocol.version(),
                msgName
        ), testProtocol.getMessageType(msgName));
    }

    @Test
    public void testConstructorWithAttr() throws VerityException {
        Context context = TestHelpers.getContext();
        PresentProofV0_6 testProtocol = PresentProof.v0_6(forRelationship, proofRequestName, attr1);

        JSONObject msg = testProtocol.requestMsg(context);
        testRequestMsgMessages(msg);

        JSONObject msg2 = testProtocol.statusMsg(context);
        testStatusMsg(msg2);
    }

    private void testRequestMsgMessages(JSONObject requestMsg) {
        assertEquals(
                "did:sov:123456789abcdefghi1234;spec/present-proof/0.6/request",
                requestMsg.getString("@type")
        );
        assertNotNull(requestMsg.getString("@id"));
        assertNotNull(requestMsg.getJSONObject("~thread").getString("thid"));
        assertEquals(forRelationship, requestMsg.getString("~for_relationship"));
        assertEquals(proofRequestName, requestMsg.getString("name"));
        assertEquals(attr1.toJson().toString(), requestMsg.getJSONArray("proofAttrs").get(0).toString());
    }

    private void testStatusMsg(JSONObject statusMsg) {
        assertEquals(
                "did:sov:123456789abcdefghi1234;spec/present-proof/0.6/get-status",
                statusMsg.getString("@type"));
        assertNotNull(statusMsg.getString("@id"));
        assertNotNull(statusMsg.getJSONObject("~thread").getString("thid"));
        assertEquals(forRelationship, statusMsg.getString("~for_relationship"));
    }

    @Test
    public void testRequest() throws Exception {
        Context context = null;
        try {
            context = TestHelpers.getContext();
            PresentProofV0_6 presentProof = PresentProof.v0_6(forRelationship, proofRequestName, attr1);
            byte [] message = presentProof.requestMsgPacked(context);
            JSONObject unpackedMessage = Util.unpackForwardMessage(context, message);
            assertEquals(
                    "did:sov:123456789abcdefghi1234;spec/present-proof/0.6/request",
                    unpackedMessage.getString("@type")
            );
        } catch(Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            TestHelpers.cleanup(context);
        }
    }

    @Test
    public void testGetStatus() throws Exception {
        Context context = null;
        try {
            context = TestHelpers.getContext();
            PresentProofV0_6 testProtocol = PresentProof.v0_6(forRelationship, UUID.randomUUID().toString());
            byte [] message = testProtocol.statusMsgPacked(context);
            JSONObject unpackedMessage = Util.unpackForwardMessage(context, message);
            assertEquals(
                    "did:sov:123456789abcdefghi1234;spec/present-proof/0.6/get-status",
                    unpackedMessage.getString("@type")
            );
        } catch(Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            TestHelpers.cleanup(context);
        }
    }

    private JSONArray getPredicates() {
        JSONArray proofPredicates = new JSONArray();

        JSONObject predicate = new JSONObject();
        predicate.put("name", "age");
        predicate.put("p_type", "GT");
        predicate.put("p_value", 18);

        JSONArray restrictions = new JSONArray();
        JSONObject restriction = new JSONObject();
        restriction.put("issuer_did", "UOISDFOPUASOFIUSAF");
        restrictions.put(restriction);

        predicate.put("restrictions", restrictions);

        proofPredicates.put(predicate);
        return proofPredicates;
    }

    private JSONObject getRevocationInterval() {
        JSONObject interval = new JSONObject();

        interval.put("from", 1000);

        return interval;
    }
}