#!/usr/bin/env python3
"""
Analyse de listings CDR Orange Cameroun.
Conversion du projet Java requisitionOrange en Python.

Usage:
    python requisition_orange.py <numero> <date_requisition>
"""

import sys
import os
import logging
from datetime import datetime
from collections import defaultdict
from dataclasses import dataclass, field
from typing import Optional

import requests

logger = logging.getLogger(__name__)

# URL du service de géolocalisation/identification (réseau interne)
MAP_SERVICE_BASE_URL = "http://192.168.1.114:8080/MapServices/webresources/service"


# ──────────────────────────────────────────────────────────────
# Data classes
# ──────────────────────────────────────────────────────────────

@dataclass
class OrangeBean:
    call_date: Optional[datetime] = None
    call_duration: int = 0
    call_reference: str = "null"
    called_imsi: str = "null"
    called_number: str = "null"
    calling_number: str = "null"
    loc_area_code: str = "null"
    loc_cell_id: str = "null"
    origination: str = "null"
    record_type: str = "null"
    roaming_number: str = "null"
    served_imei: str = "null"
    served_imsi: str = "null"
    served_msisdn: str = "null"
    localisation: str = "null"


@dataclass
class ImeiTotal:
    total: dict = field(default_factory=lambda: defaultdict(int))
    total_entrant: dict = field(default_factory=lambda: defaultdict(int))
    total_sortant: dict = field(default_factory=lambda: defaultdict(int))
    # Tranches horaires (0-2h, 2-4h, ..., 22-24h)
    slots: dict = field(default_factory=lambda: {i: defaultdict(int) for i in range(12)})

    def get_slot(self, hour: int) -> dict:
        """Retourne le dict de la tranche horaire correspondante."""
        return self.slots[hour // 2]


# ──────────────────────────────────────────────────────────────
# Fonctions utilitaires
# ──────────────────────────────────────────────────────────────

def get_sanitised_number(num: str) -> str:
    """Supprime le préfixe international camerounais (+237, 00237, 237)."""
    if num.startswith("00237"):
        return num[5:]
    if num.startswith("+237"):
        return num[4:]
    if num.startswith("237"):
        return num[3:]
    return num


def get_operator_by_telephone(tel: str) -> str:
    """Détermine l'opérateur à partir du préfixe du numéro."""
    mtn_prefixes = ("67", "650", "651", "652", "653", "654",
                    "680", "681", "682", "683", "684")
    orange_prefixes = ("69", "655", "656", "657", "658", "659",
                       "685", "686", "687", "688", "689")
    nexttel_prefixes = ("60", "61", "62", "63", "64", "66")

    if any(tel.startswith(p) for p in mtn_prefixes):
        return "Mtn"
    elif any(tel.startswith(p) for p in orange_prefixes):
        return "Orange"
    elif any(tel.startswith(p) for p in nexttel_prefixes):
        return "Nexttel"
    elif tel.startswith("2"):
        return "Camtel"
    return ""


def get_localisation_by_cell_id(cell_id: str, numero: str) -> str:
    """Appelle le service web interne pour géolocaliser une cellule BTS."""
    operator = get_operator_by_telephone(get_sanitised_number(numero))
    input_data = f"{operator},{cell_id}"
    try:
        response = requests.post(
            f"{MAP_SERVICE_BASE_URL}/bts",
            data=input_data,
            headers={"Content-Type": "text/plain"},
            timeout=10,
        )
        localisation = response.text
        if not localisation or "html" in localisation:
            return "null"
        result = localisation.replace(";", ",")
        return result if result else "null"
    except Exception:
        return "null"


def get_identification_by_numero(numero: str) -> str:
    """Appelle le service web interne pour identifier un abonné."""
    numero = get_sanitised_number(numero)
    operator = get_operator_by_telephone(numero)
    input_data = f"{operator},{numero}"
    try:
        response = requests.post(
            f"{MAP_SERVICE_BASE_URL}/bdi",
            data=input_data,
            headers={"Content-Type": "text/plain"},
            timeout=10,
        )
        identification = response.text
        if "html" in identification:
            return "null"
        if identification.endswith(";"):
            identification += "null"
        return identification.replace(";", ",")
    except Exception:
        return "null"


def _n(value):
    """Convertit None en string 'null' (comme Java le fait)."""
    if value is None:
        return "null"
    return str(value)


# ──────────────────────────────────────────────────────────────
# Classe principale d'analyse
# ──────────────────────────────────────────────────────────────

class Utils:
    DATE_FMT = "%d/%m/%Y %H:%M:%S"
    DATE_FMT2 = "%d-%m-%Y %H:%M:%S"

    def __init__(self, numero: str, date_requisition: str):
        self.numero = numero
        self.date_requisition = date_requisition

        # Compteurs
        self.occur_num_sms: dict[str, int] = defaultdict(int)
        self.occur_num_appel: dict[str, int] = defaultdict(int)
        self.duree_appel: dict[str, int] = defaultdict(int)
        self.occur_position: dict[str, int] = defaultdict(int)
        self.nombre_message: dict[str, int] = defaultdict(int)
        self.imei_occurrence: dict[str, int] = defaultdict(int)
        self.imei_first_use: dict[str, datetime] = {}
        self.imei_last_use: dict[str, datetime] = {}

        # Listes de CDR
        self.listing_appel: list[OrangeBean] = []
        self.listing_sms: list[OrangeBean] = []
        self.all_numbers: set[str] = set()

        # Statistiques par tranche horaire
        self.numero_total = ImeiTotal()
        self.numero_correspondant = ImeiTotal()

        # Chemins des fichiers
        self.base_path = f"/root/{date_requisition}/"
        num_dir = f"{self.base_path}{self.numero}/"
        self.appel_emis_file = f"{num_dir}appelemis.txt"
        self.sms_file = f"{num_dir}smsfinal.txt"
        self.frequence_correspondant = f"{num_dir}frequenceCorrespondance.txt"
        self.frequence_cellule = f"{num_dir}frequenceCellule.txt"
        self.frequence_par_duree_appel = f"{num_dir}frequenceDureeAppel.txt"
        self.frequence_par_imei = f"{num_dir}frequenceImei.txt"
        self.identification_file = f"{num_dir}IdentificationAbonneesfinal.txt"
        self.identite_numero_file = f"{num_dir}identite_numero.txt"
        self.input_file = f"{num_dir}Listing_Orange_{self.numero}.txt"

    def _sanitise(self, num: str) -> str:
        return get_sanitised_number(num)

    def _is_target_number(self, cdr: OrangeBean) -> bool:
        """Vérifie si le CDR concerne le numéro cible."""
        sn = self._sanitise(self.numero)
        return sn in (
            self._sanitise(cdr.called_number),
            self._sanitise(cdr.calling_number),
            self._sanitise(cdr.origination),
            self._sanitise(cdr.served_msisdn),
        )

    def _update_time_slot(self, imei_total: ImeiTotal, key: str, hour: int):
        """Incrémente la tranche horaire correspondante."""
        imei_total.get_slot(hour)[key] += 1

    def _update_correspondant_slots(self, hour: int, num1: str, num2: str):
        """Met à jour les tranches horaires pour les 2 correspondants."""
        for num in (num1, num2):
            self._update_time_slot(self.numero_correspondant, num, hour)

    def _update_imei(self, cdr: OrangeBean):
        """Met à jour les stats IMEI."""
        imei = cdr.served_imei
        self.imei_occurrence[imei] += 1
        if imei not in self.imei_first_use or self.imei_first_use[imei] > cdr.call_date:
            self.imei_first_use[imei] = cdr.call_date
        if imei not in self.imei_last_use or self.imei_last_use[imei] < cdr.call_date:
            self.imei_last_use[imei] = cdr.call_date

    def _update_duree_appel(self, cdr: OrangeBean, correspondant: str):
        """Met à jour durée d'appel ou nombre de messages."""
        if cdr.call_duration >= 0:
            self.duree_appel[correspondant] += cdr.call_duration
        else:
            self.nombre_message[correspondant] += 1

    def _parse_line(self, line: str) -> Optional[OrangeBean]:
        """Parse une ligne CSV en OrangeBean."""
        line = line.replace(";", ",")
        # Remplacer les champs vides
        for _ in range(5):
            line = line.replace(",,", ",null,")
        if line.endswith(","):
            line += "null"

        tab = line.split(",")
        if len(tab) < 14:
            return None

        cdr = OrangeBean()

        # Date
        if tab[0] not in ("null", ""):
            try:
                cdr.call_date = datetime.strptime(tab[0], self.DATE_FMT)
            except ValueError:
                return None

        # Durée selon le type d'enregistrement
        if tab[9] == "0000":
            if tab[1] not in ("null", ""):
                cdr.call_duration = int(tab[1])
            else:
                cdr.call_duration = -1
        elif tab[9] == "0007":
            cdr.call_duration = -1

        cdr.call_reference = tab[2]
        cdr.called_imsi = tab[3]
        cdr.called_number = tab[4]
        cdr.calling_number = tab[5]
        cdr.loc_area_code = tab[6]
        cdr.loc_cell_id = tab[7]
        cdr.origination = tab[8]
        cdr.record_type = tab[9]
        cdr.roaming_number = tab[10]
        cdr.served_imei = tab[11]
        cdr.served_imsi = tab[12]
        cdr.served_msisdn = tab[13]
        cdr.localisation = get_localisation_by_cell_id(
            tab[6] + tab[7], self.numero
        )

        return cdr

    def _process_sms(self, cdr: OrangeBean, tab: list[str]):
        """Traite un enregistrement SMS (recordType = 0007)."""
        cdr.localisation = get_localisation_by_cell_id(
            tab[6] + tab[7], self._sanitise(cdr.served_msisdn)
        )
        self.listing_sms.append(cdr)
        self.all_numbers.add(self._sanitise(cdr.origination))
        self.all_numbers.add(self._sanitise(cdr.served_msisdn))

        hour = cdr.call_date.hour

        # b) Fréquence par cellule
        self.numero_total.total[cdr.localisation] += 1
        self._update_time_slot(self.numero_total, cdr.localisation, hour)

        # c) Fréquence par correspondant
        emetteur = self._sanitise(cdr.origination)
        recepteur = self._sanitise(cdr.served_msisdn)

        self.numero_correspondant.total_entrant[emetteur] += 1
        self.numero_correspondant.total[emetteur] += 1
        self.numero_correspondant.total_sortant[recepteur] += 1
        self.numero_correspondant.total[recepteur] += 1

        self._update_correspondant_slots(hour, emetteur, recepteur)

        # c) Fréquence par durée d'appel
        sn = self._sanitise(self.numero)
        if self._sanitise(cdr.origination) == sn:
            self._update_duree_appel(cdr, self._sanitise(cdr.served_msisdn))
        if self._sanitise(cdr.served_msisdn) == sn:
            self._update_duree_appel(cdr, self._sanitise(cdr.origination))

        # d) Fréquence par IMEI
        if self._sanitise(cdr.served_msisdn) == sn:
            self._update_imei(cdr)

    def _process_appel(self, cdr: OrangeBean, tab: list[str]):
        """Traite un enregistrement d'appel (recordType = 0000)."""
        cdr.localisation = get_localisation_by_cell_id(
            tab[6] + tab[7], self._sanitise(cdr.calling_number)
        )
        self.listing_appel.append(cdr)
        self.all_numbers.add(self._sanitise(cdr.called_number))
        self.all_numbers.add(self._sanitise(cdr.calling_number))

        hour = cdr.call_date.hour

        # b) Fréquence par cellule
        self.numero_total.total[cdr.localisation] += 1
        self._update_time_slot(self.numero_total, cdr.localisation, hour)

        # c) Fréquence par correspondant
        emetteur = self._sanitise(cdr.calling_number)
        recepteur = self._sanitise(cdr.called_number)

        self.numero_correspondant.total_entrant[emetteur] += 1
        self.numero_correspondant.total[emetteur] += 1
        self.numero_correspondant.total_sortant[recepteur] += 1
        self.numero_correspondant.total[recepteur] += 1

        self._update_correspondant_slots(hour, emetteur, recepteur)

        # c) Fréquence par durée d'appel
        sn = self._sanitise(self.numero)
        if self._sanitise(cdr.calling_number) == sn:
            self._update_duree_appel(cdr, self._sanitise(cdr.called_number))
        if self._sanitise(cdr.called_number) == sn:
            self._update_duree_appel(cdr, self._sanitise(cdr.calling_number))

        # d) Fréquence par IMEI
        if self._sanitise(cdr.calling_number) == sn:
            self._update_imei(cdr)

    def _process_appel_entrant(self, cdr: OrangeBean, tab: list[str]):
        """Traite un appel entrant (recordType = 0001) d'un opérateur non-Orange."""
        opera = get_operator_by_telephone(self._sanitise(cdr.calling_number))
        if opera == "Orange":
            return

        cdr.localisation = get_localisation_by_cell_id(
            tab[6] + tab[7], self._sanitise(cdr.called_number)
        )
        self.listing_appel.append(cdr)
        self.all_numbers.add(self._sanitise(cdr.called_number))
        self.all_numbers.add(self._sanitise(cdr.calling_number))

        hour = cdr.call_date.hour

        # b) Fréquence par cellule
        self.numero_total.total[cdr.localisation] += 1
        self._update_time_slot(self.numero_total, cdr.localisation, hour)

        # c) Fréquence par correspondant
        emetteur = self._sanitise(cdr.calling_number)
        recepteur = self._sanitise(cdr.called_number)

        self.numero_correspondant.total_entrant[emetteur] += 1
        self.numero_correspondant.total[emetteur] += 1
        self.numero_correspondant.total_sortant[recepteur] += 1
        self.numero_correspondant.total[recepteur] += 1

        self._update_correspondant_slots(hour, emetteur, recepteur)

        # c) Fréquence par durée d'appel
        sn = self._sanitise(self.numero)
        if self._sanitise(cdr.calling_number) == sn:
            self._update_duree_appel(cdr, self._sanitise(cdr.called_number))
        if self._sanitise(cdr.called_number) == sn:
            self._update_duree_appel(cdr, self._sanitise(cdr.calling_number))

        # d) Fréquence par IMEI
        if self._sanitise(cdr.called_number) == sn:
            self._update_imei(cdr)

    def analyse_listing(self):
        """Méthode principale : parse le fichier et génère les rapports."""
        # ── Phase 1 : Lecture et analyse du fichier CDR ──
        try:
            with open(self.input_file, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line or line.startswith("CALLDATE"):
                        continue

                    cdr = self._parse_line(line)
                    if cdr is None:
                        continue

                    if not self._is_target_number(cdr):
                        continue

                    tab = line.replace(";", ",")
                    for _ in range(5):
                        tab = tab.replace(",,", ",null,")
                    if tab.endswith(","):
                        tab += "null"
                    tab = tab.split(",")

                    if cdr.record_type == "0007":
                        self._process_sms(cdr, tab)
                    elif cdr.record_type == "0000":
                        self._process_appel(cdr, tab)
                    elif cdr.record_type == "0001":
                        self._process_appel_entrant(cdr, tab)

        except IOError as ex:
            logger.error("Erreur lecture fichier: %s", ex)
            return

        # ── Phase 2 : Écriture des fichiers de sortie ──
        self._write_identite_numero()
        self._write_appels()
        self._write_sms()
        self._write_identification()
        self._write_frequence_correspondant()
        self._write_frequence_cellule()
        self._write_frequence_duree_appel()
        self._write_frequence_imei()

    # ── Méthodes d'écriture ──

    def _write_identite_numero(self):
        try:
            os.makedirs(os.path.dirname(self.identite_numero_file), exist_ok=True)
            with open(self.identite_numero_file, "a", encoding="utf-8") as out:
                ident = get_identification_by_numero(self.numero)
                sn = self._sanitise(self.numero)
                if ident != "null":
                    if ident.endswith(","):
                        ident += "null"
                    tab = ident.split(",")
                    if len(tab) < 5:
                        ident = "null,null,null,null,null"
                        tab = ident.split(",")
                    out.write(f"{sn},{tab[2]},{tab[0]},{tab[1]},{tab[3]},{tab[4]}\n")
                else:
                    out.write(f"{sn},null,null,null,null,null\n")
        except Exception:
            pass

    def _write_appels(self):
        try:
            os.makedirs(os.path.dirname(self.appel_emis_file), exist_ok=True)
            with open(self.appel_emis_file, "a", encoding="utf-8") as out:
                sorted_appels = sorted(
                    self.listing_appel,
                    key=lambda c: c.call_date,
                    reverse=True,
                )
                for cdr in sorted_appels:
                    loc = cdr.localisation
                    if not loc or loc == "null":
                        loc = "null,null,null,null"
                    loc = f"{cdr.loc_area_code}{cdr.loc_cell_id},{loc}"
                    chaine = (
                        f"{cdr.call_date.strftime(self.DATE_FMT)},"
                        f"{cdr.call_duration},"
                        f"{cdr.called_number},"
                        f"{cdr.calling_number},"
                        f"{loc},"
                        f"{cdr.served_imei},"
                        f"{cdr.origination},"
                        f"{cdr.record_type},"
                        f"{cdr.roaming_number},"
                        f"{cdr.served_msisdn}"
                    )
                    out.write(chaine + "\n")
        except Exception:
            pass

    def _write_sms(self):
        try:
            os.makedirs(os.path.dirname(self.sms_file), exist_ok=True)
            with open(self.sms_file, "a", encoding="utf-8") as out:
                sorted_sms = sorted(
                    self.listing_sms,
                    key=lambda c: c.call_date,
                    reverse=True,
                )
                for cdr in sorted_sms:
                    loc = cdr.localisation
                    if not loc or loc == "null":
                        loc = "null,null,null,null"
                    loc = f"{cdr.loc_area_code}{cdr.loc_cell_id},{loc}"
                    chaine = (
                        f"{cdr.call_date.strftime(self.DATE_FMT)},"
                        f"{cdr.call_duration},"
                        f"{cdr.called_number},"
                        f"{cdr.calling_number},"
                        f"{cdr.origination},"
                        f"{loc},"
                        f"{cdr.served_imei},"
                        f"{cdr.record_type},"
                        f"{cdr.roaming_number},"
                        f"{cdr.served_msisdn}"
                    )
                    out.write(chaine + "\n")
        except Exception:
            pass

    def _write_identification(self):
        try:
            os.makedirs(os.path.dirname(self.identification_file), exist_ok=True)
            with open(self.identification_file, "a", encoding="utf-8") as out:
                for num in self.all_numbers:
                    ident = get_identification_by_numero(num)
                    print(ident)
                    if ident != "null":
                        if ident.endswith(","):
                            ident += "null"
                        tab = ident.split(",")
                        if len(tab) >= 5:
                            chaine = (
                                f"{self._sanitise(num)},"
                                f"{tab[2]},{tab[0]},{tab[1]},{tab[3]},{tab[4]}"
                            )
                            chaine = chaine.replace(",,", ",null,")
                            chaine = chaine.replace(",,", ",null,")
                            out.write(chaine + "\n")
        except Exception:
            pass

    def _write_frequence_correspondant(self):
        try:
            os.makedirs(os.path.dirname(self.frequence_correspondant), exist_ok=True)
            with open(self.frequence_correspondant, "a", encoding="utf-8") as out:
                # Tri décroissant par total
                sorted_items = sorted(
                    self.numero_correspondant.total.items(),
                    key=lambda x: x[1],
                    reverse=True,
                )
                for key, _ in sorted_items:
                    ident = get_identification_by_numero(key)
                    if ident == "null":
                        ident = "null,null,null,null,null"
                    tab = ident.split(",")
                    if len(tab) < 5:
                        ident = "null,null,null,null,null"

                    slots_values = ",".join(
                        _n(self.numero_correspondant.slots[i].get(key))
                        for i in range(12)
                    )
                    chaine = (
                        f"{_n(self.numero_correspondant.total.get(key))},"
                        f"{_n(self.numero_correspondant.total_entrant.get(key))},"
                        f"{_n(self.numero_correspondant.total_sortant.get(key))},"
                        f"{key},{ident},{slots_values}"
                    )
                    out.write(chaine + "\n")
        except Exception:
            pass

    def _write_frequence_cellule(self):
        try:
            os.makedirs(os.path.dirname(self.frequence_cellule), exist_ok=True)
            with open(self.frequence_cellule, "a", encoding="utf-8") as out:
                sorted_items = sorted(
                    self.numero_total.total.items(),
                    key=lambda x: x[1],
                    reverse=True,
                )
                for key, _ in sorted_items:
                    localisation = key
                    if not localisation or localisation == "null":
                        localisation = "null,null,null,null"

                    slots_values = ",".join(
                        _n(self.numero_total.slots[i].get(key))
                        for i in range(12)
                    )
                    chaine = (
                        f"{_n(self.numero_total.total.get(key))},"
                        f"{localisation},{slots_values}"
                    )
                    out.write(chaine + "\n")
        except Exception:
            pass

    def _write_frequence_duree_appel(self):
        try:
            os.makedirs(os.path.dirname(self.frequence_par_duree_appel), exist_ok=True)
            with open(self.frequence_par_duree_appel, "a", encoding="utf-8") as out:
                # Union des clés durée d'appel + nombre de messages
                all_keys = set(self.duree_appel.keys()) | set(self.nombre_message.keys())

                print(f"Taille duree appel: {len(self.duree_appel)}")
                print(f"Taille nombre message: {len(self.nombre_message)}")
                print(f"Taille allKeys: {len(all_keys)}")

                for key in all_keys:
                    ident = get_identification_by_numero(key)
                    if ident == "null":
                        ident = "null,null,null,null,null"
                    tab = ident.split(",")
                    if len(tab) < 5:
                        ident = "null,null,null,null,null"

                    nbre_sms = self.nombre_message.get(key, 0)
                    duree = self.duree_appel.get(key, 0)
                    chaine = f"{key},{ident},{duree},{nbre_sms}"
                    out.write(chaine + "\n")
        except Exception:
            pass

    def _write_frequence_imei(self):
        try:
            os.makedirs(os.path.dirname(self.frequence_par_imei), exist_ok=True)
            with open(self.frequence_par_imei, "a", encoding="utf-8") as out:
                sorted_items = sorted(
                    self.imei_occurrence.items(),
                    key=lambda x: x[1],
                    reverse=True,
                )
                for key, count in sorted_items:
                    chaine = (
                        f"{count},{key},"
                        f"{self.imei_first_use[key].strftime(self.DATE_FMT2)},"
                        f"{self.imei_last_use[key].strftime(self.DATE_FMT2)}"
                    )
                    out.write(chaine + "\n")
        except Exception:
            pass


# ──────────────────────────────────────────────────────────────
# Point d'entrée
# ──────────────────────────────────────────────────────────────

def main():
    if len(sys.argv) < 3:
        print("Usage: python requisition_orange.py <numero> <date_requisition>")
        sys.exit(1)

    numero = sys.argv[1]
    date_requisition = sys.argv[2]

    util = Utils(numero, date_requisition)
    util.analyse_listing()


if __name__ == "__main__":
    main()
