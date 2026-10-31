"""Generates docs/sample-backup.json: fake data in Doseful's backup format (version 1).

Dose dates are relative to the day you run this, so history always looks current.
Import it in the app: Profile > Backup > Restore.

    python docs/make_sample_backup.py
"""
import json
import time
from datetime import datetime
from pathlib import Path

ALL = 0b1111111
WEEKDAYS = 0b0011111

# (name, strength, form, dose, stock, refill_at, manufacturer, expiry, batch, [(HH, MM, days_mask)])
MEDS = [
    ("Levothyroxine", "50 mcg", "Tablet", "1", 58, 14, "Sample Pharma", "03/2028", "LT2411", [(7, 0, ALL)]),
    ("Metformin", "500 mg", "Tablet", "1", 64, 14, "Sample Pharma", "08/2027", "MF8821", [(8, 0, ALL), (20, 0, ALL)]),
    ("Lisinopril", "10 mg", "Tablet", "1", 41, 10, "Demo Labs", "11/2027", "LS3307", [(8, 30, ALL)]),
    ("Vitamin D3", "2000 IU", "Capsule", "1", 90, 15, "Demo Labs", "06/2028", "VD1190", [(9, 0, ALL)]),
    ("Omega-3", "1000 mg", "Capsule", "2", 36, 12, "Sample Pharma", "01/2028", "OM5502", [(13, 0, WEEKDAYS)]),
    ("Atorvastatin", "20 mg", "Tablet", "1", 12, 14, "Demo Labs", "09/2027", "AT7716", [(21, 0, ALL)]),  # low stock
]


def main() -> None:
    now = datetime.now()
    today = int(time.mktime(now.timetuple()) // 86400) if False else (now.date() - datetime(1970, 1, 1).date()).days
    now_min = now.hour * 60 + now.minute

    medicines, schedules, logs = [], [], []
    sid = 0
    k = 0
    for mid, (name, strength, form, dose, stock, refill, mfr, exp, batch, times) in enumerate(MEDS, start=1):
        medicines.append({
            "id": mid, "name": name, "strength": strength, "form": form, "unitDose": dose,
            "stock": stock, "refillThreshold": refill, "manufacturer": mfr, "expiry": exp, "batch": batch, "notes": "",
        })
        for h, m, mask in times:
            sid += 1
            schedules.append({"id": sid, "medicineId": mid, "minuteOfDay": h * 60 + m, "daysMask": mask})
            for off in range(1, 15):
                day = today - off
                weekday = (day + 3) % 7  # epoch day 0 was a Thursday; Mon=0
                if not (mask >> weekday) & 1:
                    continue
                if (off * 3 + k) % 13 == 0:
                    continue  # a missed dose
                status = "SKIPPED" if (off * 5 + k * 3) % 9 == 0 else "TAKEN"
                logs.append({"scheduleId": sid, "epochDay": day, "status": status, "loggedAt": (day * 86400 + h * 3600 + m * 60 + 300) * 1000})
            if h * 60 + m + 30 < now_min and (mask >> ((today + 3) % 7)) & 1:
                logs.append({"scheduleId": sid, "epochDay": today, "status": "TAKEN", "loggedAt": int(time.time() * 1000)})
            k += 1

    backup = {
        "version": 1,
        "profile": {"name": "Max", "email": "", "age": 29, "gender": "Male", "heightCm": 178.0, "weightKg": 72.5, "metric": True, "onboarded": True},
        "avatar": "",
        "medicines": medicines,
        "schedules": schedules,
        "logs": logs,
    }
    out = Path(__file__).with_name("sample-backup.json")
    out.write_text(json.dumps(backup, indent=2), encoding="utf-8")
    print(f"wrote {out} ({len(medicines)} medicines, {len(schedules)} schedules, {len(logs)} logs)")


if __name__ == "__main__":
    main()
