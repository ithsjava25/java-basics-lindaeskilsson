package com.example;

import com.example.api.ElpriserAPI; // importera API:et

import java.text.NumberFormat; // Format för tal
import java.time.LocalDate; // Representerar datum
import java.time.format.DateTimeFormatter; // Formaterar datum till strängar eller tolkar strängar till datum
import java.util.*; // Importerar datastrukturer som List, Map, Set, Collections etc.


public class Main {

    // timFormatter formaterar tiden till timmar (00–23) för utskrift
    // nf formaterar priser till svenska tal med alltid 2 decimaler (t.ex. 12,30 öre)
    static DateTimeFormatter timFormatter = DateTimeFormatter.ofPattern("HH");
    static NumberFormat nf = NumberFormat.getNumberInstance(Locale.of("sv", "SE"));

    static {
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
    }

    public static void main(String[] args) {

        ElpriserAPI api = new ElpriserAPI();

        Locale.setDefault(Locale.of("sv", "SE"));
        System.out.println("Hej och välkommen till Elpris-kollen");

        // Flaggor
        // valdZon = elområde, datumStr = valt datum, laddInput = laddningstid, sorteraFallande = true om --sorted används
        String valdZon = null;
        String datumStr = null;
        String laddInput = null;
        boolean sorteraFallande = false;

        // giltiga zoner
        List<String> zoner = List.of("SE1", "SE2", "SE3", "SE4");

        if (args.length == 0) {
            helpMessage();
            return;
        }

        // loopa igenom argument
        // Kolla vilken flagga (--zone, --date, --charging, --sorted, --help) som matchar
        // och spara dess värde i rätt variabel. Om flaggan är okänd skrivs ett felmeddelande ut.
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--zone" -> { if (i+1 < args.length) valdZon = args[++i]; }
                case "--date" -> { if (i+1 < args.length) datumStr = args[++i]; }
                case "--charging" -> { if (i+1 < args.length) laddInput = args[++i]; }
                case "--sorted" -> sorteraFallande = true;
                case "--help" -> { helpMessage(); return; }
                default -> System.out.println("Okänd flagga: " + arg);
            }
        }

        // datum
        LocalDate datum;
        if (datumStr != null) {
            try {
                datum = LocalDate.parse(datumStr);
            } catch (Exception e) {
                System.out.println("Ogiltigt datum: " + datumStr);
                return;
            }
        } else {
            datum = LocalDate.now();
        }

        LocalDate imorgon = datum.plusDays(1);

        // zon
        if (valdZon == null || !zoner.contains(valdZon.toUpperCase())) {
            System.out.println("Ogiltig zon: " + valdZon);
            helpMessage();
            return;
        }

        ElpriserAPI.Prisklass zon = ElpriserAPI.Prisklass.valueOf(valdZon.toUpperCase());

        List<ElpriserAPI.Elpris> priserIdag = api.getPriser(datum, zon);
        List<ElpriserAPI.Elpris> priserImorgon = api.getPriser(imorgon, zon);

        if (priserIdag == null || priserIdag.isEmpty()) {
            System.out.println("Ingen data tillgänglig / inga priser att visa");
            return;
        }

        // kombinera listor
        List<ElpriserAPI.Elpris> allaPriser = new ArrayList<>(priserIdag);
        Optional.ofNullable(priserImorgon).ifPresent(allaPriser::addAll);

        // kolla om laddningsfönster behövs
        if (laddInput != null) {
            int timmar = 0;
            try {
                timmar = Integer.parseInt(laddInput.replace("h", ""));
            } catch (NumberFormatException e) {
                System.out.println("Fel på laddningsinput: " + laddInput);
            }
            if (timmar > 0) {
                cheapestCharging(allaPriser, timmar);
                return;
            }
        }

        // sortering
        if (sorteraFallande) {
            allaPriser.sort(Comparator.comparing(ElpriserAPI.Elpris::sekPerKWh).reversed());
            printPrices(allaPriser);
            return;
        }

        // annars visa vanliga
        if (allaPriser.size() == 96) {
            displayHourlyPrices(allaPriser);
        } else {
            printPrices(allaPriser);
        }

        minMax(priserIdag);
        avgPrice(priserIdag);
    }


    // Visar elpriser aggregerat till hela timmar
    // Tar en lista med 96 kvartstimmar och slår ihop dem 4 och 4 till 24 timmar
    static void displayHourlyPrices(List<ElpriserAPI.Elpris> priceIntervals) {
        if (priceIntervals == null || priceIntervals.isEmpty()) {
            System.out.println("Ingen data för 96-priser");
            return;
        }

        for (int i = 0; i < priceIntervals.size(); i += 4) {
            List<ElpriserAPI.Elpris> kvart = priceIntervals.subList(i, i + 4);

            double sum = kvart.stream().mapToDouble(ElpriserAPI.Elpris::sekPerKWh).sum();
            double medel = sum / 4.0;

            int timme = i / 4;
            int timme2 = (timme + 1) % 24;

            String tidspann = String.format("%02d-%02d", timme, timme2);
            System.out.printf("%s %s öre%n", tidspann, nf.format(medel * 100));
        }
    }
    // Skriver ut alla priser i listan med starttid, sluttid och pris i öre
    static void printPrices(List<ElpriserAPI.Elpris> lista) {
        for (ElpriserAPI.Elpris pris : lista) {
            System.out.printf("%s-%s %s öre%n",
                    pris.timeStart().format(timFormatter),
                    pris.timeEnd().format(timFormatter),
                    nf.format(pris.sekPerKWh() * 100));
        }
    }

    // Hittar det billigaste laddningsfönstret för en tidsperiod
    // Går igenom listan av elpriser och beräknar vilket startindex som ger lägst genomsnittspris

    static void cheapestCharging(List<ElpriserAPI.Elpris> lista, int timmar) {
        if (lista == null || lista.size() < timmar) {
            System.out.println("För lite data för laddning.");
            return;
        }

        double bestHour = Double.MAX_VALUE;
        int start = -1;

        for (int i = 0; i <= lista.size() - timmar; i++) {
            double summa = 0;
            for (int j = 0; j < timmar; j++) {
                summa += lista.get(i+j).sekPerKWh();
            }
            if (summa < bestHour) {
                bestHour = summa;
                start = i;
            }
        }

        if (start >= 0) {
            DateTimeFormatter minutFormatter = DateTimeFormatter.ofPattern("HH:mm");
            String tid = lista.get(start).timeStart().format(minutFormatter);
            double snittPris = bestHour / timmar * 100;

            System.out.printf(
                    "Billigaste %dh startar kl %s%nMedelpris för fönster: %s öre%nPåbörja laddning %s%n",
                    timmar, tid, nf.format(snittPris), tid
            );
        }
    }

    // Hittar och skriver ut det lägsta och högsta elpriset i listan
    // Jämför varje pris och sparar tidpunkten för när det är billigast/dyrast
    static void minMax(List<ElpriserAPI.Elpris> lista) {
        if (lista == null || lista.isEmpty()) {
            System.out.println("Ingen data");
            return;
        }

        double minPrice = Double.MAX_VALUE, maxPris = Double.MIN_VALUE;
        String minTid = "", maxTid = "";

        for (ElpriserAPI.Elpris p : lista) {
            double v = p.sekPerKWh();
            if (v < minPrice) { minPrice = v; minTid = p.timeStart().format(timFormatter); }
            if (v > maxPris) { maxPris = v; maxTid = p.timeStart().format(timFormatter); }
        }

        System.out.printf("Lägsta pris: %s öre Kl: %s%n", nf.format(minPrice * 100), minTid);
        System.out.printf("Högsta pris: %s öre Kl: %s%n", nf.format(maxPris * 100), maxTid);
    }

    // Beräknar och skriver ut medelpriset för alla elpriser i listan
    static void avgPrice(List<ElpriserAPI.Elpris> lista) {
        if (lista == null || lista.isEmpty()) {
            System.out.println("Ingen data för medelpris");
            return;
        }
        double sum = 0;
        for (ElpriserAPI.Elpris p : lista) sum += p.sekPerKWh();
        double medel = sum / lista.size();
        System.out.printf("Medelpris: %s öre%n", nf.format(medel*100));
    }

    // Skriver ut en hjälptext som förklarar hur programmet ska användas
    // Visar tillgängliga flaggor och exempel på hur man kör programmet

    static void helpMessage() {
        System.out.println("""
Usage:
  java -cp target/classes com.example.Main

Alternativ:
  --zone SE1|SE2|SE3|SE4   (obligatorisk)
  --date YYYY-MM-DD        (valfri)
  --charging 2h|4h|8h      (valfri)
  --sorted                 (valfri)
  --help                   (denna text)
""");
    }
}


/*
java -cp target/classes com.example.Main --zone SE3 --date 2025-09-04
java -cp target/classes com.example.Main --zone SE1 --charging 4h
java -cp target/classes com.example.Main --zone SE2 --date 2025-09-04 --sorted
java -cp target/classes com.example.Main --help
 */