package org.identigon.alterego;

import java.util.List;
import java.util.Map;

/** The postcode-area tags of every town in {@code dictionaries/GB/towns.txt}, for coherence tests. */
final class GbTownAreas {

  private GbTownAreas() {}

  static final Map<String, List<String>> BY_TOWN =
      Map.ofEntries(
          Map.entry("Belfast", List.of("BT")),
          Map.entry("Birmingham", List.of("B")),
          Map.entry("Bradford", List.of("BD")),
          Map.entry("Brighton and Hove", List.of("BN")),
          Map.entry("Bristol", List.of("BS")),
          Map.entry("Cardiff", List.of("CF")),
          Map.entry("Coventry", List.of("CV")),
          Map.entry("Derby", List.of("DE")),
          Map.entry("Edinburgh", List.of("EH")),
          Map.entry("Glasgow", List.of("G")),
          Map.entry("Kingston upon Hull", List.of("HU")),
          Map.entry("Leeds", List.of("LS")),
          Map.entry("Leicester", List.of("LE")),
          Map.entry("Liverpool", List.of("L")),
          Map.entry("London", List.of("E", "EC", "N", "NW", "SE", "SW", "W", "WC")),
          Map.entry("Manchester", List.of("M")),
          Map.entry("Newcastle upon Tyne", List.of("NE")),
          Map.entry("Nottingham", List.of("NG")),
          Map.entry("Plymouth", List.of("PL")),
          Map.entry("Sheffield", List.of("S")));
}
