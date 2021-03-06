package com.evernym.verity.sdk.utils;

import com.evernym.verity.sdk.exceptions.UndefinedContextException;
import com.evernym.verity.sdk.exceptions.WalletException;
import com.evernym.verity.sdk.protocols.MessageFamily;
import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.crypto.Crypto;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutionException;

/**
 * Static helper functions used for packaging and unpackaging messages
 */
public class Util {
    public static final String EVERNYM_MSG_QUALIFIER = "did:sov:123456789abcdefghi1234";
    public static final String COMMUNITY_MSG_QUALIFIER = "did:sov:BzCbsNYhMrjHiqZDTUASHg";

    public static byte[] packMessageForVerity(Wallet walletHandle,
                                              JSONObject message,
                                              String pairwiseRemoteDID,
                                              String pairwiseRemoteVerkey,
                                              String pairwiseLocalVerkey,
                                              String publicVerkey
    ) throws WalletException {
        try {
            String pairwiseReceiver = new JSONArray(new String[]{pairwiseRemoteVerkey}).toString();
            String verityReceiver = new JSONArray(new String[]{publicVerkey}).toString();

            byte[] agentMessage = Crypto.packMessage(
                    walletHandle,
                    pairwiseReceiver,
                    pairwiseLocalVerkey,
                    message.toString().getBytes()
            ).get();

            String innerFwd = prepareForwardMessage(
                    pairwiseRemoteDID,
                    agentMessage
            );

            return Crypto.packMessage(
                    walletHandle,
                    verityReceiver,
                    null,
                    innerFwd.getBytes()
            ).get();
        } catch (IndyException | InterruptedException | ExecutionException e) {
            throw new WalletException("Unable to pack messages", e);
        }
    }

    /**
     * Encrypts a message for the Evernym verity. This function should not be called directly because it is called by the individual protocol classes.
     * @param context an instance of Context configured with the results of the provision_sdk.py script
     * @param message the message being sent
     * @return Encrypted message ready to be sent to the verity
     * @throws WalletException when there are issues with encryption and decryption
     * @throws UndefinedContextException when the context don't have enough information for this operation
     */
    public static byte[] packMessageForVerity(Context context, JSONObject message) throws UndefinedContextException, WalletException {
        Wallet handle = context.walletHandle();
        return packMessageForVerity(
                handle,
                message,
                context.domainDID(),
                context.verityAgentVerKey(),
                context.sdkVerKey(),
                context.verityPublicVerKey()
        );
    }

    /**
     * Builds a forward message
     * @param DID the DID the message is being forwarded to
     * @param message the raw bytes of the message being forwarded
     */
    private static String prepareForwardMessage(String DID, byte[] message) {
        JSONObject fwdMessage = new JSONObject();
        fwdMessage.put("@type", "did:sov:123456789abcdefghi1234;spec/routing/1.0/FWD");
        fwdMessage.put("@fwd", DID);
        fwdMessage.put("@msg", new JSONObject(new String(message)));
        return fwdMessage.toString();
    }

    /**
     * Unpacks a message received from the Evernym verity
     * @param context an instance of Context configured with the results of the provision_sdk.py script
     * @param message the message received from the Evernym verity
     * @return an unencrypted String message
     * @throws WalletException when there are issues with encryption and decryption
     */
    public static JSONObject unpackMessage(Context context, byte[] message) throws WalletException {
        try {
            byte[] jwe = Crypto.unpackMessage(context.walletHandle(), message).get();
            return new JSONObject(new JSONObject(new String(jwe)).getString("message"));
        }
        catch (IndyException | InterruptedException | ExecutionException e) {
            throw new WalletException("Unable to unpack message", e);
        }
    }

    /**
     * Unpack message forwarded message
     * @param context an instance of Context configured with the results of the provision_sdk.py script
     * @param message the message received from the Evernym verity
     * @return an unencrypted String message
     * @throws WalletException when there are issues with encryption and decryption
     */
    public static JSONObject unpackForwardMessage(Context context, byte[] message) throws WalletException {
        JSONObject unpackedOnceMessage = unpackMessage(context, message);
        byte[] unpackedOnceMessageMessage = unpackedOnceMessage.getJSONObject("@msg").toString().getBytes();
        return unpackMessage(context, unpackedOnceMessageMessage);
    }

    // FIXME move to MessageFamily interface
    public static String getMessageType(MessageFamily f, String msgName) {
        return getMessageType(f.qualifier(), f.family(), f.version(), msgName);
    }

    public static String getMessageType(String msgQualifier, String msgFamily, String msgFamilyVersion, String msgName) {
        return msgQualifier + ";spec/" + msgFamily + "/" + msgFamilyVersion + "/" + msgName;
    }

    public static String getProblemReportMessageType(String msgQualifier, String msgFamily, String msgFamilyVersion) {
        return Util.getMessageType(msgQualifier, msgFamily, msgFamilyVersion, "problem-report");
    }

    public static String getStatusMessageType(String msgQualifier, String msgFamily, String msgFamilyVersion) {
        return Util.getMessageType(msgQualifier, msgFamily, msgFamilyVersion, "status");
    }

    public static JSONObject truncateInviteDetails(String inviteDetails) {
        return truncateInviteDetails(new JSONObject(inviteDetails));
    }

    public static JSONObject truncateInviteDetails(JSONObject inviteDetails) {
        JSONObject truncatedInviteDetails = new JSONObject();
        truncatedInviteDetails.put("id", inviteDetails.getString("connReqId"));
        truncatedInviteDetails.put("sc", inviteDetails.getString("statusCode"));
        truncatedInviteDetails.put("sm", inviteDetails.getString("statusMsg"));
        truncatedInviteDetails.put("t", inviteDetails.getString("targetName"));
        truncatedInviteDetails.put("version", inviteDetails.getString("version"));
        if(inviteDetails.has("threadId")) {
            truncatedInviteDetails.put("threadId", inviteDetails.getString("threadId"));
        }
            JSONObject s = new JSONObject();
            JSONObject senderDetail = inviteDetails.getJSONObject("senderDetail");
            if(senderDetail.has("publicDID")) {
                s.put("publicDID", senderDetail.getString("publicDID"));
            }
            s.put("n", senderDetail.getString("name"));
            s.put("d", senderDetail.getString("DID"));
            s.put("l", senderDetail.getString("logoUrl"));
            s.put("v", senderDetail.getString("verKey"));
                JSONObject dp = new JSONObject();
                JSONObject dlgProof = senderDetail.getJSONObject("agentKeyDlgProof");
                dp.put("d", dlgProof.getString("agentDID"));
                dp.put("k", dlgProof.getString("agentDelegatedKey"));
                dp.put("s", dlgProof.getString("signature"));
            s.put("dp", dp);
        truncatedInviteDetails.put("s", s);
            JSONObject sa = new JSONObject();
            JSONObject senderAgencyDetail = inviteDetails.getJSONObject("senderAgencyDetail");
            sa.put("d", senderAgencyDetail.getString("DID"));
            sa.put("e", senderAgencyDetail.getString("endpoint"));
            sa.put("v", senderAgencyDetail.getString("verKey"));
        truncatedInviteDetails.put("sa", sa);

        return truncatedInviteDetails;
    }
}
