#!/usr/bin/env python3
"""
Analyse de listings CDR Orange Cameroun - Version OPTIMISÉE.
Même résultat que requisition_orange.py mais beaucoup plus rapide.

Optimisations :
  1. Cache HTTP : évite les appels répétés BTS/BDI pour les mêmes paramètres
  2. BTS après filtre : n'appelle BTS que pour les lignes retenues
  3. Appels HTTP en parallèle : ThreadPoolExecutor pour BDI en phase d'écriture
  4. Session HTTP avec connection pooling

Usage:
    python requisition_orange_fast.py <numero> <date_requisition>
"""

import sys
import os
import logging
from datetime import datetime
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from typing import Optional

import requests
from requests.adapters import HTTPAdapter

logger = logging.getLogger(__name__)

# URL du service de géolocalisation/identification (réseau interne)
MAP_SERVICE_BASE_URL = "http://192.168.1.114:8080/MapServices/webresources/service"

# Session HTTP avec connection pooling
_session = requests.Session()
_adapter = HTTPAdapter(pool_connections=20, pool_maxsize=20)
_session.mount("http://", _adapter)

# ── Caches HTTP ──
_cache_bts = {}
_cache_bdi = {}


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
    slots: dict = field(default_factory=lambda: {i: defaultdict(int) for i in range(12)})

    def get_slot(self, hour: int) -> dict:
        return self.slots[hour // 2]


# ──────────────────────────────────────────────────────────────
# Fonctions utilitaires
# ──────────────────────────────────────────────────────────────

def get_sanitised_number(num: str) -> str:
    if num.startswith("00237"):
        return num[5:]
    if num.startswith("+237"):
        return num[4:]
    if num.startswith("237"):
        return num[3:]
    return num


def get_operator_by_telephone(tel: str) -> str:
    mtn_prefixes = ("67", "650", "651", "652", "653", "654",
                    "680", "681", "682", "683", "684")
    orange_prefixes = ("69", "64", "655", "656", "657", "658", "659",
                       "685", "686", "687", "688", "689")
    nexttel_prefixes = ("60", "61", "62", "63", "66")

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
    """Appelle BTS avec cache."""
    operator = get_operator_by_telephone(get_sanitised_number(numero))
    cache_key = f"{operator},{cell_id}"

    if cache_key in _cache_bts:
        return _cache_bts[cache_key]

    try:
        response = _session.post(
            f"{MAP_SERVICE_BASE_URL}/bts",
            data=cache_key,
            headers={"Content-Type": "text/plain"},
            timeout=10,
        )
        localisation = response.text
        if not localisation or "html" in localisation:
            result = "null"
        else:
            result = localisation.replace(";", ",")
            if not result:
                result = "null"
    except Exception:
        result = "null"

    _cache_bts[cache_key] = result
    return result


def get_identification_by_numero(numero: str) -> str:
    """Appelle BDI avec cache."""
    numero = get_sanitised_number(numero)
    operator = get_operator_by_telephone(numero)
    cache_key = f"{operator},{numero}"

    if cache_key in _cache_bdi:
        return _cache_bdi[cache_key]

    try:
        response = _session.post(
            f"{MAP_SERVICE_BASE_URL}/bdi",
            data=cache_key,
            headers={"Content-Type": "text/plain"},
            timeout=10,
        )
        identification = response.text
        if "html" in identification:
            result = "null"
        else:
            if identification.endswith(";"):
                identification += "null"
            result = identification.replace(";", ",")
    except Exception:
        result = "null"

    _cache_bdi[cache_key] = result
    return result


def _prefetch_bdi(numbers: set):
    """Pré-charge les identifications BDI en parallèle."""
    to_fetch = [n for n in numbers if f"{get_operator_by_telephone(n)},{n}" not in _cache_bdi]
    if not to_fetch:
        return

    with ThreadPoolExecutor(max_workers=10) as executor:
        futures = {executor.submit(get_identification_by_numero, num): num for num in to_fetch}
        for future in as_completed(futures):
            future.result()


def _n(value):
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

        self.occur_num_sms: dict[str, int] = defaultdict(int)
        self.occur_num_appel: dict[str, int] = defaultdict(int)
        self.duree_appel: dict[str, int] = defaultdict(int)
        self.occur_position: dict[str, int] = defaultdict(int)
        self.nombre_message: dict[str, int] = defaultdict(int)
        self.imei_occurrence: dict[str, int] = defaultdict(int)
        self.imei_first_use: dict[str, datetime] = {}
        self.imei_last_use: dict[str, datetime] = {}

        self.listing_appel: list[OrangeBean] = []
        self.listing_sms: list[OrangeBean] = []
        self.all_numbers: set[str] = set()

        self.numero_total = ImeiTotal()
        self.numero_correspondant = ImeiTotal()

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
        sn = self._sanitise(self.numero)
        return sn in (
            self._sanitise(cdr.called_number),
            self._sanitise(cdr.calling_number),
            self._sanitise(cdr.origination),
            self._sanitise(cdr.served_msisdn),
        )

    def _update_time_slot(self, imei_total: ImeiTotal, key: str, hour: int):
        imei_total.get_slot(hour)[key] += 1

    def _update_correspondant_slots(self, hour: int, num1: str, num2: str):
        for num in (num1, num2):
            self._update_time_slot(self.numero_correspondant, num, hour)

    def _update_imei(self, cdr: OrangeBean):
        imei = cdr.served_imei
        self.imei_occurrence[imei] += 1
        if imei not in self.imei_first_use or self.imei_first_use[imei] > cdr.call_date:
            self.imei_first_use[imei] = cdr.call_date
        if imei not in self.imei_last_use or self.imei_last_use[imei] < cdr.call_date:
            self.imei_last_use[imei] = cdr.call_date

    def _update_duree_appel(self, cdr: OrangeBean, correspondant: str):
        if cdr.call_duration >= 0:
            self.duree_appel[correspondant] += cdr.call_duration
        else:
            self.nombre_message[correspondant] += 1

    def _process_sms(self, cdr: OrangeBean, tab: list):
        # OPTIMISATION : BTS appelé ICI (après filtre) au lieu d'avant
        cdr.localisation = get_localisation_by_cell_id(
            tab[6] + tab[7], self._sanitise(cdr.served_msisdn)
        )
        self.listing_sms.append(cdr)
        self.all_numbers.add(self._sanitise(cdr.origination))
        self.all_numbers.add(self._sanitise(cdr.served_msisdn))

        hour = cdr.call_date.hour

        self.numero_total.total[cdr.localisation] += 1
        self._update_time_slot(self.numero_total, cdr.localisation, hour)

        emetteur = self._sanitise(cdr.origination)
        recepteur = self._sanitise(cdr.served_msisdn)

        self.numero_correspondant.total_entrant[emetteur] += 1
        self.numero_correspondant.total[emetteur] += 1
        self.numero_correspondant.total_sortant[recepteur] += 1
        self.numero_correspondant.total[recepteur] += 1

        self._update_correspondant_slots(hour, emetteur, recepteur)

        sn = self._sanitise(self.numero)
        if self._sanitise(cdr.origination) == sn:
            self._update_duree_appel(cdr, self._sanitise(cdr.served_msisdn))
        if self._sanitise(cdr.served_msisdn) == sn:
            self._update_duree_appel(cdr, self._sanitise(cdr.origination))

        if self._sanitise(cdr.served_msisdn) == sn:
            self._update_imei(cdr)

    def _process_appel(self, cdr: OrangeBean, tab: list):
        # OPTIMISATION : BTS appelé ICI (après filtre) au lieu d'avant
        cdr.localisation = get_localisation_by_cell_id(
            tab[6] + tab[7], self._sanitise(cdr.calling_number)
        )
        self.listing_appel.append(cdr)
        self.all_numbers.add(self._sanitise(cdr.called_number))
        self.all_numbers.add(self._sanitise(cdr.calling_number))

        hour = cdr.call_date.hour

        self.numero_total.total[cdr.localisation] += 1
        self._update_time_slot(self.numero_total, cdr.localisation, hour)

        emetteur = self._sanitise(cdr.calling_number)
        recepteur = self._sanitise(cdr.called_number)

        self.numero_correspondant.total_entrant[emetteur] += 1
        self.numero_correspondant.total[emetteur] += 1
        self.numero_correspondant.total_sortant[recepteur] += 1
        self.numero_correspondant.total[recepteur] += 1

        self._update_correspondant_slots(hour, emetteur, recepteur)

        sn = self._sanitise(self.numero)
        if self._sanitise(cdr.calling_number) == sn:
            self._update_duree_appel(cdr, self._sanitise(cdr.called_number))
        if self._sanitise(cdr.called_number) == sn:
            self._update_duree_appel(cdr, self._sanitise(cdr.calling_number))

        if self._sanitise(cdr.calling_number) == sn:
            self._update_imei(cdr)

    def _process_appel_entrant(self, cdr: OrangeBean, tab: list):
        opera = get_operator_by_telephone(self._sanitise(cdr.calling_number))
        if opera == "Orange":
            return

        # OPTIMISATION : BTS appelé ICI (après filtre) au lieu d'avant
        cdr.localisation = get_localisation_by_cell_id(
            tab[6] + tab[7], self._sanitise(cdr.called_number)
        )
        self.listing_appel.append(cdr)
        self.all_numbers.add(self._sanitise(cdr.called_number))
        self.all_numbers.add(self._sanitise(cdr.calling_number))

        hour = cdr.call_date.hour

        self.numero_total.total[cdr.localisation] += 1
        self._update_time_slot(self.numero_total, cdr.localisation, hour)

        emetteur = self._sanitise(cdr.calling_number)
        recepteur = self._sanitise(cdr.called_number)

        self.numero_correspondant.total_entrant[emetteur] += 1
        self.numero_correspondant.total[emetteur] += 1
        self.numero_correspondant.total_sortant[recepteur] += 1
        self.numero_correspondant.total[recepteur] += 1

        self._update_correspondant_slots(hour, emetteur, recepteur)

        sn = self._sanitise(self.numero)
        if self._sanitise(cdr.calling_number) == sn:
            self._update_duree_appel(cdr, self._sanitise(cdr.called_number))
        if self._sanitise(cdr.called_number) == sn:
            self._update_duree_appel(cdr, self._sanitise(cdr.calling_number))

        if self._sanitise(cdr.called_number) == sn:
            self._update_imei(cdr)

    def analyse_listing(self):
        df = self.DATE_FMT

        # ── Phase 1 : Lecture et analyse du fichier CDR ──
        try:
            with open(self.input_file, "r", encoding="utf-8", errors="replace") as f:
                try:
                    for line in f:
                        line = line.strip()
                        if not line or line.startswith("CALLDATE"):
                            continue

                        line = line.replace(";", ",")
                        line = line.replace(",,", ",null,")
                        line = line.replace(",,", ",null,")
                        line = line.replace(",,", ",null,")
                        line = line.replace(",,", ",null,")
                        line = line.replace(",,", ",null,")
                        if line.endswith(","):
                            line += "null"

                        tab = line.split(",")
                        if len(tab) < 14:
                            continue

                        cdr = OrangeBean()

                        if tab[0] not in ("null", ""):
                            cdr.call_date = datetime.strptime(tab[0], df)

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
                        # OPTIMISATION : PAS d'appel BTS ici
                        # L'appel BTS se fait dans _process_sms/_process_appel/_process_appel_entrant
                        # seulement pour les lignes qui passent le filtre _is_target_number

                        if not self._is_target_number(cdr):
                            continue

                        if cdr.record_type == "0007":
                            self._process_sms(cdr, tab)
                        elif cdr.record_type == "0000":
                            self._process_appel(cdr, tab)
                        elif cdr.record_type == "0001":
                            self._process_appel_entrant(cdr, tab)

                except (ValueError, KeyError) as ex:
                    logger.error("Erreur parsing: %s", ex)
        except IOError as ex:
            logger.error("Erreur lecture fichier: %s", ex)

        # ── Phase 1.5 : Pré-chargement BDI en parallèle ──
        # Collecte tous les numéros qui auront besoin d'un appel BDI
        all_bdi_numbers = set(self.all_numbers)
        all_bdi_numbers.add(self._sanitise(self.numero))
        all_bdi_numbers.update(self.numero_correspondant.total.keys())
        all_bdi_numbers.update(self.duree_appel.keys())
        all_bdi_numbers.update(self.nombre_message.keys())
        # Sanitise tous les numéros
        all_bdi_sanitised = {get_sanitised_number(n) for n in all_bdi_numbers}
        print(f"Pré-chargement BDI pour {len(all_bdi_sanitised)} numéros en parallèle...")
        _prefetch_bdi(all_bdi_sanitised)
        print("Pré-chargement BDI terminé.")

        # ── Phase 2 : Écriture des fichiers de sortie ──
        self._write_identite_numero()
        self._write_appels()
        self._write_sms()
        self._write_identification()
        self._write_frequence_correspondant()
        self._write_frequence_cellule()
        self._write_frequence_duree_appel()
        self._write_frequence_imei()

    # ── Méthodes d'écriture (identiques à requisition_orange.py) ──

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
                self.listing_appel.sort(key=lambda c: c.call_date)
                self.listing_appel.reverse()
                for cdr in self.listing_appel:
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
                self.listing_sms.sort(key=lambda c: c.call_date)
                self.listing_sms.reverse()
                for cdr in self.listing_sms:
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
        print("Usage: python requisition_orange_fast.py <numero> <date_requisition>")
        sys.exit(1)

    numero = sys.argv[1]
    date_requisition = sys.argv[2]

    util = Utils(numero, date_requisition)
    util.analyse_listing()


if __name__ == "__main__":
    main()
