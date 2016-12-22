/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import com.springrts.ai.oo.clb.WeaponDef;
import com.springrts.ai.oo.clb.WeaponMount;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import zkcbai.Command;

/**
 *
 * @author User
 */
public class FakeSlasher extends FakeEnemy {

    private static Map<WeaponDef, List<UnitDef>> weaponUnitDefs;
    private WeaponDef identWeapon;

    public FakeSlasher(AIFloat3 pos, WeaponDef weapon, Command cmd, OOAICallback clbk) {
        super(getUnitDef(cmd, weapon), pos, cmd, clbk);
        cmd.debug("New fake " + getDef().getHumanName() + " created at " + pos);
        if (getDef().getCost(command.metal) > 3000){
            command.getAvengerHandler().requestScout(getArea());
        }
        this.identWeapon = weapon;
    }

    private static UnitDef getUnitDef(Command cmd, WeaponDef weapon) {
        if (weaponUnitDefs == null) {
            weaponUnitDefs = new HashMap();
            for (UnitDef ud : cmd.getCallback().getUnitDefs()) {
                for (WeaponMount wm : ud.getWeaponMounts()) {
                    if (wm.getWeaponDef().getName().toLowerCase().contains("fake")) {
                        continue;
                    }
                    if (wm.getWeaponDef().getName().toLowerCase().contains("noweapon")) {
                        continue;
                    }
                    if (!weaponUnitDefs.containsKey(wm.getWeaponDef())) {
                        weaponUnitDefs.put(wm.getWeaponDef(), new ArrayList());
                    }
                    weaponUnitDefs.get(wm.getWeaponDef()).add(ud);
                }
            }
        }
        UnitDef best = null;
        for (UnitDef ud : weaponUnitDefs.get(weapon)) {
            if (best == null || ud.getCost(cmd.metal) < best.getCost(cmd.metal)) {
                best = ud;
            }
        }
        if (best == null || ("corsilo dante armwin").contains(best.getName())) {
            best = cmd.getCallback().getUnitDefByName("cormist");
        }
        return best;
    }

    @Override
    public boolean isUnit(Unit u) {
        return u.getDef() != null && Command.getWeaponDefs(u.getDef()).contains(identWeapon);
    }

    @Override
    public int timeSinceLastSeen() {
        return 0;
    }

}
