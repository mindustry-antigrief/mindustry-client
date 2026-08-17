package mindustry.input;

import arc.struct.*;
import arc.input.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.type.*;

public class UnitKeybinds {
  public static final Seq<KeybindEntry> entries = new Seq<>(27);
  /** Mapping from unit ID to key */
  private static final Seq<String> unitKeys = new Seq<>(80);
  public static class KeybindEntry {
    public final KeyCode key;
    public final UnitType unit;
    public final @Nullable UnitType otherUnit;
    public KeybindEntry(KeyCode key, UnitType unit, @Nullable UnitType otherUnit){
      this.key = key;
      this.unit = unit;
      this.otherUnit = otherUnit;
    }
  }
  public static void add(KeyCode key, UnitType unit, @Nullable UnitType otherUnit){
    entries.add(new KeybindEntry(key, unit, otherUnit));
    unitKeys.set(unit.id, key.value.toLowerCase());
    if(otherUnit != null) unitKeys.set(otherUnit.id, key.value.toUpperCase());
  }
  public static @Nullable String getKey(UnitType u){
    if(u.id < unitKeys.size) return unitKeys.get(u.id);
    else return null;
  }
  static {
    unitKeys.setSize(80);
    add(KeyCode.a, UnitTypes.avert, UnitTypes.anthicus);
    add(KeyCode.b, UnitTypes.bryde, UnitTypes.spiroct);
    add(KeyCode.c, UnitTypes.corvus, UnitTypes.cleroi);
    add(KeyCode.d, UnitTypes.disrupt, UnitTypes.dagger);
    add(KeyCode.e, UnitTypes.eclipse, UnitTypes.elude);
    add(KeyCode.f, UnitTypes.flare, UnitTypes.fortress);
    add(KeyCode.g, UnitTypes.aegires, UnitTypes.gamma);
    add(KeyCode.h, UnitTypes.horizon, UnitTypes.alpha);
    add(KeyCode.i, UnitTypes.merui, UnitTypes.mono);
    add(KeyCode.j, UnitTypes.antumbra, UnitTypes.precept);
    add(KeyCode.k, UnitTypes.minke, UnitTypes.conquer);
    add(KeyCode.l, UnitTypes.locus, UnitTypes.collaris);
    add(KeyCode.m, UnitTypes.mega, UnitTypes.mace);
    add(KeyCode.n, UnitTypes.nova, UnitTypes.navanax);
    add(KeyCode.o, UnitTypes.obviate, UnitTypes.omura);
    add(KeyCode.p, UnitTypes.poly, UnitTypes.pulsar);
    add(KeyCode.q, UnitTypes.quasar, UnitTypes.quad);
    add(KeyCode.r, UnitTypes.reign, UnitTypes.risso);
    add(KeyCode.s, UnitTypes.sei, UnitTypes.stell);
    add(KeyCode.t, UnitTypes.toxopid, UnitTypes.tecta);
    add(KeyCode.u, UnitTypes.quell, UnitTypes.retusa);
    add(KeyCode.v, UnitTypes.vela, UnitTypes.vanquish);
    add(KeyCode.w, UnitTypes.crawler, UnitTypes.oct);
    add(KeyCode.x, UnitTypes.oxynoe, UnitTypes.atrax);
    add(KeyCode.y, UnitTypes.cyerce, UnitTypes.arkyid);
    add(KeyCode.z, UnitTypes.zenith, UnitTypes.scepter);
    add(KeyCode.semicolon, UnitTypes.beta, null);
  }
}