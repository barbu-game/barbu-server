package com.barbu.app.variant;

import com.barbu.engine.model.Contract;
import com.barbu.engine.model.ContractType;
import com.barbu.engine.variant.Variant;
import com.barbu.engine.variant.Variants;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller("/variants")
@Secured(SecurityRule.IS_ANONYMOUS)
public class VariantController {

    @Get
    public List<VariantDto> list(HttpRequest<?> request) {
        boolean fr =
                "fr".equalsIgnoreCase(request.getLocale().orElse(Locale.ENGLISH).getLanguage());
        List<VariantDto> out = new ArrayList<>();
        for (Variant v : Variants.all()) {
            List<ContractDto> contracts = new ArrayList<>();
            for (Contract c : v.contracts()) {
                contracts.add(new ContractDto(c.name(), title(c, fr), ruleText(v, c, fr)));
            }
            out.add(new VariantDto(v.id(), v.name(), description(v, fr), contracts));
        }
        return out;
    }

    private static String ruleText(Variant v, Contract c, boolean fr) {
        if (c.type() == ContractType.MONTANTE) {
            return fr
                    ? "Domino : videz votre main en premier ; classement à somme nulle selon l'ordre d'arrivée."
                    : "Domino: empty your hand first; zero-sum ranking by finishing order.";
        }
        String english = v.trickRules().get(c).describe();
        return fr ? translateRule(english) : english;
    }

    private static String description(Variant v, boolean fr) {
        if (!fr) {
            return v.description();
        }
        return switch (v.id()) {
            case "developer" ->
                "Le jeu original à cinq contrats : éviter les plis, les cœurs, les dames"
                        + " et les rois rouges, puis la montante.";
            case "classic" ->
                "Les sept contrats traditionnels : les plis, les cœurs, les dames,"
                        + " le Roi de cœur (le Barbu), les deux derniers plis, la montante et la salade.";
            case "quick" ->
                "Une table plus courte à cinq contrats : les plis, les cœurs, les dames,"
                        + " le Roi de cœur et la montante.";
            case "extended" -> "Le Barbu classique plus un contrat sans valets — huit contrats en tout.";
            default -> v.description();
        };
    }

    private static String title(Contract c, boolean fr) {
        if (!fr) {
            return switch (c) {
                case NO_TRICKS -> "No tricks";
                case NO_HEARTS -> "No hearts";
                case NO_QUEENS -> "No queens";
                case NO_RED_KINGS -> "No red kings";
                case NO_KING_OF_HEARTS -> "King of Hearts";
                case NO_JACKS -> "No jacks";
                case NO_LAST_TWO_TRICKS -> "Last two tricks";
                case SALADE -> "Salad";
                case MONTANTE -> "Montante";
            };
        }
        return switch (c) {
            case NO_TRICKS -> "Sans plis";
            case NO_HEARTS -> "Sans cœurs";
            case NO_QUEENS -> "Sans dames";
            case NO_RED_KINGS -> "Sans rois rouges";
            case NO_KING_OF_HEARTS -> "Roi de cœur";
            case NO_JACKS -> "Sans valets";
            case NO_LAST_TWO_TRICKS -> "Deux derniers plis";
            case SALADE -> "Salade";
            case MONTANTE -> "Montante";
        };
    }

    // Rule text is composed by the engine's scoring rules (English, with the ScoringConfig point
    // values interpolated). We translate by matching the engine's fixed sentence shapes rather than
    // the exact strings, so a tweaked ScoringConfig number still yields correct French. A combined
    // rule (the salad) joins its parts with "; " — translate each part on its own.
    private static final Map<String, String> FR_PLURAL =
            Map.of("tricks", "plis", "hearts", "cœurs", "queens", "dames", "red kings", "rois rouges");
    private static final Map<String, String> FR_SINGULAR = Map.of(
            "heart",
            "cœur",
            "queen",
            "dame",
            "King of Hearts",
            "Roi de cœur",
            "Jack",
            "valet",
            "red king",
            "roi rouge");
    private static final Pattern SPREAD =
            Pattern.compile("^(-?\\d+) spread over the (tricks|hearts|queens|red kings)$");
    private static final Pattern PER_TRICK = Pattern.compile("^(-?\\d+) per trick taken$");
    private static final Pattern LAST_TRICKS = Pattern.compile("^(-?\\d+) per trick among the last (\\d+)$");
    private static final Pattern PER_CARD =
            Pattern.compile("^(-?\\d+) per (heart|queen|King of Hearts|Jack|red king)$");

    private static String translateRule(String english) {
        String[] parts = english.split("; ");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = translateSegment(parts[i]);
        }
        return String.join(" ; ", parts);
    }

    private static String translateSegment(String s) {
        Matcher spread = SPREAD.matcher(s);
        if (spread.matches()) {
            return spread.group(1) + " répartis sur les " + FR_PLURAL.get(spread.group(2));
        }
        Matcher perTrick = PER_TRICK.matcher(s);
        if (perTrick.matches()) {
            return perTrick.group(1) + " par pli remporté";
        }
        Matcher last = LAST_TRICKS.matcher(s);
        if (last.matches()) {
            return last.group(1) + " par pli parmi les " + last.group(2) + " derniers";
        }
        Matcher per = PER_CARD.matcher(s);
        if (per.matches()) {
            return per.group(1) + " par " + FR_SINGULAR.get(per.group(2));
        }
        return s;
    }

    @Schema(name = "Variant")
    public record VariantDto(String id, String name, String description, List<ContractDto> contracts) {}

    @Schema(name = "VariantContract")
    public record ContractDto(String key, String title, String rule) {}
}
