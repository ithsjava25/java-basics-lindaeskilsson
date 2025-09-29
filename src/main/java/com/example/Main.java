package com.example;

// Import av API
import com.example.api.ElpriserAPI;

import java.time.LocalDate;               // Datum utan tid/zon, för --date dagsanrop
import java.time.ZoneId;                  // Identifierar tidszon
import java.time.format.DateTimeFormatter; // Formaterar datum "yyyy-MM-dd"

import java.util.List;                    // Gränssnitt för ordnade listor. Metod-returntyp: List<>
import java.util.Map;                     // Nyckel -> värde - tabell. Bra för CLI-argument: --zone -> "SE3"
import java.util.LinkedHashSet;           // Set som behåller insättningsordningen och tar bort dubletter.
import java.util.Set;                     // Mängd utan dubletter. Bra för VALID_ZONES = {SE1, SE2, SE3, SE4}

public class Main {

    // Sätter svensk tid för idag.
    private static final ZoneId SWEDEN = ZoneId.of("Europe/Stockholm");

    // Tillåtna zoner i snygg ordning
    private static final Set<String> VALID_ZONES =
            new LinkedHashSet<>(List.of("SE1", "SE2", "SE3", "SE4"));

    // Datumformatering
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // En klass som håller de tolkade inställningarna
    static class Options {
        final String zone;
        final LocalDate date;

        Options(String zone, LocalDate date) {
            this.zone = zone;
            this.date = date;
        }
    }

    public static void main(String[] args) {
        // Tolka och validera CLI-flaggorna
        Options opts = parseArgs(args);

        // Bekräftar vad programmet har förstått datan
        System.out.println("Förvalda inställningar:");
        System.out.println("  zon   = " + opts.zone);
        System.out.println("  datum = " + opts.date);

        // API för anrop (själva anropet gör vi i nästa steg)
        ElpriserAPI elpriserAPI = new ElpriserAPI();
    }

    // Hjälpmetoder: små stöd-funktioner som används av main.
    // Bidrar till att gör koden mer organiserad och hanterar CLI-flaggor, fel och hjälptext.


    // Tolkar CLI-flaggor och returnerar ett Options-objekt med zon och datum.
    private static Options parseArgs(String[] args) {
        // Standardvärden (rimliga defaults)
        String zone = "SE3";
        LocalDate date = LocalDate.now(SWEDEN);

        Map<String, String> kv = toKeyValue(args);

        if (kv.containsKey("--help") || kv.containsKey("-h")) {
            printHelpAndExit(0);
        }

        if (kv.containsKey("--zone")) {
            String z = kv.get("--zone");
            if (z == null) fail("Flaggan --zone måste följas av SE1, SE2, SE3 eller SE4.");
            z = z.toUpperCase();
            if (!VALID_ZONES.contains(z)) {
                fail("Ogiltig zon: " + z + ". Tillåtna är: " + VALID_ZONES);
            }
            zone = z;
        }

        if (kv.containsKey("--date")) {
            String d = kv.get("--date");
            if (d == null) fail("Flaggan --date måste följas av ett datum, t.ex. 2025-09-29.");
            try {
                date = LocalDate.parse(d, DATE_FMT);
            } catch (Exception e) {
                fail("Ogiltigt datumformat: " + d + ". Använd formatet " + DATE_FMT);
            }
        }

        return new Options(zone, date);
    }

    /**
     * Mycket enkel parser för "--nyckel värde"-par.
     * Ex: --zone SE3 --date 2025-09-29  ->  {"--zone":"SE3","--date":"2025-09-29"}
     * Stödjer även --help / -h som nycklar utan värde.
     */

    // Gör om args[] till en nyckel→värde-tabell (Map) för enklare hantering.
    private static Map<String, String> toKeyValue(String[] args) {
        Map<String, String> m = new java.util.HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--help".equals(a) || "-h".equals(a)) {
                m.put(a, "");
                continue;
            }
            if (a.startsWith("--")) {
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    m.put(a, args[++i]); // ta nästa som värde
                } else {
                    fail("Flaggan " + a + " kräver ett värde.");
                }
            } else {
                fail("Okänd parameter: " + a + " (alla flaggor ska börja med --)");
            }
        }
        return m;
    }


    // Skriver ut hjälptexten och avslutar programmet (används vid --help eller fel).
    private static void printHelpAndExit(int code) {
        System.out.println("""
                Användning:
                  java -jar elpris.jar [--zone SE1|SE2|SE3|SE4] [--date yyyy-MM-dd] [--help]

                Exempel:
                  java -jar elpris.jar
                  java -jar elpris.jar --zone SE4
                  java -jar elpris.jar --date 2025-09-29
                  java -jar elpris.jar --zone SE2 --date 2025-10-01

                Standardvärden (om inget anges):
                  --zone SE3
                  --date (dagens datum i Europe/Stockholm)
                """);
        System.exit(code);
    }



    // Skriver ut felmeddelande och anropar printHelpAndExit för att stoppa programmet.
    private static void fail(String msg) {
        System.err.println("Fel uppstått: " + msg);
        printHelpAndExit(1);
    }
}
