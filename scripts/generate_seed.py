#!/usr/bin/env python3
"""
Generates scripts/seed_demo_data.sql with ~2000 rows covering all 7 live checks.
Run: python scripts/generate_seed.py
Then: psql -h localhost -U postgres -d trai_audit -f scripts/seed_demo_data.sql
"""
import random
from datetime import datetime, timedelta

random.seed(42)

TSPS = ["Airtel", "BSNL", "MTNL", "Reliance Jio", "Vodafone-Idea"]
STATUS_CHOICES = ["finished"]*70 + ["received"]*20 + ["failed"]*10  # 70/20/10

def mrad_minutes(cell):
    if cell <= 5000: return 60
    if cell <= 15000: return 90
    return 120

def rand_ts(base_now, days_back=30):
    delta = timedelta(days=random.uniform(0, days_back), hours=random.uniform(0,24), minutes=random.uniform(0,60))
    return base_now - delta

def fmt_ts(dt):
    if dt is None: return "NULL"
    return f"'{dt.strftime('%Y-%m-%d %H:%M:%S')}'"

def gen_cell():
    r = random.random()
    if r < 0.45: return random.randint(20, 5000)
    if r < 0.75: return random.randint(5001, 15000)
    if r < 0.95: return random.randint(15001, 30000)
    return random.randint(30001, 45000)  # beyond matrix 5%

now = datetime.now()

# Plan: 450 identifiers, each with 2-5 TSPs
num_alerts = 450
rows = []
id_counter = 1
for alert_idx in range(1, num_alerts+1):
    identifier = f"DEMO-ALERT-{alert_idx:04d}-{random.randint(1000,9999)}"
    num_tsps = random.choices([2,3,4,5,1], weights=[25,35,25,10,5])[0]
    chosen_tsps = random.sample(TSPS, num_tsps)
    for tsp in chosen_tsps:
        rows.append((id_counter, identifier, tsp))
        id_counter += 1

# We have ~ len(rows) rows, should be ~2000. Adjust if needed
# If less than 2000, add more alerts
while len(rows) < 2000:
    alert_idx += 1
    identifier = f"DEMO-ALERT-{alert_idx:04d}-{random.randint(1000,9999)}"
    for tsp in random.sample(TSPS, random.randint(2,4)):
        rows.append((id_counter, identifier, tsp))
        id_counter += 1
        if len(rows) >= 2000: break

random.shuffle(rows)
rows = rows[:2000]

print(f"Total rows to generate: {len(rows)} distinct identifiers: {len(set(r[1] for r in rows))}")

# Build SQL
out_lines = []
out_lines.append("-- Demo seed for trai_audit — idempotent, truncate-first, safe to re-run")
out_lines.append("-- Generated: " + now.isoformat())
out_lines.append("-- Covers all 7 live checks with realistic mix + clean rows")
out_lines.append("")
out_lines.append("BEGIN;")
out_lines.append("TRUNCATE dm.t_tsp_sms_dissemination_statistics RESTART IDENTITY CASCADE;")
out_lines.append("TRUNCATE dm.t_tsp_contact_list RESTART IDENTITY CASCADE;")
out_lines.append("")

# Insert contacts
out_lines.append("-- Contacts: 2-3 per TSP, safe @example.com, with edge cases")
out_lines.append("-- MTNL will have ZERO active email-enabled contacts to demo NO_RECIPIENTS")
contacts = [
    (1, "Airtel", "All India", "Demo Nodal Officer — Airtel", "Nodal Officer", "airtel.nodal@example.com", "9000000001", "NULL", "true", "false", "null", 101),
    (2, "Airtel", "Delhi Circle", "Airtel Escalation Desk", "Dy. General Manager", "airtel.escalation@example.com", "9000000002", "NULL", "true", "false", "null", 102),
    (3, "Airtel", "Mumbai Circle", "Airtel Inactive Contact", "Manager", "airtel.inactive@example.com", "9000000003", "2026-08-15 10:00:00+05:30", "true", "false", "null", 103),
    (4, "BSNL", "All India", "Demo Nodal Officer — BSNL", "Nodal Officer", "bsnl.nodal@example.com", "9100000001", "NULL", "true", "true", "null", 201),
    (5, "BSNL", "Kerala Circle", "BSNL Kerala Desk", "Circle Head", "bsnl.kerala@example.com", "9100000002", "NULL", "true", "false", "null", 202),
    (6, "MTNL", "Delhi", "MTNL Deactivated Officer", "Nodal Officer", "mtnl.nodal@example.com", "9200000001", "2026-07-01 09:00:00+05:30", "true", "false", "null", 301),
    (7, "MTNL", "Mumbai", "MTNL Opted-Out Officer", "Manager", "mtnl.optout@example.com", "9200000002", "NULL", "false", "false", "null", 302),
    (8, "Reliance Jio", "All India", "Demo Nodal Officer — Jio", "Nodal Officer", "jio.nodal@example.com", "9300000001", "NULL", "true", "false", "null", 401),
    (9, "Reliance Jio", "Gujarat Circle", "Jio Gujarat Desk", "Circle Head", "jio.gujarat@example.com", "9300000002", "NULL", "true", "false", "null", 402),
    (10, "Reliance Jio", "Maharashtra", "Jio Opted-Out", "Manager", "jio.optout@example.com", "9300000003", "NULL", "false", "true", "null", 403),
    (11, "Vodafone-Idea", "All India", "Demo Nodal Officer — VI", "Nodal Officer", "vi.nodal@example.com", "9400000001", "NULL", "true", "false", "null", 501),
    (12, "Vodafone-Idea", "Karnataka", "VI Karnataka Desk", "Circle Head", "vi.karnataka@example.com", "9400000002", "NULL", "true", "false", "null", 502),
    (13, "Vodafone-Idea", "Tamil Nadu", "VI TN Desk", "Manager", "vi.tn@example.com", "9400000003", "NULL", "true", "false", "null", 503),
]
out_lines.append("INSERT INTO dm.t_tsp_contact_list (contact_id, tsp_name, boundary_restriction, name, designation, email_id, contact_number, created_on, deactivated_on, in_notification_list, email_notifications, sms_notifications, element_id) VALUES")
for i, c in enumerate(contacts):
    cid, tsp, br, name, desig, email, phone, deact, enl, snl, en, elem = c
    # created_on fixed
    created = "'2026-08-01 10:00:00+05:30'"
    deact_sql = f"'{deact}'" if deact != "NULL" else "NULL"
    enl_sql = enl
    # en is legacy? Actually in_notification_list etc - we have string
    line = f"({cid}, '{tsp}', '{br}', '{name}', '{desig}', '{email}', '{phone}', {created}, {deact_sql}, 'yes', {enl_sql}, false, {elem})"
    suffix = "," if i < len(contacts)-1 else ";"
    out_lines.append(line + suffix)
out_lines.append("")

# Now generate statistics rows
# We want ~50% clean (~1000), rest flagged across checks
# Assign each row a type
types = []
# We'll generate 1000 clean
types += ["clean"]*1000
# Flagged distributions
types += ["complete_failure"]*120
types += ["feedback_not_received"]*150
types += ["feedback_delay"]*200
types += ["prefetch_breach"]*160
types += ["total_breach"]*160
types += ["expired"]*180
types += ["arith_mismatch"]*130
# Unknown status rows (3) will be taken from clean pool later
random.shuffle(types)
types = types[:2000]
# Ensure we have 2000
while len(types) < 2000:
    types.append("clean")
random.shuffle(types)

# Track expected counts (approx)
from collections import Counter
print(Counter(types))

insert_prefix = "INSERT INTO dm.t_tsp_sms_dissemination_statistics (id, identifier, tsp_name, start_time, end_time, total_subscribers, total_delivery_success, total_delivery_failure, total_cell_count, status, entry_time, remarks_by_tsp, tsp_remarks_received_timestamp, response1_received_timestamp, response2_received_timestamp, remarks_by_capplatform, internal_testing_remarks, sms_count_success, prefetch_start_time, prefetch_end_time, prefetch_total_subscribers, prefetch_total_delivery_success, prefetch_total_delivery_failure, prefetch_response2_received_timestamp, prefetch_sms_count_success, delta_received, charges, total_expired, sms_count_expired) VALUES"

# For unknown status: pick 3 random indices
unknown_indices = set(random.sample(range(2000), 3))

for idx, (row_id, identifier, tsp) in enumerate(rows):
    typ = types[idx]
    # Random base times
    start = rand_ts(now, 30)
    # Some complete failures will override to NULL later
    duration_min = random.randint(10, 45)  # default clean duration 10-45 min
    cell = gen_cell()
    # Determine MRAD threshold
    thr_min = mrad_minutes(cell)
    # For breaches we will make duration exceed
    # For clean, ensure within thr
    # total_subscribers
    subs = random.randint(200, 25000)
    # Delivery counts
    # We'll compute after deciding expired/arith

    # Determine status: pick from choices unless unknown
    if idx in unknown_indices:
        status = "partial"
    else:
        status = random.choice(STATUS_CHOICES)

    entry = start - timedelta(minutes=random.randint(5,30)) if start else None
    remarks_tsp = None
    tsp_remarks_ts = None
    r1 = None
    r2 = None
    end = None
    pre_start = None
    pre_end = None
    delta = None
    total_expired = 0
    sms_expired = 0
    success = 0
    failure = 0
    sms_success = 0
    pre_subs = None
    pre_success = None
    pre_failure = None
    pre_r2 = None
    pre_sms = None
    cap_remarks = None
    charges = None

    if typ == "complete_failure":
        start = None
        end = None
        r1 = None
        r2 = None
        entry = rand_ts(now, 30)  # entry still exists but start/end null
        # keep other counts plausible but irrelevant
        success = 0
        failure = 0
        sms_success = 0
        pre_start = None
        pre_end = None
        delta = None
        cap_remarks = "No response received from TSP"
        total_expired = 0
        sms_expired = 0
        # cell still set for indexing but start/end null makes it failure
        # ensure arithmetic will be mismatch? Not needed, but clean failure only
        # Make arithmetic hold for these: total = 0? But subs is set, so would mismatch -> we want some mismatch but not all. Let's make subs = 0+0+0 for these to be clean arith
        subs = random.randint(500, 2000)
        # For complete failure we set success/failure/expired 0, so mismatch will be subs !=0 -> that would flag arith too. Avoid by setting expired = subs
        # Actually better to make arithmetic hold: success=0, failure=0, expired=subs
        total_expired = subs  # all expired since no dissemination
        sms_expired = random.randint(0, 100)
        success = 0
        failure = 0
        sms_success = 0
    else:
        # Non-failure: start/end will be set
        # For most, set duration appropriately
        if typ == "total_breach":
            # Make total duration exceed MRAD
            # cell already chosen, thr_min known
            excess = random.randint(5, 180)  # 5 min to 3h over
            dur = thr_min + excess
            # Also sometimes beyond matrix cell already >30k will be auto beyond
            end = start + timedelta(minutes=dur)
        elif typ == "clean":
            # keep within thr - 5 min buffer
            max_ok = max(5, thr_min - random.randint(2,10))
            dur = random.randint(5, max_ok)
            end = start + timedelta(minutes=dur)
        else:
            # default duration within thr for non-breach types, but could still be over randomly 10% to allow overlap
            if random.random() < 0.1 and typ not in ["prefetch_breach"]:
                # random overlap: 10% also breach even if not primary type
                dur = thr_min + random.randint(5,60)
            else:
                dur = random.randint(5, max(5, thr_min - 2))
            end = start + timedelta(minutes=dur)

        # Response timestamps
        if typ == "feedback_not_received":
            # end set but r2 NULL, or delta not yes despite prefetch
            # 50/50 choice
            if random.random() < 0.6:
                r2 = None
                r1 = start + timedelta(minutes=random.randint(1,5)) if random.random()<0.8 else None
            else:
                r2 = start + timedelta(minutes=random.randint(1,10))  # r2 exists but delta not yes
                r1 = r2 - timedelta(minutes=1)
                delta = random.choice([None, "no", "pending"])
                pre_start = start - timedelta(hours=1, minutes=random.randint(5,30))
                pre_end = pre_start + timedelta(minutes=random.randint(10,30))
                pre_subs = int(subs * random.uniform(0.85,0.98))
                pre_success = int(pre_subs * 0.9)
                pre_failure = pre_subs - pre_success - random.randint(0,20)
                pre_r2 = pre_end + timedelta(minutes=random.randint(2,10))
                pre_sms = pre_success
                # ensure expired logic not interfering
        elif typ == "feedback_delay":
            excess_min = random.choice([2,5,12,30,120,180]) + random.randint(0,10)
            r2 = end + timedelta(minutes=10, seconds=excess_min*60)  # actually 10 min thr + excess -> r2 = end + 10min + excess
            # Actually r2 - end = 10min + excess => adjust
            r2 = end + timedelta(seconds=600 + excess_min*60 + random.randint(0,120))
            r1 = end + timedelta(minutes=random.randint(1,3))
        else:
            # normal: r2 within threshold
            r2 = end + timedelta(minutes=random.randint(1,8), seconds=random.randint(0,59))
            r1 = end + timedelta(seconds=random.randint(30,180))

        # Prefetch
        if typ == "prefetch_breach":
            # prefetch duration exceed its band
            # use same cell thr, but prefetch duration 90 min over for small cell
            pre_start = start - timedelta(hours=2, minutes=random.randint(0,30))
            excess = random.randint(5,120)
            pre_dur = thr_min + excess
            pre_end = pre_start + timedelta(minutes=pre_dur)
            pre_subs = int(subs * 0.9)
            pre_success = int(pre_subs * 0.85)
            pre_failure = pre_subs - pre_success - random.randint(0,10)
            pre_r2 = pre_end + timedelta(minutes=random.randint(2,8))
            pre_sms = pre_success
            delta = "yes" if random.random()<0.7 else random.choice(["yes","pending"])
        elif typ in ["expired", "arith_mismatch", "total_breach", "clean"] and random.random()<0.6:
            # many rows have prefetch normally but within thr
            if random.random()<0.6:
                pre_start = start - timedelta(hours=1, minutes=random.randint(10,40))
                pre_dur = random.randint(10, max(10, thr_min - 5))
                pre_end = pre_start + timedelta(minutes=pre_dur)
                pre_subs = int(subs * random.uniform(0.88,0.97))
                pre_success = int(pre_subs * 0.9)
                pre_failure = max(0, pre_subs - pre_success - random.randint(5,30))
                pre_r2 = pre_end + timedelta(minutes=random.randint(3,12))
                pre_sms = pre_success
                delta = "yes"
            else:
                pre_start = None
                pre_end = None
                delta = None
        elif typ == "feedback_not_received":
            # already handled prefetch case above, for r2 null case, no prefetch needed
            pass
        else:
            if random.random()<0.4:
                pre_start = start - timedelta(minutes=random.randint(30,90))
                pre_end = pre_start + timedelta(minutes=random.randint(8, thr_min-5 if thr_min>10 else 10))
                pre_subs = int(subs*0.9)
                pre_success = int(pre_subs*0.9)
                pre_failure = pre_subs - pre_success
                pre_r2 = pre_end + timedelta(minutes=5)
                pre_sms = pre_success
                delta = "yes"

        # Expired and success/failure
        if typ == "expired":
            total_expired = random.randint(10, int(subs*0.3))
            sms_expired = random.randint(5, int(total_expired*0.5))
            # remaining distributed to success/failure
            remaining = subs - total_expired
            success = int(remaining * random.uniform(0.7,0.95))
            failure = remaining - success
            sms_success = success - random.randint(0, min(50, success))
            if random.random()<0.2:
                cap_remarks = "Unable to connect to {} server".format(tsp)
            elif random.random()<0.1:
                cap_remarks = "<html><body>504 Gateway Timeout</body></html>"
        elif typ == "arith_mismatch":
            # make mismatch
            total_expired = random.randint(0, int(subs*0.2))
            success = int(subs * 0.6)
            failure = int(subs * 0.2)
            # sum = 0.8 subs + expired -> will not equal subs unless expired 0.2 subs exactly, so make off by random
            # Force mismatch by + random offset
            success = success + random.randint(10,100)
            sms_expired = random.randint(0, 20)
            sms_success = success - random.randint(0,30)
        elif typ == "complete_failure":
            pass  # already set
        else:
            # normal arithmetic: success+failure+expired = subs
            if random.random()<0.85:
                total_expired = random.randint(0, int(subs*0.05)) if random.random()<0.3 else 0
                sms_expired = random.randint(0, total_expired) if total_expired>0 else 0
                remaining = subs - total_expired
                success = int(remaining * random.uniform(0.85,0.98))
                failure = remaining - success
                sms_success = success - random.randint(0, 20)
            else:
                # small mismatch even for clean 15% chance gives overlap
                total_expired = random.randint(0, 30)
                success = int(subs*0.7)
                failure = subs - success - total_expired + random.randint(-20,20)
                sms_success = success

        # Ensure non-negative
        for v in [success, failure, total_expired, sms_success, sms_expired]:
            if v is not None and v <0: v=0

        # Cap remarks for non-expired mostly null
        if cap_remarks is None and random.random()<0.05:
            cap_remarks = None if random.random()<0.7 else "OK"

        # Ensure delta logic for prefetch breach vs normal
        if pre_start is not None and delta is None:
            delta = "yes" if random.random()<0.85 else "no"

    # Build row tuple for SQL
    # Handle None vs value
    def q(v):
        if v is None: return "NULL"
        if isinstance(v, str):
            # escape single quotes
            return "'" + v.replace("'", "''") + "'"
        if isinstance(v, datetime):
            return fmt_ts(v)
        return str(v)

    # For this iteration, we have variables: subs, success, failure, cell, status, entry, etc.
    # Need to map to columns order: id, identifier, tsp_name, start_time, end_time, total_subscribers, total_delivery_success, total_delivery_failure, total_cell_count, status, entry_time, remarks_by_tsp, tsp_remarks_received_timestamp, response1_received_timestamp, response2_received_timestamp, remarks_by_capplatform, internal_testing_remarks, sms_count_success, prefetch_start_time, prefetch_end_time, prefetch_total_subscribers, prefetch_total_delivery_success, prefetch_total_delivery_failure, prefetch_response2_received_timestamp, prefetch_sms_count_success, delta_received, charges, total_expired, sms_count_expired
    vals = [
        str(row_id), q(identifier), q(tsp), fmt_ts(start), fmt_ts(end), q(subs), q(success), q(failure), q(cell), q(status), fmt_ts(entry), "NULL", "NULL", fmt_ts(r1), fmt_ts(r2), q(cap_remarks), "NULL", q(sms_success), fmt_ts(pre_start), fmt_ts(pre_end), q(pre_subs), q(pre_success), q(pre_failure), fmt_ts(pre_r2), q(pre_sms), q(delta), "NULL", q(total_expired), q(sms_expired)
    ]
    out_lines.append(insert_prefix + " (" + ", ".join(vals) + ");")

out_lines.append("")
out_lines.append("COMMIT;")
out_lines.append("")
out_lines.append("-- Verification queries (run after):")
out_lines.append("-- SELECT COUNT(*) FROM dm.t_tsp_sms_dissemination_statistics; -- expect ~2000")
out_lines.append("-- SELECT COUNT(DISTINCT identifier) FROM dm.t_tsp_sms_dissemination_statistics; -- 400-500")

# Write file
with open("scripts/seed_demo_data.sql", "w", encoding="utf-8") as f:
    f.write("\n".join(out_lines))

print("Wrote scripts/seed_demo_data.sql with", len(rows), "rows")
