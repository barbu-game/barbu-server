package com.barbu.rami.meld;

import com.barbu.rami.card.RamiCard;
import java.util.List;

/** Card order is meaningful: in a run it fixes which card a joker stands for. */
public record Meld(int id, int owner, MeldKind kind, List<RamiCard> cards) {}
