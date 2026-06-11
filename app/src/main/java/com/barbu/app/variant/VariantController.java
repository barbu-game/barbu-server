package com.barbu.app.variant;

import com.barbu.engine.model.Contract;
import com.barbu.engine.model.ContractType;
import com.barbu.engine.variant.Variant;
import com.barbu.engine.variant.Variants;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

@Controller("/variants")
@Secured(SecurityRule.IS_ANONYMOUS)
public class VariantController {

    @Get
    public List<VariantDto> list() {
        List<VariantDto> out = new ArrayList<>();
        for (Variant v : Variants.all()) {
            List<ContractDto> contracts = new ArrayList<>();
            for (Contract c : v.contracts()) {
                contracts.add(new ContractDto(c.name(), title(c), ruleText(v, c)));
            }
            out.add(new VariantDto(v.id(), v.name(), v.description(), contracts));
        }
        return out;
    }

    private static String ruleText(Variant v, Contract c) {
        if (c.type() == ContractType.MONTANTE) {
            return "Domino: empty your hand first; zero-sum ranking by finishing order.";
        }
        return v.trickRules().get(c).describe();
    }

    private static String title(Contract c) {
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

    @Schema(name = "Variant")
    public record VariantDto(String id, String name, String description, List<ContractDto> contracts) {}

    @Schema(name = "VariantContract")
    public record ContractDto(String key, String title, String rule) {}
}
