/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import com.springrts.ai.oo.clb.WeaponDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author User
 */
public class ZKCBAI extends com.springrts.ai.oo.AbstractOOAI {

    final static private int START_FRAME = 0;
    
    AI ai;
    OOAICallback clbk;
    int teamId;
    Unit com;
    String mexdata;

    @Override
    public int init(int teamId, OOAICallback callback) {
        clbk = callback;
        List<UnitDef> unitdefs = callback.getUnitDefs();
        List<String> commanderNames = new ArrayList<String>();

        for (UnitDef ud : unitdefs) {
            Map<String, String> customParams = ud.getCustomParams();
            String level = customParams.get("level");
            if (level != null) {
                if (Integer.parseInt(level) == 0 && ud.getTooltip().contains("Support")) {
                    commanderNames.add(ud.getName());
                }
            }
        }

        int index = (int) Math.floor(Math.random() * commanderNames.size());
        String name = commanderNames.get(index);
        callback.getLua().callRules("ai_commander:" + name, -1);
        clbk.getGame().sendStartPosition(true, new AIFloat3(2400, 0, 1100));
        this.teamId = teamId;
        if (START_FRAME == 0) {
            ai = new Command(teamId, callback);
        }
        return 0;
    }

    @Override
    public int luaMessage(String inData) {
        mexdata = inData;
        ai.luaMessage(inData);
        return 0;
    }

    @Override
    public int unitGiven(Unit unit, int oldTeamId, int newTeamId) {
        ai.unitGiven(unit, oldTeamId, newTeamId);
        return 0;
    }

    @Override
    public int unitDamaged(Unit unit, Unit attacker, float damage, AIFloat3 dir, WeaponDef weaponDef, boolean paralyzer) {
        ai.unitDamaged(unit, attacker, damage, dir, weaponDef, paralyzer);
        return 0;
    }

    @Override
    public int enemyEnterLOS(Unit enemy) {
        ai.enemyEnterLOS(enemy);
        return 0;
    }

    @Override
    public int enemyLeaveLOS(Unit enemy) {
        ai.enemyLeaveLOS(enemy);
        return 0;
    }

    @Override
    public int enemyEnterRadar(Unit enemy) {
        ai.enemyEnterRadar(enemy);
        return 0;
    }

    @Override
    public int enemyLeaveRadar(Unit enemy) {
        ai.enemyLeaveRadar(enemy);
        return 0;
    }

    @Override
    public int enemyDamaged(Unit enemy, Unit attacker, float damage, AIFloat3 dir, WeaponDef weaponDef, boolean paralyzer) {
        ai.enemyDamaged(enemy, attacker, damage, dir, weaponDef, paralyzer);
        return 0;
    }

    @Override
    public int unitDestroyed(Unit unit, Unit attacker) {
        ai.unitDestroyed(unit, attacker);
        return 0;
    }

    @Override
    public int unitMoveFailed(Unit unit) {
        ai.unitMoveFailed(unit);
        return 0;
    }

    @Override
    public int unitIdle(Unit unit) {
        ai.unitIdle(unit);
        return 0;
    }

    @Override
    public int update(int frame) {
        if (frame >= START_FRAME && ai == null) {
            ai = new Command(teamId, clbk);
            ai.luaMessage(mexdata);
            ai.unitFinished(com);
        }
        ai.update(frame - START_FRAME);
        return 0;
    }

    @Override
    public int unitCreated(Unit unit, Unit builder) {
        ai.unitCreated(unit, builder);
        return 0;
    }

    @Override
    public int unitFinished(Unit unit) {
        if (unit.getDef().getCustomParams().containsKey("commtype")) {
            com = unit;
        }
        ai.unitFinished(unit);
        return 0;
    }

}
