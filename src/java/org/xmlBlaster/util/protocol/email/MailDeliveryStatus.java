package org.xmlBlaster.util.protocol.email;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Handle received emails like "Undelivered Mail Returned to Sender"
 */
public class MailDeliveryStatus {

    public static final String MTA_INFO = "mtaInfo";
    public static final String MTA_ACTION = "mtaAction";
    public static final String MTA_STATUS = "mtaStatus";
    public static final String MTA_DIAGNOSTIC_CODE_STR = "mtaDiagnosticCodeStr";
    public static final String MTA_DIAGNOSTIC_CODE = "mtaDiagnosticCode";
    public static final String CONTENT_ERROR_MESSAGE = "contentErrorMessage";

    private final Map<String, String> attributes = new HashMap<>();

    // --- Generic Map Setter/Getters ---

    public void setAttribute(String key, String value) {
        attributes.put(key, value);
    }

    public String getAttribute(String key) {
        return attributes.get(key);
    }

    public boolean hasAttribute(String key) {
        String val = attributes.get(key);
        return val != null && !val.isEmpty();
    }

    public Map<String, String> getAllAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    // --- Convenience Getters/Setters ---

    public String getMtaInfo() {
        return getAttribute(MTA_INFO);
    }

    public void setMtaInfo(String value) {
        setAttribute(MTA_INFO, value);
    }

    public boolean hasMtaInfo() {
        return hasAttribute(MTA_INFO);
    }

    public String getMtaAction() {
        return getAttribute(MTA_ACTION);
    }

    public void setMtaAction(String value) {
        setAttribute(MTA_ACTION, value);
    }

    public boolean hasMtaAction() {
        return hasAttribute(MTA_ACTION);
    }

    public String getMtaStatus() {
        return getAttribute(MTA_STATUS);
    }

    public void setMtaStatus(String value) {
        setAttribute(MTA_STATUS, value);
    }

    public boolean hasMtaStatus() {
        return hasAttribute(MTA_STATUS);
    }

    public String getMtaDiagnosticCodeStr() {
        return getAttribute(MTA_DIAGNOSTIC_CODE_STR);
    }

    public void setMtaDiagnosticCodeStr(String value) {
        setAttribute(MTA_DIAGNOSTIC_CODE_STR, value);
    }

    public boolean hasMtaDiagnosticCodeStr() {
        return hasAttribute(MTA_DIAGNOSTIC_CODE_STR);
    }

    public int getMtaDiagnosticCode() {
        String value = getAttribute(MTA_DIAGNOSTIC_CODE);
        return value != null ? Integer.parseInt(value) : -1;
    }

    public void setMtaDiagnosticCode(int code) {
        setAttribute(MTA_DIAGNOSTIC_CODE, Integer.toString(code));
    }

    public String getContentErrorMessage() {
        return getAttribute(CONTENT_ERROR_MESSAGE);
    }

    public void setContentErrorMessage(String value) {
        setAttribute(CONTENT_ERROR_MESSAGE, value);
    }

    public boolean hasContentErrorMessage() {
        return hasAttribute(CONTENT_ERROR_MESSAGE);
    }

    // --- Status Helpers ---

    public boolean isOk() {
        int code = getMtaDiagnosticCode();
        return code >= 200 && code < 300;
    }

    public boolean isError() {
        int code = getMtaDiagnosticCode();
        return code >= 400 && code < 600;
    }

    @Override
    public String toString() {
        return "MailDeliveryStatus{" +
                "attributes=" + attributes +
                '}';
    }
}
