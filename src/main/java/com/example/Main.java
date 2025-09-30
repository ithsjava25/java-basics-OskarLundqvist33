package com.example;

import com.example.api.ElpriserAPI;

import java.time.LocalDate;
import java.util.Arrays;
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
        int bestStartIndex = -1;


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


        List<ElpriserAPI.Elpris> priser = elpriserAPI.getPriser(date, zone);

        if (priser.isEmpty())
        {
            System.out.println("No data available for " + date + " in zone " + zone);
            return;
        }


        if (args.length > 1 && args[args.length - 1].equalsIgnoreCase("--sorted"))
        {
            printSortedPrices(priser);
        }
        else
        {
            System.out.println("\nRetrieved " + priser.size() + " hourly prices for " + zone + " on " + date + "\n");
            for (ElpriserAPI.Elpris p : priser)
            {
                int start = p.timeStart().getHour();
                int end = p.timeEnd().getHour();
                double ore = p.sekPerKWh() * 100;
                System.out.printf("  %02d-%02d    %5.2f öre%n", start, end, ore);
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
        System.out.printf("\nHögsta pris: %.2f öre", maxPrice * 100);
        System.out.printf("\nLägsta pris: %.2f öre ", minPrice * 100);
        System.out.printf("\nMedelpris: %.2f öre", meanPrice * 100);


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
        }
        else if (ChargingDetect.equals("2h"))
        {
            for (int i = 0; i < priser.size() - 1; i++)
            {
                double summa = priser.get(i).sekPerKWh() + priser.get(i + 1).sekPerKWh();
                if (summa < lowestSum)
                {
                    lowestSum = summa;
                    bestStartIndex = i;
                }
            }
            if (bestStartIndex != -1)
            {
                ElpriserAPI.Elpris p1 = priser.get(bestStartIndex);
                ElpriserAPI.Elpris p2 = priser.get(bestStartIndex + 1);

                double totalPris = (p1.sekPerKWh() + p2.sekPerKWh()) * 100;

                System.out.print("\nBilligaste 2h laddningsfönster:\n");
                System.out.printf("%02d-%02d och %02d-%02d\n", p1.timeStart().getHour(), p1.timeEnd().getHour(), p2.timeStart().getHour(), p2.timeEnd().getHour());
                System.out.printf("Totalt: %.2f öre\n", totalPris);
                System.out.printf("Medelpris: %.2f öre\n", totalPris / 2.0);
                System.out.printf("Påbörja laddning: %02d\n", p1.timeStart().getHour());
            }
        }

        else if (ChargingDetect.equals("4h"))
        {
            System.out.println("4HOURs");
        }

        else if (ChargingDetect.equals("8h"))
        {
            System.out.println("8HOURS");
        }

        else
        {
            System.out.println("Invalid charging window. Please choose between 2h, 4h or 8h.");
        }
    }


    private static void printSortedPrices(List<ElpriserAPI.Elpris> priser) {
        System.out.println("\nRetrieved " + priser.size() + " hourly prices and sorted them." + "\n");

        java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols();
        symbols.setDecimalSeparator(',');
        java.text.DecimalFormat df = new java.text.DecimalFormat("0.00", symbols);

        // Helper item to sort by price then by start hour
        record Item(int start, int end, double price)
        {
        }

        java.util.List<Item> items = new java.util.ArrayList<>();
        for (ElpriserAPI.Elpris p : priser)
        {
            items.add(new Item(p.timeStart().getHour(), p.timeEnd().getHour(), p.sekPerKWh() * 100.0));
        }

        items.sort(java.util.Comparator.comparingDouble((Item it) -> it.price).thenComparingInt(it -> it.start));

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
}