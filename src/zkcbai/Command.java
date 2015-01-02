/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.WeaponDef;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import zkcbai.unitHandlers.UnitFinishedListener;
import zkcbai.unitHandlers.CommanderHandler;
import zkcbai.unitHandlers.FactoryHandler;
import zkcbai.unitHandlers.units.AIUnit;

/**
 *
 * @author User
 */
public class Command implements AI {

    private final OOAICallback clbk;
    private final int ownTeamId;

    private List<UnitFinishedListener> unitFinishedListeners = new ArrayList();
    private List<CommanderHandler> comHandlers = new ArrayList();
    private FactoryHandler facHandler;
    private Map<Integer, AIUnit> units = new TreeMap();

    public Command(int teamId, OOAICallback callback) {
        this.clbk = callback;
        ownTeamId = teamId;
        facHandler = new FactoryHandler(this, callback);
    }

    public FactoryHandler getFactoryHandler() {
        return facHandler;
    }

    public void addUnitFinishedListener(UnitFinishedListener listener) {
        unitFinishedListeners.add(listener);
    }

    @Override
    public int luaMessage(String inData) {
        return 0;
    }

    @Override
    public int unitGiven(Unit unit, int oldTeamId, int newTeamId) {
        return 0;
    }

    @Override
    public int unitDamaged(Unit unit, Unit attacker, float damage, AIFloat3 dir, WeaponDef weaponDef, boolean paralyzer) {
        return 0;
    }

    @Override
    public int enemyEnterLOS(Unit enemy) {
        return 0;
    }

    @Override
    public int enemyLeaveLOS(Unit enemy) {
        return 0;
    }

    @Override
    public int enemyEnterRadar(Unit enemy) {
        return 0;
    }

    @Override
    public int enemyLeaveRadar(Unit enemy) {
        return 0;
    }

    @Override
    public int enemyDamaged(Unit enemy, Unit attacker, float damage, AIFloat3 dir, WeaponDef weaponDef, boolean paralyzer) {
        return 0;
    }

    @Override
    public int unitDestroyed(Unit unit, Unit attacker) {
        return 0;
    }

    @Override
    public int init(int teamId, OOAICallback callback) {
        return 0;
    }

    @Override
    public int unitMoveFailed(Unit unit) {
        units.get(unit.getUnitId()).pathFindingError();
        return 0;
    }

    @Override
    public int unitIdle(Unit unit) {
        units.get(unit.getUnitId()).idle();
        return 0;
    }

    @Override
    public int update(int frame) {
        return 0;
    }

    @Override
    public int unitCreated(Unit unit, Unit builder) {
        return 0;
    }

    @Override
    public int unitFinished(Unit unit) {
        try {
            switch (unit.getDef().getName()) {
                case "armcom1":
                    CommanderHandler comHandler = new CommanderHandler(this, clbk);
                    comHandlers.add(comHandler);
                    units.put(unit.getUnitId(), comHandler.addUnit(unit));
                    break;
                default:
                    debug("Unused UnitDef " + unit.getDef().getName() + " in UnitFinished");
            }
        } catch (Exception e) {
            debug("Exception in unitFinished: ", e);
        }
        return 0;

    }

    public void debug(String s, Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        debug(s + sw.toString());
    }

    public void debug(Integer s) {
        debug(s.toString());
    }

    public void debug(String s) {
        clbk.getGame().sendTextMessage(s, clbk.getGame().getMyTeam());
    }

}
