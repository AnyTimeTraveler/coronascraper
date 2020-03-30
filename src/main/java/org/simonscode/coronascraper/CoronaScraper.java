package org.simonscode.coronascraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class CoronaScraper {

    static DateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    static DateFormat outputFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    public static void main(String[] args) throws IOException, ParseException {
        if (args.length < 1) {
            System.out.println("Usage: [files...]");
            return;
        }

        Map<Date, Map<String, Integer>> cases = new HashMap<>();
        Map<Date, Map<String, Integer>> recoveries = new HashMap<>();

        for (String arg : args) {
            File file = new File(arg);
            Document doc = Jsoup.parse(file, "UTF-8");
            Elements select = doc.select(".content__textbox > table:nth-child(7)");
            Element table = select.first();
            Date date = inputFormat.parse(file.getName().substring(0, file.getName().lastIndexOf('+')).replace('T', ' '));

            for (Element row : table.child(0).children()) {
                try {
                    switch (row.childrenSize()) {
                        case 3:
                            recoveries.putIfAbsent(date, new HashMap<>());
                            Map<String, Integer> cityRecoveries = recoveries.get(date);
                            cityRecoveries.put(row.child(0).text(), Integer.parseInt(row.child(2).text()));
                        case 2:
                            cases.putIfAbsent(date, new HashMap<>());
                            Map<String, Integer> cityCases = cases.get(date);
                            cityCases.put(row.child(0).text(), Integer.parseInt(row.child(1).text()));
                            break;
                        case 1:
                            System.out.println("Unknown: " + row.child(0));
                            break;
                    }
                } catch (NumberFormatException e) {
                    // ignored
                }
            }
        }

        renameTotal(cases);
        renameTotal(recoveries);

        Set<String> locations = new HashSet<>();

        // know all locations
        for (Map<String, Integer> d : cases.values()) {
            locations.addAll(d.keySet());
        }
        List<String> orderedLocations = new ArrayList<>(locations);
        orderedLocations.remove("Gesamt");

        dedup(cases, orderedLocations);
        dedup(recoveries, orderedLocations);

        printCSV("Cases", orderedLocations, cases);
        printCSV("Recoveries", orderedLocations, recoveries);
    }

    private static void renameTotal(Map<Date, Map<String, Integer>> map) {
        for (Map<String, Integer> date : map.values()) {
            Map<String, Integer> keys = new HashMap<>();
            for (String key : date.keySet()) {
                if (key.toLowerCase().contains("gesamt") || key.isBlank() || key.equals("Aktuelle Covid-19-Fälle")) {
                    keys.put(key, date.get(key));
//                    System.out.print("Eliminating :");
//                    System.out.println(key);
                }
            }
            for (String key : keys.keySet()) {
                date.remove(key);
            }
            date.put("Gesamt", keys.values().stream().mapToInt(Integer::intValue).sum());
        }
    }

    private static void dedup(Map<Date, Map<String, Integer>> data, List<String> orderedLocations) {
        List<Date> dates = data.keySet().stream().sorted().collect(Collectors.toList());

        List<Date> dublicates = new ArrayList<>();
        Date lastDay = dates.get(0);
        outer:
        for (int i = 1; i < dates.size(); i++) {
            Date currentDate = dates.get(i);
            Map<String, Integer> day = data.get(currentDate);
            for (String location : orderedLocations) {
                if (!Objects.equals(day.get(location), data.get(lastDay).get(location))) {
                    // different
                    lastDay = currentDate;
                    continue outer;
                }
            }
            // the same
            dublicates.add(currentDate);
        }

        for (Date dub : dublicates) {
            data.remove(dub);
        }
    }

    private static void printCSV(String name, List<String> orderedLocations, Map<Date, Map<String, Integer>> cases) {
        // header
        System.out.print(name);
        System.out.print(",Gesamt");
        for (String location : orderedLocations) {
            System.out.print(",");
            System.out.print(location);
        }
        System.out.println();

        // body
        for (Map.Entry<Date, Map<String, Integer>> entry : cases.entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(Collectors.toList())) {

            // date at the start
            System.out.print(outputFormat.format(entry.getKey()));

            System.out.print(',');
            System.out.print(entry.getValue().getOrDefault("Gesamt", 0));
            // count at each location
            for (String location : orderedLocations) {
                System.out.print(',');
                System.out.print(entry.getValue().getOrDefault(location, 0));
            }
            System.out.println();
        }
        System.out.println();
    }
}
