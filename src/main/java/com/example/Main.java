package com.example;

// Import av API-klassen
import com.example.api.ElpriserAPI;

import java.io.Console;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public class Main {

    // metod för att räkna ut genomsnittspris
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

    // en metod för att hitta den dyraste timmen
    static ElpriserAPI.Elpris findMax(List<ElpriserAPI.Elpris> priser) {
        ElpriserAPI.Elpris dyrast = null;
        for (ElpriserAPI.Elpris pris : priser) {
            if (dyrast == null || pris.sekPerKWh() > dyrast.sekPerKWh()) {
                dyrast = pris; // byt ut om vi hittat dyrare
            }
        }
        return dyrast;
    }

    // metod för att hitta bästa laddningsfönstret
    static String findBestWindow(List<ElpriserAPI.Elpris> priser, int timmar) {
        if (priser.size() < timmar) {
            return "För lite data";
        }

        double cheapestPrice = Double.MAX_VALUE; // stort startvärde
        int cheapestStartHour = 0; // index för bästa starttimme

        // Loopa genom alla möjliga startpunkter
        for (int i = 0; i <= priser.size() - timmar; i++) {
            double sum = 0;
            for (int j = 0; j < timmar; j++) {
                sum += priser.get(i + j).sekPerKWh();
            }
            if (sum < cheapestPrice) {
                cheapestPrice = sum;
                cheapestStartHour = i;
            }
        }

        ElpriserAPI.Elpris start = priser.get(cheapestStartHour);
        return start.timeStart() + " -> " + timmar + "h (totalt " + String.format("%.2f", cheapestPrice) + " öre)";
    }

    // skriver ut hjälptext i terminal
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

        // Egna variabler istället för Map
        String zone = "";
        String dateStr = LocalDate.now().toString(); // standard = idag
        boolean sorted = false;
        String charging = "";
        boolean help = false;

        // Läs igenom argumenten från kommandoraden
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--zone":
                    if (i + 1 < args.length) {
                        zone = args[++i]; // ta nästa som värde
                    }
                    break;
                case "--date":
                    if (i + 1 < args.length) {
                        dateStr = args[++i];
                    }
                    break;
                case "--sorted":
                    sorted = true; // flagga utan värde
                    break;
                case "--charging":
                    if (i + 1 < args.length) {
                        charging = args[++i];
                    }
                    break;
                case "--help":
                    help = true;
                    break;
            }
        }

        // Om --help finns, skriv ut och avsluta
        if (help) {
            printHelp();
            return;
        }

        // Console (för input från användaren)
        Console console = System.console();
        if (console == null) {
            System.out.println("OBS: System.console() är null i din IDE. Kör i terminalen för att använda readLine().");
            return;
        }

        // Kolla zon (SE1–SE4). Om fel, fråga användaren igen tills zon vald.
        while (zone.isEmpty() || !List.of("SE1", "SE2", "SE3", "SE4").contains(zone)) {
            if (!zone.isEmpty()) {
                System.out.println("Fel: Ogiltig zon. Välj mellan SE1, SE2, SE3 eller SE4.");
            }
            zone = console.readLine("Välj elzon (SE1, SE2, SE3, SE4): ").trim();
        }

        // Sätter datum
        LocalDate date = LocalDate.parse(dateStr);

        // Hämta priser från API
        ElpriserAPI api = new ElpriserAPI();
        List<ElpriserAPI.Elpris> priser = api.getPriser(date, ElpriserAPI.Prisklass.valueOf(zone));

        if (priser.isEmpty()) {
            System.out.println("Ingen data tillgänglig för " + date + " i " + zone);
            return;
        }

        // Sortering av priser i fallande ordning
        if (sorted) {
            priser.sort(Comparator.comparingDouble(ElpriserAPI.Elpris::sekPerKWh).reversed());
            System.out.println("Priser (fallande):");
            for (ElpriserAPI.Elpris p : priser) {
                System.out.printf("%s -> %.2f öre/kWh\n", p.timeStart(), p.sekPerKWh());
            }
        }

        //Statistik
        double avarage = calculateAverage(priser);
        ElpriserAPI.Elpris billigast = findMin(priser);
        ElpriserAPI.Elpris dyrast = findMax(priser);
        System.out.println("  ");// för att skapa lite mer lättläst output när vi kör koden.
        System.out.println("Statistik för " + date + " (" + zone + "):");
        System.out.println("Genomsnittspris: " + String.format("%.2f", avarage) + " öre/kWh");
        System.out.println("Billigaste timmen: " + billigast.timeStart() + " -> " + billigast.sekPerKWh() + " öre/kWh");
        System.out.println("Dyraste timmen: " + dyrast.timeStart() + " -> " + dyrast.sekPerKWh() + " öre/kWh");

        // Laddningsfönster
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
            String priceWindow = findBestWindow(priser, timmar);
            System.out.println("Optimalt laddningsfönster (" + timmar + "h): " + priceWindow);
        }
    }
}
