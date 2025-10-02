package com.example;

import com.example.api.ElpriserAPI;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;


@SuppressWarnings("D")
public class Main {
    public static void main(String[] args) {
        ElpriserAPI elpriserAPI = new ElpriserAPI(false);
        String Zone = "";
        double minPrice = Double.POSITIVE_INFINITY;
        double maxPrice = Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        int count = 0;
        double lowestSum = Double.POSITIVE_INFINITY;
        int StartIndex = -1;


        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("0.00", symbols);


        System.out.println(Arrays.toString(args));

        if (args.length == 0 || containsHelp(args))
        {
            printHelp();
            return;
        }


        for (int i = 0; i < args.length; i++)
        {
            if (args[i].equalsIgnoreCase("--zone"))
            {
                Zone = args[i + 1];
                break;
            }
        }
        if (Zone == null)
        {
            System.out.println("Missing required --zone SE1|SE2|SE3|SE4");
            return;
        }


        ElpriserAPI.Prisklass zone;
        try
        {
            zone = ElpriserAPI.Prisklass.valueOf(Zone.toUpperCase());
        }
        catch (IllegalArgumentException e)
        {
            System.out.println("Invalid zone. Restart and enter SE1, SE2, SE3 or SE4.");
            return;
        }


        LocalDate date = LocalDate.now();
        for (int i = 0; i < args.length - 1; i++)
        {
            if (args[i].equalsIgnoreCase("--date"))
            {
                try
                {
                    date = LocalDate.parse(args[i + 1]);
                }
                catch (Exception e)
                {
                    System.out.println("Invalid date. Restart and enter YYYY-MM-DD.");
                    return;
                }
                break;
            }
        }


        List<ElpriserAPI.Elpris> priser = getChargingWindowData(date, zone, elpriserAPI);

        if (priser.isEmpty())
        {
            System.out.println("No data available for " + date + " in zone " + zone);
            return;
        }


        boolean sorted = false;
        for (String arg : args) {
            if (arg.equalsIgnoreCase("--sorted")) {
                sorted = true;
                break;
            }
        }

        if (sorted)
        {
            List<ElpriserAPI.Elpris> singleDay = elpriserAPI.getPriser(date, zone);
            printSortedPrices(singleDay, date);
        }
        else
        {
            System.out.println("\nRetrieved " + priser.size()/2 + " hourly prices for " + zone + " on " + date + "\n");
            for (ElpriserAPI.Elpris p : priser)
            {
                if (!p.timeStart().toLocalDate().equals(date)) continue;

                int start = p.timeStart().getHour();
                int end = p.timeEnd().getHour();
                double pris = p.sekPerKWh() * 100;
                System.out.printf("  %02d-%02d    %5.2f öre%n", start, end, pris);
            }
        }



        for (ElpriserAPI.Elpris p : priser)
        {
            double v = p.sekPerKWh();
            if (v < minPrice) minPrice = v;
            if (v > maxPrice) maxPrice = v;
            sum += v;
            count++;
        }

        double meanPrice = sum / count;
        System.out.printf("\nHögsta pris: %,2f öre", maxPrice * 100);
        System.out.printf("\nLägsta pris: %,2f öre ", minPrice * 100);
        System.out.printf("\nMedelpris: %,2f öre", meanPrice * 100);


        System.out.println("\n");


        String ChargingDetect = "";
        for (int i = 0; i < args.length; i++)
        {
            if (args[i].equalsIgnoreCase("--charging"))
            {
                try
                {
                    ChargingDetect = args[i + 1];
                    break;
                }
                catch (Exception e)
                {
                    System.out.println("Invalid charging window. Restart and enter 2h, 4h or 8h.");
                    return;
                }
            }
            else
            {
                ChargingDetect = null;
            }
        }


        if (ChargingDetect == null)
        {
            System.out.println("No charging window specified. No action will be taken.");
            return;
        }
        switch (ChargingDetect) {
            case "2h" -> {
                int windowSize = 2;
                if (priser.size() < windowSize) {
                    System.out.println("För få timmar tillgängliga för laddningsfönster.");
                    return;
                }
                for (int i = 0; i < priser.size() - windowSize; i++) {
                    boolean isConsecutive = true;
                    for (int j = 0; j < windowSize - 1; j++) {
                        var currentEnd = priser.get(i + j).timeEnd();
                        var nextStart = priser.get(i + j + 1).timeStart();
                        if (!currentEnd.equals(nextStart)) {
                            isConsecutive = false;
                            break;
                        }
                    }

                    if (!isConsecutive) continue;

                    double summa = 0.0;
                    for (int j = 0; j < windowSize; j++) {
                        summa += priser.get(i + j).sekPerKWh();
                    }
                    if (summa < lowestSum) {
                        lowestSum = summa;
                        StartIndex = i;
                    }
                }
                if (StartIndex != -1) {
                    ElpriserAPI.Elpris p1 = priser.get(StartIndex);
                    ElpriserAPI.Elpris p2 = priser.get(StartIndex);

                    double totalPris = (p1.sekPerKWh() + p2.sekPerKWh()) * 100;
                    double meanPris = (lowestSum / windowSize) * 100.0;

                    System.out.print("\nBilligaste 2h laddningsfönster:\n");
                    System.out.printf("%02d-%02d och %02d-%02d\n", p1.timeStart().getHour(), p1.timeEnd().getHour(), p2.timeStart().getHour(), p2.timeEnd().getHour());
                    System.out.printf("Totalt: %,2f öre\n", totalPris);
                    System.out.printf("Medelpris: %,2f öre\n", meanPris);
                    System.out.printf("Påbörja laddning: %02d:%02d\n", p1.timeStart().getHour(), p1.timeStart().getMinute());
                }
            }
            case "4h" -> {
                for (int i = 0; i <= priser.size() - 4; i++) {
                    double summa = 0.0;
                    for (int j = 0; j < 4; j++) {
                        summa += priser.get(i + j).sekPerKWh();
                    }
                    if (summa < lowestSum) {
                        lowestSum = summa;
                        StartIndex = i;
                    }
                }
                if (StartIndex != -1) {
                    ElpriserAPI.Elpris p1 = priser.get(StartIndex);
                    ElpriserAPI.Elpris p4 = priser.get(StartIndex + 3);

                    double totalPris = (p1.sekPerKWh() + p4.sekPerKWh()) * 100;
                    double meanPris = (lowestSum / 4.0) * 100.0;

                    System.out.print("\nBilligaste 4h laddningsfönster:\n");
                    System.out.printf("%02d-%02d och %02d-%02d\n", p1.timeStart().getHour(), p1.timeEnd().getHour(), p4.timeStart().getHour(), p4.timeEnd().getHour());
                    System.out.printf("Totalt: %,2f öre\n", totalPris);
                    System.out.printf("Medelpris: %,2f öre\n", meanPris);
                    System.out.printf("Påbörja laddning: %02d\n", p1.timeStart().getHour());
                }
            }
            case "8h" -> {
                for (int i = 0; i <= priser.size() - 8; i++) {
                    double summa = 0.0;
                    for (int j = 0; j < 8; j++) {
                        summa += priser.get(i + j).sekPerKWh();
                    }
                    if (summa < lowestSum) {
                        lowestSum = summa;
                        StartIndex = i;
                    }
                }
                if (StartIndex != -1) {
                    ElpriserAPI.Elpris p1 = priser.get(StartIndex);
                    ElpriserAPI.Elpris p8 = priser.get(StartIndex + 7);

                    double totalPris = (p1.sekPerKWh() + p8.sekPerKWh()) * 100;
                    double meanPris = (lowestSum / 8.0) * 100.0;

                    System.out.print("\nBilligaste 8h laddningsfönster:\n");
                    System.out.printf("%02d-%02d och %02d-%02d\n", p1.timeStart().getHour(), p1.timeEnd().getHour(), p8.timeStart().getHour(), p8.timeEnd().getHour());
                    System.out.printf("Totalt: %,2f öre\n", totalPris);
                    System.out.printf("Medelpris för fönster: %,2f öre\n", meanPris);
                    System.out.printf("Påbörja laddning: kl %02d:%02d\n", p1.timeStart().getHour(), p1.timeStart().getMinute());
                }
            }
            default -> System.out.println("Invalid charging window. Please choose between 2h, 4h or 8h.");
        }
    }


    private static void printSortedPrices(List<ElpriserAPI.Elpris> priser, LocalDate date) {
        System.out.println("\nRetrieved " + priser.size() + " hourly prices and sorted them." + "\n");

        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("0.00", symbols);

        record Item(int start, int end, double price) {}

        List<Item> items = new ArrayList<>();
        for (ElpriserAPI.Elpris p : priser)
        {
            items.add(new Item(p.timeStart().getHour(), p.timeEnd().getHour(), p.sekPerKWh() * 100.0));
        }

        items.sort(Comparator.comparingDouble((Item it) -> it.price).thenComparingInt(it -> it.start));

        for (Item it : items)
        {
            String hourRange = String.format("%02d-%02d", it.start, it.end);
            System.out.println(hourRange + " " + df.format(it.price) + " öre");
        }
    }


        private static void printHelp()
    {
        System.out.println("\n" + "  --zone SE1|SE2|SE3|SE4  --  select price zone  --  (required)");
        System.out.println("  --date YYYY-MM-DD  --  select date (defaults to today)  --  (optional)");
        System.out.println("  --sorted  --  display prices in ascending order  --  (optional)");
        System.out.println("  --charging 2h|4h|8h  --  find lowest cost charging window  --  (optional)");
        System.out.println("  --help  --  show this usage information  --  (optional)");
        System.out.println("Zones available: SE1, SE2, SE3, SE4");
    }

    private static boolean containsHelp(String[] args)
    {
        for (String a : args)
        {
            if (a.equalsIgnoreCase("--help"))
            {
                return true;
            }
        }
        return false;
    }

    private static List<ElpriserAPI.Elpris> getChargingWindowData(LocalDate date, ElpriserAPI.Prisklass zone, ElpriserAPI api) {
        List<ElpriserAPI.Elpris> all = new ArrayList<>();
        all.addAll(api.getPriser(date, zone));
        all.addAll(api.getPriser(date.plusDays(1), zone));
        return all;
    }

}


//Check the decimal formating for all methods. Check and remove comments, try and clean up the code.
//Make sure it feels like "me"
//Upload it and see if it clears tests on GitHub.

//DecimalFormatSymbols symbols = new DecimalFormatSymbols();
//    symbols.setDecimalSeparator(',');
//DecimalFormat df = new DecimalFormat("0.0", symbols);
