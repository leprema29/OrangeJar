/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cm.cirt.requisition.metier;

import cm.cirt.requisition.beans.OrangeBean;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.core.MediaType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

/**
 *
 * @author Harry Wanki
 */
public class Utils_Old {

    private HashMap<String, Integer> occurNumSms;
    private HashMap<String, Integer> occurNumAppel;
    private HashMap<String, Integer> dureeAppel;
    private HashMap<String, Integer> occurPosition;
    private List<OrangeBean> listingAppel;
    private List<OrangeBean> listingSms;
    private HashSet<String> allNumbers;

    private String appelEmisFile;
    private String smsFile;
    private String nbNumeroFile;
    private String nbSitesFile;
    private String identificationFile;
    private String identieNumeroFile;

    private String numero;
    private String dateRequisition;
    private String basePath;
    private String inputFile;

    public Utils_Old(String numero, String dateRequisition) {
        this.dureeAppel = new HashMap<>();
        this.occurNumAppel = new HashMap<>();
        this.occurNumSms = new HashMap<>();
        this.occurPosition = new HashMap<>();
        this.listingAppel = new ArrayList<>();
        this.listingSms = new ArrayList<>();
        this.allNumbers = new HashSet<>();
        this.numero = numero;
        this.dateRequisition = dateRequisition;

        this.basePath = "/root/" + dateRequisition + "/";
        this.appelEmisFile = this.basePath + this.numero + "/appelemis.txt";
        this.smsFile = this.basePath + this.numero + "/smsfinal.txt";
        this.nbNumeroFile = this.basePath + this.numero + "/NBnumero.txt";
        this.nbSitesFile = this.basePath + this.numero + "/NBSites.txt";
        this.identificationFile = this.basePath + this.numero + "/IdentificationAbonneesfinal.txt";
        this.identieNumeroFile = this.basePath + this.numero + "/identite_numero.txt";

        this.inputFile = this.basePath + this.numero + "/Listing_Orange_" + this.numero + ".txt";
    }

    public void analyseListing() {
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        NumberFormat n = NumberFormat.getIntegerInstance();
        try {
            File listingFile = new File(this.inputFile);
            LineIterator it = FileUtils.lineIterator(listingFile, "UTF-8");
            try {
                while (it.hasNext()) {
                    String line = it.nextLine();
                    System.out.println(line);
                    if (!line.startsWith("CALLDATE")) {
                        // do something with line
                        line = line.replace(",,", ",null,");
                        if (line.endsWith(",")) {
                            line = line + "null";
                        }
                        String[] tab = line.split(",");
                        OrangeBean cdr = new OrangeBean();
                        if ((!tab[0].equals("null")) && (!tab[0].equals(""))) {
                            cdr.setCallDate(df.parse(tab[0]));
                        }
                        if ((!tab[1].equals("null")) && (!tab[1].equals(""))) {
                            cdr.setCallDuration(Integer.parseInt(tab[1]));
                        }
                        cdr.setCallReference(tab[2]);
                        cdr.setCalledImsi(tab[3]);
                        cdr.setCalledNumber(tab[4]);
                        cdr.setCallingNumber(tab[5]);
                        cdr.setLocAreaCode(tab[6]);
                        cdr.setLocCellId(tab[7]);
                        cdr.setOrigination(tab[8]);
                        cdr.setRecordType(tab[9]);
                        cdr.setRoamingNumber(tab[10]);
                        cdr.setServedImei(tab[11]);
                        cdr.setServedImsi(tab[12]);
                        cdr.setServedMsisdn(tab[13]);
                        cdr.setLocalisaton(getLocalisationByCellId(tab[6] + tab[7], numero));
//                        cdr.setLocalisaton(tab[6] + tab[7]);

                        if ((getSanitisedNumber(numero).equals(getSanitisedNumber(cdr.getCalledNumber())))
                                || (getSanitisedNumber(numero).equals(getSanitisedNumber(cdr.getCallingNumber())))
                                || (getSanitisedNumber(numero).equals(getSanitisedNumber(cdr.getOrigination())))
                                || (getSanitisedNumber(numero).equals(getSanitisedNumber(cdr.getServedMsisdn())))) {
                            if (cdr.getRecordType().equals("0007")) {
                                cdr.setLocalisaton(getLocalisationByCellId(tab[6] + tab[7], getSanitisedNumber(cdr.getServedMsisdn())));
                                this.listingSms.add(cdr);
                                this.allNumbers.add(getSanitisedNumber(cdr.getOrigination()));
                                this.allNumbers.add(getSanitisedNumber(cdr.getServedMsisdn()));
                                if (!getSanitisedNumber(cdr.getOrigination()).equals(getSanitisedNumber(numero))) {
                                    if (this.occurNumSms.containsKey(cdr.getOrigination())) {
                                        this.occurNumSms.replace(cdr.getOrigination(), this.occurNumSms.get(cdr.getOrigination()) + 1);
                                    } else {
                                        this.occurNumSms.put(cdr.getOrigination(), 1);
                                    }
                                }
                                if (!getSanitisedNumber(cdr.getServedMsisdn()).equals(getSanitisedNumber(numero))) {
                                    if (this.occurNumSms.containsKey(cdr.getServedMsisdn())) {
                                        this.occurNumSms.replace(cdr.getServedMsisdn(), this.occurNumSms.get(cdr.getServedMsisdn()) + 1);
                                    } else {
                                        this.occurNumSms.put(cdr.getServedMsisdn(), 1);
                                    }
                                }
                                if (getSanitisedNumber(cdr.getOrigination()).equals(getSanitisedNumber(numero))) {
                                    if (this.occurPosition.containsKey(cdr.getLocalisaton())) {
                                        this.occurPosition.replace(cdr.getLocalisaton(), this.occurPosition.get(cdr.getLocalisaton()) + 1);
                                    } else {
                                        this.occurPosition.put(cdr.getLocalisaton(), 1);
                                    }
                                }
                            } else if (cdr.getRecordType().equals("0000")) {
                                cdr.setLocalisaton(getLocalisationByCellId(tab[6] + tab[7], getSanitisedNumber(cdr.getCallingNumber())));
                                this.listingAppel.add(cdr);
                                this.allNumbers.add(getSanitisedNumber(cdr.getCalledNumber()));
                                this.allNumbers.add(getSanitisedNumber(cdr.getCallingNumber()));
                                if (!getSanitisedNumber(cdr.getCalledNumber()).equals(getSanitisedNumber(numero))) {
                                    if (this.occurNumAppel.containsKey(cdr.getCalledNumber())) {
                                        this.occurNumAppel.replace(cdr.getCalledNumber(), this.occurNumAppel.get(cdr.getCalledNumber()) + 1);
                                    } else {
                                        this.occurNumAppel.put(cdr.getCalledNumber(), 1);
                                    }
//                            Duree appel
                                    if (this.dureeAppel.containsKey(cdr.getCalledNumber())) {
                                        this.dureeAppel.replace(cdr.getCalledNumber(), this.dureeAppel.get(cdr.getCalledNumber()) + cdr.getCallDuration());
                                    } else {
                                        this.dureeAppel.put(cdr.getCalledNumber(), cdr.getCallDuration());
                                    }

                                }
                                if (!getSanitisedNumber(cdr.getCallingNumber()).equals(getSanitisedNumber(numero))) {
                                    if (this.occurNumAppel.containsKey(cdr.getCallingNumber())) {
                                        this.occurNumAppel.replace(cdr.getCallingNumber(), this.occurNumAppel.get(cdr.getCallingNumber()) + 1);
                                    } else {
                                        this.occurNumAppel.put(cdr.getCallingNumber(), 1);
                                    }
//                            Duree appel
                                    if (this.dureeAppel.containsKey(cdr.getCallingNumber())) {
                                        this.dureeAppel.replace(cdr.getCallingNumber(), this.dureeAppel.get(cdr.getCallingNumber()) + cdr.getCallDuration());
                                    } else {
                                        this.dureeAppel.put(cdr.getCallingNumber(), cdr.getCallDuration());
                                    }
                                }

                                if (getSanitisedNumber(cdr.getCallingNumber()).equals(getSanitisedNumber(numero))) {
                                    if (this.occurPosition.containsKey(cdr.getLocalisaton())) {
                                        this.occurPosition.replace(cdr.getLocalisaton(), this.occurPosition.get(cdr.getLocalisaton()) + 1);
                                    } else {
                                        this.occurPosition.put(cdr.getLocalisaton(), 1);
                                    }
                                }

                            } else if (cdr.getRecordType().equals("0001")) {
                                if (getSanitisedNumber(cdr.getCalledNumber()).equals(getSanitisedNumber(numero))) {
                                    if (this.occurPosition.containsKey(cdr.getLocalisaton())) {
                                        this.occurPosition.replace(cdr.getLocalisaton(), this.occurPosition.get(cdr.getLocalisaton()) + 1);
                                    } else {
                                        this.occurPosition.put(cdr.getLocalisaton(), 1);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (ParseException ex) {
                Logger.getLogger(Utils_Old.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                LineIterator.closeQuietly(it);
            }
        } catch (IOException ex) {
            Logger.getLogger(Utils_Old.class.getName()).log(Level.SEVERE, null, ex);
        }

        try {
            File outputFile = new File(this.identieNumeroFile);
            FileWriter fw = new FileWriter(outputFile, true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw);
//            656026559|175677767|DEDEA AKI|1990-03-07|2022-06-04|EDEA-EDEA

//699362652,GAOJEAN  DOMINANT,1987-06-14,108904455,2019-11-06,BERTOUA-YAOUNDE
            String ident = getIdentificationByNumero(numero);
            if (!ident.equals("null")) {
                String[] tab = ident.split(",");
                String chaine = getSanitisedNumber(numero) + ","
                        + tab[2] + ","
                        + tab[0] + ","
                        + tab[1] + ","
                        + tab[3] + ","
                        + tab[4];
                out.println(chaine);
            } else {
                String chaine = getSanitisedNumber(numero) + ","
                        + "null,"
                        + "null,"
                        + "null,"
                        + "null,"
                        + "null";
                out.println(chaine);
            }

            out.close();
        } catch (Exception ex) {

        }

        try {
            File outputFile = new File(this.appelEmisFile);
            FileWriter fw = new FileWriter(outputFile, true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw);
            listingAppel.sort(Comparator.comparing(OrangeBean::getCallDate));
            Collections.reverse(listingAppel);
            for (OrangeBean cdr : listingAppel) {
//                String loc = getLocalisationByCellId(cdr.getLocalisaton(), numero);
                String loc = cdr.getLocalisaton();
                if (loc.equals("null")) {
                    loc = "null,null,null,null,null";
                }
                loc = cdr.getLocAreaCode() + cdr.getLocCellId() + "," + loc;
                String chaine = df.format(cdr.getCallDate()) + ","
                        + cdr.getCallDuration() + ","
                        + cdr.getCalledNumber() + ","
                        + cdr.getCallingNumber() + ","
                        + loc + ","
                        + cdr.getServedImei() + ","
                        + cdr.getOrigination() + ","
                        + cdr.getRecordType() + ","
                        + cdr.getRoamingNumber() + ","
                        + cdr.getServedMsisdn();
// 16/09/2018 20:17:25,90,237656069999,655981542,null,null,351690075835540,null,0000,690004000,237655981542
//                chaine = chaine.replace("null", "");
                out.println(chaine);
            }
            out.close();
        } catch (Exception ex) {

        }

        try {
            File outputFile = new File(this.smsFile);
            FileWriter fw = new FileWriter(outputFile, true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw);
            listingSms.sort(Comparator.comparing(OrangeBean::getCallDate));
            Collections.reverse(listingSms);
            for (OrangeBean cdr : listingSms) {
                String loc = cdr.getLocalisaton();
                if (loc.equals("null")) {
                    loc = "null,null,null,null,null";
                }
                loc = cdr.getLocAreaCode() + cdr.getLocCellId() + "," + loc;
                String chaine = df.format(cdr.getCallDate()) + ","
                        + cdr.getCallDuration() + ","
                        + cdr.getCalledNumber() + ","
                        + cdr.getCallingNumber() + ","
                        + cdr.getOrigination() + ","
                        + loc + ","
                        + cdr.getServedImei() + ","
                        + cdr.getRecordType() + ","
                        + cdr.getRoamingNumber() + ","
                        + cdr.getServedMsisdn();
//                chaine = chaine.replace("null", "");
                out.println(chaine);
            }
            out.close();
        } catch (Exception ex) {

        }

        try {
            File outputFile = new File(this.identificationFile);
            FileWriter fw = new FileWriter(outputFile, true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw);
            for (String num : allNumbers) {
                String ident = getIdentificationByNumero(num);
                if (!ident.equals("null")) {
                    String[] tab = ident.split(",");
                    String chaine = getSanitisedNumber(num) + ","
                            + tab[2] + ","
                            + tab[0] + ","
                            + tab[1] + ","
                            + tab[3] + ","
                            + tab[4];
                    out.println(chaine);
                }
            }
            out.close();
        } catch (Exception ex) {

        }

        try {
            File outputFile = new File(this.nbSitesFile);
            FileWriter fw = new FileWriter(outputFile, true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw);
            int nbreOccurNull = 0;
            System.out.println("Nombre Position " + this.occurPosition.size());
//            System.out.println(this.occurPosition.keySet());
//            this.occurPosition.forEach((key, value) -> out.println(key + ":" + value));
            for (String name : this.occurPosition.keySet()) {
                System.out.println(name);
                String key = name;
                String[] tab = key.split(",");
//                if (getLocalisationByCellId(key, numero).equals("null")) {
//                    nbreOccurNull = nbreOccurNull + this.occurPosition.get(key);
//                } else {
//                    String[] tab = getLocalisationByCellId(key, numero).split(",");
//                    String site = tab[0] + " " + tab[1];
//                    String chaine = n.format(this.occurPosition.get(key)) + "," + site;
//                    out.println(chaine);
//                }
                int value = this.occurPosition.get(name);
                String chaine = value + "," + tab[0] + " " + tab[1];
                chaine = chaine.replace("null", "Site inconnu");
                System.out.println(chaine);
                out.println(chaine);
            }
//            if (nbreOccurNull > 0) {
//                out.println(nbreOccurNull + ",Site inconnu");
//            }
            out.close();
        } catch (Exception ex) {

        }

        try {
            File outputFile = new File(this.nbNumeroFile);
            FileWriter fw = new FileWriter(outputFile, true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw);
            System.out.println("Nombre numero appel " + this.occurNumAppel.size());
//            this.occurPosition.forEach((key, value) -> out.println(key + ":" + value));
            for (String name : this.occurNumAppel.keySet()) {
                String key = name;
                int value1 = this.occurNumAppel.get(name);
                int value2 = this.dureeAppel.get(name);
                String ident = getIdentificationByNumero(key);
                String[] tab;
                if (!ident.equals("null")) {
                    tab = ident.split(",");
                    ident = tab[0];
                } else {
                    ident = "inconnu";
                }
                String chaine = value1 + "," + value2 + "," + key + " (" + ident + ")";
                System.out.println(chaine);
                out.println(chaine);
            }
            out.close();
        } catch (Exception ex) {

        }

        try {
            File outputFile = new File(this.nbNumeroFile);
            FileWriter fw = new FileWriter(outputFile, true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw);
            System.out.println("Nombre numero sms " + this.occurNumSms.size());
//            this.occurNumSms.forEach((key, value) -> System.out.println(key + ":" + value));
            for (String name : this.occurNumSms.keySet()) {
                String key = name;
                int value = this.occurNumSms.get(name);
                String ident = getIdentificationByNumero(key);
                String[] tab;
                if (!ident.equals("null")) {
                    tab = ident.split(",");
                    ident = tab[0];
                } else {
                    ident = "inconnu";
                }
                String chaine = value + ",," + key + " (" + ident + ")";
//                System.out.println(chaine);
                out.println(chaine);
            }
            out.close();
        } catch (Exception ex) {

        }
    }

    public String getLocalisationByCellId(String cellId, String numero) {
        String operator = getOperatorByTelephone(getSanitisedNumber(numero));
        String input = operator + "," + cellId;
//        System.out.println(input);
        String localisation = "null";
        try {
            Client client = Client.create();
            WebResource webResource = client
                    .resource("http://192.168.1.90:8080/MapServices/webresources/service/bts");
//            String input = "Mtn,679206565";//31006,3320831006
            ClientResponse response = webResource.type(MediaType.TEXT_PLAIN)
                    .post(ClientResponse.class, input);
            localisation = response.getEntity(String.class);

        } catch (Exception e) {
//            e.printStackTrace();
            localisation = "null";
        }
        return localisation.replace(";", ",");
    }

    public String getIdentificationByNumero(String numero) {
        numero = getSanitisedNumber(numero);
        String operator = getOperatorByTelephone(numero);
        String input = operator + "," + numero;
//        System.out.println(input);
        String identification = "null";
        try {
            Client client = Client.create();
            WebResource webResource = client
                    .resource("http://192.168.1.90:8080/MapServices/webresources/service/bdi");
//            String input = "Mtn,679206565";//31006,3320831006
            ClientResponse response = webResource.type(MediaType.TEXT_PLAIN)
                    .post(ClientResponse.class, input);
            identification = response.getEntity(String.class);

        } catch (Exception e) {
//            e.printStackTrace();
            identification = "null";
        }
        return identification.replace(";", ",");
    }

    public String getSanitisedNumber(String num) {
        if (num.startsWith("237")) {
            return num.substring(num.indexOf("237") + 3);
        }
        if (num.startsWith("00237")) {
            return num.substring(num.indexOf("00237") + 5);
        }
        if (num.startsWith("+237")) {
            return num.substring(num.indexOf("+237") + 4);
        }

        return num;
    }

    public String getOperatorByTelephone(String tel) {
        String op = "";
        if ((tel.startsWith("67")) || (tel.startsWith("650")) || (tel.startsWith("651")) || (tel.startsWith("652"))
                || (tel.startsWith("653")) || (tel.startsWith("654")) || (tel.startsWith("680")) || (tel.startsWith("681"))
                || (tel.startsWith("682")) || (tel.startsWith("683")) || (tel.startsWith("684"))) {
            op = "Mtn";
        } else if ((tel.startsWith("69")) || (tel.startsWith("655")) || (tel.startsWith("656")) || (tel.startsWith("657"))
                || (tel.startsWith("658")) || (tel.startsWith("659")) || (tel.startsWith("685")) || (tel.startsWith("686"))
                || (tel.startsWith("687")) || (tel.startsWith("688")) || (tel.startsWith("689"))) {
            op = "Orange";
        } else if (tel.startsWith("66")) {
            op = "Nexttel";
        } else if (tel.startsWith("64")) {
            op = "Nexttel";
        } else if (tel.startsWith("63")) {
            op = "Nexttel";
        } else if (tel.startsWith("62")) {
            op = "Nexttel";
        } else if (tel.startsWith("61")) {
            op = "Nexttel";
        } else if (tel.startsWith("60")) {
            op = "Nexttel";
        } else if (tel.startsWith("2")) {
            op = "Camtel";
        }
        return op;
    }
}
