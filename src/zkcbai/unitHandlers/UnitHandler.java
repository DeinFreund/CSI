/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import java.util.Map;
import java.util.TreeMap;
import zkcbai.Command;
import zkcbai.UnitDestroyedListener;
import zkcbai.helpers.AreaZoneChangeListener;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.AIUnitHandler;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.TaskIssuer;

/**
 *
 * @author User
 */
public abstract class UnitHandler implements TaskIssuer, UnitDestroyedListener, AIUnitHandler {

    Map<Integer, AIUnit> aiunits = new TreeMap();
    Command command;
    OOAICallback clbk;

    public UnitHandler(Command cmd, OOAICallback clbk) {
        command = cmd;
        this.clbk = clbk;
        if (cmd != null) {
            cmd.addUnitDestroyedListener(this);
        }
    }

    public abstract AIUnit addUnit(Unit u);
    
    public void addUnit(AIUnit au){
        aiunits.put(au.hashCode(), au);
        au.assignAIUnitHandler(this);
    }

    public abstract void removeUnit(AIUnit u);

    @Override
    public Command getCommand() {
        return command;

    }

    @Override
    public void unitDestroyed(AIUnit u, Enemy e) {
        removeUnit(u);
    }

    public abstract void troopIdle(AIUnit u);

    public abstract void troopIdle(AISquad s);

    @Override
    public void troopIdle(AITroop u) {
        if (u == null){
            throw new RuntimeException("AITroop is null!");
        }
        if (u instanceof AIUnit) {
            troopIdle((AIUnit) u);
        } else if (u instanceof AISquad) {
            troopIdle((AISquad) u);
        } else {
            throw new RuntimeException("This should never be executed unless there are implementations of AITroop other than AISquad and AIUnit");
        }

    }

}
