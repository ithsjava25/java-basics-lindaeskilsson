package com.example;

// Import av API-klassen
import com.example.api.ElpriserAPI;

import java.io.Console;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class Main {

    //metod för att räka ut genomsnittspris
    static double calculateAverage(List<ElpriserAPI.Elpris> priser) {
        double sum = 0;
        for (ElpriserAPI.Elpris pris : priser) {
            sum += pris.sekPerKWh(); // summera alla priser
        }
        return priser.isEmpty() ? 0 : sum / priser.size(); // dela med antal
    }

    // en metod för att hitta den billigaste timmen
    static ElpriserAPI.Elpris findMin(List<ElpriserAPI.Elpris> priser) {
        ElpriserAPI.Elpris billigast = null;
        for (ElpriserAPI.Elpris pris : priser) {
            if (billigast == null || pris.sekPerKWh() < billigast.sekPerKWh()) {
                billigast = pris; // byt ut om vi hittat billigare
            }
        }
        return billigast;
    }

    // En metod för att hitta den dyraste timmen
    static ElpriserAPI.Elpris findMax(List<ElpriserAPI.Elpris> priser) {
        ElpriserAPI.Elpris dyrast = null;
        for (ElpriserAPI.Elpris pris : priser) {
            if (dyrast == null || pris.sekPerKWh() > dyrast.sekPerKWh()) {
                dyrast = pris; // byt ut om vi hittat dyrare
            }
        }
        return dyrast;
    }

    // metod för att hitta bästa ladningsfönstret
    static String findBestWindow(List<ElpriserAPI.Elpris> priser, int timmar) {
        if (priser.size() < timmar) {
            return "För lite data";
        }

        double bästaSumma = Double.MAX_VALUE; // stort startvärde
        int bästaStart = 0; // index för bästa starttimme

        // Loopa genom alla möjliga startpunkter
        for (int i = 0; i <= priser.size() - timmar; i++) {
            double sum = 0;
            for (int j = 0; j < timmar; j++) {
                sum += priser.get(i + j).sekPerKWh();
            }
            if (sum < bästaSumma) {
                bästaSumma = sum;
                bästaStart = i;
            }
        }

        ElpriserAPI.Elpris start = priser.get(bästaStart);
        return start.timeStart() + " -> " + timmar + "h (totalt " + String.format("%.2f", bästaSumma) + " öre)";
    }

    // en metod för att läsa argument från kommandoraden
    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) { // bara flaggor som börjar med --
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    map.put(a, args[++i]); // flagga med värde, ex --zone SE3
                } else {
                    map.put(a, ""); // flagga utan värde, t.ex --help
                }
            }
        }
        return map;
    }

    // printar ut hjälptexten.
    static void printHelp() {
        System.out.println("""
        Electricity Price Optimizer CLI
        Användning: java -cp target/classes com.example.Main [alternativ]

        Välj ett alternativ:
                --zone SE1|SE2|SE3|SE4        (required)
                --date YYYY-MM-DD             (optional, defaults to current date)
                --sorted                      (optional, to display prices in descending order)
                --charging 2h|4h|8h           (optional, to find optimal charging windows)
                --help                        (optional, to display usage information)

        Exempel:
          java -cp target/classes com.example.Main --zone SE3 --date 2025-09-04
          java -cp target/classes com.example.Main --zone SE1 --charging 4h
          java -cp target/classes com.example.Main --zone SE2 --sorted
          java -cp target/classes com.example.Main --help
        """);
    }


    public static void main(String[] args) {

        // 1. Läs in argument från consol
        var input = parseArgs(args);

        // 2. Visa hjälptext om --help finns
        if (input.containsKey("--help")) {
            printHelp();
            return;
        }

        // 3. Skapa console (för input från användaren)
        Console console = System.console();
        if (console == null) {
            // I IDE (t.ex. IntelliJ) fungerar inte console.
            System.out.println("OBS: System.console() är null i din IDE. Kör i terminalen för att använda readLine().");
            return;
        }

        // 4. Kolla zon (SE1–SE4). Om fel, fråga användaren igen.
        String zone = input.getOrDefault("--zone", "");
        while (zone.isEmpty() || !List.of("SE1", "SE2", "SE3", "SE4").contains(zone)) {
            if (!zone.isEmpty()) {
                System.out.println("Fel: Ogiltig zon. Välj mellan SE1, SE2, SE3 eller SE4.");
            }
            zone = console.readLine("Välj elzon (SE1, SE2, SE3, SE4): ").trim();
        }

        // 5. Datum (standard = idag)
        String dateStr = input.getOrDefault("--date", LocalDate.now().toString());
        LocalDate date = LocalDate.parse(dateStr);

        // 6. Skapa API och hämta priser
        ElpriserAPI api = new ElpriserAPI();
        List<ElpriserAPI.Elpris> priser = api.getPriser(date, ElpriserAPI.Prisklass.valueOf(zone));

        if (priser.isEmpty()) {
            System.out.println("Ingen data tillgänglig för " + date + " i " + zone);
            return;
        }

        // 7. Om --sorted finns, sortera från dyrast till billigast
        if (input.containsKey("--sorted")) {
            priser.sort(Comparator.comparingDouble(ElpriserAPI.Elpris::sekPerKWh).reversed());
            System.out.println("Priser (fallande):");
            for (ElpriserAPI.Elpris p : priser) {
                System.out.printf("%s -> %.2f öre/kWh\n", p.timeStart(), p.sekPerKWh());
            }
        }

        // 8. Statistik: medel, billigast, dyrast
        double avg = calculateAverage(priser);
        ElpriserAPI.Elpris billigast = findMin(priser);
        ElpriserAPI.Elpris dyrast = findMax(priser);

        System.out.println("Statistik för " + date + " (" + zone + "):");
        System.out.println("Genomsnittspris: " + String.format("%.2f", avg) + " öre/kWh");
        System.out.println("Billigaste timmen: " + billigast.timeStart() + " -> " + billigast.sekPerKWh() + " öre/kWh");
        System.out.println("Dyraste timmen: " + dyrast.timeStart() + " -> " + dyrast.sekPerKWh() + " öre/kWh");

        // 9. Laddningsfönster om --charging finns
        String charging = input.getOrDefault("--charging", "");
        if (!charging.isEmpty()) {
            int timmar = -1;
            while (timmar == -1) {
                switch (charging) {
                    case "2h" -> timmar = 2;
                    case "4h" -> timmar = 4;
                    case "8h" -> timmar = 8;
                    default -> {
                        System.out.println("Fel: Endast 2h, 4h eller 8h stöds.");
                        charging = console.readLine("Välj laddningstid (2h, 4h, 8h): ").trim();
                    }
                }
            }
            String fönster = findBestWindow(priser, timmar);
            System.out.println("Optimalt laddningsfönster (" + timmar + "h): " + fönster);
        }
    }
}
