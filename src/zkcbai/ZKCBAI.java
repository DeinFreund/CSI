/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Resource;
import com.springrts.ai.oo.clb.Team;
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
    int teamToGive = -1;
    Unit com;
    String mexdata;
    boolean slave = false;

    @Override
    public int init(int teamId, OOAICallback callback) {
        /*
        int first = -1;
        for (int i = 0; i < callback.getGame().getTeams(); i++) {
            
            if (callback.getGame().getTeamAllyTeam(i) == callback.getGame().getMyAllyTeam() && first < 0) {
                first = i;
            }

            if (callback.getGame().getTeamAllyTeam(i) == callback.getGame().getMyAllyTeam() && i > callback.getGame().getMyTeam()) {
                teamToGive = i;
                break;
            }
        }
        if (teamToGive < 0) {
            teamToGive = first;
        }*/
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
        this.teamId = teamId;
        int startbox = -1;
        int myIndex = 0;
        clbk.getGame().sendTextMessage("my team id1 is " + teamId, 0);
        clbk.getGame().sendTextMessage("my team id2 is " + clbk.getGame().getMyTeam(), 0);
        clbk.getGame().sendTextMessage("my team id3 is " + clbk.getGame().getMyAllyTeam(), 0);
        for (Team t : callback.getAllyTeams()) {
            if (t.getTeamId() < clbk.getGame().getMyTeam()) {
                if (myIndex == 0){
                    teamToGive = t.getTeamId();
                }
                myIndex++;
            }
            clbk.getGame().sendTextMessage("ally: " + t.getTeamId(), 0);
            if (t.getTeamId() == clbk.getGame().getMyTeam() || startbox < 0) {
                startbox = (int) Math.round(t.getRulesParamFloat("start_box_id", -1f));
            }
        }
        int startposes = (int) Math.round(clbk.getGame().getRulesParamFloat("startpos_n_" + startbox, 1));
        clbk.getGame().sendTextMessage("selecting startpos " + (myIndex % startposes) + " of " + startposes, 0);
        float startx = (clbk.getGame().getRulesParamFloat("startpos_x_" + startbox + "_" + (myIndex % startposes + 1), 0));
        float startz = (clbk.getGame().getRulesParamFloat("startpos_z_" + startbox + "_" + (myIndex % startposes + 1), 0));

        clbk.getGame().sendStartPosition(true, new AIFloat3(startx, 0, startz));

        if (myIndex > 0) {
            slave = true;
        }
        if (START_FRAME == 0 && !slave) {
            ai = new Command(teamId, callback);
        }

        return 0;
    }

    @Override
    public int luaMessage(String inData) {
        if (slave) {
            return 0;
        }
        mexdata = inData;
        ai.luaMessage(inData);
        return 0;
    }

    @Override
    public int unitGiven(Unit unit, int oldTeamId, int newTeamId) {
        if (slave) {
            List<Unit> l = new ArrayList();
            l.add(unit);
            clbk.getEconomy().sendUnits(l, teamToGive);
            return 0;
        }
        ai.unitGiven(unit, oldTeamId, newTeamId);
        return 0;
    }

    @Override
    public int unitDamaged(Unit unit, Unit attacker, float damage, AIFloat3 dir, WeaponDef weaponDef, boolean paralyzer) {
        if (slave) {
            return 0;
        }
        ai.unitDamaged(unit, attacker, damage, dir, weaponDef, paralyzer);
        return 0;
    }

    @Override
    public int enemyEnterLOS(Unit enemy) {
        if (slave) {
            return 0;
        }
        ai.enemyEnterLOS(enemy);
        return 0;
    }

    @Override
    public int enemyLeaveLOS(Unit enemy) {
        if (slave) {
            return 0;
        }
        ai.enemyLeaveLOS(enemy);
        return 0;
    }

    @Override
    public int enemyEnterRadar(Unit enemy) {
        if (slave) {
            return 0;
        }
        ai.enemyEnterRadar(enemy);
        return 0;
    }

    @Override
    public int enemyLeaveRadar(Unit enemy) {
        if (slave) {
            return 0;
        }
        ai.enemyLeaveRadar(enemy);
        return 0;
    }

    @Override
    public int enemyDamaged(Unit enemy, Unit attacker, float damage, AIFloat3 dir, WeaponDef weaponDef, boolean paralyzer) {
        if (slave) {
            return 0;
        }
        ai.enemyDamaged(enemy, attacker, damage, dir, weaponDef, paralyzer);
        return 0;
    }

    @Override
    public int unitDestroyed(Unit unit, Unit attacker) {
        if (slave) {
            return 0;
        }
        ai.unitDestroyed(unit, attacker);
        return 0;
    }

    @Override
    public int unitMoveFailed(Unit unit) {
        if (slave) {
            return 0;
        }
        ai.unitMoveFailed(unit);
        return 0;
    }

    @Override
    public int unitIdle(Unit unit) {
        if (slave) {
            return 0;
        }
        ai.unitIdle(unit);
        return 0;
    }

    @Override
    public int update(int frame) {
        if (slave) {
            if (frame % 15 == 3) {
                Resource metal = clbk.getResources().get(0);
                Resource energy = clbk.getResources().get(1);
                clbk.getEconomy().sendResource(metal, Math.min(clbk.getEconomy().getCurrent(metal), (float) Math.max(0, 0.9 * clbk.getGame().getTeamResourceStorage(teamToGive, 0) - clbk.getGame().getTeamResourceCurrent(teamToGive, 0))), teamToGive);
                //clbk.getEconomy().sendResource(energy, Math.min(clbk.getEconomy().getCurrent(energy), (float) Math.max(0, 0.2 * clbk.getGame().getTeamResourceStorage(teamToGive, 1) - clbk.getGame().getTeamResourceCurrent(teamToGive, 1))), teamToGive);

            }
            return 0;
        }
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
        if (slave) {
            return 0;
        }
        ai.unitCreated(unit, builder);
        return 0;
    }

    @Override
    public int unitFinished(Unit unit) {
        if (slave) {
            List<Unit> l = new ArrayList();
            l.add(unit);
            clbk.getEconomy().sendUnits(l, teamToGive);
            return 0;
        }
        if (unit.getDef().getCustomParams().containsKey("commtype")) {
            com = unit;
        }
        ai.unitFinished(unit);
        return 0;
    }

}
