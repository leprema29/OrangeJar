/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cm.cirt.requisition.metier;

import cm.cirt.requisition.beans.OrangeBean;
import cm.cirt.requisition.pojo.ImeiTotal;
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
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toMap;
import javax.ws.rs.core.MediaType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

/**
 *
 * @author Harry Wanki
 */
public class Utils {

    private HashMap<String, Integer> occurNumSms;
    private HashMap<String, Integer> occurNumAppel;
    private HashMap<String, Integer> dureeAppel;
    private HashMap<String, Integer> occurPosition;
    private HashMap<String, Integer> nombreMessage;
    private HashMap<String, Integer> imeiOccurrence;
    private HashMap<String, Date> imeiFirstUse;
    private HashMap<String, Date> imeiLastUse;

    private List<OrangeBean> listingAppel;
    private List<OrangeBean> listingSms;
    private HashSet<String> allNumbers;

    private ImeiTotal numeroTotal;
    private ImeiTotal numeroCorrespondant;

    private String appelEmisFile;
    private String smsFile;
    private String identificationFile;
    private String identieNumeroFile;
    private String frequenceCorrespondant;
    private String frequenceCellule;
    private String frequenceParDureeAppel;
    private String frequenceParImei;

    private String numero;
    private String dateRequisition;
    private String basePath;
    private String inputFile;

    public Utils(String numero, String dateRequisition) {
        this.dureeAppel = new HashMap<>();
        this.occurNumAppel = new HashMap<>();
        this.occurNumSms = new HashMap<>();
        this.occurPosition = new HashMap<>();
        this.nombreMessage = new HashMap<>();
        this.imeiFirstUse = new HashMap<>();
        this.imeiLastUse = new HashMap<>();
        this.imeiOccurrence = new HashMap<>();
        this.listingAppel = new ArrayList<>();
        this.listingSms = new ArrayList<>();
        this.allNumbers = new HashSet<>();
        this.numero = numero;
        this.dateRequisition = dateRequisition;

        this.numeroTotal = new ImeiTotal();
        this.numeroCorrespondant = new ImeiTotal();

        this.basePath = "/root/" + dateRequisition + "/"; // pour l'ARMP
//        this.basePath = "/home/data/orange/operations/cdr/" + dateRequisition + "/"; // pour le SED
        this.appelEmisFile = this.basePath + this.numero + "/appelemis.txt";
        this.smsFile = this.basePath + this.numero + "/smsfinal.txt";
        this.frequenceCorrespondant = this.basePath + this.numero + "/frequenceCorrespondance.txt";
        this.frequenceCellule = this.basePath + this.numero + "/frequenceCellule.txt";
        this.frequenceParDureeAppel = this.basePath + this.numero + "/frequenceDureeAppel.txt";
        this.frequenceParImei = this.basePath + this.numero + "/frequenceImei.txt";
        this.identificationFile = this.basePath + this.numero + "/IdentificationAbonneesfinal.txt";
        this.identieNumeroFile = this.basePath + this.numero + "/identite_numero.txt";

        this.inputFile = this.basePath + this.numero + "/Listing_Orange_" + this.numero + ".txt";
    }

    public void analyseListing() {
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        SimpleDateFormat df2 = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        NumberFormat n = NumberFormat.getIntegerInstance();
        try {
            File listingFile = new File(this.inputFile);
            LineIterator it = FileUtils.lineIterator(listingFile, "UTF-8");
            try {
                while (it.hasNext()) {
                    String line = it.nextLine();
//                    System.out.println(line);
                    if (!line.startsWith("CALLDATE")) {
                        // do something with line
                        line = line.replace(";", ",");
                        line = line.replace(",,", ",null,");
                        line = line.replace(",,", ",null,");
                        line = line.replace(",,", ",null,");
                        line = line.replace(",,", ",null,");
                        line = line.replace(",,", ",null,");
                        if (line.endsWith(",")) {
                            line = line + "null";
                        }
                        String[] tab = line.split(",");
                        if (tab.length >= 14) {
                            OrangeBean cdr = new OrangeBean();
                            if ((!tab[0].equals("null")) && (!tab[0].equals(""))) {
                                cdr.setCallDate(df.parse(tab[0]));
                            }
//                            System.out.println(line);
                            if (tab[9].equals("0000")) {
                                if ((!tab[1].equals("null")) && (!tab[1].equals(""))) {
                                    cdr.setCallDuration(Integer.parseInt(tab[1]));
                                } else {
                                    cdr.setCallDuration(-1);
                                }
                            } else if (tab[9].equals("0007")) {
                                cdr.setCallDuration(-1);
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

//                            b)	Fréquence par cellule 
                                    HashMap<String, Integer> totalCellule = this.numeroTotal.getTotal();
                                    if (totalCellule.containsKey(cdr.getLocalisaton())) {
                                        totalCellule.replace(cdr.getLocalisaton(), totalCellule.get(cdr.getLocalisaton()) + 1);
                                    } else {
                                        totalCellule.put(cdr.getLocalisaton(), 1);
                                    }
                                    this.numeroTotal.setTotal(totalCellule);

                                    Calendar calendar = Calendar.getInstance();
                                    calendar.setTime(cdr.getCallDate());
                                    int hour = calendar.get(Calendar.HOUR_OF_DAY);
                                    HashMap<String, Integer> totalHeureFixe;

//                            00h à 02h
                                    if ((hour >= 0) && (hour < 2)) {
                                        totalHeureFixe = this.numeroTotal.getTotalZeroDeux();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalZeroDeux(totalHeureFixe);
                                    } else if ((hour >= 2) && (hour < 4)) {
                                        totalHeureFixe = this.numeroTotal.getTotalDeuxQuatre();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalDeuxQuatre(totalHeureFixe);
                                    } else if ((hour >= 4) && (hour < 6)) {
                                        totalHeureFixe = this.numeroTotal.getTotalQuatreSix();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalQuatreSix(totalHeureFixe);
                                    } else if ((hour >= 6) && (hour < 8)) {
                                        totalHeureFixe = this.numeroTotal.getTotalSixHuit();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalSixHuit(totalHeureFixe);
                                    } else if ((hour >= 8) && (hour < 10)) {
                                        totalHeureFixe = this.numeroTotal.getTotalHuitDix();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalHuitDix(totalHeureFixe);
                                    } else if ((hour >= 10) && (hour < 12)) {
                                        totalHeureFixe = this.numeroTotal.getTotalDixDouze();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalDixDouze(totalHeureFixe);
                                    } else if ((hour >= 12) && (hour < 14)) {
                                        totalHeureFixe = this.numeroTotal.getTotalDouzeQuatorze();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalDouzeQuatorze(totalHeureFixe);
                                    } else if ((hour >= 14) && (hour < 16)) {
                                        totalHeureFixe = this.numeroTotal.getTotalQuatorzeSeize();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalQuatorzeSeize(totalHeureFixe);
                                    } else if ((hour >= 16) && (hour < 18)) {
                                        totalHeureFixe = this.numeroTotal.getTotalSeizeDixhuit();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalSeizeDixhuit(totalHeureFixe);
                                    } else if ((hour >= 18) && (hour < 20)) {
                                        totalHeureFixe = this.numeroTotal.getTotalDixhuitVingt();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalDixhuitVingt(totalHeureFixe);
                                    } else if ((hour >= 20) && (hour < 22)) {
                                        totalHeureFixe = this.numeroTotal.getTotalVingtVingtdeux();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalVingtVingtdeux(totalHeureFixe);
                                    } else if ((hour >= 22) && (hour < 24)) {
                                        totalHeureFixe = this.numeroTotal.getTotalVingtdeuxVingquatre();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalVingtdeuxVingquatre(totalHeureFixe);
                                    }

//                            c)	Fréquence par Correspondant
                                    HashMap<String, Integer> total = this.numeroCorrespondant.getTotal();
                                    HashMap<String, Integer> totalEntrant = this.numeroCorrespondant.getTotalEntrant();
                                    String emetteur = getSanitisedNumber(cdr.getOrigination());
                                    if (totalEntrant.containsKey(emetteur)) {
                                        totalEntrant.replace(emetteur, totalEntrant.get(emetteur) + 1);
                                    } else {
                                        totalEntrant.put(emetteur, 1);
                                    }

                                    if (total.containsKey(emetteur)) {
                                        total.replace(emetteur, total.get(emetteur) + 1);
                                    } else {
                                        total.put(emetteur, 1);
                                    }
                                    this.numeroCorrespondant.setTotalEntrant(totalEntrant);
                                    this.numeroCorrespondant.setTotal(total);

                                    HashMap<String, Integer> totalSortant = this.numeroCorrespondant.getTotalSortant();
                                    total = this.numeroCorrespondant.getTotal();
                                    String recepteur = getSanitisedNumber(cdr.getServedMsisdn());
                                    if (totalSortant.containsKey(recepteur)) {
                                        totalSortant.replace(recepteur, totalSortant.get(recepteur) + 1);
                                    } else {
                                        totalSortant.put(recepteur, 1);
                                    }

                                    if (total.containsKey(recepteur)) {
                                        total.replace(recepteur, total.get(recepteur) + 1);
                                    } else {
                                        total.put(recepteur, 1);
                                    }
                                    this.numeroCorrespondant.setTotalSortant(totalSortant);
                                    this.numeroCorrespondant.setTotal(total);

                                    String num = getSanitisedNumber(cdr.getOrigination());
                                    for (int j = 1; j < 3; j++) {
                                        if ((hour >= 0) && (hour < 2)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalZeroDeux();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalZeroDeux(totalHeureFixe);
                                        } else if ((hour >= 2) && (hour < 4)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalDeuxQuatre();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalDeuxQuatre(totalHeureFixe);
                                        } else if ((hour >= 4) && (hour < 6)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalQuatreSix();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalQuatreSix(totalHeureFixe);
                                        } else if ((hour >= 6) && (hour < 8)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalSixHuit();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalSixHuit(totalHeureFixe);
                                        } else if ((hour >= 8) && (hour < 10)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalHuitDix();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalHuitDix(totalHeureFixe);
                                        } else if ((hour >= 10) && (hour < 12)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalDixDouze();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalDixDouze(totalHeureFixe);
                                        } else if ((hour >= 12) && (hour < 14)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalDouzeQuatorze();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalDouzeQuatorze(totalHeureFixe);
                                        } else if ((hour >= 14) && (hour < 16)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalQuatorzeSeize();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalQuatorzeSeize(totalHeureFixe);
                                        } else if ((hour >= 16) && (hour < 18)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalSeizeDixhuit();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalSeizeDixhuit(totalHeureFixe);
                                        } else if ((hour >= 18) && (hour < 20)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalDixhuitVingt();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalDixhuitVingt(totalHeureFixe);
                                        } else if ((hour >= 20) && (hour < 22)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalVingtVingtdeux();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalVingtVingtdeux(totalHeureFixe);
                                        } else if ((hour >= 22) && (hour < 24)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalVingtdeuxVingquatre();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalVingtdeuxVingquatre(totalHeureFixe);
                                        }
                                        num = getSanitisedNumber(cdr.getServedMsisdn());
                                    }

//                            c)	Fréquence par durée d’appel
                                    if (getSanitisedNumber(cdr.getOrigination()).equals(getSanitisedNumber(numero))) {
                                        String sanitisedRecepteur = getSanitisedNumber(cdr.getServedMsisdn());
                                        if (cdr.getCallDuration() >= 0) {
                                            if (this.dureeAppel.containsKey(sanitisedRecepteur)) {
                                                this.dureeAppel.replace(sanitisedRecepteur, this.dureeAppel.get(sanitisedRecepteur) + cdr.getCallDuration());
                                            } else {
                                                this.dureeAppel.put(sanitisedRecepteur, cdr.getCallDuration());
                                            }
                                        } else {
                                            if (this.nombreMessage.containsKey(sanitisedRecepteur)) {
                                                this.nombreMessage.replace(sanitisedRecepteur, this.nombreMessage.get(sanitisedRecepteur) + 1);
                                            } else {
                                                this.nombreMessage.put(sanitisedRecepteur, 1);
                                            }
                                        }
//                                System.out.println("Taille duree appel: " + this.dureeAppel.size());
//                                System.out.println("Taille nombre message: " + this.nombreMessage.size());
                                    }

                                    if (getSanitisedNumber(cdr.getServedMsisdn()).equals(getSanitisedNumber(numero))) {
                                        String sanitisedEmetteur = getSanitisedNumber(cdr.getOrigination());
                                        if (cdr.getCallDuration() >= 0) {
                                            if (this.dureeAppel.containsKey(sanitisedEmetteur)) {
                                                this.dureeAppel.replace(sanitisedEmetteur, this.dureeAppel.get(sanitisedEmetteur) + cdr.getCallDuration());
                                            } else {
                                                this.dureeAppel.put(sanitisedEmetteur, cdr.getCallDuration());
                                            }
                                        } else {
                                            if (this.nombreMessage.containsKey(sanitisedEmetteur)) {
                                                this.nombreMessage.replace(sanitisedEmetteur, this.nombreMessage.get(sanitisedEmetteur) + 1);
                                            } else {
                                                this.nombreMessage.put(sanitisedEmetteur, 1);
                                            }
                                        }
                                    }

//                            d)	Fréquence par IMEI 
                                    if (getSanitisedNumber(cdr.getServedMsisdn()).equals(getSanitisedNumber(numero))) {
//                                System.out.println("This is the imei " + mtn.getImei());
                                        if (this.imeiOccurrence.containsKey(cdr.getServedImei())) {
                                            this.imeiOccurrence.replace(cdr.getServedImei(), this.imeiOccurrence.get(cdr.getServedImei()) + 1);
                                        } else {
                                            this.imeiOccurrence.put(cdr.getServedImei(), 1);
                                        }
                                        if (this.imeiFirstUse.containsKey(cdr.getServedImei())) {
                                            if (this.imeiFirstUse.get(cdr.getServedImei()).after(cdr.getCallDate())) {
                                                this.imeiFirstUse.replace(cdr.getServedImei(), cdr.getCallDate());
                                            }
                                        } else {
                                            this.imeiFirstUse.put(cdr.getServedImei(), cdr.getCallDate());
                                        }

                                        if (this.imeiLastUse.containsKey(cdr.getServedImei())) {
                                            if (this.imeiLastUse.get(cdr.getServedImei()).before(cdr.getCallDate())) {
                                                this.imeiLastUse.replace(cdr.getServedImei(), cdr.getCallDate());
                                            }
                                        } else {
                                            this.imeiLastUse.put(cdr.getServedImei(), cdr.getCallDate());
                                        }
                                    }

                                } else if (cdr.getRecordType().equals("0000")) {
                                    cdr.setLocalisaton(getLocalisationByCellId(tab[6] + tab[7], getSanitisedNumber(cdr.getCallingNumber())));
                                    this.listingAppel.add(cdr);
                                    this.allNumbers.add(getSanitisedNumber(cdr.getCalledNumber()));
                                    this.allNumbers.add(getSanitisedNumber(cdr.getCallingNumber()));

//                            b)	Fréquence par cellule 
                                    HashMap<String, Integer> totalCellule = this.numeroTotal.getTotal();
                                    if (totalCellule.containsKey(cdr.getLocalisaton())) {
                                        totalCellule.replace(cdr.getLocalisaton(), totalCellule.get(cdr.getLocalisaton()) + 1);
                                    } else {
                                        totalCellule.put(cdr.getLocalisaton(), 1);
                                    }
                                    this.numeroTotal.setTotal(totalCellule);

                                    Calendar calendar = Calendar.getInstance();
                                    calendar.setTime(cdr.getCallDate());
                                    int hour = calendar.get(Calendar.HOUR_OF_DAY);
                                    HashMap<String, Integer> totalHeureFixe;

//                            00h à 02h
                                    if ((hour >= 0) && (hour < 2)) {
                                        totalHeureFixe = this.numeroTotal.getTotalZeroDeux();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalZeroDeux(totalHeureFixe);
                                    } else if ((hour >= 2) && (hour < 4)) {
                                        totalHeureFixe = this.numeroTotal.getTotalDeuxQuatre();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalDeuxQuatre(totalHeureFixe);
                                    } else if ((hour >= 4) && (hour < 6)) {
                                        totalHeureFixe = this.numeroTotal.getTotalQuatreSix();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalQuatreSix(totalHeureFixe);
                                    } else if ((hour >= 6) && (hour < 8)) {
                                        totalHeureFixe = this.numeroTotal.getTotalSixHuit();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalSixHuit(totalHeureFixe);
                                    } else if ((hour >= 8) && (hour < 10)) {
                                        totalHeureFixe = this.numeroTotal.getTotalHuitDix();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalHuitDix(totalHeureFixe);
                                    } else if ((hour >= 10) && (hour < 12)) {
                                        totalHeureFixe = this.numeroTotal.getTotalDixDouze();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalDixDouze(totalHeureFixe);
                                    } else if ((hour >= 12) && (hour < 14)) {
                                        totalHeureFixe = this.numeroTotal.getTotalDouzeQuatorze();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalDouzeQuatorze(totalHeureFixe);
                                    } else if ((hour >= 14) && (hour < 16)) {
                                        totalHeureFixe = this.numeroTotal.getTotalQuatorzeSeize();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalQuatorzeSeize(totalHeureFixe);
                                    } else if ((hour >= 16) && (hour < 18)) {
                                        totalHeureFixe = this.numeroTotal.getTotalSeizeDixhuit();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalSeizeDixhuit(totalHeureFixe);
                                    } else if ((hour >= 18) && (hour < 20)) {
                                        totalHeureFixe = this.numeroTotal.getTotalDixhuitVingt();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalDixhuitVingt(totalHeureFixe);
                                    } else if ((hour >= 20) && (hour < 22)) {
                                        totalHeureFixe = this.numeroTotal.getTotalVingtVingtdeux();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalVingtVingtdeux(totalHeureFixe);
                                    } else if ((hour >= 22) && (hour < 24)) {
                                        totalHeureFixe = this.numeroTotal.getTotalVingtdeuxVingquatre();
                                        if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                            totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotalVingtdeuxVingquatre(totalHeureFixe);
                                    }

//                            c)	Fréquence par Correspondant
                                    HashMap<String, Integer> total = this.numeroCorrespondant.getTotal();
                                    HashMap<String, Integer> totalEntrant = this.numeroCorrespondant.getTotalEntrant();
                                    String emetteur = getSanitisedNumber(cdr.getCallingNumber());
                                    if (totalEntrant.containsKey(emetteur)) {
                                        totalEntrant.replace(emetteur, totalEntrant.get(emetteur) + 1);
                                    } else {
                                        totalEntrant.put(emetteur, 1);
                                    }

                                    if (total.containsKey(emetteur)) {
                                        total.replace(emetteur, total.get(emetteur) + 1);
                                    } else {
                                        total.put(emetteur, 1);
                                    }
                                    this.numeroCorrespondant.setTotalEntrant(totalEntrant);
                                    this.numeroCorrespondant.setTotal(total);

                                    HashMap<String, Integer> totalSortant = this.numeroCorrespondant.getTotalSortant();
                                    total = this.numeroCorrespondant.getTotal();
                                    String recepteur = getSanitisedNumber(cdr.getCalledNumber());
                                    if (totalSortant.containsKey(recepteur)) {
                                        totalSortant.replace(recepteur, totalSortant.get(recepteur) + 1);
                                    } else {
                                        totalSortant.put(recepteur, 1);
                                    }

                                    if (total.containsKey(recepteur)) {
                                        total.replace(recepteur, total.get(recepteur) + 1);
                                    } else {
                                        total.put(recepteur, 1);
                                    }
                                    this.numeroCorrespondant.setTotalSortant(totalSortant);
                                    this.numeroCorrespondant.setTotal(total);

                                    String num = getSanitisedNumber(cdr.getCallingNumber());
                                    for (int j = 1; j < 3; j++) {
                                        if ((hour >= 0) && (hour < 2)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalZeroDeux();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalZeroDeux(totalHeureFixe);
                                        } else if ((hour >= 2) && (hour < 4)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalDeuxQuatre();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalDeuxQuatre(totalHeureFixe);
                                        } else if ((hour >= 4) && (hour < 6)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalQuatreSix();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalQuatreSix(totalHeureFixe);
                                        } else if ((hour >= 6) && (hour < 8)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalSixHuit();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalSixHuit(totalHeureFixe);
                                        } else if ((hour >= 8) && (hour < 10)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalHuitDix();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalHuitDix(totalHeureFixe);
                                        } else if ((hour >= 10) && (hour < 12)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalDixDouze();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalDixDouze(totalHeureFixe);
                                        } else if ((hour >= 12) && (hour < 14)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalDouzeQuatorze();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalDouzeQuatorze(totalHeureFixe);
                                        } else if ((hour >= 14) && (hour < 16)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalQuatorzeSeize();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalQuatorzeSeize(totalHeureFixe);
                                        } else if ((hour >= 16) && (hour < 18)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalSeizeDixhuit();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalSeizeDixhuit(totalHeureFixe);
                                        } else if ((hour >= 18) && (hour < 20)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalDixhuitVingt();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalDixhuitVingt(totalHeureFixe);
                                        } else if ((hour >= 20) && (hour < 22)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalVingtVingtdeux();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalVingtVingtdeux(totalHeureFixe);
                                        } else if ((hour >= 22) && (hour < 24)) {
                                            totalHeureFixe = this.numeroCorrespondant.getTotalVingtdeuxVingquatre();
                                            if (totalHeureFixe.containsKey(num)) {
                                                totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                            } else {
                                                totalHeureFixe.put(num, 1);
                                            }
                                            this.numeroCorrespondant.setTotalVingtdeuxVingquatre(totalHeureFixe);
                                        }
                                        num = getSanitisedNumber(cdr.getCalledNumber());
                                    }

//                            c)	Fréquence par durée d’appel
                                    if (getSanitisedNumber(cdr.getCallingNumber()).equals(getSanitisedNumber(numero))) {
                                        String sanitisedRecepteur = getSanitisedNumber(cdr.getCalledNumber());
                                        if (cdr.getCallDuration() >= 0) {
                                            if (this.dureeAppel.containsKey(sanitisedRecepteur)) {
                                                this.dureeAppel.replace(sanitisedRecepteur, this.dureeAppel.get(sanitisedRecepteur) + cdr.getCallDuration());
                                            } else {
                                                this.dureeAppel.put(sanitisedRecepteur, cdr.getCallDuration());
                                            }
                                        } else {
                                            if (this.nombreMessage.containsKey(sanitisedRecepteur)) {
                                                this.nombreMessage.replace(sanitisedRecepteur, this.nombreMessage.get(sanitisedRecepteur) + 1);
                                            } else {
                                                this.nombreMessage.put(sanitisedRecepteur, 1);
                                            }
                                        }
//                                System.out.println("Taille duree appel: " + this.dureeAppel.size());
//                                System.out.println("Taille nombre message: " + this.nombreMessage.size());
                                    }

                                    if (getSanitisedNumber(cdr.getCalledNumber()).equals(getSanitisedNumber(numero))) {
                                        String sanitisedEmetteur = getSanitisedNumber(cdr.getCallingNumber());
                                        if (cdr.getCallDuration() >= 0) {
                                            if (this.dureeAppel.containsKey(sanitisedEmetteur)) {
                                                this.dureeAppel.replace(sanitisedEmetteur, this.dureeAppel.get(sanitisedEmetteur) + cdr.getCallDuration());
                                            } else {
                                                this.dureeAppel.put(sanitisedEmetteur, cdr.getCallDuration());
                                            }
                                        } else {
                                            if (this.nombreMessage.containsKey(sanitisedEmetteur)) {
                                                this.nombreMessage.replace(sanitisedEmetteur, this.nombreMessage.get(sanitisedEmetteur) + 1);
                                            } else {
                                                this.nombreMessage.put(sanitisedEmetteur, 1);
                                            }
                                        }
                                    }

//                            d)	Fréquence par IMEI 
                                    if (getSanitisedNumber(cdr.getCallingNumber()).equals(getSanitisedNumber(numero))) {
//                                System.out.println("This is the imei " + mtn.getImei());
                                        if (this.imeiOccurrence.containsKey(cdr.getServedImei())) {
                                            this.imeiOccurrence.replace(cdr.getServedImei(), this.imeiOccurrence.get(cdr.getServedImei()) + 1);
                                        } else {
                                            this.imeiOccurrence.put(cdr.getServedImei(), 1);
                                        }
                                        if (this.imeiFirstUse.containsKey(cdr.getServedImei())) {
                                            if (this.imeiFirstUse.get(cdr.getServedImei()).after(cdr.getCallDate())) {
                                                this.imeiFirstUse.replace(cdr.getServedImei(), cdr.getCallDate());
                                            }
                                        } else {
                                            this.imeiFirstUse.put(cdr.getServedImei(), cdr.getCallDate());
                                        }

                                        if (this.imeiLastUse.containsKey(cdr.getServedImei())) {
                                            if (this.imeiLastUse.get(cdr.getServedImei()).before(cdr.getCallDate())) {
                                                this.imeiLastUse.replace(cdr.getServedImei(), cdr.getCallDate());
                                            }
                                        } else {
                                            this.imeiLastUse.put(cdr.getServedImei(), cdr.getCallDate());
                                        }
                                    }

                                } else if (cdr.getRecordType().equals("0001")) {
                                    String opera = getOperatorByTelephone(getSanitisedNumber(cdr.getCallingNumber()));
                                    if (!opera.equals("Orange")) {
                                        cdr.setLocalisaton(getLocalisationByCellId(tab[6] + tab[7], getSanitisedNumber(cdr.getCalledNumber())));
                                        this.listingAppel.add(cdr);
                                        this.allNumbers.add(getSanitisedNumber(cdr.getCalledNumber()));
                                        this.allNumbers.add(getSanitisedNumber(cdr.getCallingNumber()));

//                            b)	Fréquence par cellule 
                                        HashMap<String, Integer> totalCellule = this.numeroTotal.getTotal();
                                        if (totalCellule.containsKey(cdr.getLocalisaton())) {
                                            totalCellule.replace(cdr.getLocalisaton(), totalCellule.get(cdr.getLocalisaton()) + 1);
                                        } else {
                                            totalCellule.put(cdr.getLocalisaton(), 1);
                                        }
                                        this.numeroTotal.setTotal(totalCellule);

                                        Calendar calendar = Calendar.getInstance();
                                        calendar.setTime(cdr.getCallDate());
                                        int hour = calendar.get(Calendar.HOUR_OF_DAY);
                                        HashMap<String, Integer> totalHeureFixe;

//                            00h à 02h
                                        if ((hour >= 0) && (hour < 2)) {
                                            totalHeureFixe = this.numeroTotal.getTotalZeroDeux();
                                            if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                                totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                            } else {
                                                totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                            }
                                            this.numeroTotal.setTotalZeroDeux(totalHeureFixe);
                                        } else if ((hour >= 2) && (hour < 4)) {
                                            totalHeureFixe = this.numeroTotal.getTotalDeuxQuatre();
                                            if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                                totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                            } else {
                                                totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                            }
                                            this.numeroTotal.setTotalDeuxQuatre(totalHeureFixe);
                                        } else if ((hour >= 4) && (hour < 6)) {
                                            totalHeureFixe = this.numeroTotal.getTotalQuatreSix();
                                            if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                                totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                            } else {
                                                totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                            }
                                            this.numeroTotal.setTotalQuatreSix(totalHeureFixe);
                                        } else if ((hour >= 6) && (hour < 8)) {
                                            totalHeureFixe = this.numeroTotal.getTotalSixHuit();
                                            if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                                totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                            } else {
                                                totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                            }
                                            this.numeroTotal.setTotalSixHuit(totalHeureFixe);
                                        } else if ((hour >= 8) && (hour < 10)) {
                                            totalHeureFixe = this.numeroTotal.getTotalHuitDix();
                                            if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                                totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                            } else {
                                                totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                            }
                                            this.numeroTotal.setTotalHuitDix(totalHeureFixe);
                                        } else if ((hour >= 10) && (hour < 12)) {
                                            totalHeureFixe = this.numeroTotal.getTotalDixDouze();
                                            if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                                totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                            } else {
                                                totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                            }
                                            this.numeroTotal.setTotalDixDouze(totalHeureFixe);
                                        } else if ((hour >= 12) && (hour < 14)) {
                                            totalHeureFixe = this.numeroTotal.getTotalDouzeQuatorze();
                                            if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                                totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                            } else {
                                                totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                            }
                                            this.numeroTotal.setTotalDouzeQuatorze(totalHeureFixe);
                                        } else if ((hour >= 14) && (hour < 16)) {
                                            totalHeureFixe = this.numeroTotal.getTotalQuatorzeSeize();
                                            if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                                totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                            } else {
                                                totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                            }
                                            this.numeroTotal.setTotalQuatorzeSeize(totalHeureFixe);
                                        } else if ((hour >= 16) && (hour < 18)) {
                                            totalHeureFixe = this.numeroTotal.getTotalSeizeDixhuit();
                                            if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                                totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                            } else {
                                                totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                            }
                                            this.numeroTotal.setTotalSeizeDixhuit(totalHeureFixe);
                                        } else if ((hour >= 18) && (hour < 20)) {
                                            totalHeureFixe = this.numeroTotal.getTotalDixhuitVingt();
                                            if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                                totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                            } else {
                                                totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                            }
                                            this.numeroTotal.setTotalDixhuitVingt(totalHeureFixe);
                                        } else if ((hour >= 20) && (hour < 22)) {
                                            totalHeureFixe = this.numeroTotal.getTotalVingtVingtdeux();
                                            if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                                totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                            } else {
                                                totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                            }
                                            this.numeroTotal.setTotalVingtVingtdeux(totalHeureFixe);
                                        } else if ((hour >= 22) && (hour < 24)) {
                                            totalHeureFixe = this.numeroTotal.getTotalVingtdeuxVingquatre();
                                            if (totalHeureFixe.containsKey(cdr.getLocalisaton())) {
                                                totalHeureFixe.replace(cdr.getLocalisaton(), totalHeureFixe.get(cdr.getLocalisaton()) + 1);
                                            } else {
                                                totalHeureFixe.put(cdr.getLocalisaton(), 1);
                                            }
                                            this.numeroTotal.setTotalVingtdeuxVingquatre(totalHeureFixe);
                                        }

//                            c)	Fréquence par Correspondant
                                        HashMap<String, Integer> total = this.numeroCorrespondant.getTotal();
                                        HashMap<String, Integer> totalEntrant = this.numeroCorrespondant.getTotalEntrant();
                                        String emetteur = getSanitisedNumber(cdr.getCallingNumber());
                                        if (totalEntrant.containsKey(emetteur)) {
                                            totalEntrant.replace(emetteur, totalEntrant.get(emetteur) + 1);
                                        } else {
                                            totalEntrant.put(emetteur, 1);
                                        }

                                        if (total.containsKey(emetteur)) {
                                            total.replace(emetteur, total.get(emetteur) + 1);
                                        } else {
                                            total.put(emetteur, 1);
                                        }
                                        this.numeroCorrespondant.setTotalEntrant(totalEntrant);
                                        this.numeroCorrespondant.setTotal(total);

                                        HashMap<String, Integer> totalSortant = this.numeroCorrespondant.getTotalSortant();
                                        total = this.numeroCorrespondant.getTotal();
                                        String recepteur = getSanitisedNumber(cdr.getCalledNumber());
                                        if (totalSortant.containsKey(recepteur)) {
                                            totalSortant.replace(recepteur, totalSortant.get(recepteur) + 1);
                                        } else {
                                            totalSortant.put(recepteur, 1);
                                        }

                                        if (total.containsKey(recepteur)) {
                                            total.replace(recepteur, total.get(recepteur) + 1);
                                        } else {
                                            total.put(recepteur, 1);
                                        }
                                        this.numeroCorrespondant.setTotalSortant(totalSortant);
                                        this.numeroCorrespondant.setTotal(total);

                                        String num = getSanitisedNumber(cdr.getCallingNumber());
                                        for (int j = 1; j < 3; j++) {
                                            if ((hour >= 0) && (hour < 2)) {
                                                totalHeureFixe = this.numeroCorrespondant.getTotalZeroDeux();
                                                if (totalHeureFixe.containsKey(num)) {
                                                    totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                                } else {
                                                    totalHeureFixe.put(num, 1);
                                                }
                                                this.numeroCorrespondant.setTotalZeroDeux(totalHeureFixe);
                                            } else if ((hour >= 2) && (hour < 4)) {
                                                totalHeureFixe = this.numeroCorrespondant.getTotalDeuxQuatre();
                                                if (totalHeureFixe.containsKey(num)) {
                                                    totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                                } else {
                                                    totalHeureFixe.put(num, 1);
                                                }
                                                this.numeroCorrespondant.setTotalDeuxQuatre(totalHeureFixe);
                                            } else if ((hour >= 4) && (hour < 6)) {
                                                totalHeureFixe = this.numeroCorrespondant.getTotalQuatreSix();
                                                if (totalHeureFixe.containsKey(num)) {
                                                    totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                                } else {
                                                    totalHeureFixe.put(num, 1);
                                                }
                                                this.numeroCorrespondant.setTotalQuatreSix(totalHeureFixe);
                                            } else if ((hour >= 6) && (hour < 8)) {
                                                totalHeureFixe = this.numeroCorrespondant.getTotalSixHuit();
                                                if (totalHeureFixe.containsKey(num)) {
                                                    totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                                } else {
                                                    totalHeureFixe.put(num, 1);
                                                }
                                                this.numeroCorrespondant.setTotalSixHuit(totalHeureFixe);
                                            } else if ((hour >= 8) && (hour < 10)) {
                                                totalHeureFixe = this.numeroCorrespondant.getTotalHuitDix();
                                                if (totalHeureFixe.containsKey(num)) {
                                                    totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                                } else {
                                                    totalHeureFixe.put(num, 1);
                                                }
                                                this.numeroCorrespondant.setTotalHuitDix(totalHeureFixe);
                                            } else if ((hour >= 10) && (hour < 12)) {
                                                totalHeureFixe = this.numeroCorrespondant.getTotalDixDouze();
                                                if (totalHeureFixe.containsKey(num)) {
                                                    totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                                } else {
                                                    totalHeureFixe.put(num, 1);
                                                }
                                                this.numeroCorrespondant.setTotalDixDouze(totalHeureFixe);
                                            } else if ((hour >= 12) && (hour < 14)) {
                                                totalHeureFixe = this.numeroCorrespondant.getTotalDouzeQuatorze();
                                                if (totalHeureFixe.containsKey(num)) {
                                                    totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                                } else {
                                                    totalHeureFixe.put(num, 1);
                                                }
                                                this.numeroCorrespondant.setTotalDouzeQuatorze(totalHeureFixe);
                                            } else if ((hour >= 14) && (hour < 16)) {
                                                totalHeureFixe = this.numeroCorrespondant.getTotalQuatorzeSeize();
                                                if (totalHeureFixe.containsKey(num)) {
                                                    totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                                } else {
                                                    totalHeureFixe.put(num, 1);
                                                }
                                                this.numeroCorrespondant.setTotalQuatorzeSeize(totalHeureFixe);
                                            } else if ((hour >= 16) && (hour < 18)) {
                                                totalHeureFixe = this.numeroCorrespondant.getTotalSeizeDixhuit();
                                                if (totalHeureFixe.containsKey(num)) {
                                                    totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                                } else {
                                                    totalHeureFixe.put(num, 1);
                                                }
                                                this.numeroCorrespondant.setTotalSeizeDixhuit(totalHeureFixe);
                                            } else if ((hour >= 18) && (hour < 20)) {
                                                totalHeureFixe = this.numeroCorrespondant.getTotalDixhuitVingt();
                                                if (totalHeureFixe.containsKey(num)) {
                                                    totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                                } else {
                                                    totalHeureFixe.put(num, 1);
                                                }
                                                this.numeroCorrespondant.setTotalDixhuitVingt(totalHeureFixe);
                                            } else if ((hour >= 20) && (hour < 22)) {
                                                totalHeureFixe = this.numeroCorrespondant.getTotalVingtVingtdeux();
                                                if (totalHeureFixe.containsKey(num)) {
                                                    totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                                } else {
                                                    totalHeureFixe.put(num, 1);
                                                }
                                                this.numeroCorrespondant.setTotalVingtVingtdeux(totalHeureFixe);
                                            } else if ((hour >= 22) && (hour < 24)) {
                                                totalHeureFixe = this.numeroCorrespondant.getTotalVingtdeuxVingquatre();
                                                if (totalHeureFixe.containsKey(num)) {
                                                    totalHeureFixe.replace(num, totalHeureFixe.get(num) + 1);
                                                } else {
                                                    totalHeureFixe.put(num, 1);
                                                }
                                                this.numeroCorrespondant.setTotalVingtdeuxVingquatre(totalHeureFixe);
                                            }
                                            num = getSanitisedNumber(cdr.getCalledNumber());
                                        }

//                            c)	Fréquence par durée d’appel
                                        if (getSanitisedNumber(cdr.getCallingNumber()).equals(getSanitisedNumber(numero))) {
                                            String sanitisedRecepteur = getSanitisedNumber(cdr.getCalledNumber());
                                            if (cdr.getCallDuration() >= 0) {
                                                if (this.dureeAppel.containsKey(sanitisedRecepteur)) {
                                                    this.dureeAppel.replace(sanitisedRecepteur, this.dureeAppel.get(sanitisedRecepteur) + cdr.getCallDuration());
                                                } else {
                                                    this.dureeAppel.put(sanitisedRecepteur, cdr.getCallDuration());
                                                }
                                            } else {
                                                if (this.nombreMessage.containsKey(sanitisedRecepteur)) {
                                                    this.nombreMessage.replace(sanitisedRecepteur, this.nombreMessage.get(sanitisedRecepteur) + 1);
                                                } else {
                                                    this.nombreMessage.put(sanitisedRecepteur, 1);
                                                }
                                            }
//                                System.out.println("Taille duree appel: " + this.dureeAppel.size());
//                                System.out.println("Taille nombre message: " + this.nombreMessage.size());
                                        }

                                        if (getSanitisedNumber(cdr.getCalledNumber()).equals(getSanitisedNumber(numero))) {
                                            String sanitisedEmetteur = getSanitisedNumber(cdr.getCallingNumber());
                                            if (cdr.getCallDuration() >= 0) {
                                                if (this.dureeAppel.containsKey(sanitisedEmetteur)) {
                                                    this.dureeAppel.replace(sanitisedEmetteur, this.dureeAppel.get(sanitisedEmetteur) + cdr.getCallDuration());
                                                } else {
                                                    this.dureeAppel.put(sanitisedEmetteur, cdr.getCallDuration());
                                                }
                                            } else {
                                                if (this.nombreMessage.containsKey(sanitisedEmetteur)) {
                                                    this.nombreMessage.replace(sanitisedEmetteur, this.nombreMessage.get(sanitisedEmetteur) + 1);
                                                } else {
                                                    this.nombreMessage.put(sanitisedEmetteur, 1);
                                                }
                                            }
                                        }

//                            d)	Fréquence par IMEI 
                                        if (getSanitisedNumber(cdr.getCalledNumber()).equals(getSanitisedNumber(numero))) {
//                                System.out.println("This is the imei " + mtn.getImei());
                                            if (this.imeiOccurrence.containsKey(cdr.getServedImei())) {
                                                this.imeiOccurrence.replace(cdr.getServedImei(), this.imeiOccurrence.get(cdr.getServedImei()) + 1);
                                            } else {
                                                this.imeiOccurrence.put(cdr.getServedImei(), 1);
                                            }
                                            if (this.imeiFirstUse.containsKey(cdr.getServedImei())) {
                                                if (this.imeiFirstUse.get(cdr.getServedImei()).after(cdr.getCallDate())) {
                                                    this.imeiFirstUse.replace(cdr.getServedImei(), cdr.getCallDate());
                                                }
                                            } else {
                                                this.imeiFirstUse.put(cdr.getServedImei(), cdr.getCallDate());
                                            }

                                            if (this.imeiLastUse.containsKey(cdr.getServedImei())) {
                                                if (this.imeiLastUse.get(cdr.getServedImei()).before(cdr.getCallDate())) {
                                                    this.imeiLastUse.replace(cdr.getServedImei(), cdr.getCallDate());
                                                }
                                            } else {
                                                this.imeiLastUse.put(cdr.getServedImei(), cdr.getCallDate());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (ParseException ex) {
                Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                LineIterator.closeQuietly(it);
            }
        } catch (IOException ex) {
            Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
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
                if (ident.endsWith(",")) {
                    ident = ident + "null";
                }
                String[] tab = ident.split(",");
                if (tab.length < 5) {
                    ident = "null,null,null,null,null";
                }
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
                    loc = "null,null,null,null";
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
                    loc = "null,null,null,null";
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
                System.out.println(ident);
                if (!ident.equals("null")) {
                    if (ident.endsWith(",")) {
                        ident = ident + "null";
                    }
                    String[] tab = ident.split(",");
                    if (tab.length >= 5) {
                        String chaine = getSanitisedNumber(num) + ","
                                + tab[2] + ","
                                + tab[0] + ","
                                + tab[1] + ","
                                + tab[3] + ","
                                + tab[4];
                        chaine = chaine.replaceAll(",,", ",null,");
                        chaine = chaine.replaceAll(",,", ",null,");
                        out.println(chaine);
                    }
                }
            }
            out.close();
        } catch (Exception ex) {

        }

        try {
            File outputFile = new File(this.frequenceCorrespondant);
            FileWriter fw = new FileWriter(outputFile, true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw);

//            Sorting values
            HashMap<String, Integer> sorted = this.numeroCorrespondant.getTotal()
                    .entrySet()
                    .stream()
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                    .collect(
                            toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2,
                                    LinkedHashMap::new));

            for (String name : sorted.keySet()) {
//                System.out.println(name);
                String key = name;
                String ident = getIdentificationByNumero(key);
                if (ident.equals("null")) {
                    ident = "null,null,null,null,null";
                }
                String[] tab = ident.split(",");
                if (tab.length < 5) {
                    ident = "null,null,null,null,null";
                }
                String chaine = this.numeroCorrespondant.getTotal().get(key) + ","
                        + this.numeroCorrespondant.getTotalEntrant().get(key) + ","
                        + this.numeroCorrespondant.getTotalSortant().get(key) + ","
                        + key + ","
                        + ident + ","
                        + this.numeroCorrespondant.getTotalZeroDeux().get(key) + ","
                        + this.numeroCorrespondant.getTotalDeuxQuatre().get(key) + ","
                        + this.numeroCorrespondant.getTotalQuatreSix().get(key) + ","
                        + this.numeroCorrespondant.getTotalSixHuit().get(key) + ","
                        + this.numeroCorrespondant.getTotalHuitDix().get(key) + ","
                        + this.numeroCorrespondant.getTotalDixDouze().get(key) + ","
                        + this.numeroCorrespondant.getTotalDouzeQuatorze().get(key) + ","
                        + this.numeroCorrespondant.getTotalQuatorzeSeize().get(key) + ","
                        + this.numeroCorrespondant.getTotalSeizeDixhuit().get(key) + ","
                        + this.numeroCorrespondant.getTotalDixhuitVingt().get(key) + ","
                        + this.numeroCorrespondant.getTotalVingtVingtdeux().get(key) + ","
                        + this.numeroCorrespondant.getTotalVingtdeuxVingquatre().get(key);
                out.println(chaine);
            }
            out.close();
        } catch (Exception ex) {

        }

        try {
            File outputFile = new File(this.frequenceCellule);
            FileWriter fw = new FileWriter(outputFile, true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw);
            int nbreOccurNull = 0;
            String nullChaine = "";

//            Sorting values
            HashMap<String, Integer> sorted = this.numeroTotal.getTotal()
                    .entrySet()
                    .stream()
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                    .collect(
                            toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2,
                                    LinkedHashMap::new));
            for (String name : sorted.keySet()) {
//                System.out.println(name);
                String key = name;
                String localisation = key;
                if (localisation.equals("null")) {
                    localisation = "null,null,null,null";
                }
                String chaine = this.numeroTotal.getTotal().get(key) + ","
                        + localisation + ","
                        + this.numeroTotal.getTotalZeroDeux().get(key) + ","
                        + this.numeroTotal.getTotalDeuxQuatre().get(key) + ","
                        + this.numeroTotal.getTotalQuatreSix().get(key) + ","
                        + this.numeroTotal.getTotalSixHuit().get(key) + ","
                        + this.numeroTotal.getTotalHuitDix().get(key) + ","
                        + this.numeroTotal.getTotalDixDouze().get(key) + ","
                        + this.numeroTotal.getTotalDouzeQuatorze().get(key) + ","
                        + this.numeroTotal.getTotalQuatorzeSeize().get(key) + ","
                        + this.numeroTotal.getTotalSeizeDixhuit().get(key) + ","
                        + this.numeroTotal.getTotalDixhuitVingt().get(key) + ","
                        + this.numeroTotal.getTotalVingtVingtdeux().get(key) + ","
                        + this.numeroTotal.getTotalVingtdeuxVingquatre().get(key);
                out.println(chaine);
            }
            out.close();
        } catch (Exception ex) {

        }

        try {
            File outputFile = new File(this.frequenceParDureeAppel);
            FileWriter fw = new FileWriter(outputFile, true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw);

//            Sorting values
            HashMap<String, Integer> sorted = this.dureeAppel
                    .entrySet()
                    .stream()
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                    .collect(
                            toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2,
                                    LinkedHashMap::new));

            HashSet<String> allKeys = new HashSet<>();

            for (String c : sorted.keySet()) {
                allKeys.add(c);
            }
            for (String c : this.nombreMessage.keySet()) {
                allKeys.add(c);
            }
//            allKeys.addAll(this.nombreMessage.keySet());

            System.out.println("Taille duree appel: " + this.dureeAppel.size());
            System.out.println("Taille duree appel sorted: " + sorted.size());
            System.out.println("Taille nombre message: " + this.nombreMessage.size());
            System.out.println("Taille allKeys: " + allKeys.size());

            for (String name : allKeys) {
//                System.out.println(name);
                String key = name;
                String ident = getIdentificationByNumero(key);
                if (ident.equals("null")) {
                    ident = "null,null,null,null,null";
                }
                String[] tab = ident.split(",");
                if (tab.length < 5) {
                    ident = "null,null,null,null,null";
                }
                String nbreSms;
                int duree;
                if (this.nombreMessage.get(key) == null) {
                    nbreSms = "0";
                } else {
                    nbreSms = this.nombreMessage.get(key) + "";
                }

                if (this.dureeAppel.get(key) == null) {
                    duree = 0;
                } else {
                    duree = this.dureeAppel.get(key);
                }
                String chaine = key + ","
                        + ident + ","
                        + duree + ","
                        + nbreSms;
                out.println(chaine);
            }
            out.close();
        } catch (Exception ex) {

        }

        try {
            File outputFile = new File(this.frequenceParImei);
            FileWriter fw = new FileWriter(outputFile, true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw);

//            System.out.println("Occurrence IMEI = " + this.imeiOccurrence.size());
//            Sorting values
            HashMap<String, Integer> sorted = this.imeiOccurrence
                    .entrySet()
                    .stream()
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                    .collect(
                            toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2,
                                    LinkedHashMap::new));

            for (String name : sorted.keySet()) {
                String key = name;

                String chaine = this.imeiOccurrence.get(key) + ","
                        + key + ","
                        + df2.format(this.imeiFirstUse.get(key)) + ","
                        + df2.format(this.imeiLastUse.get(key));
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
                    //                                        .resource("http://192.168.1.24:8080/MapServices/webresources/service/bts");
                    .resource("http://192.168.1.114:8080/MapServices/webresources/service/bts");
//            String input = "Mtn,679206565";//31006,3320831006
            ClientResponse response = webResource.type(MediaType.TEXT_PLAIN)
                    .post(ClientResponse.class, input);
            localisation = response.getEntity(String.class);

        } catch (Exception e) {
//            e.printStackTrace();
            localisation = "null";
        }
        if (localisation.contains("html")) {
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
                    //                                        .resource("http://192.168.1.24:8080/MapServices/webresources/service/bdi");
                    .resource("http://192.168.1.114:8080/MapServices/webresources/service/bdi");
//            String input = "Mtn,679206565";//31006,3320831006
            ClientResponse response = webResource.type(MediaType.TEXT_PLAIN)
                    .post(ClientResponse.class, input);
            identification = response.getEntity(String.class);

        } catch (Exception e) {
//            e.printStackTrace();
            identification = "null";
        }
        if (identification.contains("html")) {
            identification = "null";
        }
        if (identification.endsWith(";")) {
            identification = identification + "null";
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
