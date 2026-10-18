import json, urllib.request, pathlib, time

tok = pathlib.Path("/home/aaarifajar/.config/okx-notif/api_token").read_text().strip()
req = urllib.request.Request("http://127.0.0.1:8765/screening.json",
                             headers={"Authorization": "Bearer " + tok})
d = json.load(urllib.request.urlopen(req, timeout=20))

def px(v):
    if v is None:
        return "-"
    if v >= 1000:
        return "{:,.0f}".format(v)
    if v >= 1:
        return "{:,.2f}".format(v)
    return "{:.4f}".format(v)

def fmt(v):
    return ("+" if v >= 0 else "") + "{:,.2f}".format(v)

coins = [c for c in d.get("coins", []) if c.get("px", 0) > 1][:2]
total = sum(c["pnl"] for c in coins if c.get("pnl") is not None)
title = "Total PnL " + ("$" + format(abs(total), ",.2f") if total >= 0 else "-$" + format(abs(total), ",.2f"))

btc_chg = d.get("btcChg") or 0.0
b_arrow = "\u25b2" if btc_chg >= 0 else "\u25bc"
btc_row = "BTC $" + px(d.get("btcPx")) + " \u00b7 " + b_arrow + " " + "{:+.1f}".format(btc_chg) + "%"

rows = [btc_row]
for c in coins:
    chg = c.get("chg") or 0.0
    a = "\u25b2" if chg >= 0 else "\u25bc"
    line = c["sym"] + " $" + px(c.get("px")) + " \u00b7 " + a + " " + "{:+.1f}".format(chg) + "%"
    if c.get("pnl") is not None:
        line += " \u00b7 " + ("+" if c["pnl"] >= 0 else "-") + "$" + format(abs(c["pnl"]), ",.2f")
    rows.append(line)

print("=== COLLAPSED ===")
print("JUDUL : " + title)
print("ISI   : " + " \u00b7 ".join(
    [("BTC " + "{:+.1f}".format(btc_chg) + "%")] +
    [c["sym"] + " " + "{:+.1f}".format(c.get("chg") or 0.0) + "%" for c in coins]))
print("=== EXPANDED (3 baris) ===")
print("JUDUL : " + title)
for r in rows:
    print("        " + r)
print("=== SUMBER ===")
print("wib  :", d.get("wib"))
print("json :", time.strftime("%d %b %H:%M", time.localtime()))
