/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cm.cirt.requisition.beans;

import java.util.Date;

/**
 *
 * @author Harry Wanki
 */
public class OrangeBean {

    private Date callDate;
    private int callDuration;
    private String callReference;
    private String calledImsi;
    private String calledNumber;
    private String callingNumber;
    private String locAreaCode;
    private String locCellId;
    private String origination;
    private String recordType;
    private String roamingNumber;
    private String servedImei;
    private String servedImsi;
    private String servedMsisdn;
    private String localisaton;

    public OrangeBean() {
    }

    public OrangeBean(Date callDate, int callDuration, String callReference, String calledImsi, String calledNumber, String callingNumber, String locAreaCode, String locCellId, String origination, String recordType, String roamingNumber, String servedImei, String servedImsi, String servedMsisdn, String localisaton) {
        this.callDate = callDate;
        this.callDuration = callDuration;
        this.callReference = callReference;
        this.calledImsi = calledImsi;
        this.calledNumber = calledNumber;
        this.callingNumber = callingNumber;
        this.locAreaCode = locAreaCode;
        this.locCellId = locCellId;
        this.origination = origination;
        this.recordType = recordType;
        this.roamingNumber = roamingNumber;
        this.servedImei = servedImei;
        this.servedImsi = servedImsi;
        this.servedMsisdn = servedMsisdn;
        this.localisaton = localisaton;
    }

    public Date getCallDate() {
        return callDate;
    }

    public void setCallDate(Date callDate) {
        this.callDate = callDate;
    }

    public int getCallDuration() {
        return callDuration;
    }

    public void setCallDuration(int callDuration) {
        this.callDuration = callDuration;
    }

    public String getCallReference() {
        return callReference;
    }

    public void setCallReference(String callReference) {
        this.callReference = callReference;
    }

    public String getCalledImsi() {
        return calledImsi;
    }

    public void setCalledImsi(String calledImsi) {
        this.calledImsi = calledImsi;
    }

    public String getCalledNumber() {
        return calledNumber;
    }

    public void setCalledNumber(String calledNumber) {
        this.calledNumber = calledNumber;
    }

    public String getCallingNumber() {
        return callingNumber;
    }

    public void setCallingNumber(String callingNumber) {
        this.callingNumber = callingNumber;
    }

    public String getLocAreaCode() {
        return locAreaCode;
    }

    public void setLocAreaCode(String locAreaCode) {
        this.locAreaCode = locAreaCode;
    }

    public String getLocCellId() {
        return locCellId;
    }

    public void setLocCellId(String locCellId) {
        this.locCellId = locCellId;
    }

    public String getOrigination() {
        return origination;
    }

    public void setOrigination(String origination) {
        this.origination = origination;
    }

    public String getRecordType() {
        return recordType;
    }

    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }

    public String getRoamingNumber() {
        return roamingNumber;
    }

    public void setRoamingNumber(String roamingNumber) {
        this.roamingNumber = roamingNumber;
    }

    public String getServedImei() {
        return servedImei;
    }

    public void setServedImei(String servedImei) {
        this.servedImei = servedImei;
    }

    public String getServedImsi() {
        return servedImsi;
    }

    public void setServedImsi(String servedImsi) {
        this.servedImsi = servedImsi;
    }

    public String getServedMsisdn() {
        return servedMsisdn;
    }

    public void setServedMsisdn(String servedMsisdn) {
        this.servedMsisdn = servedMsisdn;
    }

    public String getLocalisaton() {
        return localisaton;
    }

    public void setLocalisaton(String localisaton) {
        this.localisaton = localisaton;
    }
}
